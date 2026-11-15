import {randomBytes,randomInt} from 'node:crypto';
import type pg from 'pg';
import {transaction} from '../db/pool.js';
import {ApiError} from '../http/errors.js';
import {tokenHash} from './sessions.js';
import {hashPassword,checkPassword} from './passwords.js';
import type {AccountMailer} from '../mail/accounts.js';
import {normalizeHandle,handleConflict} from './handles.js';
const invalid=()=>new ApiError(401,'INVALID_CREDENTIALS','Неверные данные входа или код');
export class Accounts {
 constructor(private pool:pg.Pool,private mail:AccountMailer) {}
 async budget(ip:string,identity:string,operation:string) {
  for(const [key,limit] of [[`ip:${ip}`,60],[`${operation}:${identity.toLowerCase()}`,10]] as const) {
   const result=await this.pool.query(`INSERT INTO auth_budgets(key_hash,window_start,count) VALUES($1,date_trunc('hour',now()),1)
    ON CONFLICT(key_hash) DO UPDATE SET count=CASE WHEN auth_budgets.window_start=date_trunc('hour',now()) THEN least(auth_budgets.count+1,1000) ELSE 1 END,
    window_start=date_trunc('hour',now()) RETURNING count`,[tokenHash(key)]);
   if(result.rows[0].count>limit) throw new ApiError(429,'RATE_LIMITED','Слишком много попыток; повторите позже');
  }
 }
 async signup(email:string,password:string,displayName:string,adult:boolean,rawHandle?:string) {
  const handle=rawHandle===undefined?null:normalizeHandle(rawHandle);
  if(!adult) throw new ApiError(422,'ADULT_REQUIRED','KraPiva доступна только с 18 лет');
  const hash=await hashPassword(password);
  await transaction(this.pool,async client=>{
   const result=await client.query(`INSERT INTO users(email,password_hash,display_name,adult_confirmed_at,handle) VALUES($1,$2,$3,now(),$4)
    ON CONFLICT(email) DO NOTHING RETURNING id`,[email.toLowerCase(),hash,displayName.trim(),handle]);
   if(!result.rows[0]) return;
   const id=result.rows[0].id;
   await client.query('INSERT INTO user_profiles(user_id) VALUES($1)',[id]);
   await this.sendToken(client,id,email,'verify_email');
  }).catch(handleConflict);
 }
 private async sendToken(client:pg.PoolClient,id:string,email:string,purpose:'verify_email'|'reset_password') {
  const recent=await client.query("SELECT 1 FROM account_tokens WHERE user_id=$1 AND purpose=$2 AND created_at>now()-interval '1 minute'",[id,purpose]);
  if(recent.rowCount) return;
  const token=randomBytes(32).toString('base64url');
  await client.query('UPDATE account_tokens SET consumed_at=coalesce(consumed_at,now()) WHERE user_id=$1 AND purpose=$2',[id,purpose]);
  await client.query(`INSERT INTO account_tokens(user_id,purpose,token_hash,expires_at) VALUES($1,$2,$3,now()+interval '30 minutes')`,[id,purpose,tokenHash(token)]);
  const numeric=randomInt(0,1_000_000).toString().padStart(6,'0');
  const table=purpose==='verify_email'?'email_verification_codes':'password_reset_codes';
  await client.query(`INSERT INTO ${table}(user_id,code_hash,expires_at) VALUES($1,$2,now()+interval '10 minutes')
    ON CONFLICT(user_id) DO UPDATE SET code_hash=excluded.code_hash,expires_at=excluded.expires_at,attempts=0,consumed_at=NULL,created_at=now()`,[id,await hashPassword(numeric)]);
  // A verification email contains one credential only. The legacy endpoint remains
  // available for previously issued tokens; new clients always use the six digits.
  const text=purpose==='verify_email'
   ? `Код подтверждения: ${numeric}\nВведите эти 6 цифр на экране подтверждения почты KraPiva.\nКод действует 10 минут; не более 5 попыток. Это не пароль аккаунта.`
   : `Код восстановления: ${numeric}\nВведите эти 6 цифр и ваш e-mail в форме нового пароля KraPiva.\nКод действует 10 минут; не более 5 попыток.`;
  try {await this.mail.send(email,purpose==='verify_email'?'KraPiva — подтверждение почты':'KraPiva — восстановление доступа',
   `${text}\nЕсли вы не запрашивали письмо, проигнорируйте его.`);}
  catch(error) {if(error instanceof ApiError) throw error;throw new ApiError(503,'MAIL_UNAVAILABLE','Почта временно недоступна; повторите позже');}
 }
 async request(email:string,purpose:'verify_email'|'reset_password') {
  await transaction(this.pool,async client=>{
   const user=(await client.query("SELECT id,email_verified_at FROM users WHERE email=$1 AND status='active' FOR UPDATE",[email.toLowerCase()])).rows[0];
   if(user&&(purpose==='reset_password'||!user.email_verified_at)) await this.sendToken(client,user.id,email,purpose);
  });
 }
 async consume(token:string,purpose:'verify_email'|'reset_password',password?:string) {
  const hash=password?await hashPassword(password):null;
  await transaction(this.pool,async client=>{
   // User lock precedes token lock consistently with resend/login/refresh.
   const u=(await client.query("SELECT u.id FROM users u JOIN account_tokens t ON t.user_id=u.id WHERE t.token_hash=$1 AND u.status='active' FOR UPDATE OF u",[tokenHash(token)])).rows[0];
   if(!u) throw invalid();
   const t=await client.query('UPDATE account_tokens SET consumed_at=now() WHERE token_hash=$1 AND purpose=$2 AND consumed_at IS NULL AND expires_at>now() RETURNING id',[tokenHash(token),purpose]);
   if(!t.rowCount) throw invalid();
   if(purpose==='verify_email') {
    await client.query('UPDATE users SET email_verified_at=coalesce(email_verified_at,now()),updated_at=now() WHERE id=$1',[u.id]);
    await client.query('UPDATE email_verification_codes SET consumed_at=now() WHERE user_id=$1',[u.id]);
   }
   else {
    await client.query('UPDATE users SET password_hash=$2,updated_at=now() WHERE id=$1',[u.id,hash]);
    await client.query('UPDATE sessions SET revoked_at=coalesce(revoked_at,now()) WHERE user_id=$1',[u.id]);
    await client.query("UPDATE account_tokens SET consumed_at=coalesce(consumed_at,now()) WHERE user_id=$1 AND purpose='reset_password'",[u.id]);
    await client.query('UPDATE password_reset_codes SET consumed_at=now() WHERE user_id=$1',[u.id]);
   }
  });
 }
 async consumeCode(email:string,code:string,purpose:'verify_email'|'reset_password'='verify_email',password?:string) {
  const table=purpose==='verify_email'?'email_verification_codes':'password_reset_codes';
  const success=await transaction(this.pool,async client=>{
   // Same user-first ordering as resend/legacy verification; serializes concurrent guesses.
   const user=(await client.query(`SELECT id FROM users WHERE email=$1 AND status='active' ${purpose==='verify_email'?'AND email_verified_at IS NULL':''} FOR UPDATE`,[email.toLowerCase()])).rows[0];
   if(!user) {await checkPassword(code,null);return false;}
   const row=(await client.query(`SELECT code_hash,attempts,consumed_at,expires_at>now() AS valid FROM ${table} WHERE user_id=$1 FOR UPDATE`,[user.id])).rows[0];
   if(!row||row.consumed_at||!row.valid||row.attempts>=5) {await checkPassword(code,null);return false;}
   await client.query(`UPDATE ${table} SET attempts=attempts+1 WHERE user_id=$1`,[user.id]);
   if(!await checkPassword(code,row.code_hash)) return false; // commit failed attempt, never rollback it
   await client.query(`UPDATE ${table} SET consumed_at=now() WHERE user_id=$1`,[user.id]);
   await client.query('UPDATE account_tokens SET consumed_at=coalesce(consumed_at,now()) WHERE user_id=$1 AND purpose=$2',[user.id,purpose]);
   if(purpose==='verify_email') await client.query('UPDATE users SET email_verified_at=now(),updated_at=now() WHERE id=$1',[user.id]);
   else {
    if(password===undefined) throw new Error('Password required for reset');
    await client.query('UPDATE users SET password_hash=$2,updated_at=now() WHERE id=$1',[user.id,await hashPassword(password)]);
    await client.query('UPDATE sessions SET revoked_at=coalesce(revoked_at,now()) WHERE user_id=$1',[user.id]);
   }
   return true;
  });
  if(!success) throw invalid();
 }
 private async pair(client:pg.PoolClient,userId:string) {
  const accessToken=randomBytes(32).toString('base64url'),refreshToken=randomBytes(32).toString('base64url');
  const row=(await client.query(`INSERT INTO sessions(user_id,token_hash,expires_at) VALUES($1,$2,now()+interval '1 hour') RETURNING id,expires_at`,[userId,tokenHash(accessToken)])).rows[0];
  await client.query("INSERT INTO refresh_tokens(token_hash,user_id,session_id,expires_at) VALUES($1,$2,$3,now()+interval '30 days')",[tokenHash(refreshToken),userId,row.id]);
  return {userId,accessToken,refreshToken,expiresAt:row.expires_at};
 }
 async login(email:string,password:string) {
  const candidate=(await this.pool.query('SELECT id,password_hash FROM users WHERE email=$1',[email.toLowerCase()])).rows[0];
  if(!await checkPassword(password,candidate?.password_hash??null)) throw invalid();
  return transaction(this.pool,async client=>{
   const user=(await client.query("SELECT * FROM users WHERE id=$1 AND status='active' AND email_verified_at IS NOT NULL FOR UPDATE",[candidate.id])).rows[0];
   if(!user||user.password_hash!==candidate.password_hash) throw invalid();
   return this.pair(client,user.id);
  });
 }
 async refresh(token:string) {
  const result=await transaction(this.pool,async client=>{
   const user=(await client.query("SELECT u.id FROM users u JOIN refresh_tokens r ON r.user_id=u.id WHERE r.token_hash=$1 AND u.status='active' FOR UPDATE OF u",[tokenHash(token)])).rows[0];
   if(!user) return null;
   const row=(await client.query('SELECT r.*,s.revoked_at FROM refresh_tokens r JOIN sessions s ON s.id=r.session_id WHERE r.token_hash=$1',[tokenHash(token)])).rows[0];
   if(new Date(row.expires_at).getTime()<=Date.now()) return null;
   if(row.consumed_at) {
    await client.query('UPDATE sessions SET revoked_at=coalesce(revoked_at,now()) WHERE user_id=$1',[user.id]);
    return null; // commit replay revocation before reporting 401
   }
   if(row.revoked_at) return null;
   await client.query('UPDATE refresh_tokens SET consumed_at=now() WHERE token_hash=$1',[tokenHash(token)]);
   await client.query('UPDATE sessions SET revoked_at=now() WHERE id=$1',[row.session_id]);
   return this.pair(client,user.id);
  });
  if(!result) throw invalid(); return result;
 }
}
