import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { test } from "node:test";
import { buildApp } from "../src/app.js";
import { readConfig } from "../src/config.js";
import { migrate } from "../src/db/migrate.js";
import { testDatabase } from "./database.js";
import { fixtures } from "./fixtures.js";
import { tokenHash } from "../src/identity/sessions.js";

test("real HTTP+SQL: two accounts, ACL, durable idempotency, cursor and concurrency, revocation and DB outage",async()=>{
  const db=await testDatabase(); await migrate(db.pool);
  const data=await fixtures(db.pool);
  let app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
  let address=await app.listen({host:'127.0.0.1',port:0});
  const call=async(path:string,method='GET',body?:unknown,token=data.a.token,key?:string)=>{
    const response=await fetch(address+path,{method,headers:{authorization:`Bearer ${token}`,
      ...(body===undefined?{}:{'content-type':'application/json'}),...(key?{'idempotency-key':key}:{})},
      ...(body===undefined?{}:{body:JSON.stringify(body)})});
    const text=await response.text(); return {status:response.status,body:text?JSON.parse(text):null};
  };
  try {
    assert.equal((await fetch(address+'/health/ready')).status,200);
    assert.equal((await fetch(address+'/v1/me')).status,401);
    assert.equal((await call('/v1/me','GET',undefined,'bad')).status,401);
    const me=await call('/v1/me'); assert.equal(me.body.userId,data.a.id);
    const profile={firstName:'Synthetic',lastName:null,country:'Test',city:'Test city',bio:'Private',gender:'undisclosed',genderVisibility:'private',profileVisibility:'private',version:1};
    assert.equal((await call('/v1/me/profile','PATCH',profile)).status,200);
    assert.equal((await call('/v1/me/profile','PATCH',profile)).status,409);
    assert.equal((await call('/v1/me','GET',undefined,data.b.token)).body.bio,null);
    assert.equal((await call('/v1/users/'+data.a.id,'GET',undefined,data.b.token)).status,404);
    const payload={positionId:data.position,ratingQuarters:17,body:'Synthetic checkin',occurredAt:'2026-11-04T12:00:00Z',visibility:'private'};
    for(const rating of [0,21,1.5,'17']) assert.equal((await call('/v1/checkins','POST',{...payload,ratingQuarters:rating},data.a.token,randomUUID())).status,400);
    assert.equal((await call('/v1/checkins','POST',{...payload,userId:data.b.id},data.a.token,randomUUID())).status,400);
    assert.equal((await call('/v1/checkins','POST',payload)).status,400);
    assert.equal((await call('/v1/checkins','POST',{...payload,positionId:randomUUID()},data.a.token,randomUUID())).status,422);
    const key=randomUUID();
    const results=await Promise.all([call('/v1/checkins','POST',payload,data.a.token,key),call('/v1/checkins','POST',payload,data.a.token,key)]);
    assert.deepEqual(results.map(r=>r.status).sort(),[200,201]);
    const id=results[0]!.body.id;
    assert.equal(results[1]!.body.id,id);
    assert.equal((await db.pool.query('SELECT * FROM checkins')).rowCount,1);
    assert.equal((await call('/v1/checkins','POST',{...payload,body:'Different'},data.a.token,key)).status,409);
    assert.equal((await call('/v1/checkins/'+id,'GET',undefined,data.b.token)).status,404);
    assert.equal((await call('/v1/checkins','GET',undefined,data.b.token)).body.items.length,0);
    const {positionId:_position,...mutable}=payload;
    assert.equal((await call('/v1/checkins/'+id,'PATCH',{...mutable,version:1},data.b.token)).status,404);
    assert.equal((await call('/v1/checkins/'+id,'DELETE',{version:1},data.b.token)).status,404);
    const edits=await Promise.all([call('/v1/checkins/'+id,'PATCH',{...mutable,body:'A',version:1}),call('/v1/checkins/'+id,'PATCH',{...mutable,body:'B',version:1})]);
    assert.deepEqual(edits.map(r=>r.status).sort(),[200,409]);
    await call('/v1/checkins','POST',{...payload,ratingQuarters:null},data.a.token,randomUUID());
    const first=await call('/v1/checkins?limit=1');
    assert.equal(first.body.items.length,1); assert.ok(first.body.nextCursor);
    const second=await call('/v1/checkins?limit=1&cursor='+first.body.nextCursor);
    assert.equal(second.body.items.length,1); assert.notEqual(second.body.items[0].id,first.body.items[0].id); assert.equal(second.body.nextCursor,null);
    assert.equal((await call('/v1/checkins?limit=51')).status,400);
    assert.equal((await call('/v1/checkins?cursor=broken')).status,400);
    // A new API instance sees persisted sessions, profile, checkins and replay ledger.
    await app.close(); app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
    address=await app.listen({host:'127.0.0.1',port:0});
    assert.equal((await call('/v1/me')).body.bio,'Private');
    assert.equal((await call('/v1/checkins')).body.items.length,2);
    assert.equal((await call('/v1/checkins','POST',payload,data.a.token,key)).status,200);
    assert.equal((await call('/v1/checkins/'+id,'DELETE',{version:1})).status,409);
    assert.equal((await call('/v1/checkins/'+id,'DELETE',{version:2})).status,204);
    assert.equal((await call('/v1/checkins/'+id)).status,404);
    assert.equal((await call('/v1/checkins','POST',payload,data.a.token,key)).status,409);
    await db.pool.query("UPDATE request_budgets SET count=120,window_start=date_trunc('minute',now()) WHERE user_id=$1",[data.a.id]);
    assert.equal((await call('/v1/me')).status,429);
    await db.pool.query("UPDATE request_budgets SET window_start=now()-interval '2 minutes' WHERE user_id=$1",[data.a.id]);
    assert.equal((await call('/v1/me')).status,200);
    assert.equal((await call('/v1/session','DELETE')).status,204);
    assert.equal((await call('/v1/me')).status,401);
    await db.pool.query("UPDATE sessions SET expires_at=now()-interval '1 second',created_at=now()-interval '2 days' WHERE token_hash=$1",[tokenHash(data.b.token)]);
    assert.equal((await call('/v1/me','GET',undefined,data.b.token)).status,401);
    await db.pool.query('ALTER TABLE schema_migrations RENAME TO unavailable_migrations');
    assert.equal((await fetch(address+'/health/ready')).status,503);
    assert.equal((await fetch(address+'/health/live')).status,200);
  } finally { await app.close(); await db.close(); }
});
