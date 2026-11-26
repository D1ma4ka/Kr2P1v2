import assert from 'node:assert/strict';
import {test} from 'node:test';
import {randomUUID} from 'node:crypto';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import sharp from 'sharp';
import {testDatabase} from './database.js';
import {migrate} from '../src/db/migrate.js';
import {fixtures} from './fixtures.js';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {MediaService} from '../src/media/service.js';
import {FileStorage} from '../src/media/storage.js';
import {cp} from 'node:fs/promises';

async function context() {
 const db=await testDatabase();await migrate(db.pool);const f=await fixtures(db.pool);
 const dir=await mkdtemp(join(tmpdir(),'krapiva-p7-'));
 const media=new MediaService(db.pool,new FileStorage(dir));
 const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,media);
 const url=await app.listen({host:'127.0.0.1',port:0});
 async function call(path:string,method='GET',body?:unknown,token=f.a.token,status=200) {
  const response=await fetch(url+path,{method,headers:{authorization:`Bearer ${token}`,...(body===undefined?{}:{'content-type':'application/json'}),'idempotency-key':randomUUID()},...(body===undefined?{}:{body:JSON.stringify(body)})});
  const text=await response.text();assert.equal(response.status,status,`${method} ${path}`);return text?JSON.parse(text):null;
 }
 async function photo() {
  const bytes=await sharp({create:{width:16,height:16,channels:3,background:'#ffb63c'}}).png().toBuffer();
  const response=await fetch(url+'/v1/media',{method:'POST',headers:{authorization:`Bearer ${f.a.token}`,'content-type':'image/png'},body:new Uint8Array(bytes)});
  assert.equal(response.status,201);return (await response.json() as {id:string}).id;
 }
 return {db,f,dir,app,url,call,photo,async close(){await app.close();await db.close();await rm(dir,{recursive:true,force:true});}};
}
const venue=(name='Synthetic bar',extra:Record<string,unknown>={})=>({version:0,name,type:'bar',city:'Synthetic city',country:null,address:'Synthetic test address',source:'Isolated test fixture; not a real venue',latitude:55.75,longitude:37.61,coordinatesSource:'Synthetic coordinates, isolated test only',imageAssetId:null,imageSource:null,rightsConfirmed:false,testOnly:true,verified:true,...extra});

