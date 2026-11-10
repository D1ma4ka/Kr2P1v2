import assert from 'node:assert/strict';
import {test} from 'node:test';
import {randomUUID} from 'node:crypto';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {testDatabase} from './database.js';

test('P6.1 email-bound numeric verification: single use, expiry, atomic guess limit and resend invalidation',async()=>{
 const db=await testDatabase();await migrate(db.pool);
 const sent=new Map<string,string>();
 const mail={async send(to:string,_subject:string,text:string){sent.set(to,text);},close(){}};
 const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,undefined,mail);
 const call=(path:string,body:unknown)=>app.inject({method:'POST',url:path,headers:{'content-type':'application/json'},payload:JSON.stringify(body)});
 const password='Synthetic numeric code password!';
 const register=async()=>{
  const email=randomUUID()+'@example.invalid';
  assert.equal((await call('/v1/auth/register',{email,password,displayName:'Code test',adultConfirmed:true})).statusCode,202);
  const code=sent.get(email)?.match(/Код подтверждения: ([0-9]{6})/)?.[1];
  assert.ok(code,'mail must contain a six-digit verification code');return {email,code};
 };
 try {
  const a=await register();
  assert.equal((await call('/v1/auth/verify-code',{email:'other@example.invalid',code:a.code})).statusCode,401);
  assert.equal((await call('/v1/auth/verify-code',{email:a.email,code:'12345'})).statusCode,400);
  assert.equal((await call('/v1/auth/verify-code',{email:a.email,code:a.code})).statusCode,204);
  assert.equal((await call('/v1/auth/verify-code',{email:a.email,code:a.code})).statusCode,401);
  assert.equal((await call('/v1/auth/login',{email:a.email,password})).statusCode,200);
  // Old credential is invalidated by successful numeric verification.
  const legacy=sent.get(a.email)!.match(/Код: ([A-Za-z0-9_-]{43})/)![1]!;
  assert.equal((await call('/v1/auth/verify',{token:legacy})).statusCode,401);
  const expired=await register();
  await db.pool.query("UPDATE email_verification_codes SET expires_at=now()-interval '1 second' WHERE user_id=(SELECT id FROM users WHERE email=$1)",[expired.email]);
  assert.equal((await call('/v1/auth/verify-code',expired)).statusCode,401);
  const guessed=await register();
  const wrong=guessed.code==='000000'?'000001':'000000';
  const results=await Promise.all(Array.from({length:5},()=>call('/v1/auth/verify-code',{email:guessed.email,code:wrong})));
  assert.ok(results.every(r=>r.statusCode===401));
  assert.equal((await call('/v1/auth/verify-code',guessed)).statusCode,401);
  const row=(await db.pool.query('SELECT attempts,code_hash FROM email_verification_codes WHERE user_id=(SELECT id FROM users WHERE email=$1)',[guessed.email])).rows[0];
  assert.equal(row.attempts,5);assert.ok(row.code_hash.startsWith('scrypt-v1$'));assert.ok(!row.code_hash.includes(guessed.code));
  const resend=await register();
  await db.pool.query("UPDATE account_tokens SET created_at=now()-interval '2 minutes' WHERE user_id=(SELECT id FROM users WHERE email=$1)",[resend.email]);
  assert.equal((await call('/v1/auth/resend',{email:resend.email})).statusCode,202);
  const current=sent.get(resend.email)!.match(/Код подтверждения: ([0-9]{6})/)![1]!;
  if(current!==resend.code) assert.equal((await call('/v1/auth/verify-code',resend)).statusCode,401);
  assert.equal((await call('/v1/auth/verify-code',{email:resend.email,code:current})).statusCode,204);
  const legacyUser=await register();
  const old=sent.get(legacyUser.email)!.match(/Код: ([A-Za-z0-9_-]{43})/)![1]!;
  assert.equal((await call('/v1/auth/verify',{token:old})).statusCode,204);
  assert.equal((await call('/v1/auth/verify-code',legacyUser)).statusCode,401);
 } finally {await app.close();await db.close();}
});
