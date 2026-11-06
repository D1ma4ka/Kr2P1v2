import {createHash} from 'node:crypto';
import type pg from 'pg';
import {ApiError,notFound} from '../http/errors.js';
import {validCursorTimestamp} from '../http/pagination.js';
import {normalizeGtin} from './gtin.js';
export type Db=pg.Pool|pg.PoolClient;
export interface CatalogQuery {limit?:string;cursor?:string;q?:string;breweryId?:string;styleId?:string;packaging?:string;abvMin?:string;abvMax?:string;sort?:'newest'|'name'|'abv';}
export const normalize=(s:string)=>s.normalize('NFKC').toLocaleLowerCase('ru').replaceAll('ё','е').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
function signature(query:CatalogQuery,scope:string) {
 const {cursor:_cursor,limit:_limit,...filters}=query;
 return createHash('sha256').update(JSON.stringify([scope,Object.entries(filters).sort()])).digest('hex').slice(0,24);
}
function cursorFor(query:CatalogQuery,scope:string):[string,string]|null {
 if(!query.cursor) return null;
 try {
  const [sig,key,id]=JSON.parse(Buffer.from(query.cursor,'base64url').toString('utf8')) as unknown[];
  if(sig!==signature(query,scope)||typeof key!=='string'||key.length>600||typeof id!=='string'||!/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(id)) throw Error();
  if((query.sort??'newest')==='newest' && !validCursorTimestamp(key)) throw Error();
  if(query.sort==='abv' && (!/^-?\d+(?:\.\d+)?$/.test(key)||Number(key)<-1||Number(key)>100)) throw Error();
  return [key,id];
 } catch {throw new ApiError(400,'INVALID_CURSOR','Курсор не соответствует запросу. Обновите список.');}
}
export function catalogPage(rows:Record<string,unknown>[],query:CatalogQuery,scope:string) {
 const limit=Number(query.limit??20),items=rows.slice(0,limit),last=items.at(-1);
 const nextCursor=rows.length>limit&&last?Buffer.from(JSON.stringify([signature(query,scope),String(last._key),last.id])).toString('base64url'):null;
 return {items:items.map(({_key,...item})=>item),nextCursor};
}
export const positionColumns=`p.id,p.id AS "requestedId",p.id AS "canonicalId",p.release_id AS "releaseId",r.name,
 r.brewery_id AS "breweryId",b.name AS "breweryName",r.style_id AS "styleId",s.name AS "styleName",r.brand,
 r.abv::float8,r.ibu::float8,r.description,p.packaging,p.volume_ml AS "volumeMl",p.edition,p.variant,p.source,p.status,
 p.verified_at AS "verifiedAt",to_char(p.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "createdAt",
 CASE WHEN a.moderation_status='approved' AND a.deleted_at IS NULL THEN a.id ELSE NULL END AS "imageAssetId",
 CASE WHEN a.moderation_status='approved' AND a.deleted_at IS NULL THEN p.image_source ELSE NULL END AS "imageSource"`;
