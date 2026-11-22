import assert from 'node:assert/strict';
import {test} from 'node:test';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {testDatabase} from './database.js';
import {fixtures} from './fixtures.js';

test('Advisor history: bounded single-owner snapshot with stable ties, edits and removals', async () => {
 const db = await testDatabase();
 const app = buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
 const url = '/v1/me/advisor-history';
 try {
  await migrate(db.pool);
  const {a,b,position} = await fixtures(db.pool);
  async function history(token: string) {
   const response = await app.inject({url,headers:{authorization:'Bearer '+token}});
   assert.equal(response.statusCode,200,response.body);
   assert.match(String(response.headers['cache-control']),/no-store/);
   return response.json().items;
  }
  assert.equal((await app.inject({url})).statusCode,401);
  assert.deepEqual(await history(a.token),[]);
  const id = (n: number) => '00000000-0000-4000-8000-'+String(n).padStart(12,'0');
  await db.pool.query(`INSERT INTO checkins(id,user_id,position_id,rating_quarters,body,visibility,occurred_at)
   SELECT ('00000000-0000-4000-8000-' || lpad(n::text,12,'0'))::uuid,$1,$2,NULL,'private test note','private',
    '2026-06-01 12:00:00.123456+00'::timestamptz FROM generate_series(1,102) AS n`,[a.id,position]);
  await db.pool.query(`INSERT INTO checkins(id,user_id,position_id,rating_quarters,body,visibility,occurred_at)
   VALUES ($1,$2,$3,20,'other account','public',now())`,[id(999),b.id,position]);
  let rows = await history(a.token);
  assert.equal(rows.length,100);
  assert.deepEqual(rows.map((row:{id:string})=>row.id),Array.from({length:100},(_,i)=>id(102-i)));
  assert.equal(rows[0].occurredAt,'2026-06-01T12:00:00.123456Z');
  assert.equal(rows[0].ratingQuarters,null);
  assert.equal(rows[0].firstForPosition,false);
  assert.deepEqual(Object.keys(rows[0]).sort(),['breweryId','firstForPosition','id','occurredAt','positionId','ratingQuarters','styleId']);
  const other = await history(b.token);
  assert.deepEqual(other.map((row:{id:string})=>row.id),[id(999)]);
  assert.equal(other[0].firstForPosition,true);

  await db.pool.query('UPDATE checkins SET hidden_at=now() WHERE id=$1',[id(102)]);
  await app.inject({url:'/v1/checkins/'+id(101),method:'DELETE',headers:{authorization:'Bearer '+a.token},payload:{version:1}})
   .then(response=>assert.equal(response.statusCode,204,response.body));
  await app.inject({url:'/v1/checkins/'+id(100),method:'PATCH',headers:{authorization:'Bearer '+a.token},payload:{
   version:1,ratingQuarters:6,body:'edited private text',occurredAt:'2026-06-02T12:00:00Z',visibility:'private',
  }}).then(response=>assert.equal(response.statusCode,200,response.body));
  rows = await history(a.token);
  assert.equal(rows.length,100);
  assert.equal(rows[0].id,id(100));
  assert.equal(rows[0].ratingQuarters,6);
  assert.equal(rows.at(-1).id,id(1));
  assert.equal(rows.at(-1).firstForPosition,true);
 } finally {
  await app.close();
  await db.close();
 }
});
