import {randomBytes} from 'node:crypto';
import type pg from 'pg';
import {transaction} from '../db/pool.js';
import {ApiError} from '../http/errors.js';
import {tokenHash} from './sessions.js';
import {hashPassword,checkPassword} from './passwords.js';
import type {AccountMailer} from '../mail/accounts.js';
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
 async signup(email:string,password:string,displayName:string,adult:boolean) {
  if(!adult) throw new ApiError(422,'ADULT_REQUIRED','KraPiva доступна только с 18 лет');
  const hash=await hashPassword(password);
  await transaction(this.pool,async client=>{
   const result=await client.query(`INSERT INTO users(email,password_hash,display_name,adult_confirmed_at) VALUES($1,$2,$3,now())
    ON CONFLICT(email) DO NOTHING RETURNING id`,[email.toLowerCase(),hash,displayName.trim()]);
   if(!result.rows[0]) return;
   const id=result.rows[0].id;
   await client.query('INSERT INTO user_profiles(user_id) VALUES($1)',[id]);
   await this.sendToken(client,id,email,'verify_email');
  });
 }
 private async sendToken(client:pg.PoolClient,id:string,email:string,purpose:'verify_email'|'reset_password') {
  const recent=await client.query("SELECT 1 FROM account_tokens WHERE user_id=$1 AND purpose=$2 AND created_at>now()-interval '1 minute'",[id,purpose]);
  if(recent.rowCount) return;
  const token=randomBytes(32).toString('base64url');
  await client.query('UPDATE account_tokens SET consumed_at=coalesce(consumed_at,now()) WHERE user_id=$1 AND purpose=$2',[id,purpose]);
  await client.query(`INSERT INTO account_tokens(user_id,purpose,token_hash,expires_at) VALUES($1,$2,$3,now()+interval '30 minutes')`,[id,purpose,tokenHash(token)]);
  try {await this.mail.send(email,purpose==='verify_email'?'KraPiva — подтверждение почты':'KraPiva — восстановление доступа',
   `Откройте KraPiva и вставьте одноразовый код в ${purpose==='verify_email'?'форму подтверждения почты':'форму нового пароля'}.\nКод: ${token}\nСрок: 30 минут. Если вы не запрашивали письмо, проигнорируйте его.`);}
  catch {throw new ApiError(503,'MAIL_UNAVAILABLE','Почта временно недоступна; повторите позже');}
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
   if(purpose==='verify_email') await client.query('UPDATE users SET email_verified_at=coalesce(email_verified_at,now()),updated_at=now() WHERE id=$1',[u.id]);
   else {
    await client.query('UPDATE users SET password_hash=$2,updated_at=now() WHERE id=$1',[u.id,hash]);
    await client.query('UPDATE sessions SET revoked_at=coalesce(revoked_at,now()) WHERE user_id=$1',[u.id]);
    await client.query("UPDATE account_tokens SET consumed_at=coalesce(consumed_at,now()) WHERE user_id=$1 AND purpose='reset_password'",[u.id]);
   }
  });
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
   if(row.consumed_at) {
    await client.query('UPDATE sessions SET revoked_at=coalesce(revoked_at,now()) WHERE user_id=$1',[user.id]);
    return null; // commit replay revocation before reporting 401
   }
   if(row.revoked_at||new Date(row.expires_at).getTime()<=Date.now()) return null;
   await client.query('UPDATE refresh_tokens SET consumed_at=now() WHERE token_hash=$1',[tokenHash(token)]);
   await client.query('UPDATE sessions SET revoked_at=now() WHERE id=$1',[row.session_id]);
   return this.pair(client,user.id);
  });
  if(!result) throw invalid(); return result;
 }
}
