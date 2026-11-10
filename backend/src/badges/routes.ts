import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import * as s from '../http/schemas.js';
import * as r from './repository.js';
const badge=s.object({id:s.uuid,metric:{type:'string'},target:{type:'integer'},title:{type:'string'},description:{type:'string'},iconKey:{type:'string'},version:s.version,progress:{type:'integer'},earnedAt:{anyOf:[s.timestamp,{type:'null'}]},pinSlot:{type:['integer','null'],minimum:1,maximum:3}});
export async function badgeRoutes(app:FastifyInstance,pool:pg.Pool) {
 const response={...s.errors,200:s.object({items:{type:'array',items:badge}})},secure={security:[{bearerAuth:[]}],response};
 app.get('/v1/me/badges',{schema:secure},req=>r.badges(pool,req.userId));
 app.put<{Body:{ids:string[]}}>('/v1/me/badge-pins',{schema:{...secure,body:s.object({ids:{type:'array',maxItems:3,uniqueItems:true,items:s.uuid}})}},req=>r.pin(pool,req.userId,req.body.ids));
 app.get<{Params:{id:string}}>('/v1/users/:id/badges',{schema:{...secure,params:s.idParams}},req=>r.badges(pool,req.params.id,req.userId));
}
