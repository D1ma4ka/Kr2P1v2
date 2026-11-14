import assert from 'node:assert/strict';
import {test} from 'node:test';
import {randomUUID,randomBytes} from 'node:crypto';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import sharp from 'sharp';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {testDatabase} from './database.js';
import {FileStorage} from '../src/media/storage.js';
import {MediaService} from '../src/media/service.js';
import {tokenHash} from '../src/identity/sessions.js';
import {accountMailer} from '../src/mail/accounts.js';

test('P3 real HTTP/SQL/SMTP: registration, single-use confirmation/reset, session rotation, profile/media ACL and deletion request',async()=>{
 const db=await testDatabase();await migrate(db.pool);
 const directory=await mkdtemp(join(tmpdir(),'krapiva-p3-'));
 let app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,new MediaService(db.pool,new FileStorage(directory)));
 let address=await app.listen({host:'127.0.0.1',port:0});
 const call=async(path:string,body?:unknown,token?:string,method=body===undefined?'GET':'POST')=>{
  const response=await fetch(address+path,{method,headers:{...(token?{authorization:`Bearer ${token}`} :{}),...(body===undefined?{}:{'content-type':'application/json'})},
   ...(body===undefined?{}:{body:JSON.stringify(body)})});
  const text=await response.text();return {status:response.status,body:text?JSON.parse(text):null};
 };
 const code=async(email:string,reset=false)=>{
  const api=process.env.MAILPIT_API_URL??'http://127.0.0.1:58025';
  const search=await (await fetch(api+'/api/v1/search?query='+encodeURIComponent('to:'+email))).json() as {messages:{ID:string}[]};
  assert.ok(search.messages.length);
  const message=await (await fetch(api+'/api/v1/message/'+search.messages[0]!.ID)).json() as {Text:string};
  return message.Text.match(reset?/Код: ([A-Za-z0-9_-]{43})/:/Код подтверждения: ([0-9]{6})\b/)![1]!;
 };
 const suffix=randomUUID().replaceAll('-',''), email=`a${suffix}@example.invalid`, other=`b${suffix}@example.invalid`,password='Synthetic password 2026!';
 const signup={email,password,displayName:'Synthetic P3',adultConfirmed:true};
 try {
  assert.equal((await call('/v1/auth/register',{...signup,adultConfirmed:false})).status,422);
  assert.equal((await call('/v1/auth/register',{...signup,password:'short'})).status,400);
  assert.equal((await call('/v1/auth/register',signup)).status,202);
  const verification=await code(email);
  assert.equal((await call('/v1/auth/register',signup)).status,202);
  assert.equal((await call('/v1/auth/login',{email,password})).status,401);
  assert.equal((await call('/v1/auth/verify-code',{email,code:verification})).status,204);
  assert.equal((await call('/v1/auth/verify-code',{email,code:verification})).status,401);
  const login=await call('/v1/auth/login',{email,password});assert.equal(login.status,200);
  const user=login.body.userId,access=login.body.accessToken;
  assert.equal((await call('/v1/auth/login',{email,password:'incorrect password'})).status,401);
  assert.equal((await call('/v1/auth/login',{email:'unknown@example.invalid',password})).status,401);
  assert.equal((await call('/v1/auth/register',{...signup,email:other})).status,202);
  assert.equal((await call('/v1/auth/verify-code',{email:other,code:await code(other)})).status,204);
  const second=(await call('/v1/auth/login',{email:other,password})).body;
  const original=(await call('/v1/me',undefined,access)).body;
  const edit={firstName:'Имя',lastName:'Фамилия',country:'Россия',city:'Москва',bio:'Серверный профиль',gender:'Не указан',
   genderVisibility:'private',profileVisibility:'public',version:original.version,displayName:'Новое имя',links:[{url:'https://example.invalid/profile',visibility:'private'}]};
  assert.equal((await call('/v1/me/profile',{...edit,links:[{url:'https://name:password@example.invalid',visibility:'public'}]},access,'PATCH')).status,422);
  assert.equal((await call('/v1/me/profile',edit,access,'PATCH')).status,200);
  const visible=(await call('/v1/users/'+user,undefined,second.accessToken)).body;
  assert.equal(visible.displayName,'Новое имя');assert.equal(visible.bio,edit.bio);assert.equal(visible.gender,null);assert.deepEqual(visible.links,[]);
  const bytes=await sharp({create:{width:40,height:40,channels:3,background:'#f5a623'}}).png().toBuffer();
  const upload=await fetch(address+'/v1/media',{method:'POST',headers:{authorization:`Bearer ${access}`,'content-type':'image/png'},body:new Uint8Array(bytes)});
  assert.equal(upload.status,201);const asset=await upload.json() as {id:string};
  assert.equal((await call('/v1/me/profile',{...edit,version:2,avatarAssetId:asset.id},second.accessToken,'PATCH')).status,409);
  assert.equal((await call('/v1/me/profile',{...edit,version:1,avatarAssetId:asset.id},second.accessToken,'PATCH')).status,404);
  assert.equal((await call('/v1/me/profile',{...edit,version:2,avatarAssetId:asset.id,coverAssetId:asset.id},access,'PATCH')).status,200);
  assert.equal((await fetch(address+'/v1/media/'+asset.id,{headers:{authorization:`Bearer ${second.accessToken}`}})).status,200);
  assert.equal((await call('/v1/me/profile',{...edit,version:3,profileVisibility:'friends'},access,'PATCH')).status,200);
  assert.equal((await call('/v1/users/'+user,undefined,second.accessToken)).status,404);
  assert.equal((await fetch(address+'/v1/media/'+asset.id,{headers:{authorization:`Bearer ${second.accessToken}`}})).status,404);
  await db.pool.query('INSERT INTO friendships(user_low,user_high) VALUES(least($1::uuid,$2::uuid),greatest($1::uuid,$2::uuid))',[user,second.userId]);
  assert.equal((await call('/v1/users/'+user,undefined,second.accessToken)).status,200);
  assert.equal((await call('/v1/me/profile',{...edit,version:4,profileVisibility:'private',avatarAssetId:null},access,'PATCH')).status,200);
  const own=(await call('/v1/me',undefined,access)).body;assert.equal(own.avatarAssetId,null);assert.equal(own.coverAssetId,asset.id);
  await app.close();app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,new MediaService(db.pool,new FileStorage(directory)));
  address=await app.listen({host:'127.0.0.1',port:0});
  assert.equal((await call('/v1/me',undefined,access)).body.city,'Москва');
  const rotated=await call('/v1/auth/refresh',{refreshToken:login.body.refreshToken});assert.equal(rotated.status,200);
  assert.equal((await call('/v1/me',undefined,access)).status,401);
  assert.equal((await call('/v1/me',undefined,rotated.body.accessToken)).status,200);
  assert.equal((await call('/v1/auth/refresh',{refreshToken:login.body.refreshToken})).status,401);
  assert.equal((await call('/v1/me',undefined,rotated.body.accessToken)).status,401);
  const fresh=(await call('/v1/auth/login',{email,password})).body;
  assert.equal((await call('/v1/auth/forgot-password',{email})).status,202);
  const reset=await code(email,true);
  assert.equal((await call('/v1/auth/forgot-password',{email})).status,202);
  assert.equal((await call('/v1/auth/reset-password',{token:reset,password:'New synthetic password!'})).status,204);
  assert.equal((await call('/v1/auth/reset-password',{token:reset,password:'New synthetic password!'})).status,401);
  assert.equal((await call('/v1/me',undefined,fresh.accessToken)).status,401);
  assert.equal((await call('/v1/auth/refresh',{refreshToken:fresh.refreshToken})).status,401);
  assert.equal((await call('/v1/auth/login',{email,password})).status,401);
  const final=(await call('/v1/auth/login',{email,password:'New synthetic password!'})).body;
  const deletion=await call('/v1/me/deletion-request',{},final.accessToken);assert.equal(deletion.status,202);assert.ok(deletion.body.eraseAfter);
  assert.equal((await call('/v1/me',undefined,final.accessToken)).status,401);
  assert.equal((await call('/v1/users/'+user,undefined,second.accessToken)).status,404);
  const stored=(await db.pool.query('SELECT password_hash FROM users WHERE id=$1',[user])).rows[0].password_hash;
  assert.ok(stored.startsWith('scrypt-v1$'));assert.ok(!stored.includes(password));
  assert.equal((await db.pool.query('SELECT 1 FROM account_tokens WHERE token_hash=$1',[tokenHash(reset)])).rowCount,1);
  await db.pool.query("INSERT INTO auth_budgets(key_hash,window_start,count) VALUES($1,date_trunc('hour',now()),60) ON CONFLICT(key_hash) DO UPDATE SET count=60",[tokenHash('ip:127.0.0.1')]);
  assert.equal((await call('/v1/auth/login',{email:other,password})).status,429);
 } finally {await app.close();await db.close();await rm(directory,{recursive:true,force:true});}
});

