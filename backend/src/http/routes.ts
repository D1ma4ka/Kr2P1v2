import {petRoutes} from '../pets/routes.js';
import {menuRoutes} from '../menus/routes.js';
import {nearbyFindsRoutes} from "../nearby-finds/routes.js";
import {discoveryRoutes} from '../discovery/routes.js';
import {badgeRoutes} from '../badges/routes.js';
import {venueRoutes} from '../venues/routes.js';
import {moderationRoutes} from '../moderation/routes.js';
import {socialRoutes} from '../social/routes.js';
import {catalogRoutes} from '../catalog/routes.js';
import { mediaRoutes } from "../media/routes.js";
import type { MediaService } from "../media/service.js";
import type { FastifyInstance } from "fastify";
import type pg from "pg";
import { authenticate, tokenHash } from "../identity/sessions.js";
import {diaryRoutes} from "../checkins/routes.js";
import * as s from "./schemas.js";
declare module "fastify" { interface FastifyRequest { userId: string; } }
import {profile,updateProfile,type ProfileUpdate} from '../profiles/repository.js';
import {normalizeHandle,handleConflict} from '../identity/handles.js';
export async function foundationRoutes(app: FastifyInstance,pool: pg.Pool, media?: MediaService, environment='production') {
  app.decorateRequest("userId", "");
  app.addHook("onRequest",async request => { request.userId = await authenticate(pool,request.headers.authorization); });
  if(media) app.register(async mediaApi=>mediaRoutes(mediaApi,media));
  const secure = { security: [{ bearerAuth: [] }], response: s.errors };
  app.get("/v1/me", { schema: { ...secure, response: { ...s.errors,200:s.profileSchema } } },async request => {
    return profile(pool,request.userId);
  });
  app.put<{Body:{handle:string}}>('/v1/me/handle',{schema:{...secure,
    body:s.object({handle:{type:'string',minLength:3,maxLength:24}}),response:{...s.errors,200:s.profileSchema}}},async req=>{
    const handle=normalizeHandle(req.body.handle);
    const result=await pool.query("UPDATE users SET handle=$2,updated_at=now() WHERE id=$1 AND (handle IS NULL OR handle=$2) RETURNING id",[req.userId,handle]).catch(handleConflict);
    if(!result.rowCount) {const {ApiError}=await import('./errors.js');throw new ApiError(409,'HANDLE_LOCKED','Никнейм уже выбран');}
    return profile(pool,req.userId);
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
  await petRoutes(app,pool,environment!=='production');
  await diaryRoutes(app,pool,environment!=='production');
  await socialRoutes(app,pool);
  await venueRoutes(app,pool,environment!=='production');
  await menuRoutes(app,pool,environment!=='production');
  await nearbyFindsRoutes(app,pool,environment!=="production");
  await badgeRoutes(app,pool);
  await discoveryRoutes(app,pool,environment!=='production');
  await moderationRoutes(app,pool,environment!=='production');
}
