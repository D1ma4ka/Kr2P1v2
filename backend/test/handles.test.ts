import assert from 'node:assert/strict';
import {test} from 'node:test';
import {randomUUID} from 'node:crypto';
import {mkdtemp,readdir,copyFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {testDatabase} from './database.js';
import {fixtures} from './fixtures.js';
import {hashPassword} from '../src/identity/passwords.js';
import {normalizeHandle} from '../src/identity/handles.js';
import sharp from 'sharp';
import {FileStorage} from '../src/media/storage.js';
import {MediaService} from '../src/media/service.js';

test('handle policy: ASCII, boundaries, normalization, service names and abusive names',()=>{
 for(const h of ['CraftLover','beer_fan','krapiva2026','a12','a'.repeat(24)])assert.equal(normalizeHandle(h),h.toLowerCase());
 for(const h of ['ab','a'.repeat(25),'1abc','_abc','abc_','ab__cd',' abcd','abcd ','abcd\n','abcd\r','ab\tcd','абв','Kevin','beer🍺','a@b','@beer','admin','ADMIN','support','fuckyou'])assert.throws(()=>normalizeHandle(h));
});

test('P8.3 HTTP/SQL/Mailpit: additive upgrade, legacy identity/session/history, claims/search and concurrent signup',async()=>{
 const db=await testDatabase(),dir=await mkdtemp(join(tmpdir(),'krapiva-handles-'));
 for(const name of await readdir('migrations'))if(name<'012')await copyFile('migrations/'+name,join(dir,name));
 await migrate(db.pool,dir);
 const f=await fixtures(db.pool),password='Existing password 2026!';
 const original=await hashPassword(password);
 await db.pool.query('UPDATE users SET password_hash=$2,email_verified_at=now() WHERE id=$1',[f.a.id,original]);
 await db.pool.query('INSERT INTO checkins(user_id,position_id,occurred_at) VALUES($1,$2,now())',[f.a.id,f.position]);
 await db.pool.query('INSERT INTO friendships(user_low,user_high) VALUES(least($1::uuid,$2::uuid),greatest($1::uuid,$2::uuid))',[f.a.id,f.b.id]);
 const before=(await db.pool.query('SELECT id,email,password_hash FROM users ORDER BY id')).rows;
 const storage=new FileStorage(join(dir,'media'));await storage.initialize();
 const media=new MediaService(db.pool,storage);
 const image=await sharp({create:{width:24,height:24,channels:3,background:'#f3af45'}}).png().toBuffer();
 const asset=await media.upload(f.a.id,image,'image/png');
 const photoBefore=await media.get(f.a.id,asset.id);
 await db.pool.query('INSERT INTO user_badges(user_id,rule_id,progress,earned_at,rule_version) SELECT $1,id,target,now(),version FROM badge_rules ORDER BY id LIMIT 1',[f.a.id]);
 const badgesBefore=(await db.pool.query('SELECT * FROM user_badges WHERE user_id=$1 ORDER BY rule_id',[f.a.id])).rows;
 await migrate(db.pool);await migrate(db.pool);
 assert.deepEqual((await db.pool.query('SELECT id,email,password_hash FROM users ORDER BY id')).rows,before);
 assert.equal((await db.pool.query('SELECT count(*)::int AS n FROM checkins')).rows[0].n,1);
 assert.equal((await db.pool.query('SELECT count(*)::int AS n FROM friendships')).rows[0].n,1);
 assert.deepEqual((await db.pool.query('SELECT * FROM user_badges WHERE user_id=$1 ORDER BY rule_id',[f.a.id])).rows,badgesBefore);
 assert.deepEqual(await media.get(f.a.id,asset.id),photoBefore);
 const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,media);
 const address=await app.listen({host:'127.0.0.1',port:0});
 const call=(path:string,body?:unknown,token?:string,method=body===undefined?'GET':'POST')=>fetch(address+path,{method,headers:{...(body===undefined?{}:{'content-type':'application/json'}),...(token?{authorization:'Bearer '+token}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
 try {
  assert.equal((await call('/health/ready')).status,200);
  const old=await call('/v1/me',undefined,f.a.token);assert.equal(old.status,200);assert.equal((await old.json() as any).handle,null);
  const downloaded=await call('/v1/media/'+asset.id,undefined,f.a.token);assert.equal(downloaded.status,200);
  assert.deepEqual(Buffer.from(await downloaded.arrayBuffer()),photoBefore);
  assert.equal((await call('/v1/auth/login',{email:'alpha@example.invalid',password})).status,200);
  const claim=await call('/v1/me/handle',{handle:'Legacy_Fan'},f.a.token,'PUT');assert.equal(claim.status,200);
  const profile=await claim.json() as any;assert.equal(profile.handle,'legacy_fan');assert.equal(profile.userId,f.a.id);assert.equal(profile.displayName,'Synthetic Alpha');
  for(const identifier of ['alpha@example.invalid','Legacy_Fan','@LEGACY_FAN']) {
   const login=await call('/v1/auth/login',{identifier,password});assert.equal(login.status,200);
   assert.equal((await login.json() as any).userId,f.a.id);
  }
  for(const identifier of ['alpha@example.invalid','legacy_fan','@legacy_fan','nobody_here']) {
   const invalid=await call('/v1/auth/login',{identifier,password:'Incorrect password!'});assert.equal(invalid.status,401);
   assert.equal((await invalid.json() as any).error.code,'INVALID_CREDENTIALS');
  }
  assert.equal((await call('/v1/auth/login',{email:'alpha@example.invalid',identifier:'legacy_fan',password})).status,400);
  assert.equal((await call('/v1/me/handle',{handle:'LEGACY_FAN'},f.b.token,'PUT')).status,409);
  assert.equal((await call('/v1/me/handle',{handle:'another_name'},f.a.token,'PUT')).status,409);
  const update:any={version:profile.version,displayName:'Русское имя'};
  for(const key of ['firstName','lastName','city','country','bio','gender','genderVisibility','profileVisibility'])update[key]=profile[key];
  const edited=await call('/v1/me/profile',update,f.a.token,'PATCH');assert.equal(edited.status,200);assert.equal((await edited.json() as any).handle,'legacy_fan');
  const search=await call('/v1/users?q=%40Legacy_Fan',undefined,f.b.token);assert.equal(search.status,200);
  // Existing private profile stays private even when its exact handle is known.
  assert.equal((await search.json() as any).items.length,0);
  await db.pool.query("UPDATE user_profiles SET profile_visibility='public' WHERE user_id=$1",[f.a.id]);
  const found=await call('/v1/users?q=%40Legacy_Fan',undefined,f.b.token);
  assert.equal((await found.json() as any).items[0].handle,'legacy_fan');
  await assert.rejects(db.pool.query("UPDATE users SET handle='UPPERCASE' WHERE id=$1",[f.b.id]),{code:'23514'});
  const handle='craft_'+randomUUID().replaceAll('-','').slice(0,10);
  const emails=[0,1].map(i=>`handle-${i}-${randomUUID()}@example.invalid`);
  const registrationPassword='Aa 12_34';assert.equal(registrationPassword.length,8);
  for(const invalidPassword of ['1234567','🍺'.repeat(7),'a'.repeat(129)])
   assert.equal((await call('/v1/auth/register',{email:emails[0],password:invalidPassword,displayName:'New user',adultConfirmed:true,handle})).status,400);
  const results=await Promise.all(emails.map((email,i)=>call('/v1/auth/register',{email,password:registrationPassword,displayName:'New user',adultConfirmed:true,handle:i?handle.toUpperCase():handle})));
  assert.deepEqual(results.map(r=>r.status).sort(),[202,409]);
  const loser=await results.find(r=>r.status===409)!.json() as any;assert.equal(loser.error.message,'Этот никнейм уже занят. Попробуй другой');
  assert.equal((await db.pool.query('SELECT count(*)::int AS n FROM users WHERE handle=$1',[handle])).rows[0].n,1);
  const availability=await call('/v1/auth/handles/'+handle);assert.equal((await availability.json() as any).available,false);
  for(const h of ['admin','bad__name','bad_','кириллица'])assert.equal((await call('/v1/auth/handles/'+encodeURIComponent(h))).status,422);
  const email=emails[results.findIndex(r=>r.status===202)]!;
  const mailbox=process.env.MAILPIT_API_URL??'http://127.0.0.1:58025';
  const list=await(await fetch(mailbox+'/api/v1/search?query='+encodeURIComponent('to:'+email))).json() as any;
  const letter=await(await fetch(mailbox+'/api/v1/message/'+list.messages[0].ID)).json() as any;
  const code=letter.Text.match(/Код подтверждения: ([0-9]{6})/)[1];
  assert.equal((await call('/v1/auth/verify-code',{email,code})).status,204);
  assert.equal((await call('/v1/auth/login',{email,password:registrationPassword})).status,200);
  assert.equal((await call('/v1/auth/login',{identifier:'@'+handle,password:registrationPassword.toLowerCase()})).status,401);
  assert.equal((await call('/v1/auth/login',{identifier:handle,password:registrationPassword.replace(' ','')})).status,401);
  assert.equal((await db.pool.query('SELECT password_hash FROM users WHERE id=$1',[f.a.id])).rows[0].password_hash,original);
 } finally {await app.close();await db.close();await rm(dir,{recursive:true,force:true});}
});