test('P7 real directory API: provenance, roles, bbox, antimeridian, filters/cursor, bounded map and production test exclusion',async()=>{
 const c=await context(),{db,f,call}=c;
 try {
  assert.deepEqual((await call('/v1/venues')).items,[]);
  await call('/v1/moderation/venues','GET',undefined,f.b.token,403);
  const id=randomUUID();await call('/v1/moderation/venues/'+id,'PUT',venue(),f.a.token,403);
  await db.pool.query("UPDATE users SET role='moderator' WHERE id=$1",[f.a.id]);
  await call('/v1/moderation/venues/'+id,'PUT',venue());
  assert.equal((await call('/v1/moderation/venues?status=verified')).items[0].id,id);
  assert.equal((await call('/v1/moderation/venues/'+id)).version,1);
  const shop=randomUUID();await call('/v1/moderation/venues/'+shop,'PUT',venue('Synthetic shop',{type:'beer_shop',longitude:37.612}));
  const noCoords=randomUUID();await call('/v1/moderation/venues/'+noCoords,'PUT',venue('Synthetic no coordinates',{latitude:null,longitude:null,coordinatesSource:null}));
  await call('/v1/moderation/venues/'+randomUUID(),'PUT',venue('Bad provenance',{coordinatesSource:null}),f.a.token,422);
  const maps=await call('/v1/venues/map?bbox=37.5,55.6,37.8,55.9');assert.equal(maps.items.length,2);assert.equal(maps.truncated,false);
  assert.equal((await call('/v1/venues?type=beer_shop')).items[0].id,shop);
  assert.equal((await call('/v1/venues?q=no%20coordinates')).items[0].id,noCoords);
  const first=await call('/v1/venues?limit=1');assert.ok(first.nextCursor);
  const second=await call('/v1/venues?limit=1&cursor='+first.nextCursor);assert.notEqual(first.items[0].id,second.items[0].id);
  await call('/v1/venues?type=bar&cursor='+first.nextCursor,'GET',undefined,f.a.token,400);
  for(const box of ['0,0,0,0','a,b,c,d','-181,-5,180,5','0,20,2,10'])await call('/v1/venues/map?bbox='+box,'GET',undefined,f.a.token,400);
  const dateline=randomUUID();await call('/v1/moderation/venues/'+dateline,'PUT',venue('Synthetic dateline',{longitude:-179,latitude:0}));
  assert.equal((await call('/v1/venues/map?bbox=170,-10,-170,10')).items[0].id,dateline);
  const near={latitude:55.75,longitude:37.61,radiusKm:1};
  const nearby=await call('/v1/venues/nearby','POST',near);
  assert.deepEqual(nearby.items.map((row:{id:string})=>row.id),[id,shop]);
  assert.ok(nearby.items[0].distanceKm<0.001);
  assert.ok(nearby.items[1].distanceKm>0.1&&nearby.items[1].distanceKm<1);
  assert.deepEqual((await call('/v1/venues/nearby','POST',{...near,limit:1})).truncated,true);
  assert.equal((await call('/v1/venues/nearby','POST',{...near,type:'beer_shop'})).items[0].id,shop);
  assert.equal((await call('/v1/venues/nearby','POST',{latitude:0,longitude:180,radiusKm:50})).items.length,0);
  const across=randomUUID();await call('/v1/moderation/venues/'+across,'PUT',venue('Synthetic across',{longitude:-179.997,latitude:0}));
  assert.equal((await call('/v1/venues/nearby','POST',{latitude:0,longitude:179.997,radiusKm:1})).items[0].id,across);
  assert.equal((await call('/v1/venues/nearby','POST',{latitude:0,longitude:-179.1,radiusKm:50})).items[0].id,dateline);
  await call('/v1/venues/nearby','POST',{...near,radiusKm:2},f.a.token,400);

  await db.pool.query(`INSERT INTO venues(name,type,city,address,source,latitude,longitude,coordinates_verified_at,coordinates_source,status,is_test)
   SELECT 'Synthetic density '||n,'bar','Synthetic city','Test','Isolated density fixture',55.751,37.611,now(),'Synthetic test coordinates','verified',true FROM generate_series(1,501)n`);
  const bounded=await call('/v1/venues/map?bbox=37.5,55.6,37.8,55.9');assert.equal(bounded.items.length,500);assert.equal(bounded.truncated,true);
  const production=buildApp(readConfig({NODE_ENV:'production',LOG_LEVEL:'silent'}),db.pool,undefined,{async send(){throw Error('No delivery');},close(){}});
  try {
   const headers={authorization:`Bearer ${f.a.token}`};const result=await production.inject({url:'/v1/venues',headers});assert.equal(result.statusCode,200);assert.deepEqual(result.json().items,[]);
   const attached=await production.inject({method:'POST',url:'/v1/checkins',headers:{...headers,'idempotency-key':randomUUID()},payload:{positionId:f.position,body:'Synthetic production rejection',venueId:id,ratingQuarters:null,occurredAt:'2026-11-10T10:00:00Z',visibility:'private'}});assert.equal(attached.statusCode,422);
  }finally{await production.close();}
 }finally{await c.close();}
});

test('P7 venue photo provenance/rights, shared approved edits, actual aggregates and hide/restore ACL',async()=>{
 const c=await context(),{db,f,call}=c;
 try {
  await db.pool.query("UPDATE users SET role='moderator' WHERE id=ANY($1::uuid[])",[[f.a.id,f.b.id]]);
  const photo=await c.photo(),id=randomUUID(),input=venue('Synthetic photo place',{testOnly:false,imageAssetId:photo,imageSource:'Isolated test image, generated by test',rightsConfirmed:true});
  await call('/v1/moderation/venues/'+id,'PUT',{...input,rightsConfirmed:false},f.a.token,422);
  await call('/v1/moderation/venues/'+id,'PUT',input);
  assert.equal((await call('/v1/moderation/venues/'+id,'PUT',{...input,version:1},f.b.token)).version,2);
  await call('/v1/moderation/venues/'+id,'PUT',{...input,version:1},f.b.token,409);
  assert.equal((await fetch(c.url+'/v1/media/'+photo,{headers:{authorization:`Bearer ${f.b.token}`}})).status,200);
  await call('/v1/checkins','POST',{positionId:f.position,body:'Synthetic private rating',ratingQuarters:17,occurredAt:'2026-11-10T10:00:00Z',visibility:'private',venueId:id},f.a.token,201);
  assert.equal((await call('/v1/venues/'+id)).statistics.averageRating,4.25);
  assert.equal((await call('/v1/venues/'+id,'GET',undefined,f.b.token)).statistics.averageRating,null);
  const reportId=randomUUID();await call('/v1/reports/'+reportId,'PUT',{subjectType:'venue',subjectId:id,reason:'Synthetic photo venue report'},f.b.token);
  await call('/v1/moderation/reports/'+reportId+'/decision','POST',{version:1,action:'hide',reason:'Synthetic hide'});
  assert.equal((await fetch(c.url+'/v1/media/'+photo,{headers:{authorization:`Bearer ${f.b.token}`}})).status,404);
  await call('/v1/moderation/reports/'+reportId+'/decision','POST',{version:2,action:'restore',reason:'Synthetic restore'});
  assert.equal((await fetch(c.url+'/v1/media/'+photo,{headers:{authorization:`Bearer ${f.b.token}`}})).status,200);
 }finally {await c.close();}
});

