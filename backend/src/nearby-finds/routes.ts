import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import * as s from '../http/schemas.js';
import {menu} from '../menus/routes.js';
import {nearbyFields} from '../venues/routes.js';
import {filterSchema} from './filter.js';
import {modes,reasons,searchFinds,sorts,statuses,venueFinds,type SearchConditions,type SearchRequest} from './repository.js';

const nullable = (schema: unknown) => ({anyOf:[schema,{type:'null'}]});
const count = {type:'integer',minimum:0};
const text = {type:'string'};
const list = (items: unknown) => ({type:'array',items});
const fields = {...nearbyFields,mode:{type:'string',enum:modes},filter:filterSchema};
const required = [...Object.keys(nearbyFields),'mode'];
const venue = s.object({
 id:s.uuid,name:text,type:{type:'string',enum:['bar','beer_shop']},city:text,latitude:nearbyFields.latitude,
 longitude:nearbyFields.longitude,distanceKm:{type:'number',minimum:0},status:{type:'string',enum:statuses},
 updatedAt:nullable(s.timestamp),
});
const summary = s.object({
 venue,matches:count,untried:count,wishlist:count,favorite:count,styles:list(text),
 untriedStyle:nullable(s.object({name:text,count})),relevance:count,
 newReleasePreview:{type:'array',maxItems:3,items:s.object({positionId:s.uuid,name:text})},
},['venue','matches','untried','wishlist','favorite','styles','untriedStyle','relevance']);
const totals = s.object({current:count,stale:count,unpublished:count,empty:count,withMatches:count});
const secure = {security:[{bearerAuth:[]}]};

export async function nearbyFindsRoutes(app: FastifyInstance,pool: pg.Pool,allowTest: boolean) {
 app.post<{Body:SearchRequest}>('/v1/venues/nearby-finds',{
  bodyLimit:64*1024,
  schema:{...secure,body:s.object({...fields,sort:{type:'string',enum:sorts},
   limit:{type:'integer',minimum:1,maximum:50},cursor:s.pageQuery.properties.cursor},[...required,'sort']),
   response:{...s.errors,200:s.object({items:list(summary),nextCursor:nullable(text),totals,historySize:count,personalized:{type:'boolean'}})}},
 },(req,reply) => {
  reply.header('Cache-Control','private, no-store');
  return searchFinds(pool,req.userId,req.body,allowTest);
 });
 app.post<{Body:SearchConditions;Params:{id:string}}>('/v1/venues/:id/nearby-finds',{
  bodyLimit:64*1024,
  schema:{...secure,params:s.idParams,body:s.object(fields,required),
   response:{...s.errors,200:s.object({summary,menu:nullable(menu),
    matches:list(s.object({positionId:s.uuid,wished:{type:'boolean'},reasons:list({type:'string',enum:reasons}),
     releasedOn:{type:'string',format:'date'}},['positionId','wished','reasons']))})}},
 },(req,reply) => {
  reply.header('Cache-Control','private, no-store');
  return venueFinds(pool,req.userId,req.body,allowTest,req.params.id);
 });
}
