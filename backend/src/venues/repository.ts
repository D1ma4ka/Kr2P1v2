import type pg from 'pg';
import {createHash} from 'node:crypto';
import {transaction} from '../db/pool.js';
import {ApiError,conflict,notFound} from '../http/errors.js';
import {requireModerator} from '../catalog/proposals.js';
import * as diary from '../checkins/queries.js';
export type VenueScope = 'all'|'visited'|'saved';
export interface VenueQuery {scope?:VenueScope;q?:string;city?:string;type?:string;bbox?:string;limit?:string;cursor?:string;status?:string;}
export interface VenueInput {version:number;name:string;type:string;city:string;country:string|null;address:string|null;source:string;latitude:number|null;longitude:number|null;coordinatesSource:string|null;imageAssetId:string|null;imageSource:string|null;rightsConfirmed:boolean;testOnly:boolean;verified:boolean;}
const cols=`v.id,v.name,v.type,v.city,v.country,v.address,v.source,v.latitude,v.longitude,
 v.coordinates_source AS "coordinatesSource",v.coordinates_verified_at AS "coordinatesVerifiedAt",
 CASE WHEN ma.deleted_at IS NULL AND ma.moderation_status='approved' THEN v.image_asset_id ELSE NULL END AS "imageAssetId",
 CASE WHEN ma.deleted_at IS NULL AND ma.moderation_status='approved' THEN v.image_source ELSE NULL END AS "imageSource",v.version,v.status,v.is_test AS "testOnly"`;
