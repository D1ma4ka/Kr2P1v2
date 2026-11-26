import type {LightMyRequestResponse} from 'fastify';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {AdvisorWeights} from '../src/nearby-finds/advisor.js';
import {styleVocabulary} from '../src/nearby-finds/styles.js';
import {fixtures} from './fixtures.js';
import {testDatabase} from './database.js';

const endpoint = '/v1/venues/nearby-finds';
const origin = {latitude:0,longitude:179.997,radiusKm:1};
const filter = {
 version:1,query:'НАХОДКА',families:['hoppy-ales'],styles:[],breweries:[],countries:['ru'],formats:['bottle'],
 tried:'NO',abvMin:'5,5',abvMax:'5.5',ibuMin:'30',ibuMax:'30',volumeMin:'330',volumeMax:'330',
 ratingMin:'',priceMin:'2.5',priceMax:'4',currency:'EUR',available:true,sort:'RATING',
};

test('nearby finds uses published availability, individual packages, owner history, filters and cursor scope', async () => {
 const db = await testDatabase();
 const app = buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
 try {
  await migrate(db.pool);
  const {a,b,position:can} = await fixtures(db.pool);
  const ipa = (await db.pool.query("INSERT INTO styles(name) VALUES('IPA') RETURNING id")).rows[0].id;
  await db.pool.query(`UPDATE beer_releases SET style_id=$2,name='Проверенная находка',abv=5.5,ibu=30
   WHERE id=(SELECT release_id FROM beer_positions WHERE id=$1)`,[can,ipa]);
  await db.pool.query("UPDATE breweries SET country='RU'");
  await db.pool.query("UPDATE beer_positions SET packaging='can',volume_ml=330 WHERE id=$1",[can]);
  const [bottle,unavailable,notOnMenu] = (await db.pool.query(`INSERT INTO beer_positions(release_id,source,status,packaging,volume_ml)
   SELECT release_id,'fixture','verified',format,330 FROM beer_positions CROSS JOIN (VALUES('bottle'),('keg'),('cask')) p(format)
   WHERE id=$1 RETURNING id`,[can])).rows.map(row => row.id as string);
  await db.pool.query("UPDATE users SET role='moderator' WHERE id=$1",[a.id]);

  async function venue(name: string,longitude: number,type = 'bar') {
   return (await db.pool.query(`INSERT INTO venues(name,type,city,source,status,latitude,longitude,coordinates_source,coordinates_verified_at)
    VALUES($1,$3,'Fixture city','test','verified',0,$2,'test',now()) RETURNING id`,[name,longitude,type])).rows[0].id as string;
  }
  const near = await venue('Near',179.997), across = await venue('Across',-179.997,'beer_shop');
  const stale = await venue('Stale',179.996), empty = await venue('Empty',179.995);
  const unpublished = await venue('Unpublished',179.994);
  const outside = await venue('Outside',179.987), brewery = await venue('Brewery',179.997,'brewery');

  async function call(url: string, token: string | null, payload: object, status = 200, method: 'POST' | 'PUT' = 'POST') {
   const res = await app.inject({method,url,headers:token ? {authorization:'Bearer '+token} : {},payload});
   assert.equal(res.statusCode,status,res.body);
   return res.json();
  }
  const search = (token: string, mode: string, params = {}, status = 200) =>
   call(endpoint,token,{...origin,mode,sort:'distance',...params},status);
  const detail = (token: string,id: string,mode: string,params = {}) =>
   call(`/v1/venues/${id}/nearby-finds`,token,{...origin,mode,...params});
  async function draft(id: string, ids: string[], publish = true) {
   const sectionId = randomUUID(), path = `/v1/venues/${id}/menu`;
   const items = ids.map((positionId,ordinal) => ({
    positionId,sectionId,ordinal,price:3.25,currency:'EUR',volumeMl:null,servingFormat:null,available:positionId !== unavailable,
   }));
   await call(path+'/draft',a.token,{expectedRevision:0,source:'Fixture',sections:[{id:sectionId,name:'Menu'}],items},200,'PUT');
   if (publish) await call(path+'/publish',a.token,{expectedRevision:1});
  }
  await draft(near,[can,bottle!,unavailable!]);
  await draft(across,[bottle!]);
  await draft(stale,[bottle!]);
  await draft(empty,[]);
  await draft(unpublished,[notOnMenu!],false);
  await draft(outside,[bottle!]);
  await draft(brewery,[bottle!]);
  await db.pool.query("UPDATE venue_menus SET updated_at=now()-interval '24 hours' WHERE venue_id=$1 AND state='published'",[stale]);
  await db.pool.query("UPDATE venue_menus SET updated_at=now()-interval '1 hour' WHERE venue_id=$1 AND state='published'",[near]);
  await db.pool.query(`INSERT INTO checkins(user_id,position_id,rating_quarters,body,occurred_at,hidden_at)
   VALUES($1,$2,20,'owner secret',now()-interval '2 years',NULL),
    ($1,$3,20,'hidden',now(),now()),($4,$3,20,'other secret',now(),NULL)`,[a.id,can,bottle,b.id]);
  await db.pool.query(`INSERT INTO checkins(user_id,position_id,rating_quarters,occurred_at)
   SELECT $1,$2,20,now()-s*interval '1 minute' FROM generate_series(1,102) s`,[a.id,notOnMenu]);
  await db.pool.query('INSERT INTO wishlist_items(user_id,position_id) VALUES($1,$2),($1,$3),($4,$5)',[a.id,bottle,notOnMenu,b.id,can]);

  await call(endpoint,null,{...origin,mode:'untried',sort:'distance'},401);
  await search(a.token,'wishlist',{userId:b.id},400);
  await search(a.token,'filter',{},422);
  await search(a.token,'filter',{filter:{...filter,abvMin:'6'}},422);
  const first = await search(a.token,'wishlist',{limit:1});
  assert.equal(first.historySize,100);
  assert.equal(first.items[0].venue.id,near);
  assert.equal(first.items[0].matches,1);
  assert.equal(first.items[0].wishlist,1);
  assert.deepEqual(first.totals,{current:2,stale:1,unpublished:1,empty:1,withMatches:2});
  const page = await search(a.token,'wishlist',{cursor:first.nextCursor});
  assert.equal(page.items[0].venue.id,across);
  assert.equal(page.items[0].venue.distanceKm.toFixed(2),'0.67');
  assert.deepEqual(Object.fromEntries(page.items.slice(1).map((row: {venue: {id: string;status: string}}) =>
   [row.venue.id,row.venue.status])),{[stale]:'stale',[empty]:'empty',[unpublished]:'unpublished'});
  await search(b.token,'wishlist',{cursor:first.nextCursor},400);
  await search(a.token,'wishlist',{cursor:first.nextCursor,radiusKm:3},400);
  assert.equal((await search(a.token,'wishlist',{radiusKm:3})).totals.withMatches,3);
  const boundary = 179.987 + 180 / Math.PI / 6371.0088;
  const includesOutside = (body: {items: {venue:{id:string}}[]}) => body.items.some(row => row.venue.id === outside);
  assert.ok(includesOutside(await search(a.token,'wishlist',{longitude:boundary - 0.00000001})));
  assert.ok(!includesOutside(await search(a.token,'wishlist',{longitude:boundary + 0.00000001})));
  for (const radiusKm of [5,10,25,50]) assert.equal((await search(a.token,'wishlist',{radiusKm})).totals.withMatches,3);
  await search(a.token,'wishlist',{radiusKm:2},400);
  await call(`/v1/venues/${outside}/nearby-finds`,a.token,{...origin,mode:'wishlist'},404);

  const hits = await detail(a.token,near,'untried');
  assert.deepEqual(hits.matches,[{positionId:bottle,wished:true,reasons:['wishlist','untried','favorite']}]);
  assert.equal(hits.menu.items[0].name,'Проверенная находка');
  assert.equal(hits.menu.items[0].price,3.25);
  assert.equal(hits.menu.items[0].tried,false);
  assert.equal(hits.menu.items[0].averageRating,null);
  assert.equal((await detail(a.token,stale,'wishlist')).menu,null);
  assert.equal((await detail(a.token,unpublished,'wishlist')).menu,null);
  assert.equal((await detail(a.token,empty,'wishlist')).summary.venue.status,'empty');
  assert.deepEqual((await detail(b.token,near,'wishlist')).matches.map((match: {positionId: string}) => match.positionId),[can]);
  assert.equal((await detail(b.token,near,'wishlist')).menu.items[0].averageRating,null);
  assert.equal((await search(b.token,'recommended')).personalized,false);
  assert.equal((await detail(b.token,near,'recommended')).matches.every((match: {reasons: string[]}) =>
   match.reasons.includes('neutral_advisor')),true);
  assert.equal((await search(a.token,'favorite')).items[0].matches,2);
  assert.equal((await search(a.token,'recommended')).items[0].matches,2);
  assert.deepEqual((await detail(a.token,near,'filter',{filter})).menu.items.map((row: {positionId: string}) => row.positionId),[bottle]);
  assert.equal((await search(a.token,'filter',{filter:{...filter,volumeMin:'331',volumeMax:'400'}})).totals.withMatches,0);
  assert.equal((await search(a.token,'filter',{filter:{...filter,ratingMin:'4'}})).totals.withMatches,0);
  assert.equal((await search(a.token,'filter',{filter:{...filter,query:'unmatched'}})).totals.withMatches,0);
  assert.equal((await search(a.token,'filter',{filter:{...filter,priceMin:'4',priceMax:'5'}})).totals.withMatches,0);
  assert.equal((await search(a.token,'recommended',{sort:'matches'})).items[0].venue.id,near);
  assert.equal((await search(a.token,'recommended',{sort:'relevance'})).items[0].venue.id,near);
  assert.equal((await search(a.token,'wishlist',{sort:'freshness'})).items[0].venue.id,across);
  assert.equal((await search(b.token,'recommended',{sort:'untried'})).items[0].venue.id,near);
  assert.equal((await search(b.token,'recommended',{sort:'wishlist'})).items[0].venue.id,near);

  await db.pool.query("UPDATE beer_positions SET status='rejected' WHERE id=$1",[bottle]);
  assert.equal((await detail(a.token,across,'wishlist')).summary.venue.status,'empty');
  assert.equal((await search(a.token,'wishlist')).totals.withMatches,0);
 } finally {
  await app.close();
  await db.close();
 }
});

