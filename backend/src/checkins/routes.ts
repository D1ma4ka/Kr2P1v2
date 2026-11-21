import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import * as s from '../http/schemas.js';
import * as r from './repository.js';
import * as stats from './statistics.js';
import type {DiaryQuery} from './queries.js';
export const nullableUuid={anyOf:[s.uuid,{type:'null'}]};
const ids={type:'array',items:s.uuid,uniqueItems:true};
export const extended={servingFormat:s.nullableText(80),venueId:nullableUuid,venueText:s.nullableText(300),tagIds:{...ids,maxItems:12},mediaIds:{...ids,maxItems:4}};
const labels={type:'array',items:{type:'string'}};
export const checkin=s.object({...s.checkinSchema.properties,...extended,releaseId:s.uuid,positionName:{type:'string'},breweryId:s.uuid,breweryName:{type:'string'},styleId:nullableUuid,styleName:s.nullableText(200),positionStatus:{type:'string'},venueName:s.nullableText(300),venueCity:s.nullableText(300),venueAddress:s.nullableText(1000),venueSource:s.nullableText(2000),tagLabels:labels});
const count={type:'integer',minimum:0},average={anyOf:[{type:'number',minimum:0.25,maximum:5},{type:'null'}]};
const breakdown=s.object({id:nullableUuid,name:s.nullableText(200),count});
const stat=s.object({totalCheckins:count,uniquePositions:count,ratedCheckins:count,unratedCheckins:count,ratingSumQuarters:{type:'string',pattern:'^[0-9]+$'},averageRating:average,earnedBadges:count,
 histogram:{type:'array',items:s.object({ratingQuarters:{type:'integer',minimum:1,maximum:20},count})},styles:{type:'array',items:breakdown},breweries:{type:'array',items:breakdown}});
const query={limit:{type:"string",pattern:"^(?:[1-9]|[1-4][0-9]|50)$"},cursor:{type:'string',maxLength:2048,pattern:'^[A-Za-z0-9_-]+$'},q:{type:'string',maxLength:120},styleId:s.uuid,breweryId:s.uuid,positionId:s.uuid,releaseId:s.uuid,
 from:s.timestamp,to:s.timestamp,ratingMin:{type:'string',pattern:'^(?:[1-9]|1[0-9]|20)$'},ratingMax:{type:'string',pattern:'^(?:[1-9]|1[0-9]|20)$'},unrated:{type:'string',enum:['true','false']},hasPhoto:{type:'string',enum:['true','false']},venueId:s.uuid,venue:{type:'string',maxLength:300},sort:{type:'string',enum:['date_desc','date_asc','rating_desc','rating_asc']}};