export function bbox(value:string):[number,number,number,number] {
 const parts=value.split(',');const n=parts.map(Number);
 if(parts.length!==4||parts.some(x=>!x.trim())||n.some(x=>!Number.isFinite(x))||n[0]! < -180||n[0]!>180||n[2]! < -180||n[2]!>180||n[1]! < -90||n[3]!>90||n[1]!>=n[3]!||n[0]===n[2])throw new ApiError(400,'INVALID_BBOX','Некорректные границы карты');
 return n as [number,number,number,number];
}
function where(q:Pick<VenueQuery,'q'|'city'|'type'|'bbox'|'status'|'scope'>,allowTest:boolean,map=false,admin=false,viewer?:string) {
 const args:unknown[]=[allowTest],parts=[...(admin?[]:["v.status='verified'"]),'(NOT v.is_test OR $1)'];
 const add=(sql:string,value:unknown)=>{args.push(value);parts.push(sql.replaceAll('?',`$${args.length}`));};
 if(viewer&&q.scope==='saved') add('EXISTS(SELECT 1 FROM user_saved_venues sv WHERE sv.venue_id=v.id AND sv.user_id=?)',viewer);
 if(viewer&&q.scope==='visited') add(`EXISTS(SELECT 1 FROM checkins c WHERE c.venue_id=v.id AND c.user_id=? AND ${diary.active})`,viewer);
 if(admin&&q.status)add('v.status=?',q.status);
 if(q.type)add('v.type=?',q.type);
 if(q.city)add('catalog_normalize(v.city)=catalog_normalize(?)',q.city);
 if(q.q)add("catalog_normalize(v.name||' '||v.city||' '||coalesce(v.address,'')) LIKE '%'||catalog_normalize(?)||'%'",q.q);
 if(map)parts.push('v.latitude IS NOT NULL AND v.coordinates_source IS NOT NULL');
 if(q.bbox) {
  const [west,south,east,north]=bbox(q.bbox);
  add('v.latitude>=?',south);add('v.latitude<=?',north);
  args.push(west,east);parts.push(`(v.longitude >= $${args.length-1} ${west<=east?'AND':'OR'} v.longitude<= $${args.length})`);
  parts.push('v.coordinates_source IS NOT NULL');
 }
 return {args,sql:parts.join(' AND ')};
}
async function queryList(pool:pg.Pool,q:VenueQuery,allowTest:boolean,map=false,admin=false,viewer?:string) {
 const w=where(q,allowTest,map,admin,viewer),limit=map?500:Number(q.limit??20);
 const hash=createHash('sha256').update(JSON.stringify([q.q??'',q.city??'',q.type??'',q.bbox??'',allowTest,admin,q.status??'',...(q.scope&&q.scope!=='all'?[q.scope,viewer]:[])])).digest('hex').slice(0,24);
 if(q.cursor&&!map) {
  try {const c=JSON.parse(Buffer.from(q.cursor,'base64url').toString());if(!Array.isArray(c)||c.length!==2||c[0]!==hash||!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(c[1]))throw Error();w.args.push(c[1]);w.sql+=` AND v.id>$${w.args.length}::uuid`;}
  catch{throw new ApiError(400,'INVALID_CURSOR','Курсор не соответствует фильтрам');}
 }
 w.args.push(limit+1);
 const result=(await pool.query(`SELECT ${map?'v.id,v.name,v.type,v.latitude,v.longitude,v.is_test AS "testOnly"':cols} FROM venues v ${map?'':'LEFT JOIN media_assets ma ON ma.id=v.image_asset_id'} WHERE ${w.sql} ORDER BY v.id LIMIT $${w.args.length}`,w.args)).rows;
 const items=result.slice(0,limit);
 return map?{items,truncated:result.length>limit}:{items,nextCursor:result.length>limit?Buffer.from(JSON.stringify([hash,items.at(-1)!.id])).toString('base64url'):null};
}
export interface NearbyQuery {scope?:VenueScope;latitude:number;longitude:number;radiusKm:number;q?:string;type?:string;limit?:number;}
export function distanceSql(latitude:string,longitude:string) {
 return `6371.0088 * 2 * asin(sqrt(least(1.0,
    power(sin(radians(v.latitude-${latitude}::float8)/2),2) +
    cos(radians(${latitude}::float8))*cos(radians(v.latitude)) *
    power(sin(radians(v.longitude-${longitude}::float8)/2),2))))`;
}
export async function nearby(pool:pg.Pool,q:NearbyQuery,allowTest:boolean,viewer?:string) {
 const filters=where(q,allowTest,true,false,viewer),limit=q.limit??20;
 const offset=filters.args.length;
 filters.args.push(q.latitude,q.longitude,q.radiusKm,limit+1);
 const rows=(await pool.query(`WITH measured AS (
    SELECT ${cols},${distanceSql(`$${offset+1}`,`$${offset+2}`)} AS "distanceKm"
    FROM venues v LEFT JOIN media_assets ma ON ma.id=v.image_asset_id
    WHERE ${filters.sql} AND abs(v.latitude-$${offset+1}::float8) <= $${offset+3}::float8 / 110
  ) SELECT * FROM measured WHERE "distanceKm" <= $${offset+3}
  ORDER BY "distanceKm",id LIMIT $${offset+4}`,filters.args)).rows;
 return {items:rows.slice(0,limit),truncated:rows.length>limit};
}
export function list(pool:pg.Pool,q:VenueQuery,allowTest:boolean,map=false,viewer?:string) {return queryList(pool,q,allowTest,map,false,viewer);}
export async function moderationList(pool:pg.Pool,actor:string,q:VenueQuery,allowTest:boolean) {
 await requireModerator(pool,actor);return queryList(pool,q,allowTest,false,true);
}
export async function moderationDetail(pool:pg.Pool,actor:string,id:string,allowTest:boolean) {
 await requireModerator(pool,actor);
 const row=(await pool.query(`SELECT ${cols} FROM venues v LEFT JOIN media_assets ma ON ma.id=v.image_asset_id WHERE v.id=$1 AND (NOT v.is_test OR $2)`,[id,allowTest])).rows[0];if(!row)throw notFound();return row;
}
export async function cities(pool:pg.Pool,q:string,allowTest:boolean) {
 return {items:(await pool.query(`SELECT v.city,count(*)::int AS count,
 avg(v.latitude) FILTER(WHERE v.coordinates_source IS NOT NULL) AS latitude,
 degrees(atan2(avg(sin(radians(v.longitude))) FILTER(WHERE v.coordinates_source IS NOT NULL),avg(cos(radians(v.longitude))) FILTER(WHERE v.coordinates_source IS NOT NULL))) AS longitude
 FROM venues v WHERE v.status='verified' AND (NOT v.is_test OR $1) AND catalog_normalize(v.city) LIKE '%'||catalog_normalize($2)||'%'
 GROUP BY v.city ORDER BY v.city COLLATE "C" LIMIT 50`,[allowTest,q])).rows};
}
export async function detail(pool:pg.Pool,user:string,id:string,allowTest:boolean) {
 const row=(await pool.query(`SELECT ${cols},EXISTS(
   SELECT 1 FROM user_saved_venues sv WHERE sv.user_id=$3 AND sv.venue_id=v.id
 ) AS saved FROM venues v LEFT JOIN media_assets ma ON ma.id=v.image_asset_id
 WHERE v.id=$1 AND v.status='verified' AND (NOT v.is_test OR $2)`,[id,allowTest,user])).rows[0];
 if(!row)throw notFound();
 const stats=(await pool.query(`SELECT count(*)::int AS "totalCheckins",count(c.rating_quarters)::int AS "ratedCheckins",(avg(c.rating_quarters)/4)::float8 AS "averageRating"
 FROM checkins c JOIN users u ON u.id=c.user_id JOIN user_profiles p ON p.user_id=u.id ${diary.joins}
 WHERE c.venue_id=$2 AND ${diary.active} AND ${diary.visible}`,[user,id])).rows[0];
 return {...row,statistics:{scope:'visible_to_viewer',...stats}};
}
export async function changeSaved(pool:pg.Pool,user:string,id:string,saved:boolean,allowTest:boolean) {
 if (!saved) {
  await pool.query('DELETE FROM user_saved_venues WHERE user_id=$1 AND venue_id=$2',[user,id]);
  return {saved:false};
 }
 return transaction(pool,async db=>{
  const venue=await db.query("SELECT id FROM venues WHERE id=$1 AND status='verified' AND (NOT is_test OR $2) FOR SHARE",[id,allowTest]);
  if(!venue.rowCount) throw notFound();
  await db.query('INSERT INTO user_saved_venues(user_id,venue_id) VALUES($1,$2) ON CONFLICT DO NOTHING',[user,id]);
  return {saved:true};
 });
}