test('P7 ten data-driven badges: actual history, idempotent recalculation, ordered three pins, revocation and peer ACL',async t=>{
 const c=await context(),{db,f,call}=c;
 try {
  const empty=await call('/v1/me/badges');assert.equal(empty.items.length,10);assert.ok(empty.items.every((r:any)=>r.progress===0&&r.earnedAt===null));
  const venues=[];
  for(let i=0;i<2;i++) venues.push((await db.pool.query("INSERT INTO venues(name,type,city,source,status)VALUES($1,'bar','Synthetic','isolated test','verified')RETURNING id",['Synthetic badge place '+i])).rows[0].id);
  const photos=[await c.photo(),await c.photo(),await c.photo()];
  const ids=[];
  for(let i=0;i<10;i++) {
   let br=(await db.pool.query('SELECT id FROM breweries WHERE name=$1',['Synthetic badge brewery '+(i%5)])).rows[0]?.id;
   if(!br)br=(await db.pool.query("INSERT INTO breweries(name,source,verification_status)VALUES($1,'isolated test','verified')RETURNING id",['Synthetic badge brewery '+(i%5)])).rows[0].id;
   // The same five style IDs are reused; different release strings are not new styles.
   let style=(await db.pool.query('SELECT id FROM styles WHERE name=$1',['Synthetic badge style '+(i%5)])).rows[0]?.id;
   if(!style)style=(await db.pool.query('INSERT INTO styles(name)VALUES($1)RETURNING id',['Synthetic badge style '+(i%5)])).rows[0].id;
   const rel=(await db.pool.query('INSERT INTO beer_releases(brewery_id,name,style_id)VALUES($1,$2,$3)RETURNING id',[br,'Synthetic badge position '+i,style])).rows[0].id;
   const pos=(await db.pool.query("INSERT INTO beer_positions(release_id,source,status,verified_at)VALUES($1,'isolated test','verified',now())RETURNING id",[rel])).rows[0].id;
   const row=await call('/v1/checkins','POST',{positionId:pos,body:'Synthetic note',ratingQuarters:17,occurredAt:'2026-11-10T10:00:00Z',visibility:'private',mediaIds:i<3?[photos[i]]:[],venueId:venues[i%2]},f.a.token,201);ids.push(row.id);
   if(i===0) {
    const duplicate=await call('/v1/checkins','POST',{positionId:pos,body:'Another note, same position',ratingQuarters:null,occurredAt:'2026-11-10T10:00:00Z',visibility:'private',mediaIds:[photos[0]],venueId:venues[0]},f.a.token,201);ids.push(duplicate.id);
    const repeated=await call('/v1/me/badges');
    for(const metric of ['positions','styles','breweries','notes','photos','venues'])assert.ok(repeated.items.filter((r:any)=>r.metric===metric).every((r:any)=>r.progress===1),metric+' counts distinct identities');
   }
  }
  const earned=await call('/v1/me/badges');
  for(const [metric,target] of [['checkins',1],['positions',3],['positions',10],['styles',3],['styles',5],['breweries',3],['breweries',5],['notes',3],['photos',3],['venues',2]] as const)await t.test(`${metric} reaches ${target} from SQL facts`,()=>{
   const r=earned.items.find((r:any)=>r.metric===metric&&r.target===target);assert.ok(r);assert.equal(r.progress,target);assert.ok(r.earnedAt);
  });
  const previous=(await db.pool.query('SELECT updated_at FROM user_badges WHERE user_id=$1 ORDER BY rule_id',[f.a.id])).rows;
  await call('/v1/me/badges');assert.deepEqual((await db.pool.query('SELECT updated_at FROM user_badges WHERE user_id=$1 ORDER BY rule_id',[f.a.id])).rows,previous);
  const chosen=earned.items.slice(0,3).map((r:any)=>r.id);
  await call('/v1/me/badge-pins','PUT',{ids:chosen});
  const reordered=await call('/v1/me/badge-pins','PUT',{ids:[...chosen].reverse()});
  assert.deepEqual(reordered.items.filter((r:any)=>r.pinSlot!==null).sort((a:any,b:any)=>a.pinSlot-b.pinSlot).map((r:any)=>r.id),[...chosen].reverse());
  const concurrent=await Promise.all([call('/v1/me/badge-pins','PUT',{ids:chosen}),call('/v1/me/badge-pins','PUT',{ids:[...chosen].reverse()})]);
  assert.ok(concurrent.every(result=>result.items.filter((r:any)=>r.pinSlot!==null).length===3));
  const slots=(await call('/v1/me/badges')).items.filter((r:any)=>r.pinSlot!==null).map((r:any)=>r.pinSlot);assert.deepEqual(slots,[1,2,3]);
  await call('/v1/me/badge-pins','PUT',{ids:earned.items.slice(0,4).map((r:any)=>r.id)},f.a.token,400);
  await call('/v1/me/badge-pins','PUT',{ids:[chosen[0]]},f.b.token,422);
  await call('/v1/users/'+f.a.id+'/badges','GET',undefined,f.b.token,404);
  await db.pool.query("UPDATE user_profiles SET profile_visibility='public' WHERE user_id=$1",[f.a.id]);
  assert.equal((await call('/v1/users/'+f.a.id+'/badges','GET',undefined,f.b.token)).items.length,3);
  await db.pool.query('UPDATE badge_rules SET active=false WHERE id=$1',[chosen[0]]);
  assert.equal((await call('/v1/me/statistics')).earnedBadges,9);
  assert.equal((await call('/v1/users/'+f.a.id+'/badges','GET',undefined,f.b.token)).items.length,2);
  await db.pool.query('UPDATE badge_rules SET active=true WHERE id=$1',[chosen[0]]);
  assert.equal((await call('/v1/me/statistics')).earnedBadges,10);
  // Tombstones simulate deletion of these synthetic records only; reconciliation must revoke pins.
  await db.pool.query('UPDATE checkins SET deleted_at=now() WHERE id=ANY($1::uuid[])',[ids]);
  const revoked=await call('/v1/me/badges');assert.ok(revoked.items.every((r:any)=>r.progress===0&&r.earnedAt===null&&r.pinSlot===null));
  assert.equal((await call('/v1/me/statistics')).earnedBadges,0);
 }finally{await c.close();}
});

