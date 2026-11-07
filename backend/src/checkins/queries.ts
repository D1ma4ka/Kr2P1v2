import {createHash} from 'node:crypto';
import {ApiError} from '../http/errors.js';
import {validCursorTimestamp} from '../http/pagination.js';
export interface DiaryQuery {limit?:string;cursor?:string;q?:string;styleId?:string;breweryId?:string;positionId?:string;from?:string;to?:string;ratingMin?:string;ratingMax?:string;unrated?:string;hasPhoto?:string;venueId?:string;venue?:string;sort?:string;}
export const active='c.deleted_at IS NULL AND c.hidden_at IS NULL';
export const joins=`JOIN beer_positions bp ON bp.id=c.position_id JOIN beer_releases r ON r.id=bp.release_id
 JOIN breweries b ON b.id=r.brewery_id LEFT JOIN styles st ON st.id=r.style_id LEFT JOIN venues v ON v.id=c.venue_id`;
export const cols=`c.id,c.user_id AS "userId",c.position_id AS "positionId",c.rating_quarters AS "ratingQuarters",c.body,c.visibility,c.version,
 to_char(c.occurred_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "occurredAt",
 to_char(c.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "createdAt",
 to_char(c.updated_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "updatedAt",
 c.serving_format AS "servingFormat",c.venue_id AS "venueId",c.venue_text AS "venueText",
 v.name AS "venueName",v.city AS "venueCity",v.address AS "venueAddress",v.source AS "venueSource",
 r.name AS "positionName",b.id AS "breweryId",b.name AS "breweryName",r.style_id AS "styleId",st.name AS "styleName",bp.status AS "positionStatus",
 ARRAY(SELECT ct.tag_id FROM checkin_tags ct WHERE ct.checkin_id=c.id ORDER BY ct.tag_id) AS "tagIds",
 ARRAY(SELECT tt.label FROM checkin_tags ct JOIN taste_tags tt ON tt.id=ct.tag_id WHERE ct.checkin_id=c.id ORDER BY ct.tag_id) AS "tagLabels",
 ARRAY(SELECT cm.media_id FROM checkin_media cm JOIN media_assets ma ON ma.id=cm.media_id WHERE cm.checkin_id=c.id AND ma.deleted_at IS NULL AND ma.moderation_status<>'hidden' ORDER BY cm.sort_order) AS "mediaIds"`;
export function validDate(value:string) {
 if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})$/.test(value)||value.startsWith('0000')||!Number.isFinite(Date.parse(value))) return false;
 const [y,m,d]=value.slice(0,10).split('-').map(Number) as [number,number,number];
 return m>=1&&m<=12&&d>=1&&d<=new Date(Date.UTC(y,m,0)).getUTCDate()&&Number(value.slice(11,13))<24&&Number(value.slice(14,16))<60&&Number(value.slice(17,19))<60;
}
export function where(user:string,q:DiaryQuery) {
 const args:unknown[]=[user],parts=[`c.user_id=$1`,active];
 const add=(template:string,value:unknown)=>{args.push(value);parts.push(template.replaceAll('?',`$${args.length}`));};
 if(q.q?.trim()) for(const word of q.q.normalize('NFKC').trim().split(/\s+/u).slice(0,8)) add(`catalog_normalize(r.name||' '||b.name||' '||coalesce(st.name,'')||' '||c.body) LIKE '%'||catalog_normalize(?)||'%'`,word);
 if(q.styleId) add(`r.style_id=?`,q.styleId);
 if(q.breweryId) add(`b.id=?`,q.breweryId);
 if(q.positionId) add(`c.position_id=?`,q.positionId);
 for(const [key,operator] of [['from','>='],['to','<=']] as const) if(q[key]) {if(!validDate(q[key]!))throw new ApiError(400,'INVALID_DATE','Некорректная дата');add(`c.occurred_at${operator}?::timestamptz`,q[key]);}
 if(q.from&&q.to&&Date.parse(q.from)>Date.parse(q.to))throw new ApiError(400,'INVALID_RANGE','Начало позже окончания');
 if(q.ratingMin) add(`c.rating_quarters>=?::int`,Number(q.ratingMin));
 if(q.ratingMax) add(`c.rating_quarters<=?::int`,Number(q.ratingMax));
 if(q.ratingMin&&q.ratingMax&&Number(q.ratingMin)>Number(q.ratingMax))throw new ApiError(400,'INVALID_RANGE','Некорректный диапазон оценок');
 if(q.unrated==='true') parts.push('c.rating_quarters IS NULL');
 if(q.hasPhoto)parts.push(`${q.hasPhoto==='false'?'NOT ':''}EXISTS(SELECT 1 FROM checkin_media cm JOIN media_assets ma ON ma.id=cm.media_id WHERE cm.checkin_id=c.id AND ma.deleted_at IS NULL AND ma.moderation_status<>'hidden')`);
 if(q.venueId)add('c.venue_id=?',q.venueId);
 if(q.venue?.trim())add(`catalog_normalize(coalesce(c.venue_text,'')||' '||coalesce(v.name,'')) LIKE '%'||catalog_normalize(?)||'%'`,q.venue.trim());
 return {args,sql:parts.join(' AND ')};
}
export function fingerprint(q:DiaryQuery,scope:string) {const {limit:_,cursor:__,...rest}=q;return createHash('sha256').update(JSON.stringify([scope,Object.entries(rest).sort()])).digest('hex');}
export function cursor(value:string|undefined,hash:string,type:'date'|'number'|'text'):[string|number,string]|null {
 if(!value)return null;
 try {const c=JSON.parse(Buffer.from(value,'base64url').toString('utf8'));
 if(!Array.isArray(c)||c.length!==3||c[0]!==hash||typeof c[2]!=='string'||!/^[a-f0-9-]{36}$/i.test(c[2])||
 (type==='date'?(typeof c[1]!=='string'||!validCursorTimestamp(c[1])):type==='number'?(typeof c[1]!=='number'||!Number.isFinite(c[1])):typeof c[1]!=='string'))throw Error();return[c[1],c[2]];
 }catch{throw new ApiError(400,'INVALID_CURSOR','Курсор не соответствует фильтрам');}
}
export const encode=(hash:string,key:unknown,id:string)=>Buffer.from(JSON.stringify([hash,key,id])).toString('base64url');
// $1 is the viewer; profile and checkin privacy must both allow the read.
export const visible=`u.status='active' AND (c.user_id=$1 OR ((p.profile_visibility='public' OR p.profile_visibility='friends' AND EXISTS(SELECT 1 FROM friendships f WHERE f.user_low=least(c.user_id,$1::uuid) AND f.user_high=greatest(c.user_id,$1::uuid))) AND
 (c.visibility='public' OR c.visibility='friends' AND EXISTS(SELECT 1 FROM friendships f WHERE f.user_low=least(c.user_id,$1::uuid) AND f.user_high=greatest(c.user_id,$1::uuid)))))`;