test('account failure paths: SMTP rollback, expired tokens and independent token rate budgets',async()=>{
 const db=await testDatabase();await migrate(db.pool);
 const mail=accountMailer({NODE_ENV:'test',SMTP_HOST:'127.0.0.1',SMTP_PORT:'1'});
 const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,undefined,mail);
 const address=await app.listen({host:'127.0.0.1',port:0});
 const call=(path:string,body:unknown)=>fetch(address+path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body)});
 try {
  assert.equal((await call('/v1/auth/register',{email:'failure@example.invalid',password:'Synthetic failure password!',displayName:'Failure',adultConfirmed:true})).status,503);
  assert.equal((await db.pool.query('SELECT 1 FROM users')).rowCount,0);
  // Different users/codes must not share a global 10/hour verification bucket.
  for(let i=0;i<11;i++) assert.equal((await call('/v1/auth/verify',{token:randomBytes(32).toString('base64url')})).status,401);
  const user=(await db.pool.query("INSERT INTO users(email,display_name,adult_confirmed_at) VALUES('expired@example.invalid','Expired',now()) RETURNING id")).rows[0].id;
  const expired=randomBytes(32).toString('base64url');
  await db.pool.query("INSERT INTO account_tokens(user_id,purpose,token_hash,created_at,expires_at) VALUES($1,'verify_email',$2,now()-interval '2 hours',now()-interval '1 hour')",[user,tokenHash(expired)]);
  assert.equal((await call('/v1/auth/verify',{token:expired})).status,401);
  assert.equal((await db.pool.query('SELECT email_verified_at FROM users WHERE id=$1',[user])).rows[0].email_verified_at,null);
  const oldSession=(await db.pool.query("INSERT INTO sessions(user_id,token_hash,created_at,expires_at,revoked_at) VALUES($1,$2,now()-interval '2 days',now()-interval '1 day',now()-interval '2 hours') RETURNING id",[user,tokenHash(randomUUID())])).rows[0].id;
  const freshSession=(await db.pool.query("INSERT INTO sessions(user_id,token_hash,expires_at) VALUES($1,$2,now()+interval '1 hour') RETURNING id",[user,tokenHash(randomUUID())])).rows[0].id;
  const oldRefresh=randomBytes(32).toString('base64url');
  await db.pool.query("INSERT INTO refresh_tokens(token_hash,user_id,session_id,created_at,expires_at,consumed_at) VALUES($1,$2,$3,now()-interval '3 hours',now()-interval '1 hour',now()-interval '2 hours')",[tokenHash(oldRefresh),user,oldSession]);
  assert.equal((await call('/v1/auth/refresh',{refreshToken:oldRefresh})).status,401);
  assert.equal((await db.pool.query('SELECT revoked_at FROM sessions WHERE id=$1',[freshSession])).rows[0].revoked_at,null);
 } finally {await app.close();mail.close();await db.close();}
});