test('P7 reports cover comment, venue, position and user holds; dismissal and deleted content cannot be restored',async()=>{
 const c=await context(),{db,f,call}=c;
 try {
  await db.pool.query("UPDATE users SET role='admin' WHERE id=$1",[f.a.id]);
  await db.pool.query("UPDATE user_profiles SET profile_visibility='public' WHERE user_id=ANY($1::uuid[])",[[f.a.id,f.b.id]]);
  const post=await call('/v1/checkins','POST',{positionId:f.position,body:'Synthetic public parent',ratingQuarters:16,occurredAt:'2026-11-10T10:00:00Z',visibility:'public'},f.a.token,201);
  const commentId=randomUUID();await call(`/v1/social/checkins/${post.id}/comments/${commentId}`,'PUT',{body:'Synthetic public comment'},f.b.token);
  const placeId=randomUUID();await call('/v1/moderation/venues/'+placeId,'PUT',venue());
  for(const [type,id]of [['comment',commentId],['venue',placeId],['position',f.position],['user',f.b.id]]) {
   const reportId=randomUUID();await call('/v1/reports/'+reportId,'PUT',{subjectType:type,subjectId:id,reason:'Synthetic report'});
   await call('/v1/moderation/reports/'+reportId+'/decision','POST',{version:1,action:'hide',reason:'Synthetic hide'});
   if(type==='comment')assert.equal((await call(`/v1/social/checkins/${post.id}/comments`)).items.length,0);
   if(type==='venue') {await call('/v1/venues/'+id,'GET',undefined,f.a.token,404);assert.equal((await call('/v1/venues/map?bbox=37,55,38,56')).items.length,0);}
   if(type==='position')await call('/v1/positions/'+id,'GET',undefined,f.a.token,404);
   if(type==='user')await call('/v1/users/'+id,'GET',undefined,f.a.token,404);
   await call('/v1/moderation/reports/'+reportId+'/decision','POST',{version:2,action:'restore',reason:'Synthetic restore'});
  }
  const dismissed=randomUUID();await call('/v1/reports/'+dismissed,'PUT',{subjectType:'checkin',subjectId:post.id,reason:'Synthetic dismiss'});
  assert.equal((await call('/v1/moderation/reports/'+dismissed+'/decision','POST',{version:1,action:'dismiss',reason:'No violation'})).status,'rejected');
  const erased=randomUUID();await call('/v1/reports/'+erased,'PUT',{subjectType:'checkin',subjectId:post.id,reason:'Synthetic deletion race'});
  await call('/v1/moderation/reports/'+erased+'/decision','POST',{version:1,action:'hide',reason:'Synthetic hold'});
  await db.pool.query('UPDATE checkins SET deleted_at=now() WHERE id=$1',[post.id]);
  await call('/v1/moderation/reports/'+erased+'/decision','POST',{version:2,action:'restore',reason:'Must not revive'},f.a.token,422);
 }finally {await c.close();}
});

