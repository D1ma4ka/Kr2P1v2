import assert from 'node:assert/strict';
import {test} from 'node:test';
import {randomBytes,randomUUID} from 'node:crypto';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {testDatabase} from './database.js';
import {hashPassword} from '../src/identity/passwords.js';
import {tokenHash} from '../src/identity/sessions.js';

test('P8.2 real HTTP/SQL/Mailpit: existing hash, numeric reset, resend, expiry, atomic guesses and legacy compatibility',async()=>{
 const db=await testDatabase();await migrate(db.pool);
 let app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
 let address=await app.listen({host:'127.0.0.1',port:0});
 const call=(path:string,body:unknown)=>fetch(address+path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body)});
 const oldPassword='old pass',password='New synthetic P8.2 password!';
 const originalHash=await hashPassword(oldPassword);
 const fixture=async()=>{
  const email=`reset-${randomUUID()}@example.invalid`;
  const id=(await db.pool.query(`INSERT INTO users(email,password_hash,display_name,adult_confirmed_at,email_verified_at)
   VALUES($1,$2,'Existing account',now(),now()) RETURNING id`,[email,originalHash])).rows[0].id;
  return {email,id};
 };
 const request=async(email:string)=>assert.equal((await call('/v1/auth/forgot-password',{email})).status,202);
 const letter=async(email:string)=>{
  const base=process.env.MAILPIT_API_URL??'http://127.0.0.1:58025';
  const response=await fetch(base+'/api/v1/search?query='+encodeURIComponent('to:'+email));assert.equal(response.status,200);
  const list=await response.json() as {messages:{ID:string}[]};assert.ok(list.messages.length);
  const message=await (await fetch(base+'/api/v1/message/'+list.messages[0]!.ID)).json() as {Text:string};
  const codes=[...message.Text.matchAll(/^Код восстановления: ([0-9]{6})$/gm)];assert.equal(codes.length,1);
  assert.doesNotMatch(message.Text,/[A-Za-z0-9_-]{43}/);
  return {code:codes[0]![1]!,id:list.messages[0]!.ID,count:list.messages.length};
 };
 const reset=(email:string,code:string,next=password)=>call('/v1/auth/reset-password-code',{email,code,password:next});
 try {
  const a=await fixture();
  const login=await call('/v1/auth/login',{email:a.email,password:oldPassword});assert.equal(login.status,200);
  const session=await login.json() as {accessToken:string;refreshToken:string};
  await request(a.email);const first=await letter(a.email);
  assert.equal((await db.pool.query('SELECT password_hash FROM users WHERE id=$1',[a.id])).rows[0].password_hash,originalHash);
  assert.equal((await reset('unknown@example.invalid',first.code)).status,401);
  for(const code of ['12345','1234567','１２３４５６','12a456']) assert.equal((await reset(a.email,code)).status,400);
  assert.equal((await reset(a.email,first.code,'short')).status,400);
  await request(a.email);assert.equal((await letter(a.email)).id,first.id);
  await db.pool.query('UPDATE password_reset_codes SET code_hash=$2 WHERE user_id=$1',[a.id,await hashPassword('000042')]);
  assert.equal((await reset(a.email,'000042')).status,204);
  assert.equal((await reset(a.email,'000042')).status,401);
  assert.equal((await fetch(address+'/v1/me',{headers:{authorization:`Bearer ${session.accessToken}`}})).status,401);
  assert.equal((await call('/v1/auth/refresh',{refreshToken:session.refreshToken})).status,401);
  assert.equal((await call('/v1/auth/login',{email:a.email,password:oldPassword})).status,401);
  await app.close();app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
  address=await app.listen({host:'127.0.0.1',port:0});
  assert.equal((await call('/v1/auth/login',{email:a.email,password})).status,200);
  const resend=await fixture();await request(resend.email);const before=await letter(resend.email);
  await db.pool.query("UPDATE account_tokens SET created_at=now()-interval '2 minutes' WHERE user_id=$1",[resend.id]);
  await request(resend.email);const after=await letter(resend.email);assert.notEqual(after.id,before.id);assert.equal(after.count,2);
  if(before.code!==after.code) assert.equal((await reset(resend.email,before.code)).status,401);
  assert.equal((await reset(resend.email,after.code)).status,204);
  const expired=await fixture();await request(expired.email);const expiredCode=(await letter(expired.email)).code;
  await db.pool.query("UPDATE password_reset_codes SET expires_at=now()-interval '1 second' WHERE user_id=$1",[expired.id]);
  assert.equal((await reset(expired.email,expiredCode)).status,401);
  const guessed=await fixture();await request(guessed.email);const real=(await letter(guessed.email)).code;
  const wrong=real==='000000'?'000001':'000000';
  const results=await Promise.all(Array.from({length:5},()=>reset(guessed.email,wrong)));
  assert.ok(results.every(r=>r.status===401));assert.equal((await reset(guessed.email,real)).status,401);
  const stored=(await db.pool.query('SELECT attempts,code_hash FROM password_reset_codes WHERE user_id=$1',[guessed.id])).rows[0];
  assert.equal(stored.attempts,5);assert.ok(stored.code_hash.startsWith('scrypt-v1$'));assert.ok(!stored.code_hash.includes(real));
  const legacy=await fixture();await request(legacy.email);const otp=(await letter(legacy.email)).code;
  const token=randomBytes(32).toString('base64url');
  await db.pool.query("UPDATE account_tokens SET token_hash=$2 WHERE user_id=$1 AND purpose='reset_password'",[legacy.id,tokenHash(token)]);
  assert.equal((await call('/v1/auth/reset-password',{token,password})).status,204);
  assert.equal((await reset(legacy.email,otp)).status,401);
  assert.equal((await call('/v1/auth/login',{email:legacy.email,password})).status,200);
 } finally {await app.close();await db.close();}
});
