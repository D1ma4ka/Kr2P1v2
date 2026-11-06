import {catalogRoutes} from '../catalog/routes.js';
import { mediaRoutes } from "../media/routes.js";
import type { MediaService } from "../media/service.js";
import type { FastifyInstance } from "fastify";
import type pg from "pg";
import { authenticate, tokenHash } from "../identity/sessions.js";
import { createCheckin, deleteCheckin, getCheckin, listCheckins, updateCheckin, type CheckinInput } from "../checkins/repository.js";
import * as s from "./schemas.js";
declare module "fastify" { interface FastifyRequest { userId: string; } }
import {profile,updateProfile,type ProfileUpdate} from '../profiles/repository.js';
export async function foundationRoutes(app: FastifyInstance,pool: pg.Pool, media?: MediaService) {
  app.decorateRequest("userId", "");
  app.addHook("onRequest",async request => { request.userId = await authenticate(pool,request.headers.authorization); });
  if(media) app.register(async mediaApi=>mediaRoutes(mediaApi,media));
  const secure = { security: [{ bearerAuth: [] }], response: s.errors };
  app.get("/v1/me", { schema: { ...secure, response: { ...s.errors,200:s.profileSchema } } },async request => {
    return profile(pool,request.userId);
  });
  app.get<{Params:{id:string}}>("/v1/users/:id",{schema:{...secure,params:s.idParams,response:{...s.errors,200:s.profileSchema}}},
    request=>profile(pool,request.params.id,request.userId));
  app.patch<{ Body: ProfileUpdate }>("/v1/me/profile", { schema: { ...secure,
    body:s.object({...s.profileFields,...s.extraProfileFields,version:s.version},[...Object.keys(s.profileFields),'version']),
    response:{...s.errors,200:s.profileSchema} } },request=>updateProfile(pool,request.userId,request.body));
  app.post('/v1/me/deletion-request',{schema:{...secure,response:{...s.errors,202:s.object({eraseAfter:s.timestamp,message:{type:'string'}})}}},async(request,reply)=>{
    const {transaction}=await import('../db/pool.js');
    const row=await transaction(pool,async client=>{
      await client.query('SELECT id FROM users WHERE id=$1 FOR UPDATE',[request.userId]);
      const result=await client.query('INSERT INTO account_deletion_requests(user_id) VALUES($1) ON CONFLICT(user_id) DO UPDATE SET user_id=EXCLUDED.user_id RETURNING erase_after',[request.userId]);
      await client.query("UPDATE users SET status='suspended',updated_at=now() WHERE id=$1",[request.userId]);
      await client.query('UPDATE sessions SET revoked_at=coalesce(revoked_at,now()) WHERE user_id=$1',[request.userId]);
      return result.rows[0];
    });
    return reply.code(202).send({eraseAfter:row.erase_after,message:'Запрос принят. Доступ и публичность отключены. Удаление личных материалов — в течение 30 дней; обязательные по закону записи сохраняются только при законном основании.'});
  });
  app.delete("/v1/session", { schema:{...secure,response:{...s.errors,204:{type:'null'}}} },async(request,reply)=>{
    await pool.query("UPDATE sessions SET revoked_at=now() WHERE token_hash=$1",[tokenHash(request.headers.authorization!.slice(7))]);
    return reply.code(204).send();
  });
  await catalogRoutes(app,pool);
  app.get<{ Querystring:{limit?:string;cursor?:string} }>("/v1/checkins", {schema:{...secure,querystring:s.pageQuery,response:{...s.errors,200:s.page(s.checkinSchema)}}},
    request=>listCheckins(pool,request.userId,Number(request.query.limit??20),request.query.cursor));
  app.get<{ Params:{id:string} }>("/v1/checkins/:id",{schema:{...secure,params:s.idParams,response:{...s.errors,200:s.checkinSchema}}},
    request=>getCheckin(pool,request.userId,request.params.id));
  app.post<{ Body:CheckinInput;Headers:{'idempotency-key':string} }>("/v1/checkins",{schema:{...secure,
    headers:{type:'object',required:['idempotency-key'],properties:{'idempotency-key':s.uuid}},
    body:s.object(s.checkinFields),response:{...s.errors,200:s.checkinSchema,201:s.checkinSchema}}},async(request,reply)=>{
    const result=await createCheckin(pool,request.userId,request.headers['idempotency-key'],request.body);
    return reply.code(result.replay?200:201).send(result.row);
  });
  const {positionId:_positionId,...mutableFields}=s.checkinFields;
  app.patch<{Params:{id:string};Body:Omit<CheckinInput,'positionId'>&{version:number}}>("/v1/checkins/:id",{schema:{...secure,
    params:s.idParams,body:s.object({...mutableFields,version:s.version}),response:{...s.errors,200:s.checkinSchema}}},
    request=>updateCheckin(pool,request.userId,request.params.id,request.body));
  app.delete<{Params:{id:string};Body:{version:number}}>("/v1/checkins/:id",{schema:{...secure,params:s.idParams,
    body:s.object({version:s.version}),response:{...s.errors,204:{type:'null'}}}},async(request,reply)=>{
    await deleteCheckin(pool,request.userId,request.params.id,request.body.version); return reply.code(204).send();
  });
}
