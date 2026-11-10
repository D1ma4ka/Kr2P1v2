import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import * as s from '../http/schemas.js';
import * as r from './repository.js';
const nullableDate={anyOf:[s.timestamp,{type:'null'}]},nullableNumber={type:['number','null']};
export const venue=s.object({id:s.uuid,name:{type:'string'},type:{type:'string',enum:['bar','beer_shop']},city:{type:'string'},country:s.nullableText(100),address:s.nullableText(1000),source:{type:'string'},
 latitude:nullableNumber,longitude:nullableNumber,coordinatesSource:s.nullableText(2000),coordinatesVerifiedAt:nullableDate,
 imageAssetId:{anyOf:[s.uuid,{type:'null'}]},imageSource:s.nullableText(2000),version:s.version,status:{type:'string'},testOnly:{type:'boolean'}});
const query={...s.pageQuery.properties,cursor:{type:'string',maxLength:512,pattern:'^[A-Za-z0-9_-]+$'},q:{type:'string',maxLength:120},city:{type:'string',maxLength:120},type:{type:'string',enum:['bar','beer_shop']},bbox:{type:'string',maxLength:160}};
export async function venueRoutes(app:FastifyInstance,pool:pg.Pool,allowTest:boolean) {
 const secure={security:[{bearerAuth:[]}],response:s.errors};
 app.get<{Querystring:r.VenueQuery}>('/v1/venues',{schema:{...secure,querystring:s.object(query,[]),response:{...s.errors,200:s.page(venue)}}},req=>r.list(pool,req.query,allowTest));
 app.get<{Querystring:r.VenueQuery}>('/v1/venues/map',{schema:{...secure,querystring:s.object({q:query.q,city:query.city,type:query.type,bbox:query.bbox},['bbox']),response:{...s.errors,200:s.object({items:{type:'array',maxItems:500,items:s.object({id:s.uuid,name:{type:'string'},type:{type:'string'},latitude:{type:'number'},longitude:{type:'number'},testOnly:{type:'boolean'}})},truncated:{type:'boolean'}})}}},req=>r.list(pool,req.query,allowTest,true));
 app.get<{Querystring:{q?:string}}>('/v1/venue-cities',{schema:{...secure,querystring:s.object({q:query.q},[]),response:{...s.errors,200:s.object({items:{type:'array',items:s.object({city:{type:'string'},count:{type:'integer'},latitude:nullableNumber,longitude:nullableNumber})}})}}},req=>r.cities(pool,req.query.q??'',allowTest));
 const stats=s.object({scope:{type:'string',const:'visible_to_viewer'},totalCheckins:{type:'integer'},ratedCheckins:{type:'integer'},averageRating:nullableNumber});
 app.get<{Params:{id:string}}>('/v1/venues/:id',{schema:{...secure,params:s.idParams,response:{...s.errors,200:s.object({...venue.properties,statistics:stats})}}},req=>r.detail(pool,req.userId,req.params.id,allowTest));
 app.get<{Querystring:r.VenueQuery}>('/v1/moderation/venues',{schema:{...secure,querystring:s.object({...query,status:{type:'string',enum:['pending','verified','hidden']}},[]),response:{...s.errors,200:s.page(venue)}}},req=>r.moderationList(pool,req.userId,req.query,allowTest));
 app.get<{Params:{id:string}}>('/v1/moderation/venues/:id',{schema:{...secure,params:s.idParams,response:{...s.errors,200:venue}}},req=>r.moderationDetail(pool,req.userId,req.params.id,allowTest));
 const body=s.object({version:{type:'integer',minimum:0,maximum:2147483646},name:{type:'string',minLength:1,maxLength:300},type:query.type,city:{type:'string',minLength:1,maxLength:120},country:s.nullableText(100),address:s.nullableText(1000),source:{type:'string',minLength:1,maxLength:2000},latitude:{type:['number','null'],minimum:-90,maximum:90},longitude:{type:['number','null'],minimum:-180,maximum:180},coordinatesSource:s.nullableText(2000),imageAssetId:{anyOf:[s.uuid,{type:'null'}]},imageSource:s.nullableText(2000),rightsConfirmed:{type:'boolean'},testOnly:{type:'boolean'},verified:{type:'boolean'}});
 app.put<{Params:{id:string};Body:r.VenueInput}>('/v1/moderation/venues/:id',{schema:{...secure,params:s.idParams,body,response:{...s.errors,200:venue}}},req=>r.save(pool,req.userId,req.params.id,req.body,allowTest));
}