test('nearby finds paginates a dense area without sending offer rows in summaries', async t => {
 const db = await testDatabase();
 const app = buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
 try {
  await migrate(db.pool);
  const {a,position} = await fixtures(db.pool);
  await db.pool.query(`INSERT INTO beer_positions(release_id,source,status)
    SELECT release_id,'fixture','verified' FROM beer_positions,generate_series(1,74) WHERE id=$1`,[position]);
  await db.pool.query(`INSERT INTO venues(name,type,city,source,status,latitude,longitude,coordinates_source,coordinates_verified_at)
   SELECT 'Fixture '||n,'bar','Fixture city','test','verified',0,179.997,'test',now() FROM generate_series(1,1200) n`);
  await db.pool.query("INSERT INTO venue_menus(venue_id,state,revision,source) SELECT id,'published',1,'fixture' FROM venues");
  await db.pool.query("INSERT INTO venue_menu_sections SELECT id,'published',gen_random_uuid(),'fixture',0 FROM venues");
  await db.pool.query(`INSERT INTO venue_menu_items(venue_id,state,section_id,position_id,ordinal,available)
   SELECT s.venue_id,'published',s.id,p.id,0,true FROM venue_menu_sections s CROSS JOIN beer_positions p`);
  let cursor: string | null = null;
  const seen = new Set<string>();
  for (let page = 0; page < 2; page++) {
   const start = performance.now();
   const response: LightMyRequestResponse = await app.inject({method:'POST',url:endpoint,headers:{authorization:'Bearer '+a.token},
    payload:{...origin,mode:'untried',sort:'relevance',limit:50,...(cursor ? {cursor} : {})}});
   assert.equal(response.statusCode,200,response.body);
   const body: {
    totals: {withMatches: number}; historySize: number;
    items: {venue:{id:string};matches:number}[]; nextCursor: string | null;
   } = response.json();
   t.diagnostic(`1200 venues × 75 offers, page ${page+1}: ${Math.round(performance.now()-start)} ms, ${Buffer.byteLength(response.body)} bytes`);
   assert.equal(body.totals.withMatches,1200);
   assert.equal(body.historySize,0);
   assert.equal(body.items.length,50);
   for (const row of body.items) {
    assert.equal(row.matches,75);
    assert.equal('offers' in row,false);
    assert.equal(seen.has(row.venue.id),false);
    seen.add(row.venue.id);
   }
   assert.ok(response.body.length < 30_000);
   cursor = body.nextCursor;
   assert.ok(cursor);
  }
 } finally {
  await app.close();
  await db.close();
 }
});

