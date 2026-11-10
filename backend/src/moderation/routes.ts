import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import * as s from '../http/schemas.js';
import * as r from './repository.js';
const subject={type:'string',enum:['user','checkin','comment','venue','position']};
const reportFields={id:s.uuid,subjectType:subject,subjectId:s.uuid,reason:{type:'string'},status:{type:'string'},version:s.version,createdAt:s.timestamp};
const reason={type:'string',minLength:1,maxLength:2000,pattern:'\\S'},role={type:'string',enum:['user','moderator','admin']};
export async function moderationRoutes(app:FastifyInstance,pool:pg.Pool,allowTest:boolean) {
 const secure={security:[{bearerAuth:[]}],response:s.errors};
 app.put<{Params:{id:string};Body:r.ReportInput}>('/v1/reports/:id',{schema:{...secure,params:s.idParams,body:s.object({subjectType:subject,subjectId:s.uuid,reason}),response:{...s.errors,200:s.object(reportFields)}}},req=>r.report(pool,req.userId,req.params.id,req.body,allowTest));
 const moderatorReport=s.object({...reportFields,reporterId:s.uuid});
 app.get<{Querystring:{limit?:string;cursor?:string;status?:string}}>('/v1/moderation/reports',{schema:{...secure,querystring:s.object({...s.pageQuery.properties,status:{type:'string',enum:['pending','resolved','rejected']}},[]),response:{...s.errors,200:s.page(moderatorReport)}}},req=>r.reports(pool,req.userId,req.query));
 app.post<{Params:{id:string};Body:{version:number;action:'hide'|'restore'|'dismiss';reason:string}}>('/v1/moderation/reports/:id/decision',{schema:{...secure,params:s.idParams,body:s.object({version:s.version,action:{type:'string',enum:['hide','restore','dismiss']},reason}),response:{...s.errors,200:moderatorReport}}},req=>r.decide(pool,req.userId,req.params.id,req.body));
 const event=s.object({id:s.uuid,actorId:{anyOf:[s.uuid,{type:'null'}]},action:{type:'string'},entityType:{type:'string'},entityId:{anyOf:[s.uuid,{type:'null'}]},reason:s.nullableText(2000),createdAt:s.timestamp});
 app.get<{Querystring:{limit?:string;cursor?:string;entityId?:string}}>('/v1/moderation/audit',{schema:{...secure,querystring:s.object({...s.pageQuery.properties,entityId:s.uuid},[]),response:{...s.errors,200:s.page(event)}}},req=>r.audit(pool,req.userId,req.query));
 app.post<{Params:{id:string};Body:{expectedRole:string;role:string;reason:string}}>('/v1/moderation/users/:id/role',{schema:{...secure,params:s.idParams,body:s.object({expectedRole:role,role,reason}),response:{...s.errors,200:s.object({userId:s.uuid,role})}}},req=>r.role(pool,req.userId,req.params.id,req.body));
}
