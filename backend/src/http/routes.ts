import type { FastifyInstance } from "fastify";
import type pg from "pg";
import { authenticate, tokenHash } from "../identity/sessions.js";
import { createCheckin, deleteCheckin, getCheckin, listCheckins, updateCheckin, type CheckinInput } from "../checkins/repository.js";
import { conflict, notFound } from "./errors.js";
import { decodeCursor, encodeCursor } from "./pagination.js";
import * as s from "./schemas.js";
declare module "fastify" { interface FastifyRequest { userId: string; } }
const profileColumns = `p.user_id AS "userId",u.display_name AS "displayName",p.first_name AS "firstName",p.last_name AS "lastName",
 p.country,p.city,p.bio,p.gender,p.gender_visibility AS "genderVisibility",p.profile_visibility AS "profileVisibility",p.version,p.updated_at AS "updatedAt"`;
interface ProfileUpdate { firstName: string|null; lastName: string|null; country: string|null; city: string|null;
 bio: string|null; gender: string|null; genderVisibility: string; profileVisibility: string; version: number; }
export async function foundationRoutes(app: FastifyInstance,pool: pg.Pool) {
  app.decorateRequest("userId", "");
  app.addHook("onRequest",async request => { request.userId = await authenticate(pool,request.headers.authorization); });
  const secure = { security: [{ bearerAuth: [] }], response: s.errors };
  app.get("/v1/me", { schema: { ...secure, response: { ...s.errors,200:s.profileSchema } } },async request => {
    const result = await pool.query(`SELECT ${profileColumns} FROM user_profiles p JOIN users u ON u.id=p.user_id WHERE p.user_id=$1`,[request.userId]);
    if (!result.rows[0]) throw notFound(); return result.rows[0];
  });
  app.patch<{ Body: ProfileUpdate }>("/v1/me/profile", { schema: { ...secure,
    body:s.object({...s.profileFields,version:s.version}), response:{...s.errors,200:s.profileSchema} } },async request => {
    const b = request.body;
    const result = await pool.query(`UPDATE user_profiles SET first_name=$3,last_name=$4,country=$5,city=$6,bio=$7,gender=$8,
      gender_visibility=$9,profile_visibility=$10,version=version+1,updated_at=now() WHERE user_id=$1 AND version=$2 RETURNING user_id`,
      [request.userId,b.version,b.firstName,b.lastName,b.country,b.city,b.bio,b.gender,b.genderVisibility,b.profileVisibility]);
    if (!result.rowCount) throw conflict();
    return (await pool.query(`SELECT ${profileColumns} FROM user_profiles p JOIN users u ON u.id=p.user_id WHERE p.user_id=$1`,[request.userId])).rows[0];
  });
  app.delete("/v1/session", { schema:{...secure,response:{...s.errors,204:{type:'null'}}} },async(request,reply)=>{
    await pool.query("UPDATE sessions SET revoked_at=now() WHERE token_hash=$1",[tokenHash(request.headers.authorization!.slice(7))]);
    return reply.code(204).send();
  });
  app.get<{ Querystring:{limit?:string;cursor?:string} }>("/v1/positions", { schema:{...secure,querystring:s.pageQuery,
    response:{...s.errors,200:s.page(s.object({id:s.uuid,releaseId:s.uuid,name:{type:'string'},createdAt:s.timestamp}))} } },async request=>{
    const limit=Number(request.query.limit??20),after=decodeCursor(request.query.cursor);
    const result=await pool.query<{id:string;releaseId:string;name:string;createdAt:string}>(`SELECT p.id,p.release_id AS "releaseId",r.name,
      to_char(p.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "createdAt"
      FROM beer_positions p JOIN beer_releases r ON r.id=p.release_id WHERE p.status='verified'
      AND ($1::timestamptz IS NULL OR (p.created_at,p.id)<($1::timestamptz,$2::uuid))
      ORDER BY p.created_at DESC,p.id DESC LIMIT $3`,[after?.[0]??null,after?.[1]??null,limit+1]);
    const items=result.rows.slice(0,limit);
    return {items,nextCursor:result.rows.length>limit?encodeCursor(items.at(-1)!):null};
  });
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
