import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {test} from 'node:test';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {testDatabase} from './database.js';
import {fixtures} from './fixtures.js';

test('venue menus: role delegation, venue isolation, publication, ratings, and package identity', async () => {
 const db = await testDatabase();
 const app = buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
 try {
  await migrate(db.pool);
  const {a,b,position} = await fixtures(db.pool);
  const operator = (await fixtures(db.pool,'operator')).b;
  const first = randomUUID(), second = randomUUID(), sectionId = randomUUID();
  await db.pool.query(`INSERT INTO venues(id,name,type,city,source,status)
   VALUES ($1,'Test first','bar','Test city','Isolated fixture','verified'),
          ($2,'Test second','bar','Test city','Isolated fixture','verified')`,[first,second]);
  await db.pool.query("UPDATE users SET role='moderator' WHERE id=$1",[a.id]);
  await db.pool.query("UPDATE beer_positions SET packaging='can' WHERE id=$1",[position]);
  const bottle = (await db.pool.query(`INSERT INTO beer_positions(release_id,source,status,packaging)
   SELECT release_id,'Isolated fixture','verified','bottle' FROM beer_positions WHERE id=$1 RETURNING id`,[position])).rows[0].id as string;
  const path = (venue: string) => '/v1/venues/'+venue+'/menu';
  async function call(url: string, token: string, method: 'GET'|'PUT'|'POST'|'DELETE' = 'GET', payload?: object, status = 200) {
   const res = await app.inject({url,method,headers:{authorization:'Bearer '+token},...(payload ? {payload} : {})});
   assert.equal(res.statusCode,status,res.body);
   return res.json();
  }
  const grant = (user: string) => '/v1/moderation/venues/'+first+'/menu/operators/'+user;
  const offer = {sectionId,ordinal:0,price:3.25,currency:'EUR',volumeMl:330,servingFormat:'can',available:true};
  const draft = {expectedRevision:0,source:'Isolated fixture',sections:[{id:sectionId,name:'Test menu'}],
   items:[{positionId:position,...offer},{positionId:bottle,...offer,ordinal:1,available:false}]};
  assert.equal((await app.inject({url:path(first)})).statusCode,401);
  assert.equal((await call(path(first),b.token)).menu,null);
  await call(grant(b.id),b.token,'PUT',{role:'owner'},403);
  await call(grant(operator.id),a.token,'PUT',{role:'operator'});
  await call(path(second)+'/draft',operator.token,'PUT',draft,403);
  await call(path(first)+'/draft',b.token,'PUT',draft,403);
  await call(path(first)+'/draft',operator.token,'PUT',{...draft,items:[draft.items[0]!,draft.items[0]!]},422);
  await call(path(first)+'/draft',operator.token,'PUT',{...draft,items:[{...draft.items[0],currency:null}]},422);
  const oversized = Array.from({length:251}, () => ({...draft.items[0],positionId:randomUUID()}));
  await call(path(first)+'/draft',operator.token,'PUT',{...draft,items:oversized},400);
  await call(path(first)+'/draft',operator.token,'PUT',{...draft,items:[draft.items[0],{...draft.items[1],positionId:randomUUID()}]},422);
  assert.equal((await call(path(first)+'/draft',operator.token)).menu,null);
  const writes = await Promise.all([1,2].map(() => app.inject({
   url:path(first)+'/draft',method:'PUT',headers:{authorization:'Bearer '+operator.token},payload:draft,
  })));
  assert.deepEqual(writes.map(res => res.statusCode).sort(),[200,409]);
  assert.equal((await call(path(first)+'/draft',operator.token)).menu.items.length,2);
  await call(path(first)+'/draft',b.token,'GET',undefined,403);
  assert.equal((await call(path(first),b.token)).menu,null);
  await call(path(first)+'/publish',operator.token,'POST',{expectedRevision:1});
  await call(path(second)+'/draft',a.token,'PUT',{...draft,items:[draft.items[1]!]});
  await call(path(second)+'/publish',a.token,'POST',{expectedRevision:1});
  assert.deepEqual((await call(path(second),b.token)).menu.items.map((i:{positionId:string})=>i.positionId),[bottle]);
  await db.pool.query(`INSERT INTO checkins(user_id,position_id,rating_quarters,body,visibility,occurred_at)
   VALUES ($1,$2,20,'','private',now())`,[b.id,position]);
  const published = (await call(path(first),b.token)).menu;
  assert.equal(published.state,'published');
  assert.ok(Number.isFinite(Date.parse(published.updatedAt)));
  assert.deepEqual(published.items.map((i:{packaging:string})=>i.packaging),['can','bottle']);
  assert.equal(published.items[0].price,3.25);
  assert.equal(published.items[0].tried,true);
  assert.equal(published.items[1].tried,false);
  assert.equal(published.items[0].averageRating,5);
  assert.equal(published.items[0].publicAverageRating,null);
  assert.equal(published.items[0].ratedCheckins,1);
  assert.equal(published.items[0].personalCheckins,1);
  assert.equal(published.items[0].personalAverageRating,5);
  assert.equal(published.items[1].personalAverageRating,null);
  assert.equal(published.items[1].personalCheckins,0);
  assert.equal(published.items[1].ibu,null);
  assert.equal(published.items[0].name,published.items[1].name);
  const other = (await call(path(first),operator.token)).menu;
  assert.equal(other.items[0].averageRating,null);
  assert.equal(other.items[0].tried,false);
  assert.equal(other.items[0].personalCheckins,0);
  assert.equal(other.items[0].personalAverageRating,null);
  assert.equal(other.items[0].ratedCheckins,0);
  await db.pool.query("UPDATE user_profiles SET profile_visibility='public' WHERE user_id=$1",[b.id]);
  await db.pool.query(`INSERT INTO checkins(user_id,position_id,rating_quarters,body,visibility,occurred_at)
   VALUES ($1,$2,12,'','public',now())`,[b.id,position]);
  const withPublicRating = (await call(path(first),b.token)).menu.items[0];
  assert.equal(withPublicRating.averageRating,4);
  assert.equal(withPublicRating.publicAverageRating,3);
  await db.pool.query("UPDATE beer_positions SET status='rejected' WHERE id=$1",[bottle]);
  assert.deepEqual((await call(path(first),b.token)).menu.items.map((i:{positionId:string})=>i.positionId),[position]);
  await db.pool.query("UPDATE beer_positions SET status='verified' WHERE id=$1",[bottle]);
  await call(path(first)+'/draft/items/'+position,operator.token,'PUT',{...offer,expectedRevision:1,available:false});
  await call(path(first)+'/draft/items/'+bottle+'?expectedRevision=2',operator.token,'DELETE');
  assert.equal((await call(path(first),b.token)).menu.items.length,2);
  await call(path(first)+'/publish',operator.token,'POST',{expectedRevision:1},409);
  await call(path(first)+'/publish',operator.token,'POST',{expectedRevision:3});
  assert.equal((await call(path(first),b.token)).menu.items[0].available,false);
  await call(path(first)+'/draft',operator.token,'PUT',{...draft,expectedRevision:3,items:[]});
  await call(path(first)+'/publish',operator.token,'POST',{expectedRevision:4});
  assert.deepEqual((await call(path(first),b.token)).menu.items,[]);
  await call(grant(operator.id),a.token,'PUT',{role:null});
  await call(path(first)+'/draft',operator.token,'PUT',{...draft,expectedRevision:4},403);
  await db.pool.query("UPDATE venues SET status='hidden' WHERE id=$1",[first]);
  await call(path(first),b.token,'GET',undefined,404);
 } finally {
  await app.close();
  await db.close();
 }
});