test('P7 additive upgrade from 009 preserves existing IDs and never certifies old coordinates without provenance',async()=>{
 const db=await testDatabase(),dir=await mkdtemp(join(tmpdir(),'krapiva-p7-upgrade-'));
 try {
  await cp('migrations',dir,{recursive:true});await rm(join(dir,'010_places_badges_moderation.sql'));await rm(join(dir,'011_password_reset_codes.sql'));await rm(join(dir,'012_user_handles.sql'));await rm(join(dir,'013_local_discovery.sql'));await rm(join(dir,'014_saved_venues.sql'));await rm(join(dir,'015_venue_menus.sql'));await rm(join(dir,'016_verified_release_dates.sql'));await rm(join(dir,'017_krapet.sql'));await migrate(db.pool,dir);
  const f=await fixtures(db.pool);
  const old=(await db.pool.query("INSERT INTO venues(name,type,city,source,status,latitude,longitude,coordinates_verified_at)VALUES('Old fixture','bar','Synthetic','test','verified',55,37,now())RETURNING id")).rows[0].id;
  const checkin=(await db.pool.query("INSERT INTO checkins(user_id,position_id,occurred_at,venue_id)VALUES($1,$2,now(),$3)RETURNING id",[f.a.id,f.position,old])).rows[0].id;
  await migrate(db.pool);await migrate(db.pool);
  assert.equal((await db.pool.query('SELECT venue_id FROM checkins WHERE id=$1',[checkin])).rows[0].venue_id,old);
  assert.equal((await db.pool.query('SELECT coordinates_source FROM venues WHERE id=$1',[old])).rows[0].coordinates_source,null);
  assert.equal((await db.pool.query('SELECT count(*)::int AS n FROM badge_rules WHERE active')).rows[0].n,10);
 }finally {await db.close();await rm(dir,{recursive:true,force:true});}
});