const joins=`JOIN beer_releases r ON r.id=p.release_id JOIN breweries b ON b.id=r.brewery_id LEFT JOIN styles s ON s.id=r.style_id LEFT JOIN media_assets a ON a.id=p.image_asset_id`;
export async function positions(db:Db,query:CatalogQuery) {
 if(Number(query.abvMin??0)>Number(query.abvMax??100)||Number(query.abvMax??100)>100) throw new ApiError(422,'INVALID_RANGE','Некорректный диапазон ABV');
 const values:unknown[]=[],where:string[]=[];
 const bind=(value:unknown)=>{values.push(value);return `$${values.length}`;};
 const terms=[...new Set(normalize(query.q??'').split(' ').filter(Boolean))];
 if(terms.length>8) throw new ApiError(422,'QUERY_TOO_LONG','Введите не более восьми слов');
 for(const term of terms) where.push(`c.search_text LIKE ${bind('%'+term+'%')}`);
 for(const [key,column] of [['breweryId','brewery_id'],['styleId','style_id'],['packaging','packaging']] as const)
  if(query[key]) where.push(`c.${column}=${bind(query[key])}`);
 if(query.abvMin!==undefined) where.push(`c.abv>=${bind(Number(query.abvMin))}`);
 if(query.abvMax!==undefined) where.push(`c.abv>=0 AND c.abv<=${bind(Number(query.abvMax))}`);
 const sort=query.sort??'newest',col=sort==='name'?'c.name_key COLLATE "C"':sort==='abv'?'c.abv':'c.created_at';
 const after=cursorFor(query,'positions');
 if(after) where.push(`(${col},c.position_id)${sort==='newest'?'<':'>'}(${bind(after[0])}${sort==='name'?'::text COLLATE "C"':sort==='abv'?'::numeric':'::timestamptz'},${bind(after[1])}::uuid)`);
 const key=sort==='newest'?`to_char(c.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"')`:sort==='abv'?'c.abv::text':'c.name_key';
 const result=await db.query(`SELECT ${positionColumns},${key} AS _key FROM catalog_search c JOIN beer_positions p ON p.id=c.position_id ${joins}
 ${where.length?'WHERE '+where.join(' AND '):''} ORDER BY ${col} ${sort==='newest'?'DESC':'ASC'},c.position_id ${sort==='newest'?'DESC':'ASC'} LIMIT ${bind(Number(query.limit??20)+1)}`,values);
 return catalogPage(result.rows,query,'positions');
}
export async function position(db:Db,id:string) {
 const resolved=await db.query(`WITH RECURSIVE path AS (
 SELECT id,status,merged_into_id,ARRAY[id] AS visited FROM beer_positions WHERE id=$1
 UNION ALL SELECT p.id,p.status,p.merged_into_id,path.visited||p.id FROM beer_positions p JOIN path ON p.id=path.merged_into_id
 WHERE NOT p.id=ANY(path.visited) AND cardinality(path.visited)<32)
 SELECT id FROM path WHERE status='verified'`,[id]);
 const canonical=resolved.rows[0]?.id;
 if(!canonical) throw notFound();
 const result=await db.query(`SELECT ${positionColumns} FROM beer_positions p ${joins} WHERE p.id=$1 AND b.verification_status='verified'`,[canonical]);
 if(!result.rows[0]) throw notFound();
 return {...result.rows[0],requestedId:id};
}
const breweryColumns=`b.id,b.name,b.country,b.city,b.description,b.source,b.verification_status AS status,
 CASE WHEN a.moderation_status='approved' AND a.deleted_at IS NULL THEN a.id ELSE NULL END AS "imageAssetId",
 CASE WHEN a.moderation_status='approved' AND a.deleted_at IS NULL THEN b.image_source ELSE NULL END AS "imageSource",
 to_char(b.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "createdAt"`;
export async function brewery(db:Db,id:string) {
 const result=await db.query(`SELECT ${breweryColumns} FROM breweries b LEFT JOIN media_assets a ON a.id=b.image_asset_id WHERE b.id=$1 AND b.verification_status='verified'`,[id]);
 if(!result.rows[0]) throw notFound(); return result.rows[0];
}
export async function breweries(db:Db,query:CatalogQuery) {
 const after=cursorFor(query,'breweries');
 const result=await db.query(`SELECT ${breweryColumns},to_char(b.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS _key
 FROM breweries b LEFT JOIN media_assets a ON a.id=b.image_asset_id WHERE b.verification_status='verified'
 AND catalog_normalize(b.name) LIKE $1 AND ($2::timestamptz IS NULL OR (b.created_at,b.id)<($2::timestamptz,$3::uuid))
 ORDER BY b.created_at DESC,b.id DESC LIMIT $4`,['%'+normalize(query.q??'')+'%',after?.[0]??null,after?.[1]??null,Number(query.limit??20)+1]);
 return catalogPage(result.rows,query,'breweries');
}
export async function lookup(db:Db,code:string,kind:string) {
 if(kind==='qr') return {state:'unsupported_qr',gtin:null,position:null,source:null};
 const gtin=normalizeGtin(code);
 const mapping=await db.query(`SELECT m.position_id FROM barcode_mappings m JOIN catalog_search c ON c.position_id=m.position_id WHERE m.gtin=$1 AND m.status='verified'`,[gtin]);
 if(mapping.rows[0]) return {state:'found',gtin,position:await position(db,mapping.rows[0].position_id),source:null};
 const exclusion=await db.query('SELECT source FROM barcode_exclusions WHERE gtin=$1',[gtin]);
 return {state:exclusion.rowCount?'non_beer':'not_found',gtin,position:null,source:exclusion.rows[0]?.source??null};
}
