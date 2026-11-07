import type pg from 'pg';
import {tokenHash} from '../identity/sessions.js';
import {transaction} from '../db/pool.js';
import {ApiError,conflict,notFound} from '../http/errors.js';
import * as d from './queries.js';
export interface CheckinInput {positionId:string;ratingQuarters:number|null;body:string;occurredAt:string;visibility:string;servingFormat?:string|null;venueId?:string|null;venueText?:string|null;tagIds?:string[];mediaIds?:string[];}
export interface Checkin extends CheckinInput {id:string;userId:string;version:number;createdAt:string;updatedAt:string;}
export async function getCheckin(client:pg.Pool|pg.PoolClient,userId:string,id:string) {
 const row=(await client.query<Checkin>(`SELECT ${d.cols} FROM checkins c ${d.joins} WHERE c.user_id=$1 AND c.id=$2 AND ${d.active}`,[userId,id])).rows[0];
 if(!row)throw notFound();return row;
}
export async function listCheckins(pool:pg.Pool,user:string,limit:number,oldCursor?:string,query:d.DiaryQuery={}) {
 const q:d.DiaryQuery={...query,...(query.cursor??oldCursor?{cursor:query.cursor??oldCursor!}:{})},filter=d.where(user,q),sort=q.sort??'date_desc',asc=sort.endsWith('_asc'),rating=sort.startsWith('rating');
 const key=rating?'coalesce(c.rating_quarters,-1)':'c.occurred_at',hash=d.fingerprint(q,`diary:${user}`),after=d.cursor(q.cursor,hash,rating?'number':'date');
 let boundary='';if(after){filter.args.push(...after);boundary=`AND (${key},c.id)${asc?'>':'<'}($${filter.args.length-1}::${rating?'int':'timestamptz'},$${filter.args.length}::uuid)`;}
 filter.args.push(limit+1);
 const rows=(await pool.query<Checkin>(`SELECT ${d.cols} FROM checkins c ${d.joins} WHERE ${filter.sql} ${boundary} ORDER BY ${key} ${asc?'ASC':'DESC'},c.id ${asc?'ASC':'DESC'} LIMIT $${filter.args.length}`,filter.args)).rows;
 const items=rows.slice(0,limit),last=items.at(-1);
 return{items,nextCursor:rows.length>limit?d.encode(hash,rating?(last!.ratingQuarters??-1):last!.occurredAt,last!.id):null};
}
function normalized(input:CheckinInput) {
 if(!d.validDate(input.occurredAt))throw new ApiError(400,'INVALID_DATE','Некорректная дата');
 if(input.venueId&&input.venueText)throw new ApiError(400,'INVALID_VENUE','Выберите место или введите его вручную');
 return{positionId:input.positionId.toLowerCase(),ratingQuarters:input.ratingQuarters,body:input.body,occurredAt:new Date(input.occurredAt).toISOString(),visibility:input.visibility,
 servingFormat:input.servingFormat?.trim()||null,venueId:input.venueId?.toLowerCase()||null,venueText:input.venueText?.trim()||null,
 tagIds:input.tagIds?.map(x=>x.toLowerCase()).sort()??[],mediaIds:input.mediaIds?.map(x=>x.toLowerCase())??[]};
}
async function attachments(client:pg.PoolClient,user:string,id:string,input:ReturnType<typeof normalized>,write=true) {
 if(input.venueId&&!(await client.query("SELECT id FROM venues WHERE id=$1 AND status='verified' FOR SHARE",[input.venueId])).rowCount)throw new ApiError(422,'VENUE_UNAVAILABLE','Место недоступно');
 const tags=await client.query('SELECT id FROM taste_tags WHERE id=ANY($1::uuid[]) ORDER BY id FOR SHARE',[input.tagIds]);
 if(tags.rowCount!==input.tagIds.length)throw new ApiError(422,'INVALID_TAG','Неизвестный вкусовой тег');
 const media=await client.query("SELECT id FROM media_assets WHERE id=ANY($1::uuid[]) AND owner_id=$2 AND deleted_at IS NULL AND moderation_status<>'hidden' ORDER BY id FOR SHARE",[input.mediaIds,user]);
 if(media.rowCount!==input.mediaIds.length)throw notFound();
 if(!write)return;
 await client.query('DELETE FROM checkin_tags WHERE checkin_id=$1',[id]);
 await client.query('INSERT INTO checkin_tags(checkin_id,tag_id) SELECT $1,unnest($2::uuid[])',[id,input.tagIds]);
 await client.query('DELETE FROM checkin_media WHERE checkin_id=$1',[id]);
 for(const [i,asset]of input.mediaIds.entries())await client.query('INSERT INTO checkin_media(checkin_id,owner_id,media_id,sort_order)VALUES($1,$2,$3,$4)',[id,user,asset,i+1]);
}
export async function createCheckin(pool:pg.Pool,user:string,key:string,input:CheckinInput) {
 const n=normalized(input);
 // Preserve the P2/P4 request hash for existing persisted create retries without P5 fields.
 const hashInput=!n.servingFormat&&!n.venueId&&!n.venueText&&!n.tagIds.length&&!n.mediaIds.length?{positionId:n.positionId,ratingQuarters:n.ratingQuarters,body:n.body,occurredAt:n.occurredAt,visibility:n.visibility}:n;
 const hash=tokenHash(JSON.stringify(hashInput));
 return transaction(pool,async client=>{
 await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',[`${user}:${key.toLowerCase()}:create-checkin`]);
 const old=(await client.query("SELECT request_hash,resource_id FROM idempotency_keys WHERE user_id=$1 AND key=$2 AND operation='create-checkin'",[user,key])).rows[0];
 if(old){if(old.request_hash!==hash)throw conflict();return{row:await getCheckin(client,user,old.resource_id).catch(e=>{if(e instanceof ApiError&&e.statusCode===404)throw conflict();throw e;}),replay:true};}
 await client.query('SELECT pg_advisory_xact_lock_shared(220024)');
 // Shared catalog lock serializes transitive alias resolution with P4 merge.
 const source=(await client.query("SELECT id,status,merged_into_id FROM beer_positions WHERE id=$1 FOR SHARE",[n.positionId])).rows[0];
 const position=source?.status==='merged'?source.merged_into_id:n.positionId;
 if(!(await client.query("SELECT id FROM beer_positions WHERE id=$1 AND status='verified' FOR SHARE",[position])).rowCount)throw new ApiError(422,'POSITION_UNAVAILABLE','Позиция недоступна');
 const row=(await client.query<{id:string}>(`INSERT INTO checkins(user_id,position_id,rating_quarters,body,occurred_at,visibility,serving_format,venue_id,venue_text)
 VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id`,[user,position,n.ratingQuarters,n.body,n.occurredAt,n.visibility,n.servingFormat,n.venueId,n.venueText])).rows[0]!;
 await attachments(client,user,row.id,n);
 await client.query("INSERT INTO idempotency_keys(user_id,key,operation,request_hash,resource_id)VALUES($1,$2,'create-checkin',$3,$4)",[user,key,hash,row.id]);
 return{row:await getCheckin(client,user,row.id),replay:false};
 });
}
export async function updateCheckin(pool:pg.Pool,user:string,id:string,input:Omit<CheckinInput,'positionId'>&{version:number}) {
 return transaction(pool,async client=>{
 const old=(await client.query('SELECT position_id,version FROM checkins c WHERE c.user_id=$1 AND c.id=$2 AND '+d.active+' FOR UPDATE',[user,id])).rows[0];
 if(!old)throw notFound();if(old.version!==input.version)throw conflict();
 const current=await getCheckin(client,user,id);
 const n=normalized({...input,positionId:old.position_id,tagIds:input.tagIds??current.tagIds??[],mediaIds:input.mediaIds??current.mediaIds??[],
 servingFormat:input.servingFormat===undefined?current.servingFormat??null:input.servingFormat,venueId:input.venueId===undefined?current.venueId??null:input.venueId,venueText:input.venueText===undefined?current.venueText??null:input.venueText});
 await attachments(client,user,id,n);
 await client.query(`UPDATE checkins SET rating_quarters=$3,body=$4,occurred_at=$5,visibility=$6,serving_format=$7,venue_id=$8,venue_text=$9,version=version+1,updated_at=now() WHERE id=$1 AND user_id=$2`,[id,user,n.ratingQuarters,n.body,n.occurredAt,n.visibility,n.servingFormat,n.venueId,n.venueText]);
 return getCheckin(client,user,id);
 });
}
export async function deleteCheckin(pool:pg.Pool,user:string,id:string,version:number) {
 const result=await pool.query(`UPDATE checkins c SET deleted_at=now(),version=version+1,updated_at=now() WHERE c.user_id=$1 AND c.id=$2 AND c.version=$3 AND ${d.active}`,[user,id,version]);
 if(!result.rowCount){await getCheckin(pool,user,id);throw conflict();}
}