test('P7 real reporting/moderation: public-only IDOR, idempotency, hide/restore media ACL, audit and admin roles',async()=>{
 const c=await context(),{db,f,call}=c;
 try {
  await db.pool.query("UPDATE users SET role='admin' WHERE id=$1",[f.a.id]);
  await db.pool.query("UPDATE user_profiles SET profile_visibility='public' WHERE user_id=$1",[f.a.id]);
  const photo=await c.photo();
  const row=await call('/v1/checkins','POST',{positionId:f.position,body:'Synthetic public report target',ratingQuarters:15,occurredAt:'2026-11-10T10:00:00Z',visibility:'public',mediaIds:[photo]},f.a.token,201);
  const privateRow=await call('/v1/checkins','POST',{positionId:f.position,body:'Synthetic private target',ratingQuarters:null,occurredAt:'2026-11-10T10:00:00Z',visibility:'private'},f.a.token,201);
  const report=randomUUID(),body={subjectType:'checkin',subjectId:row.id,reason:'Synthetic review request'};
  await call('/v1/reports/'+randomUUID(),'PUT',{...body,subjectId:privateRow.id},f.b.token,404);
  await call('/v1/reports/'+report,'PUT',body,f.b.token);
  await call('/v1/reports/'+report,'PUT',body,f.b.token);
  await call('/v1/reports/'+report,'PUT',{...body,reason:'Different retry'},f.b.token,409);
  await call('/v1/moderation/reports','GET',undefined,f.b.token,403);
  assert.ok((await call('/v1/moderation/reports')).items.some((r:any)=>r.id===report));
  await call('/v1/moderation/reports/'+report+'/decision','POST',{version:1,action:'hide',reason:'Synthetic moderation evidence'});
  await call('/v1/social/checkins/'+row.id,'GET',undefined,f.b.token,404);
  assert.equal((await fetch(c.url+'/v1/media/'+photo,{headers:{authorization:`Bearer ${f.b.token}`}})).status,404);
  assert.equal((await call('/v1/feed','GET',undefined,f.b.token)).items.length,0);
  await call('/v1/moderation/reports/'+report+'/decision','POST',{version:1,action:'restore',reason:'Stale version'},f.a.token,409);
  await call('/v1/moderation/reports/'+report+'/decision','POST',{version:2,action:'restore',reason:'Synthetic restore'});
  await call('/v1/social/checkins/'+row.id,'GET',undefined,f.b.token);
  assert.equal((await fetch(c.url+'/v1/media/'+photo,{headers:{authorization:`Bearer ${f.b.token}`}})).status,200);
  const audit=await call('/v1/moderation/audit?entityId='+row.id);assert.equal(audit.items.length,2);assert.ok(audit.items.every((r:any)=>r.actorId===f.a.id));
  await call('/v1/moderation/users/'+f.a.id+'/role','POST',{expectedRole:'admin',role:'user',reason:'Self demotion'},f.a.token,422);
  await call('/v1/moderation/users/'+f.b.id+'/role','POST',{expectedRole:'user',role:'moderator',reason:'Synthetic role test'});
  await call('/v1/moderation/reports','GET',undefined,f.b.token);
  await call('/v1/moderation/users/'+f.b.id+'/role','POST',{expectedRole:'moderator',role:'user',reason:'Synthetic role revoke'});
  await call('/v1/moderation/reports','GET',undefined,f.b.token,403);
 }finally{await c.close();}
});

