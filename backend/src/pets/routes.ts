import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import * as s from '../http/schemas.js';
import * as r from './repository.js';

const id={type:'string',pattern:'^[a-z][a-z0-9_]{2,63}$'};
const secure={security:[{bearerAuth:[]}],response:s.errors};
const root='/v1/pets';
export async function petRoutes(app:FastifyInstance,pool:pg.Pool,allowTest:boolean) {
  app.get(root+'/definitions',{schema:secure},()=>r.petDefinitions(pool));
  app.get(root+'/state',{schema:secure},req=>r.state(pool,req.userId));
  app.get(root+'/catalog',{schema:secure},()=>r.catalog(pool));
  app.get<{Querystring:{before?:string}}>(root+'/wallet',{schema:{...secure,
    querystring:s.object({before:{type:'string',pattern:'^[1-9][0-9]{0,17}$'}},[])}},req=>r.history(pool,req.userId,req.query.before));
  app.post<{Body:{definitionId:string;name:string}}>(root+'/adopt',{schema:{...secure,
    body:s.object({definitionId:id,name:{type:'string',minLength:1,maxLength:32}})}},async req=>{
    await r.adopt(pool,req.userId,req.body.definitionId,req.body.name);
    return r.state(pool,req.userId);
  });
  app.put<{Body:{petId:string}}>(root+'/active',{schema:{...secure,body:s.object({petId:s.uuid})}},async req=>{
    await r.selectPet(pool,req.userId,req.body.petId);
    return r.state(pool,req.userId);
  });
  app.post<{Body:{definitionId:string;requestId:string}}>(root+'/purchase',{schema:{...secure,
    body:s.object({definitionId:id,requestId:s.uuid})}},req=>r.purchase(pool,req.userId,req.body.requestId,req.body.definitionId));
  app.post<{Body:{itemId:string;requestId:string}}>(root+'/consume',{schema:{...secure,
    body:s.object({itemId:s.uuid,requestId:s.uuid})}},req=>r.consume(pool,req.userId,req.body.requestId,req.body.itemId));
  for(const kind of ['wear','decor'] as const) {
    app.put<{Params:{slot:string};Body:{itemId:string|null}}>(root+'/'+kind+'/:slot',{schema:{...secure,
      params:s.object({slot:{type:'string',enum:kind==='wear'?r.wearSlots:r.decorSlots}}),
      body:s.object({itemId:{anyOf:[s.uuid,{type:'null'}]}})}},async req=>{
      await r.arrange(pool,req.userId,kind,req.params.slot,req.body.itemId);
      return r.state(pool,req.userId);
    });
  }
  app.post<{Body:{code:string}}>(root+'/redeem',{schema:{...secure,
    body:s.object({code:{type:'string',pattern:'^krapiva:reward:[A-Za-z0-9_-]{16,180}$'}})}},req=>
    r.redeem(pool,req.userId,req.body.code,allowTest));
}
