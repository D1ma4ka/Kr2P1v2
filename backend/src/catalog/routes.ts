import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import * as s from '../http/schemas.js';
import * as c from './schemas.js';
import * as repo from './repository.js';
import * as p from './proposals.js';
import {normalizeGtin} from './gtin.js';
import {ApiError,notFound} from '../http/errors.js';
import {transaction} from '../db/pool.js';
export async function catalogRoutes(app:FastifyInstance,pool:pg.Pool) {
 const secure={security:[{bearerAuth:[]}],response:s.errors};
 const simpleQuery=s.object({limit:s.pageQuery.properties.limit,cursor:c.query.properties.cursor,q:c.query.properties.q},[]);
 const schema=(response:unknown)=>({...secure,response:{...s.errors,200:response}});
 app.get<{Querystring:repo.CatalogQuery}>('/v1/positions',{schema:{...schema(s.page(c.position)),querystring:c.query}},request=>repo.positions(pool,request.query));
 app.get<{Params:{id:string}}>('/v1/positions/:id',{schema:{...schema(c.position),params:s.idParams}},request=>repo.position(pool,request.params.id));
 app.get<{Querystring:repo.CatalogQuery}>('/v1/breweries',{schema:{...schema(s.page(c.brewery)),querystring:simpleQuery}},request=>repo.breweries(pool,request.query));
 app.get<{Params:{id:string}}>('/v1/breweries/:id',{schema:{...schema(c.brewery),params:s.idParams}},request=>repo.brewery(pool,request.params.id));
 app.get<{Params:{id:string};Querystring:repo.CatalogQuery}>('/v1/breweries/:id/positions',{schema:{...schema(s.page(c.position)),params:s.idParams,querystring:c.query}},async request=>{
  await repo.brewery(pool,request.params.id);return repo.positions(pool,{...request.query,breweryId:request.params.id});
 });
 app.get<{Querystring:{code:string;kind:'gtin'|'qr'}}>('/v1/barcodes/lookup',{schema:{...schema(s.object({state:{type:'string',enum:['found','not_found','non_beer','unsupported_qr']},gtin:{type:['string','null']},position:{anyOf:[c.position,{type:'null'}]},source:{type:['string','null']}})),querystring:s.object({code:{type:'string',minLength:1,maxLength:2048},kind:{type:'string',enum:['gtin','qr']}})}},request=>repo.lookup(pool,request.query.code,request.query.kind));
 app.get<{Querystring:{q?:string;limit?:string;cursor?:string}}>('/v1/styles',{schema:{...schema(s.page(s.object({id:s.uuid,name:{type:'string'}}))),querystring:s.object({q:c.query.properties.q,...s.pageQuery.properties},[])}},async request=>{
  const {decodeCursor,encodeCursor}=await import('../http/pagination.js');const after=decodeCursor(request.query.cursor),limit=Number(request.query.limit??20);
  const rows=(await pool.query(`SELECT s.id,s.name FROM styles s WHERE EXISTS(SELECT 1 FROM catalog_search c WHERE c.style_id=s.id) AND catalog_normalize(s.name) LIKE $1 AND ($2::uuid IS NULL OR s.id>$2::uuid) ORDER BY s.id LIMIT $3`,['%'+repo.normalize(request.query.q??'')+'%',after?.[1]??null,limit+1])).rows;
  const items=rows.slice(0,limit);return {items,nextCursor:rows.length>limit?encodeCursor({id:items.at(-1)!.id,createdAt:'2000-01-01T00:00:00.000Z'}):null};
 });
 app.post<{Body:p.ProposalInput&{requestId:string}}>('/v1/position-proposals',{schema:{...secure,body:s.object({...c.inputFields,requestId:s.uuid},['name','breweryName','source','requestId']),response:{...s.errors,201:c.proposal}}},async(request,reply)=>{
  const {requestId,...data}=request.body;return reply.code(201).send(await p.propose(pool,request.userId,requestId,data));
 });
 app.get<{Querystring:{limit?:string;cursor?:string}}>('/v1/me/position-proposals',{schema:{...schema(s.page(c.proposal)),querystring:s.pageQuery}},request=>p.proposalList(pool,request.userId,request.query));
 app.patch<{Params:{id:string};Body:p.ProposalInput&{version:number}}>('/v1/position-proposals/:id',{schema:{...schema(c.proposal),params:s.idParams,body:s.object({...c.inputFields,version:s.version},['name','breweryName','source','version'])}},request=>{
  const {version,...data}=request.body;return p.editProposal(pool,request.userId,request.params.id,version,data);
 });
 const candidate=s.object({id:s.uuid,name:{type:'string'},breweryName:{type:'string'},packaging:s.nullableText(80),volumeMl:{type:['integer','null']},edition:s.nullableText(200),variant:s.nullableText(200)});
 app.get<{Querystring:{limit?:string;cursor?:string}}>('/v1/moderation/position-proposals',{schema:{...schema(s.page(c.proposal)),querystring:s.pageQuery}},request=>p.proposalList(pool,request.userId,request.query,true));
 app.get<{Params:{id:string}}>('/v1/moderation/position-proposals/:id/duplicates',{schema:{...schema(s.object({items:{type:'array',items:candidate}})),params:s.idParams}},async request=>{
  await p.requireModerator(pool,request.userId);const row=(await pool.query('SELECT proposed_data FROM position_proposals WHERE id=$1',[request.params.id])).rows[0];
  if(!row) throw notFound();return {items:await p.duplicates(pool,row.proposed_data)};
 });
 app.post<{Params:{id:string};Body:p.Decision}>('/v1/moderation/position-proposals/:id/decision',{schema:{...schema(c.proposal),params:s.idParams,body:s.object({version:s.version,action:{type:'string',enum:['accept','reject','link']},reason:{type:'string',minLength:1,maxLength:2000,pattern:'\\S'},positionId:s.uuid,breweryId:s.uuid,releaseId:s.uuid,allowDuplicate:{type:'boolean'}},['version','action','reason'])}},request=>p.decide(pool,request.userId,request.params.id,request.body));
 app.post<{Params:{id:string};Body:{targetId:string;reason:string}}>('/v1/moderation/positions/:id/merge',{schema:{...schema(c.position),params:s.idParams,body:s.object({targetId:s.uuid,reason:{type:'string',minLength:1,maxLength:2000,pattern:'\\S'}})}},request=>p.merge(pool,request.userId,request.params.id,request.body.targetId,request.body.reason));
 app.put<{Body:{code:string;source:string}}>('/v1/moderation/barcode-exclusions',{schema:{...schema(s.object({gtin:{type:'string'}})),body:s.object({code:{type:'string',minLength:8,maxLength:14},source:{type:'string',minLength:1,maxLength:2000,pattern:'\\S'}})}},async request=>transaction(pool,async db=>{
  await p.requireModerator(db,request.userId);await db.query('SELECT pg_advisory_xact_lock(220024)');const gtin=normalizeGtin(request.body.code);
  if((await db.query("SELECT 1 FROM barcode_mappings WHERE gtin=$1 AND status='verified'",[gtin])).rowCount) throw new ApiError(409,'BARCODE_CONFLICT','Код уже связан с пивом');
  await db.query('INSERT INTO barcode_exclusions(gtin,source,moderator_id) VALUES($1,$2,$3) ON CONFLICT(gtin) DO UPDATE SET source=EXCLUDED.source,moderator_id=EXCLUDED.moderator_id',[gtin,request.body.source,request.userId]);
  await db.query("INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason) VALUES($1,'catalog.non_beer','barcode',$1,$2)",[request.userId,JSON.stringify({gtin,source:request.body.source})]);return {gtin};
 }));
 app.delete<{Params:{code:string};Body:{reason:string}}>('/v1/moderation/barcode-exclusions/:code',{schema:{...secure,params:s.object({code:{type:'string',minLength:8,maxLength:14}}),body:s.object({reason:{type:'string',minLength:1,maxLength:2000,pattern:'\\S'}}),response:{...s.errors,204:{type:'null'}}}},async(request,reply)=>{
  await transaction(pool,async db=>{await p.requireModerator(db,request.userId);await db.query('SELECT pg_advisory_xact_lock(220024)');const gtin=normalizeGtin(request.params.code);
   await db.query('DELETE FROM barcode_exclusions WHERE gtin=$1',[gtin]);await db.query("INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason) VALUES($1,'catalog.clear_non_beer','barcode',$1,$2)",[request.userId,JSON.stringify({gtin,reason:request.body.reason})]);});return reply.code(204).send();
 });
 app.put<{Params:{id:string};Body:{imageAssetId:string;imageSource:string;rightsConfirmed:boolean}}>('/v1/moderation/breweries/:id/image',{schema:{...schema(c.brewery),params:s.idParams,body:s.object({imageAssetId:s.uuid,imageSource:{type:'string',minLength:1,maxLength:2000,pattern:'\\S'},rightsConfirmed:{type:'boolean',const:true}})}},async request=>{
  await transaction(pool,async db=>{await p.requireModerator(db,request.userId);
   const asset=await db.query("SELECT id FROM media_assets WHERE id=$1 AND owner_id=$2 AND deleted_at IS NULL AND moderation_status<>'hidden' FOR UPDATE",[request.body.imageAssetId,request.userId]);if(!asset.rowCount) throw notFound();
   const b=await db.query("UPDATE breweries SET image_asset_id=$2,image_source=$3,updated_at=now() WHERE id=$1 AND verification_status='verified' RETURNING id",[request.params.id,request.body.imageAssetId,request.body.imageSource]);if(!b.rowCount) throw notFound();
   await db.query("UPDATE media_assets SET moderation_status='approved' WHERE id=$1",[request.body.imageAssetId]);await db.query("INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason) VALUES($1,'catalog.brewery_image','brewery',$2,$3)",[request.userId,request.params.id,request.body.imageSource]);
  });return repo.brewery(pool,request.params.id);
 });
}