test('Nearby discovery uses real venues and grants local badges only after moderated attendance',async()=>{
 const c=await context(),{db,f,call}=c;
 try {
  await db.pool.query("UPDATE users SET role='moderator' WHERE id=$1",[f.a.id]);
  const location=randomUUID(),eventId=randomUUID(),badgeId=randomUUID();
  await call('/v1/moderation/venues/'+location,'PUT',venue('Synthetic taproom',{type:'brewery'}));
  const now=Date.now();
  const startsAt=new Date(now-3_600_000).toISOString(),endsAt=new Date(now+3_600_000).toISOString();
  const event={version:0,venueId:location,title:'Synthetic event',description:'Isolated test event',
    source:'Synthetic organizer confirmation',startsAt,endsAt,published:true};
  await call('/v1/moderation/events/'+eventId,'PUT',event,f.b.token,403);
  assert.equal((await call('/v1/moderation/events/'+eventId,'PUT',event)).version,1);
  await call('/v1/moderation/local-badges/'+badgeId,'PUT',{
    version:0,venueId:location,eventId,title:'Synthetic local badge',description:'Moderator confirms attendance',
    startsAt,endsAt,active:true,
  });
  const nearby={latitude:55.75,longitude:37.61,radiusKm:1};
  const discovery=await call('/v1/discover/nearby','POST',nearby,f.b.token);
  assert.deepEqual(discovery.places.map((place:{id:string})=>place.id),[location]);
  assert.equal(discovery.events[0].id,eventId);
  assert.deepEqual(discovery.events[0].badgeTitles,['Synthetic local badge']);
  assert.equal(discovery.badges[0].id,badgeId);
  const outside=await call('/v1/discover/nearby','POST',{...nearby,longitude:38},f.b.token);
  assert.deepEqual(outside,{places:[],events:[],badges:[]});
  const ownerBadge=async()=> (await call('/v1/me/badges','GET',undefined,f.b.token)).items.find((row:{id:string})=>row.id===badgeId);
  assert.equal((await ownerBadge()).earnedAt,null);
  const createCheckin=async(occurredAt:string)=>call('/v1/checkins','POST',{
    positionId:f.position,venueId:location,body:'Synthetic visitation test',visibility:'private',
    occurredAt,ratingQuarters:null,
  },f.b.token,201);
  const wrong=await createCheckin(new Date(now-7_200_000).toISOString());
  const eligible=await createCheckin(new Date(now-60_000).toISOString());
  assert.equal((await ownerBadge()).earnedAt,null);
  const attendance='/v1/moderation/local-badges/'+badgeId+'/attendance';
  await call(attendance,'PUT',{checkinId:wrong.id,evidence:'Synthetic verification'},f.a.token,422);
  await call(attendance,'PUT',{checkinId:eligible.id,evidence:'Synthetic verification'},f.b.token,403);
  const evidence={checkinId:eligible.id,evidence:'Synthetic organizer check'};
  await call(attendance,'PUT',evidence);
  await call(attendance,'PUT',evidence);
  assert.equal((await ownerBadge()).progress,1);
  await call('/v1/me/badge-pins','PUT',{ids:[badgeId]},f.b.token);
  await call('/v1/moderation/events/'+eventId,'PUT',{...event,version:1,published:false});
  assert.equal((await ownerBadge()).earnedAt,null);
  assert.equal((await ownerBadge()).pinSlot,null);
  assert.deepEqual((await call('/v1/discover/nearby','POST',nearby)).events,[]);
 } finally {await c.close();}
});


test('My places filters only the signed-in account and follow active checkins',async()=>{
 const c=await context(),{db,f,call}=c;
 try {
  await db.pool.query("UPDATE users SET role='moderator' WHERE id=$1",[f.a.id]);
  const saved=randomUUID(),visited=randomUUID();
  await call('/v1/moderation/venues/'+saved,'PUT',venue('Synthetic saved place'));
  await call('/v1/moderation/venues/'+visited,'PUT',venue('Synthetic visited place',{longitude:37.612}));
  const save='/v1/me/saved-venues/'+saved;
  const ids=(page:{items:Array<{id:string}>})=>page.items.map(row=>row.id);
  await call('/v1/me/saved-venues/'+randomUUID(),'PUT',undefined,f.a.token,404);
  assert.deepEqual(await call(save,'PUT'),{saved:true});
  assert.deepEqual(await call(save,'PUT'),{saved:true});
  assert.equal((await call('/v1/venues/'+saved)).saved,true);
  assert.equal((await call('/v1/venues/'+saved,'GET',undefined,f.b.token)).saved,false);
  const checkin=await call('/v1/checkins','POST',{positionId:f.position,venueId:visited,
    occurredAt:'2026-11-17T12:00:00Z',ratingQuarters:null,body:'Synthetic venue visit',visibility:'private'},f.a.token,201);
  assert.deepEqual(ids(await call('/v1/venues?scope=visited')),[visited]);
  assert.deepEqual(ids(await call('/v1/venues?scope=visited','GET',undefined,f.b.token)),[]);
  assert.deepEqual(ids(await call('/v1/venues?scope=saved','GET',undefined,f.b.token)),[]);
  assert.deepEqual(ids(await call('/v1/venues/map?scope=saved&bbox=37.5,55.6,37.8,55.9')),[saved]);
  assert.deepEqual(ids(await call('/v1/venues/nearby','POST',{latitude:55.75,longitude:37.61,radiusKm:1,scope:'visited'})),[visited]);
  await call('/v1/checkins/'+checkin.id,'DELETE',{version:checkin.version},f.a.token,204);
  assert.deepEqual(ids(await call('/v1/venues?scope=visited')),[]);
  assert.deepEqual(await call(save,'DELETE'),{saved:false});
  assert.deepEqual(ids(await call('/v1/venues?scope=saved')),[]);
 } finally {await c.close();}
});
