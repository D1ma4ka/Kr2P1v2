import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import {Accounts} from './accounts.js';
import type {AccountMailer} from '../mail/accounts.js';
import * as s from '../http/schemas.js';
import {normalizeHandle} from './handles.js';
const email={type:'string',format:'email',maxLength:254,pattern:'^[^\\s]+$'};
const password={type:'string',minLength:8,maxLength:128};
const token={type:'string',pattern:'^[A-Za-z0-9_-]{43}$'};
const accepted=s.object({message:{type:'string'}});
const pair=s.object({userId:s.uuid,accessToken:token,refreshToken:token,expiresAt:s.timestamp});
export async function accountRoutes(app:FastifyInstance,pool:pg.Pool,mail:AccountMailer) {
 const accounts=new Accounts(pool,mail);
 const generic={message:'Если данные подходят, письмо отправлено. Проверьте почту.'};
 app.post<{Body:{email:string;password:string;displayName:string;adultConfirmed:boolean;handle?:string}}>('/v1/auth/register',{
  schema:{body:s.object({email,password,displayName:{type:'string',minLength:1,maxLength:80,pattern:'\\S'},adultConfirmed:{type:'boolean'},handle:{type:'string',minLength:3,maxLength:24}},['email','password','displayName','adultConfirmed']),response:{...s.errors,202:accepted}}},async(req,reply)=>{
  await accounts.budget(req.ip,req.body.email,'register');
  await accounts.signup(req.body.email,req.body.password,req.body.displayName,req.body.adultConfirmed,req.body.handle);return reply.code(202).send(generic);
 });
 app.get<{Params:{handle:string}}>('/v1/auth/handles/:handle',{
  schema:{params:s.object({handle:{type:'string',minLength:3,maxLength:24}}),response:{...s.errors,200:s.object({available:{type:'boolean'}})}}},async req=>{
  const handle=normalizeHandle(req.params.handle);await accounts.budget(req.ip,handle,'handle-check');
  return {available:!(await pool.query('SELECT 1 FROM users WHERE handle=$1',[handle])).rowCount};
 });
 app.post<{Body:{email?:string;identifier?:string;password:string}}>('/v1/auth/login',{schema:{body:{
  ...s.object({email,identifier:{type:'string',minLength:3,maxLength:254,pattern:'^[^\\s]+$'},password:{type:'string',minLength:1,maxLength:128}},['password']),
  oneOf:[{required:['email']},{required:['identifier']}]},response:{...s.errors,200:pair}}},async req=>{
  const identity=req.body.identifier??req.body.email!;
  await accounts.budget(req.ip,identity.replace(/^@/,''),'login');return accounts.login(identity,req.body.password);
 });
 for(const [path,purpose] of [['resend','verify_email'],['forgot-password','reset_password']] as const) {
  app.post<{Body:{email:string}}>(`/v1/auth/${path}`,{schema:{body:s.object({email}),response:{...s.errors,202:accepted}}},async(req,reply)=>{
   await accounts.budget(req.ip,req.body.email,path);await accounts.request(req.body.email,purpose);return reply.code(202).send(generic);
  });
 }
 app.post<{Body:{token:string}}>('/v1/auth/verify',{schema:{body:s.object({token}),response:{...s.errors,204:{type:'null'}}}},async(req,reply)=>{
  await accounts.budget(req.ip,req.body.token,'verify');await accounts.consume(req.body.token,'verify_email');return reply.code(204).send();
 });
 app.post<{Body:{email:string;code:string;createSession?:boolean}}>('/v1/auth/verify-code',{
  schema:{body:s.object({email,code:{type:'string',pattern:'^[0-9]{6}$'},createSession:{type:'boolean',description:'Return a session with confirmation. Omit for legacy 204.'}},['email','code']),
   response:{...s.errors,200:pair,204:{type:'null'}}}
 },async(req,reply)=>{
  await accounts.budget(req.ip,req.body.email,'verify-code');
  const session=await accounts.consumeCode(req.body.email,req.body.code,{purpose:'verify_email',createSession:req.body.createSession===true});
  return session ? reply.header('Cache-Control','no-store').code(200).send(session) : reply.code(204).send();
 });
 app.post<{Body:{token:string;password:string}}>('/v1/auth/reset-password',{schema:{body:s.object({token,password}),response:{...s.errors,204:{type:'null'}}}},async(req,reply)=>{
  await accounts.budget(req.ip,req.body.token,'reset');await accounts.consume(req.body.token,'reset_password',req.body.password);return reply.code(204).send();
 });
 app.post<{Body:{email:string;code:string;password:string}}>('/v1/auth/reset-password-code',{
  schema:{body:s.object({email,code:{type:'string',pattern:'^[0-9]{6}$'},password}),response:{...s.errors,204:{type:'null'}}}},async(req,reply)=>{
  await accounts.budget(req.ip,req.body.email,'reset-code');
  await accounts.consumeCode(req.body.email,req.body.code,{purpose:'reset_password',password:req.body.password});return reply.code(204).send();
 });
 app.post<{Body:{refreshToken:string}}>('/v1/auth/refresh',{schema:{body:s.object({refreshToken:token}),response:{...s.errors,200:pair}}},async req=>{
  await accounts.budget(req.ip,req.body.refreshToken,'refresh');return accounts.refresh(req.body.refreshToken);
 });
}