export async function save(pool:pg.Pool,actor:string,id:string,input:VenueInput,allowTest:boolean) {
 return transaction(pool,async db=>{
  await requireModerator(db,actor);
  if(input.testOnly&&!allowTest)throw new ApiError(422,'TEST_ONLY','Тестовые заведения запрещены в production');
  if(!input.name.trim()||!input.city.trim()||!input.source.trim())throw new ApiError(422,'SOURCE_REQUIRED','Нужны название, город и источник');
  if((input.latitude===null)!==(input.longitude===null)||input.latitude!==null&&!input.coordinatesSource?.trim())throw new ApiError(422,'COORDINATE_SOURCE_REQUIRED','Подтвердите координаты отдельным источником');
  if(input.latitude===null&&input.coordinatesSource!==null)throw new ApiError(422,'INVALID_COORDINATES','Источник координат задан без координат');
  if(input.imageAssetId) {
   if(!input.rightsConfirmed||!input.imageSource?.trim())throw new ApiError(422,'IMAGE_RIGHTS_REQUIRED','Подтвердите источник и права на фото');
   const asset=await db.query("SELECT id FROM media_assets WHERE id=$1 AND (owner_id=$2 OR moderation_status='approved' AND EXISTS(SELECT 1 FROM venues WHERE venues.id=$3 AND venues.image_asset_id=media_assets.id)) AND deleted_at IS NULL AND moderation_status<>'hidden' FOR UPDATE",[input.imageAssetId,actor,id]);if(!asset.rowCount)throw notFound();
  } else if(input.imageSource!==null||input.rightsConfirmed)throw new ApiError(422,'INVALID_IMAGE','Фотография не выбрана');
  await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,220028))',[`venue:${id.toLowerCase()}`]);
  const old=(await db.query('SELECT version,status FROM venues WHERE id=$1 FOR UPDATE',[id])).rows[0];
  if(old?.status==='hidden')throw conflict();
  if(old?old.version!==input.version:input.version!==0)throw conflict();
  const values=[id,input.name.trim(),input.type,input.city.trim(),input.country?.trim()||null,input.address?.trim()||null,input.source.trim(),input.latitude,input.longitude,input.coordinatesSource?.trim()||null,input.imageAssetId,input.imageSource?.trim()||null,input.testOnly,input.verified?'verified':'pending'];
  await db.query(`INSERT INTO venues(id,name,type,city,country,address,source,latitude,longitude,coordinates_source,coordinates_verified_at,image_asset_id,image_source,is_test,status)
   VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,CASE WHEN $8::float8 IS NOT NULL THEN now() ELSE NULL END,$11,$12,$13,$14)
   ON CONFLICT(id) DO UPDATE SET name=excluded.name,type=excluded.type,city=excluded.city,country=excluded.country,address=excluded.address,source=excluded.source,
    latitude=excluded.latitude,longitude=excluded.longitude,coordinates_source=excluded.coordinates_source,coordinates_verified_at=excluded.coordinates_verified_at,
    image_asset_id=excluded.image_asset_id,image_source=excluded.image_source,is_test=excluded.is_test,status=excluded.status,version=venues.version+1,updated_at=now()`,values);
  if(input.imageAssetId)await db.query("UPDATE media_assets SET moderation_status='approved' WHERE id=$1",[input.imageAssetId]);
  await db.query("INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason)VALUES($1,'venue.save','venue',$2,$3)",[actor,id,input.source.trim()]);
  return (await db.query(`SELECT ${cols} FROM venues v LEFT JOIN media_assets ma ON ma.id=v.image_asset_id WHERE v.id=$1`,[id])).rows[0];
 });
}
