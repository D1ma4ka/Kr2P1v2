import { randomUUID, createHash } from 'node:crypto';
import sharp from 'sharp';
import type pg from 'pg';
import { transaction } from '../db/pool.js';
import { ApiError, conflict, notFound } from '../http/errors.js';
import type { Storage } from './storage.js';
export interface Media { id:string;width:number;height:number;sizeBytes:number;contentType:string; }
const columns='id,width,height,size_bytes AS "sizeBytes",content_type AS "contentType"';
export class MediaService {
 private inFlight=0;
 constructor(private pool:pg.Pool,private storage:Storage) {}
 async upload(owner:string,input:Buffer,contentType:string):Promise<Media> {
  if(this.inFlight>=2) throw new ApiError(429,'UPLOAD_BUSY','Повторите загрузку позже');
  this.inFlight++;
  const id=randomUUID(),key=`${id}-original.jpg`,thumbnailKey=`${id}-thumbnail.jpg`;
  let databaseAttempted=false;
  try {
   let data:Buffer,width:number,height:number,thumbnail:Buffer;
   try {
    const image=sharp(input,{limitInputPixels:16_000_000,animated:false,failOn:'warning'});
    const metadata=await image.metadata();
    const expected:Record<string,string>={'image/jpeg':'jpeg','image/png':'png','image/webp':'webp'};
    if(metadata.format!==expected[contentType] || (metadata.pages??1)>1) throw Error();
    const normalized=await image.rotate().resize({width:2048,height:2048,fit:'inside',withoutEnlargement:true})
      .flatten({background:'#ffffff'}).jpeg({quality:85}).toBuffer({resolveWithObject:true});
    data=normalized.data;width=normalized.info.width;height=normalized.info.height;
    thumbnail=await sharp(data).resize({width:320,height:320,fit:'inside',withoutEnlargement:true}).jpeg({quality:80}).toBuffer();
   } catch {throw new ApiError(422,'INVALID_IMAGE','Изображение повреждено или превышает ограничения');}
   await this.storage.put(key,data);
   await this.storage.put(thumbnailKey,thumbnail);
   databaseAttempted=true;
   return await transaction(this.pool,async client=>{
    const result=await client.query<Media>(`INSERT INTO media_assets(id,owner_id,storage_key,thumbnail_key,content_type,size_bytes,width,height,sha256)
     VALUES($1,$2,$3,$4,'image/jpeg',$5,$6,$7,$8) RETURNING ${columns}`,
     [id,owner,key,thumbnailKey,data.length,width,height,createHash('sha256').update(data).digest('hex')]);
    return result.rows[0]!;
   });
  } catch(error) {
   // A lost COMMIT acknowledgement is ambiguous: retain files rather than destroy possibly committed data.
   if(!databaseAttempted) await Promise.allSettled([this.storage.delete(key),this.storage.delete(thumbnailKey)]);
   throw error;
  } finally {this.inFlight--;}
 }
 async get(owner:string,id:string,thumbnail=false) {
  const result=await this.pool.query(`SELECT storage_key,thumbnail_key FROM media_assets WHERE id=$1 AND owner_id=$2 AND deleted_at IS NULL AND moderation_status<>'hidden'`,[id,owner]);
  if(!result.rows[0]) throw notFound();
  return this.storage.get(thumbnail?result.rows[0].thumbnail_key:result.rows[0].storage_key);
 }
 async remove(owner:string,id:string) {
  const keys=await transaction(this.pool,async client=>{
   const result=await client.query('SELECT storage_key,thumbnail_key FROM media_assets WHERE id=$1 AND owner_id=$2 FOR UPDATE',[id,owner]);
   if(!result.rows[0]) throw notFound();
   const attached=await client.query(`SELECT 1 FROM checkin_media m JOIN checkins c ON c.id=m.checkin_id WHERE m.media_id=$1 AND c.deleted_at IS NULL
    UNION ALL SELECT 1 FROM user_profiles WHERE avatar_asset_id=$1 OR cover_asset_id=$1 LIMIT 1`,[id]);
   if(attached.rowCount) throw conflict();
   await client.query('UPDATE media_assets SET deleted_at=coalesce(deleted_at,now()) WHERE id=$1',[id]);
   return result.rows[0];
  });
  // A tombstone prevents new reads/attachments; an interrupted deletion is safe to retry.
  await this.storage.delete(keys.storage_key); await this.storage.delete(keys.thumbnail_key);
 }
 async attach(owner:string,checkinId:string,version:number,ids:string[]) {
  return transaction(this.pool,async client=>{
   const c=await client.query('SELECT version FROM checkins WHERE id=$1 AND user_id=$2 AND deleted_at IS NULL AND hidden_at IS NULL FOR UPDATE',[checkinId,owner]);
   if(!c.rows[0]) throw notFound(); if(c.rows[0].version!==version) throw conflict();
   // Stable lock order prevents deadlocks when several requests attach overlapping assets.
   const assets=await client.query("SELECT id FROM media_assets WHERE id=ANY($1::uuid[]) AND owner_id=$2 AND deleted_at IS NULL AND moderation_status<>'hidden' ORDER BY id FOR SHARE",[ids,owner]);
   if(assets.rowCount!==ids.length) throw notFound();
   await client.query('DELETE FROM checkin_media WHERE checkin_id=$1',[checkinId]);
   for(const [index,id] of ids.entries()) await client.query('INSERT INTO checkin_media(checkin_id,owner_id,media_id,sort_order) VALUES($1,$2,$3,$4)',[checkinId,owner,id,index+1]);
   await client.query('UPDATE checkins SET version=version+1,updated_at=now() WHERE id=$1',[checkinId]);
   return {mediaIds:ids,version:version+1};
  });
 }
 async list(owner:string,checkinId:string) {
  const c=await this.pool.query('SELECT version FROM checkins WHERE id=$1 AND user_id=$2 AND deleted_at IS NULL AND hidden_at IS NULL',[checkinId,owner]);
  if(!c.rows[0]) throw notFound();
  const rows=await this.pool.query("SELECT m.media_id FROM checkin_media m JOIN media_assets a ON a.id=m.media_id WHERE m.checkin_id=$1 AND a.deleted_at IS NULL AND a.moderation_status<>'hidden' ORDER BY m.sort_order",[checkinId]);
  return {mediaIds:rows.rows.map(row=>row.media_id as string),version:c.rows[0].version as number};
 }
}
