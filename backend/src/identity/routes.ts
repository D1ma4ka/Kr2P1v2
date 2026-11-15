import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import {Accounts} from './accounts.js';
import type {AccountMailer} from '../mail/accounts.js';
import * as s from '../http/schemas.js';
const email={type:'string',format:'email',maxLength:254,pattern:'^[^\\s]+$'};
const password={type:'string',minLength:12,maxLength:128};
const token={type:'string',pattern:'^[A-Za-z0-9_-]{43}$'};
const accepted=s.object({message:{type:'string'}});
const pair=s.object({userId:s.uuid,accessToken:token,refreshToken:token,expiresAt:s.timestamp});
export async function accountRoutes(app:FastifyInstance,pool:pg.Pool,mail:AccountMailer,localMail=true) {
 const accounts=new Accounts(pool,mail);
 const generic={message:localMail
  ? 'Если данные подходят, письмо отправлено в локальный Mailpit. Доставка во внешний почтовый ящик не настроена.'
  : 'Если данные подходят, письмо отправлено. Проверьте почту.'};
 app.post<{Body:{email:string;password:string;displayName:string;adultConfirmed:boolean}}>('/v1/auth/register',{
  schema:{body:s.object({email,password,displayName:{type:'string',minLength:1,maxLength:80,pattern:'\\S'},adultConfirmed:{type:'boolean'}}),response:{...s.errors,202:accepted}}},async(req,reply)=>{
  await accounts.budget(req.ip,req.body.email,'register');
  await accounts.signup(req.body.email,req.body.password,req.body.displayName,req.body.adultConfirmed);return reply.code(202).send(generic);
 });
 app.post<{Body:{email:string;password:string}}>('/v1/auth/login',{schema:{body:s.object({email,password:{type:'string',minLength:1,maxLength:128}}),response:{...s.errors,200:pair}}},async req=>{
  await accounts.budget(req.ip,req.body.email,'login');return accounts.login(req.body.email,req.body.password);
 });
 for(const [path,purpose] of [['resend','verify_email'],['forgot-password','reset_password']] as const) {
  app.post<{Body:{email:string}}>(`/v1/auth/${path}`,{schema:{body:s.object({email}),response:{...s.errors,202:accepted}}},async(req,reply)=>{
   await accounts.budget(req.ip,req.body.email,path);await accounts.request(req.body.email,purpose);return reply.code(202).send(generic);
  });
 }
 app.post<{Body:{token:string}}>('/v1/auth/verify',{schema:{body:s.object({token}),response:{...s.errors,204:{type:'null'}}}},async(req,reply)=>{
  await accounts.budget(req.ip,req.body.token,'verify');await accounts.consume(req.body.token,'verify_email');return reply.code(204).send();
 });
 app.post<{Body:{email:string;code:string}}>('/v1/auth/verify-code',{schema:{body:s.object({email,code:{type:'string',pattern:'^[0-9]{6}$'}}),response:{...s.errors,204:{type:'null'}}}},async(req,reply)=>{
  await accounts.budget(req.ip,req.body.email,'verify-code');await accounts.consumeCode(req.body.email,req.body.code);return reply.code(204).send();
 });
 app.post<{Body:{token:string;password:string}}>('/v1/auth/reset-password',{schema:{body:s.object({token,password}),response:{...s.errors,204:{type:'null'}}}},async(req,reply)=>{
  await accounts.budget(req.ip,req.body.token,'reset');await accounts.consume(req.body.token,'reset_password',req.body.password);return reply.code(204).send();
 });
 app.post<{Body:{email:string;code:string;password:string}}>('/v1/auth/reset-password-code',{
  schema:{body:s.object({email,code:{type:'string',pattern:'^[0-9]{6}$'},password}),response:{...s.errors,204:{type:'null'}}}},async(req,reply)=>{
  await accounts.budget(req.ip,req.body.email,'reset-code');
  await accounts.consumeCode(req.body.email,req.body.code,'reset_password',req.body.password);return reply.code(204).send();
 });
 app.post<{Body:{refreshToken:string}}>('/v1/auth/refresh',{schema:{body:s.object({refreshToken:token}),response:{...s.errors,200:pair}}},async req=>{
  await accounts.budget(req.ip,req.body.refreshToken,'refresh');return accounts.refresh(req.body.refreshToken);
 });
}