const uniqueQuery=s.object({limit:query.limit,cursor:query.cursor,q:query.q,styleId:{anyOf:[s.uuid,{type:'string',const:'unknown'}]},breweryId:s.uuid,sort:{type:'string',enum:['name','latest','rating']}},[]);
const unique=s.object({id:s.uuid,releaseId:s.uuid,name:{type:'string'},breweryId:s.uuid,breweryName:{type:'string'},styleId:nullableUuid,styleName:s.nullableText(200),status:{type:'string'},checkins:count,averageRating:average,sortName:{type:'string'},lastSeen:s.timestamp});
export async function diaryRoutes(app:FastifyInstance,pool:pg.Pool,allowTest=false){
 const secure={security:[{bearerAuth:[]}],response:s.errors};
 app.get<{Querystring:DiaryQuery}>('/v1/checkins',{schema:{...secure,querystring:s.object(query,[]),response:{...s.errors,200:s.page(checkin)}}},req=>r.listCheckins(pool,req.userId,Number(req.query.limit??20),req.query.cursor,req.query));
 app.get<{Params:{id:string}}>('/v1/checkins/:id',{schema:{...secure,params:s.idParams,response:{...s.errors,200:checkin}}},req=>r.getCheckin(pool,req.userId,req.params.id));
 app.post<{Body:r.CheckinInput;Headers:{'idempotency-key':string}}>('/v1/checkins',{bodyLimit:64*1024,schema:{...secure,headers:{type:'object',required:['idempotency-key'],properties:{'idempotency-key':s.uuid}},body:s.object({...s.checkinFields,...extended},Object.keys(s.checkinFields)),response:{...s.errors,200:checkin,201:checkin}}},async(req,reply)=>{const data=await r.createCheckin(pool,req.userId,req.headers['idempotency-key'],req.body,allowTest);return reply.code(data.replay?200:201).send(data.row);});
 const {positionId:_,...mutable}=s.checkinFields;
 app.patch<{Params:{id:string};Body:Omit<r.CheckinInput,'positionId'>&{version:number}}>('/v1/checkins/:id',{bodyLimit:64*1024,schema:{...secure,params:s.idParams,body:s.object({...mutable,...extended,version:s.version},[...Object.keys(mutable),'version']),response:{...s.errors,200:checkin}}},req=>r.updateCheckin(pool,req.userId,req.params.id,req.body,allowTest));
 app.delete<{Params:{id:string};Body:{version:number}}>('/v1/checkins/:id',{schema:{...secure,params:s.idParams,body:s.object({version:s.version}),response:{...s.errors,204:{type:'null'}}}},async(req,reply)=>{await r.deleteCheckin(pool,req.userId,req.params.id,req.body.version);return reply.code(204).send();});
 app.get<{Params:{id:string}}>('/v1/me/checkin-requests/:id',{schema:{...secure,params:s.idParams,response:{...s.errors,200:s.object({checkinId:nullableUuid})}}},async req=>({checkinId:(await pool.query("SELECT resource_id FROM idempotency_keys WHERE user_id=$1 AND key=$2 AND operation='create-checkin'",[req.userId,req.params.id])).rows[0]?.resource_id??null}));
 app.get('/v1/taste-tags',{schema:{...secure,response:{...s.errors,200:s.object({items:{type:'array',items:s.object({id:s.uuid,label:{type:'string'}})}})}}},async()=>({items:(await pool.query('SELECT id,label FROM taste_tags ORDER BY label,id')).rows}));
 app.get('/v1/me/statistics',{schema:{...secure,response:{...s.errors,200:stat}}},req=>stats.statistics(pool,req.userId));
 app.get('/v1/me/home',{schema:{...secure,response:{...s.errors,200:s.object({statistics:stat,recent:s.page(checkin)})}}},async req=>({statistics:await stats.statistics(pool,req.userId),recent:await r.listCheckins(pool,req.userId,5)}));
 for(const wishlist of [false,true])app.get<{Querystring:DiaryQuery}>(wishlist?'/v1/me/wishlist':'/v1/me/unique-positions',{schema:{...secure,querystring:uniqueQuery,response:{...s.errors,200:s.page(unique)}}},req=>stats.uniquePositions(pool,req.userId,req.query,wishlist));
 const wished=s.object({positionId:s.uuid,wished:{type:'boolean'}});
 for(const method of ['PUT','DELETE'] as const)app.route<{Params:{id:string}}>({method,url:'/v1/me/wishlist/:id',schema:{...secure,params:s.idParams,response:{...s.errors,200:wished}},handler:req=>stats.wishlistChange(pool,req.userId,req.params.id,method==='PUT')});
 app.get<{Params:{id:string}}>('/v1/me/wishlist/:id',{schema:{...secure,params:s.idParams,response:{...s.errors,200:wished}}},async req=>{const row=(await pool.query('SELECT id,coalesce(merged_into_id,id) AS canonical FROM beer_positions WHERE id=$1',[req.params.id])).rows[0];return{positionId:row?.canonical??req.params.id,wished:!!(await pool.query('SELECT 1 FROM wishlist_items WHERE user_id=$1 AND position_id=$2',[req.userId,row?.canonical??req.params.id])).rowCount};});
 app.get<{Params:{id:string}}>('/v1/positions/:id/statistics',{schema:{...secure,params:s.idParams,response:{...s.errors,200:s.object({positionId:s.uuid,scope:{type:'string',const:'visible_to_viewer'},totalCheckins:count,users:count,ratedCheckins:count,averageRating:average,personalCheckins:count,personalRatedCheckins:count,personalAverageRating:average})}}},req=>stats.positionStatistics(pool,req.userId,req.params.id));
}