test('server uses the same Advisor weights and exact aliases as Android', async () => {
 const src = '../android/app/src/main/java/ru/krapiva/app/';
 const adviser = await readFile(src+'offline/Advisor.kt','utf8');
 for (const [key,value] of Object.entries(AdvisorWeights)) {
  const expression = typeof value === 'number' ? `const val ${key} = ${value}` : `val ${key} = listOf(${value.join(', ')})`;
  assert.ok(adviser.split('\n').some(line => line.trim() === expression),expression);
 }
 const menuStyles = await readFile(src+'menu/MenuStyles.kt','utf8');
 const families = Object.fromEntries([...menuStyles.matchAll(/const val (\w+) = "([^"]+)"/g)].map(match => match.slice(1)));
 const vocabulary = [...menuStyles.matchAll(/style\("([^"]+)", (\w+), (.+)\),/g)]
  .map(match => [match[1],families[match[2]!],JSON.parse('['+match[3]+']')]);
 assert.deepEqual(styleVocabulary,vocabulary);
});

test('new releases require verified dates, expand for sparse menus, and use release-wide checkin identity', async () => {
 const db = await testDatabase();
 const app = buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
 try {
  await migrate(db.pool);
  const {a,b,position:historyPosition} = await fixtures(db.pool);
  await db.pool.query("UPDATE users SET role='moderator' WHERE id=$1",[a.id]);
  const {rows:[brewery]} = await db.pool.query<{id: string}>('SELECT id FROM breweries');
  const venueIds = (await db.pool.query<{id: string}>(`
   INSERT INTO venues(name,type,city,source,status,latitude,longitude,coordinates_source,coordinates_verified_at)
   SELECT name,'bar','Fixture city','test','verified',0,179.997,'test',now()
   FROM (VALUES ('Recent visit'),('Long absence'),('Sparse menu'),('Only old beers')) v(name)
   RETURNING id
  `)).rows.map(row => row.id);
  const [recent,away,sparse,oldOnly] = venueIds as [string,string,string,string];
  const day = (age: number) => new Date(Date.now()-age*86_400_000).toISOString().slice(0,10);
  async function release(name: string) {
   const {rows:[row]} = await db.pool.query<{releaseId:string; positionId:string}>(`
    WITH release AS (INSERT INTO beer_releases(brewery_id,name) VALUES($1,$2) RETURNING id)
    INSERT INTO beer_positions(release_id,source,status,packaging)
    SELECT id,'fixture','verified','can' FROM release RETURNING id AS "positionId",release_id AS "releaseId"
   `,[brewery!.id,name]);
   return row!;
  }
  const request = (url: string,token: string,payload: object,method: 'POST' | 'PUT' = 'POST') =>
   app.inject({method,url,headers:{authorization:'Bearer '+token},payload});
  const sources = (id: string) => '/v1/moderation/beer-releases/'+id+'/release-date';
  async function verify(id: string,age: number) {
   const response = await request(sources(id),a.token,{releasedOn:day(age),source:'Publisher announcement'},'PUT');
   assert.equal(response.statusCode,200,response.body);
   assert.deepEqual(response.json(),{releaseId:id,releasedOn:day(age),source:'Publisher announcement'});
  }

  const [fresh,second,third,longAgo,widened,old,already,unknown,unavailable] = await Promise.all([
   'Fresh','Second','Third','Long absence','Widened search','Old restock','Already tried','Unknown date','Unavailable',
  ].map(release));
  const ages = [[fresh,3],[second,7],[third,11],[longAgo,40],[widened,58],[old,120],[already,5],[unavailable,2]] as const;
  await Promise.all(ages.map(([item,age]) => verify(item!.releaseId,age)));
  const forbidden = await request(sources(unknown!.releaseId),b.token,{releasedOn:day(1),source:'Publisher'},'PUT');
  assert.equal(forbidden.statusCode,403,forbidden.body);
  const future = await request(sources(unknown!.releaseId),a.token,{releasedOn:day(-1),source:'Publisher'},'PUT');
  assert.equal(future.statusCode,422,future.body);
  await verify(fresh!.releaseId,3);
  assert.equal((await db.pool.query(
   "SELECT * FROM audit_events WHERE action='catalog.verify_release_date' AND entity_id=$1",[fresh!.releaseId],
  )).rowCount,1);

  const bottle = (await db.pool.query<{id: string}>(`
   INSERT INTO beer_positions(release_id,source,status,packaging) VALUES($1,'fixture','verified','bottle') RETURNING id
  `,[already!.releaseId])).rows[0]!.id;
  await db.pool.query(`
   INSERT INTO checkins(user_id,position_id,venue_id,rating_quarters,occurred_at)
   VALUES($1,$2,$3,4,now()-interval '7 days'),($1,$2,$4,4,now()-interval '46 days'),
    ($1,$5,NULL,4,now()-interval '8 days'),($6,$7,NULL,20,now()-interval '2 days')
  `,[a.id,historyPosition,recent,away,bottle,b.id,fresh!.positionId]);
  const ids = [fresh!,second!,third!,longAgo!,old!,already!,unknown!,unavailable!].map(row => row.positionId);
  const menus = [[recent,ids],[away,ids],[sparse,[widened!.positionId]],[oldOnly,[old!.positionId]] ] as const;
  for (const [venue,positions] of menus) {
   await db.pool.query("INSERT INTO venue_menus(venue_id,state,revision,source) VALUES($1,'published',1,'fixture')",[venue]);
   const section = randomUUID();
   await db.pool.query("INSERT INTO venue_menu_sections VALUES($1,'published',$2,'Fixture menu',0)",[venue,section]);
   await db.pool.query(`
    INSERT INTO venue_menu_items(venue_id,state,section_id,position_id,ordinal,available)
    SELECT $1,'published',$2,id,0,id<>$4 FROM unnest($3::uuid[]) AS id
   `,[venue,section,positions,unavailable!.positionId]);
  }
  const query = {...origin,mode:'new_releases'};
  const search = await request(endpoint,a.token,{...query,sort:'relevance'});
  assert.equal(search.statusCode,200,search.body);
  const results = search.json<{totals:{withMatches:number};items:{
   venue:{id:string};matches:number;newReleasePreview:{positionId:string;name:string}[];
  }[]}>();
  assert.equal(results.totals.withMatches,3);
  assert.deepEqual(Object.fromEntries(results.items.map(result => [result.venue.id,result.matches])),
   {[recent]:3,[away]:4,[sparse]:1});
  assert.deepEqual(results.items.find(row => row.venue.id === away)?.newReleasePreview.map(row => row.name),
   ['Fresh','Second','Third']);

  for (const [venue,expected] of [
   [recent,[fresh,second,third]], [away,[fresh,second,third,longAgo]], [sparse,[widened]], [oldOnly,[]],
  ] as const) {
   const detail = await request('/v1/venues/'+venue+'/nearby-finds',a.token,query);
   assert.equal(detail.statusCode,200,detail.body);
   const body = detail.json<{matches:{positionId:string;reasons:string[];releasedOn:string}[]}>();
   assert.deepEqual(body.matches.map(row => row.positionId),expected.map(row => row!.positionId));
   for (const match of body.matches) {
    assert.ok(match.reasons.includes('new_release'));
    assert.ok(match.reasons.includes('advisor'));
    assert.match(match.releasedOn,/^\d{4}-\d{2}-\d{2}$/);
   }
  }

  // The new mode only excludes the requesting owner's checkins. The
  // traditional package-level mode still includes an untried can.
  const other = await request('/v1/venues/'+recent+'/nearby-finds',b.token,query);
  assert.equal(other.statusCode,200,other.body);
  assert.ok(other.json<{matches:{positionId:string}[]}>().matches.some(row => row.positionId === already!.positionId));
  const oldMode = await request('/v1/venues/'+recent+'/nearby-finds',a.token,{...query,mode:'untried'});
  assert.equal(oldMode.statusCode,200,oldMode.body);
  assert.ok(oldMode.json<{matches:{positionId:string}[]}>().matches.some(row => row.positionId === already!.positionId));
 } finally {
  await app.close();
  await db.close();
 }
});
