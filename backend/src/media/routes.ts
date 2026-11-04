import type {FastifyInstance} from 'fastify';
import {MediaService} from './service.js';
import * as s from '../http/schemas.js';
export async function mediaRoutes(app:FastifyInstance,media:MediaService) {
 const secure={security:[{bearerAuth:[]}],response:s.errors};
 app.addContentTypeParser(['image/jpeg','image/png','image/webp'],{parseAs:'buffer',bodyLimit:8*1024*1024},(_req,body,done)=>done(null,body));
 app.post<{Body:Buffer}>('/v1/media',{bodyLimit:8*1024*1024,
  schema:{...secure,consumes:['image/jpeg','image/png','image/webp'],body:{type:'string',format:'binary'},
   response:{...s.errors,201:s.object({id:s.uuid,width:{type:'integer'},height:{type:'integer'},sizeBytes:{type:'integer'},contentType:{type:'string',enum:['image/jpeg']}})}},
  validatorCompiler:()=>data=>Buffer.isBuffer(data)?{value:data}:{error:new Error('Binary image required')},
 },async(request,reply)=>reply.code(201).send(await media.upload(request.userId,request.body,request.headers['content-type']!.split(';')[0]!)));
 app.get<{Params:{id:string};Querystring:{size?:string}}>('/v1/media/:id',{schema:{...secure,params:s.idParams,
  querystring:s.object({size:{type:'string',enum:['original','thumbnail']}},[]),produces:['image/jpeg'],response:{...s.errors,200:{type:'string',format:'binary'}}}},
  async(request,reply)=>reply.type('image/jpeg').send(await media.get(request.userId,request.params.id,request.query.size==='thumbnail')));
 app.delete<{Params:{id:string}}>('/v1/media/:id',{schema:{...secure,params:s.idParams,response:{...s.errors,204:{type:'null'}}}},
  async(request,reply)=>{await media.remove(request.userId,request.params.id);return reply.code(204).send();});
 const attachment=s.object({mediaIds:{type:'array',items:s.uuid,maxItems:4,uniqueItems:true},version:s.version});
 app.put<{Params:{id:string};Body:{mediaIds:string[];version:number}}>('/v1/checkins/:id/media',{schema:{...secure,params:s.idParams,body:attachment,response:{...s.errors,200:attachment}}},
  request=>media.attach(request.userId,request.params.id,request.body.version,request.body.mediaIds));
 app.get<{Params:{id:string}}>('/v1/checkins/:id/media',{schema:{...secure,params:s.idParams,response:{...s.errors,200:attachment}}},
  request=>media.list(request.userId,request.params.id));
}
