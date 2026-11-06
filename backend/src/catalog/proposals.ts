import type pg from 'pg';
import {transaction} from '../db/pool.js';
import {ApiError,conflict,notFound} from '../http/errors.js';
import {decodeCursor,encodeCursor} from '../http/pagination.js';
import {normalizeGtin} from './gtin.js';
import {type Db,position} from './repository.js';
export interface ProposalInput {
 name:string;breweryName:string;source:string;breweryId?:string|null;breweryCountry?:string|null;breweryCity?:string|null;brewerySource?:string|null;
 styleName?:string|null;brand?:string|null;abv?:number|null;ibu?:number|null;packaging?:string|null;volumeMl?:number|null;
 edition?:string|null;variant?:string|null;description?:string|null;gtin?:string|null;imageAssetId?:string|null;imageSource?:string|null;rightsConfirmed?:boolean;
}
const columns=`id,status,version,proposed_data AS "proposedData",position_id AS "positionId",decision_reason AS "decisionReason",
 to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "createdAt",decided_at AS "decidedAt"`;
export async function requireModerator(db:Db,user:string) {
 const result=await db.query("SELECT role FROM users WHERE id=$1 AND status='active'",[user]);
 if(!['moderator','admin'].includes(result.rows[0]?.role)) throw new ApiError(403,'FORBIDDEN','Требуется роль модератора');
}
async function validate(db:Db,user:string,data:ProposalInput) {
 const cleaned=Object.fromEntries(Object.entries(data).map(([k,v])=>[k,typeof v==='string'?v.trim()||null:v])) as unknown as ProposalInput;
 if(!cleaned.name||!cleaned.breweryName||!cleaned.source) throw new ApiError(422,'SOURCE_REQUIRED','Укажите название, пивоварню и источник');
 if(cleaned.gtin) cleaned.gtin=normalizeGtin(cleaned.gtin);
 if(cleaned.breweryId) cleaned.breweryId=cleaned.breweryId.toLowerCase();
 if(cleaned.imageAssetId) cleaned.imageAssetId=cleaned.imageAssetId.toLowerCase();
 if(cleaned.breweryId) {
  const b=await db.query("SELECT name FROM breweries WHERE id=$1 AND verification_status='verified'",[cleaned.breweryId]);
  if(!b.rows[0]) throw notFound(); cleaned.breweryName=b.rows[0].name;
 }
 if(cleaned.imageAssetId) {
  if(!cleaned.rightsConfirmed||!cleaned.imageSource) throw new ApiError(422,'IMAGE_RIGHTS_REQUIRED','Подтвердите права и источник изображения');
  const a=await db.query("SELECT id FROM media_assets WHERE id=$1 AND owner_id=$2 AND deleted_at IS NULL AND moderation_status<>'hidden' FOR SHARE",[cleaned.imageAssetId,user]);
  if(!a.rows[0]) throw notFound();
 }
 return cleaned;
}
export async function proposalList(db:Db,user:string,query:{limit?:string;cursor?:string},moderation=false) {
 if(moderation) await requireModerator(db,user);
 const after=decodeCursor(query.cursor),limit=Number(query.limit??20);
 const result=await db.query(`SELECT ${columns} FROM position_proposals WHERE ${moderation?"status='pending' AND $1::uuid IS NULL":'submitter_id=$1'}
 AND ($2::timestamptz IS NULL OR (created_at,id)${moderation?'>':'<'}($2::timestamptz,$3::uuid))
 ORDER BY created_at ${moderation?'ASC':'DESC'},id ${moderation?'ASC':'DESC'} LIMIT $4`,[...(moderation?[null]:[user]),after?.[0]??null,after?.[1]??null,limit+1]);
 const items=result.rows.slice(0,limit);
 return {items,nextCursor:result.rows.length>limit?encodeCursor(items.at(-1)!):null};
}
export async function propose(pool:pg.Pool,user:string,key:string,data:ProposalInput) {
 return transaction(pool,async db=>{
  const value=await validate(db,user,data);
  // Serialize this user's request key; retries never create duplicate proposals.
  await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',[user+key]);
  const old=await db.query(`SELECT ${columns} FROM position_proposals WHERE submitter_id=$1 AND client_request_id=$2`,[user,key]);
  if(old.rows[0]) {
   if(JSON.stringify(Object.entries(old.rows[0].proposedData).sort())!==JSON.stringify(Object.entries(value).sort())) throw conflict();
   return old.rows[0];
  }
  const result=await db.query(`INSERT INTO position_proposals(submitter_id,client_request_id,proposed_data) VALUES($1,$2,$3) RETURNING ${columns}`,[user,key,value]);
  return result.rows[0];
 });
}
export async function editProposal(pool:pg.Pool,user:string,id:string,version:number,data:ProposalInput) {
 return transaction(pool,async db=>{
  const row=await db.query('SELECT status,version,proposed_data FROM position_proposals WHERE id=$1 AND submitter_id=$2 FOR UPDATE',[id,user]);
  if(!row.rows[0]) throw notFound();
  if(row.rows[0].status!=='pending'||row.rows[0].version!==version) throw conflict();
  const value=await validate(db,user,data);
  await db.query('INSERT INTO proposal_revisions(proposal_id,version,proposed_data) VALUES($1,$2,$3)',[id,version,row.rows[0].proposed_data]);
  return (await db.query(`UPDATE position_proposals SET proposed_data=$2,version=version+1 WHERE id=$1 RETURNING ${columns}`,[id,value])).rows[0];
 });
}
export async function duplicates(db:Db,data:ProposalInput) {
 return (await db.query(`SELECT p.id,r.name,b.name AS "breweryName",p.packaging,p.volume_ml AS "volumeMl",p.edition,p.variant
 FROM beer_releases r JOIN breweries b ON b.id=r.brewery_id JOIN beer_positions p ON p.release_id=r.id
 WHERE catalog_normalize(r.name)=catalog_normalize($1) AND
 (($2::uuid IS NOT NULL AND b.id=$2) OR ($2::uuid IS NULL AND catalog_normalize(b.name)=catalog_normalize($3)))
 AND p.status='verified' ORDER BY p.id LIMIT 20`,[data.name,data.breweryId??null,data.breweryName])).rows;
}
export interface Decision {version:number;action:'accept'|'reject'|'link';reason:string;positionId?:string;breweryId?:string;releaseId?:string;allowDuplicate?:boolean;}
export async function decide(pool:pg.Pool,user:string,id:string,input:Decision) {
 return transaction(pool,async db=>{
  await requireModerator(db,user);
  await db.query('SELECT pg_advisory_xact_lock(220024)');
  const row=(await db.query('SELECT * FROM position_proposals WHERE id=$1 FOR UPDATE',[id])).rows[0];
  if(!row) throw notFound();
  if(row.status!=='pending'||row.version!==input.version) throw conflict();
  const data=input.action==='accept'?await validate(db,row.submitter_id,row.proposed_data as ProposalInput):row.proposed_data as ProposalInput;
  let positionId:string|null=null;
  if(input.action==='link') {
   if(!input.positionId) throw new ApiError(422,'POSITION_REQUIRED','Укажите подтверждённую позицию');
   positionId=(await position(db,input.positionId)).id;
  }
  if(input.action==='accept') {
   const candidates=await duplicates(db,{...data,breweryId:input.breweryId??data.breweryId??null});
   if(candidates.length&&!input.allowDuplicate) throw new ApiError(409,'POTENTIAL_DUPLICATE','Найдены похожие позиции. Свяжите с существующей или подтвердите различие.');
   let breweryId=input.breweryId??data.breweryId;
   if(breweryId) {
    if(!(await db.query("SELECT id FROM breweries WHERE id=$1 AND verification_status='verified'",[breweryId])).rowCount) throw notFound();
   } else {
    const existing=await db.query("SELECT id FROM breweries WHERE catalog_normalize(name)=catalog_normalize($1) AND verification_status='verified'",[data.breweryName]);
    if(existing.rowCount) throw new ApiError(409,'BREWERY_EXISTS','Выберите существующую пивоварню по ID');
    breweryId=(await db.query(`INSERT INTO breweries(name,country,city,source,verification_status) VALUES($1,$2,$3,$4,'verified') RETURNING id`,[data.breweryName,data.breweryCountry??null,data.breweryCity??null,data.brewerySource||data.source])).rows[0].id;
   }
   let releaseId=input.releaseId;
   if(releaseId) {
    if(!(await db.query('SELECT id FROM beer_releases WHERE id=$1 AND brewery_id=$2 AND catalog_normalize(name)=catalog_normalize($3)',[releaseId,breweryId,data.name])).rowCount) throw new ApiError(422,'RELEASE_MISMATCH','Выпуск не соответствует пивоварне и названию');
   } else {
    let styleId=null;
    if(data.styleName) styleId=(await db.query('INSERT INTO styles(name) VALUES($1) ON CONFLICT(name) DO UPDATE SET name=EXCLUDED.name RETURNING id',[data.styleName])).rows[0].id;
    releaseId=(await db.query('INSERT INTO beer_releases(brewery_id,name,style_id,abv,ibu,description,brand) VALUES($1,$2,$3,$4,$5,$6,$7) RETURNING id',[breweryId,data.name,styleId,data.abv??null,data.ibu??null,data.description??null,data.brand??null])).rows[0].id;
   }
   positionId=(await db.query(`INSERT INTO beer_positions(release_id,packaging,volume_ml,edition,variant,source,status,verified_at,image_asset_id,image_source)
   VALUES($1,$2,$3,$4,$5,$6,'verified',now(),$7,$8) RETURNING id`,[releaseId,data.packaging??null,data.volumeMl??null,data.edition??null,data.variant??null,data.source,data.imageAssetId??null,data.imageSource??null])).rows[0].id;
   if(data.imageAssetId) await db.query("UPDATE media_assets SET moderation_status='approved' WHERE id=$1",[data.imageAssetId]);
  }
  if(positionId&&data.gtin) {
   if((await db.query('SELECT gtin FROM barcode_exclusions WHERE gtin=$1',[data.gtin])).rowCount) throw new ApiError(409,'NON_BEER_CONFLICT','Код отмечен как непивной; сначала исправьте решение');
   const old=await db.query("SELECT position_id FROM barcode_mappings WHERE gtin=$1 AND status='verified'",[data.gtin]);
   if(old.rows[0]&&old.rows[0].position_id!==positionId) throw new ApiError(409,'BARCODE_CONFLICT','Код уже связан с другой позицией');
   if(!old.rowCount) await db.query("INSERT INTO barcode_mappings(position_id,gtin,source,status) VALUES($1,$2,$3,'verified')",[positionId,data.gtin,data.source]);
  }
  const result=await db.query(`UPDATE position_proposals SET status=$2,position_id=$3,moderator_id=$4,decision_reason=$5,decided_at=now(),version=version+1 WHERE id=$1 RETURNING ${columns}`,[id,input.action==='reject'?'rejected':'accepted',positionId,user,input.reason]);
  await db.query("INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason) VALUES($1,$2,'position_proposal',$3,$4)",[user,'catalog.'+input.action,id,input.reason]);
  return result.rows[0];
 });
}
export async function merge(pool:pg.Pool,user:string,from:string,to:string,reason:string) {
 if(from===to) throw new ApiError(422,'INVALID_MERGE','Нельзя объединить позицию с собой');
 return transaction(pool,async db=>{
  await requireModerator(db,user);await db.query('SELECT pg_advisory_xact_lock(220024)');
  const canonical=await position(db,to);
  if(canonical.id===from) throw new ApiError(422,'INVALID_MERGE','Циклическое объединение запрещено');
  const source=await db.query("SELECT id FROM beer_positions WHERE id=$1 AND status='verified' FOR UPDATE",[from]);
  if(!source.rowCount) throw conflict();
  const target=canonical.id as string;
  await db.query('UPDATE checkins SET position_id=$2,version=version+1,updated_at=now() WHERE position_id=$1',[from,target]);
  await db.query('INSERT INTO wishlist_items(user_id,position_id,created_at) SELECT user_id,$2,created_at FROM wishlist_items WHERE position_id=$1 ON CONFLICT(user_id,position_id) DO NOTHING',[from,target]);
  await db.query('DELETE FROM wishlist_items WHERE position_id=$1',[from]);
  await db.query('UPDATE barcode_mappings SET position_id=$2 WHERE position_id=$1',[from,target]);
  await db.query('UPDATE position_proposals SET position_id=$2 WHERE position_id=$1',[from,target]);
  await db.query("UPDATE beer_positions SET merged_into_id=$2,updated_at=now() WHERE merged_into_id=$1",[from,target]);
  await db.query("UPDATE beer_positions SET status='merged',merged_into_id=$2,updated_at=now() WHERE id=$1",[from,target]);
  await db.query("INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason) VALUES($1,'catalog.merge','position',$2,$3)",[user,from,JSON.stringify({target,reason})]);
  return position(db,from);
 });
}
