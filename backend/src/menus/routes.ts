import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import * as s from '../http/schemas.js';
import * as menus from './repository.js';

const text = (maxLength: number) => ({type:'string',minLength:1,maxLength,pattern:'\\S'});
const nullable = (value: object) => ({anyOf:[value,{type:'null'}]});
const count = {type:'integer',minimum:0};
const number = {type:['number','null']};
const section = s.object({id:s.uuid,name:text(120)});
const offer = {
 sectionId:s.uuid,ordinal:{type:'integer',minimum:0,maximum:249},
 price:nullable({type:'number',minimum:0,maximum:9999999999.99}),
 currency:nullable({type:'string',pattern:'^[A-Z]{3}$'}),
 volumeMl:nullable({type:'integer',minimum:1,maximum:100000}),
 servingFormat:nullable(text(80)),available:{type:'boolean'},
};
const item = s.object({positionId:s.uuid,...offer});
const menu = s.object({
 state:{type:'string',enum:['draft','published']},revision:s.version,source:text(500),updatedAt:s.timestamp,
 sections:{type:'array',items:section},items:{type:'array',items:s.object({...item.properties,
  name:{type:'string'},breweryId:s.uuid,breweryName:{type:'string'},country:{type:['string','null']},
  styleId:nullable(s.uuid),styleName:{type:['string','null']},releaseId:s.uuid,packaging:{type:['string','null']},
  abv:number,ibu:number,catalogVolumeMl:{type:['integer','null']},averageRating:number,tried:{type:'boolean'},
  publicAverageRating:number,ratedCheckins:count,personalCheckins:count,personalAverageRating:number,description:{type:['string','null']},
 })},
});
const expected = {expectedRevision:s.version};
const result = s.object({revision:s.version});

export async function menuRoutes(app: FastifyInstance, pool: pg.Pool, allowTest: boolean) {
 const secure = {security:[{bearerAuth:[]}],params:s.idParams};
 const base = '/v1/venues/:id/menu';
 for (const state of ['published','draft'] as const) {
  app.get<{Params:{id:string}}>(state === 'draft' ? base+'/draft' : base, {
   schema:{...secure,response:{...s.errors,200:s.object({venueId:s.uuid,menu:nullable(menu)})}},
  }, async (req, reply) => {
   reply.header('Cache-Control','private, no-store');
   return menus.readMenu(pool,req.userId,req.params.id,allowTest,state);
  });
 }
 app.put<{Params:{id:string};Body:menus.Draft}>(base+'/draft', {
  bodyLimit:256*1024,
  schema:{...secure,body:s.object({
   expectedRevision:{...s.version,minimum:0},source:text(500),
   sections:{type:'array',maxItems:40,items:section},items:{type:'array',maxItems:250,items:item},
  }),response:{...s.errors,200:result}},
 }, req => menus.saveDraft(pool,req.userId,req.params.id,allowTest,req.body));
 const params = s.object({id:s.uuid,positionId:s.uuid});
 app.put<{Params:{id:string;positionId:string};Body:menus.Offer & {expectedRevision:number}}>(base+'/draft/items/:positionId', {
  schema:{...secure,params,body:s.object({...expected,...offer}),response:{...s.errors,200:result}},
 }, req => menus.changeItem(pool,req.userId,req.params.id,allowTest,req.body.expectedRevision,req.params.positionId,req.body));
 app.delete<{Params:{id:string;positionId:string};Querystring:{expectedRevision:string}}>(base+'/draft/items/:positionId', {
  schema:{...secure,params,querystring:s.object({expectedRevision:{type:'string',pattern:'^[1-9][0-9]{0,9}$'}}),response:{...s.errors,200:result}},
 }, req => menus.changeItem(pool,req.userId,req.params.id,allowTest,Number(req.query.expectedRevision),req.params.positionId,null));
 app.post<{Params:{id:string};Body:{expectedRevision:number}}>(base+'/publish', {
  schema:{...secure,body:s.object(expected),response:{...s.errors,200:result}},
 }, req => menus.publish(pool,req.userId,req.params.id,allowTest,req.body.expectedRevision));
 const roles = {type:['string','null'],enum:['owner','operator',null]};
 app.put<{Params:{id:string;userId:string};Body:{role:'owner'|'operator'|null}}>('/v1/moderation/venues/:id/menu/operators/:userId', {
  schema:{...secure,params:s.object({id:s.uuid,userId:s.uuid}),body:s.object({role:roles}),response:{...s.errors,200:s.object({role:roles})}},
 }, req => menus.setOperator(pool,req.userId,req.params.id,req.params.userId,req.body.role,allowTest));
}
