import assert from 'node:assert/strict';
import {test} from 'node:test';
import {randomUUID,randomBytes} from 'node:crypto';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {testDatabase} from './database.js';
import {tokenHash} from '../src/identity/sessions.js';
import {hashPassword} from '../src/identity/passwords.js';
import {accountMailer} from '../src/mail/accounts.js';

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
  assert.ok(code,'mail must contain a six-digit verification code');
  assert.doesNotMatch(sent.get(email)!,/Код:|[A-Za-z0-9_-]{43}/,'one unambiguous code per verification email');
  return {email,code};
 };
 try {
  const legacyToken=async(email:string)=>{
   const token=randomBytes(32).toString('base64url');
   await db.pool.query("UPDATE account_tokens SET token_hash=$2 WHERE user_id=(SELECT id FROM users WHERE email=$1) AND purpose='verify_email' AND consumed_at IS NULL",[email,tokenHash(token)]);
   return token;
  };
  const a=await register();
  const legacy=await legacyToken(a.email);
  assert.equal((await call('/v1/auth/verify-code',{email:'other@example.invalid',code:a.code})).statusCode,401);
  for(const code of ['12345','1234567','12345a','１２３４５６',' '+a.code])
   assert.equal((await call('/v1/auth/verify-code',{email:a.email,code})).statusCode,400);
  assert.equal((await db.pool.query('SELECT attempts FROM email_verification_codes WHERE user_id=(SELECT id FROM users WHERE email=$1)',[a.email])).rows[0].attempts,0);
  assert.equal((await call('/v1/auth/verify-code',{email:a.email,code:a.code})).statusCode,204);
  assert.equal((await call('/v1/auth/verify-code',{email:a.email,code:a.code})).statusCode,401);
  assert.equal((await call('/v1/auth/login',{email:a.email,password})).statusCode,200);
  // Old credential is invalidated by successful numeric verification.
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
  const firstMail=sent.get(resend.email);
  assert.equal((await call('/v1/auth/resend',{email:resend.email})).statusCode,202);
  assert.equal(sent.get(resend.email),firstMail,'one-minute resend cooldown remains effective');
  await db.pool.query("UPDATE account_tokens SET created_at=now()-interval '2 minutes' WHERE user_id=(SELECT id FROM users WHERE email=$1)",[resend.email]);
  assert.equal((await call('/v1/auth/resend',{email:resend.email})).statusCode,202);
  const current=sent.get(resend.email)!.match(/Код подтверждения: ([0-9]{6})/)![1]!;
  if(current!==resend.code) assert.equal((await call('/v1/auth/verify-code',resend)).statusCode,401);
  assert.equal((await call('/v1/auth/verify-code',{email:resend.email,code:current})).statusCode,204);
  const legacyUser=await register();
  const old=await legacyToken(legacyUser.email);
  assert.equal((await call('/v1/auth/verify',{token:old})).statusCode,204);
  assert.equal((await call('/v1/auth/verify-code',legacyUser)).statusCode,401);
  const leading=await register();
  await db.pool.query('UPDATE email_verification_codes SET code_hash=$2 WHERE user_id=(SELECT id FROM users WHERE email=$1)',[leading.email,await hashPassword('000042')]);
  assert.equal((await call('/v1/auth/verify-code',{email:leading.email,code:'000042'})).statusCode,204);
  const auto=await register();
  const confirmations=await Promise.all([
   call('/v1/auth/verify-code',{...auto,createSession:true}),
   call('/v1/auth/verify-code',{...auto,createSession:true}),
  ]);
  assert.deepEqual(confirmations.map(result=>result.statusCode).sort(),[200,401]);
  const session=confirmations.find(result=>result.statusCode===200)!.json();
  const sessions=await db.pool.query('SELECT token_hash FROM sessions WHERE user_id=$1',[session.userId]);
  assert.deepEqual(sessions.rows,[{token_hash:tokenHash(session.accessToken)}]);
  const refresh=await db.pool.query('SELECT token_hash FROM refresh_tokens WHERE user_id=$1',[session.userId]);
  assert.deepEqual(refresh.rows,[{token_hash:tokenHash(session.refreshToken)}]);
 } finally {await app.close();await db.close();}
});

test('real HTTP and Mailpit: one six-digit OTP, persisted email and authenticated profile after server restart',async()=>{
 const db=await testDatabase();await migrate(db.pool);
 let app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,undefined,accountMailer({...process.env,NODE_ENV:'test'}));
 let address=await app.listen({host:'127.0.0.1',port:0});
 const email=`otp-${randomUUID()}@example.invalid`,password='Synthetic OTP password!';
 const call=(path:string,body:unknown)=>fetch(address+path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body)});
 try {
  assert.equal((await call('/v1/auth/register',{email,password,displayName:'OTP HTTP',adultConfirmed:true})).status,202);
  assert.equal((await call('/v1/auth/login',{email,password})).status,401);
  const mailbox=process.env.MAILPIT_API_URL??'http://127.0.0.1:58025';
  const search=await fetch(mailbox+'/api/v1/search?query='+encodeURIComponent('to:'+email));assert.equal(search.status,200);
  const messages=await search.json() as {messages:{ID:string}[]};assert.equal(messages.messages.length,1);
  const response=await fetch(mailbox+'/api/v1/message/'+messages.messages[0]!.ID);assert.equal(response.status,200);
  const message=await response.json() as {Text:string};
  const codes=[...message.Text.matchAll(/^Код подтверждения: ([0-9]{6})$/gm)];assert.equal(codes.length,1);
  assert.doesNotMatch(message.Text,/Код:|[A-Za-z0-9_-]{43}/);
  const verified=await call('/v1/auth/verify-code',{email,code:codes[0]![1],createSession:true});
  assert.equal(verified.status,200);
  assert.equal(verified.headers.get('cache-control'),'no-store');
  const session=await verified.json() as {userId:string;accessToken:string;refreshToken:string};
  await app.close();
  app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,undefined,accountMailer({...process.env,NODE_ENV:'test'}));
  address=await app.listen({host:'127.0.0.1',port:0});
  const profile=await fetch(address+'/v1/me',{headers:{authorization:`Bearer ${session.accessToken}`}});assert.equal(profile.status,200);
  assert.equal((await call('/v1/auth/refresh',{refreshToken:session.refreshToken})).status,200);
  assert.equal((await call('/v1/auth/login',{email,password})).status,200);
  const saved=await db.pool.query('SELECT email,email_verified_at FROM users WHERE id=$1',[session.userId]);
  assert.equal(saved.rows[0].email,email);assert.ok(saved.rows[0].email_verified_at);
  assert.equal((await call('/v1/auth/verify-code',{email,code:codes[0]![1]})).status,401);
 } finally {await app.close();await db.close();}
});
