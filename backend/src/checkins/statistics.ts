import type pg from 'pg';
import * as d from './queries.js';
import {notFound} from '../http/errors.js';
export async function statistics(pool:pg.Pool,user:string) {
 const row=(await pool.query(`WITH mine AS (SELECT c.*,r.style_id,st.name AS style_name,b.id AS brewery_id,b.name AS brewery_name FROM checkins c ${d.joins} WHERE c.user_id=$1 AND ${d.active}),
 unique_beers AS (SELECT DISTINCT position_id,style_id,style_name,brewery_id,brewery_name FROM mine),
 styles AS (SELECT style_id AS id,style_name AS name,count(*)::int AS count FROM unique_beers GROUP BY style_id,style_name),
 breweries AS (SELECT brewery_id AS id,brewery_name AS name,count(*)::int AS count FROM unique_beers GROUP BY brewery_id,brewery_name)
 SELECT count(*)::int AS "totalCheckins",count(DISTINCT position_id)::int AS "uniquePositions",count(rating_quarters)::int AS "ratedCheckins",
 count(*) FILTER(WHERE rating_quarters IS NULL)::int AS "unratedCheckins",coalesce(sum(rating_quarters),0)::bigint::text AS "ratingSumQuarters",
 (avg(rating_quarters)/4)::float8 AS "averageRating",
 (SELECT coalesce(jsonb_agg(jsonb_build_object('ratingQuarters',n,'count',(SELECT count(*)::int FROM mine WHERE rating_quarters=n)) ORDER BY n),'[]') FROM generate_series(1,20)n) AS histogram,
 (SELECT coalesce(jsonb_agg(styles ORDER BY count DESC,name NULLS LAST,id),'[]') FROM styles) AS styles,
 (SELECT coalesce(jsonb_agg(breweries ORDER BY count DESC,name,id),'[]') FROM breweries) AS breweries,
 (SELECT count(*)::int FROM user_badges WHERE user_id=$1 AND earned_at IS NOT NULL) AS "earnedBadges"
 FROM mine`,[user])).rows[0];
 return row;
}
export async function positionStatistics(pool:pg.Pool,user:string,id:string) {
 const bp=(await pool.query('SELECT id,status,merged_into_id FROM beer_positions WHERE id=$1',[id])).rows[0];
 if(!bp)throw notFound();const canonical=bp.status==='merged'?bp.merged_into_id:id;
 const row=(await pool.query(`SELECT count(*)::int AS "totalCheckins",count(DISTINCT c.user_id)::int AS users,count(c.rating_quarters)::int AS "ratedCheckins",(avg(c.rating_quarters)/4)::float8 AS "averageRating",
 count(*) FILTER(WHERE c.user_id=$1)::int AS "personalCheckins",count(c.rating_quarters) FILTER(WHERE c.user_id=$1)::int AS "personalRatedCheckins",(avg(c.rating_quarters) FILTER(WHERE c.user_id=$1)/4)::float8 AS "personalAverageRating"
 FROM checkins c JOIN users u ON u.id=c.user_id JOIN user_profiles p ON p.user_id=u.id ${d.joins}
 WHERE c.position_id=$2 AND ${d.active} AND bp.status='verified' AND b.verification_status='verified' AND ${d.visible}`,[user,canonical])).rows[0];
 return {positionId:canonical,scope:'visible_to_viewer',...row};
}
export async function uniquePositions(pool:pg.Pool,user:string,q:d.DiaryQuery, wishlist=false) {
 const limit=Number(q.limit??20),hash=d.fingerprint(q,`${wishlist?'wishlist':'unique'}:${user}`),sort=q.sort??'name',key=sort==='latest'?'last_seen':sort==='rating'?'coalesce(average_rating,-1)':'sort_name',text=sort==='name';
 const after=d.cursor(q.cursor,hash,text?'text':sort==='latest'?'date':'number');
 const args:unknown[]=[user],conditions:string[]=[];
 const add=(sql:string,v:unknown)=>{args.push(v);conditions.push(sql.replaceAll('?',`$${args.length}`));};
 if(q.q?.trim())for(const word of q.q.trim().split(/\s+/).slice(0,8))add(`catalog_normalize(name||' '||brewery_name||' '||coalesce(style_name,'')) LIKE '%'||catalog_normalize(?)||'%'`,word);
 if(q.styleId==='unknown')conditions.push('style_id IS NULL');else if(q.styleId)add('style_id=?::uuid',q.styleId);
 if(q.breweryId)add('brewery_id=?::uuid',q.breweryId);
 if(after){args.push(...after);conditions.push(`(${key},id)${text?'>':'<'}($${args.length-1}::${text?'text':sort==='latest'?'timestamptz':'float8'},$${args.length}::uuid)`);}
 args.push(limit+1);
 const base=wishlist?`SELECT w.position_id,max(w.created_at) AS last_seen,0::int AS checkins,NULL::float8 AS average_rating FROM wishlist_items w WHERE w.user_id=$1 GROUP BY w.position_id`:
 `SELECT c.position_id,max(c.occurred_at) AS last_seen,count(*)::int AS checkins,(avg(c.rating_quarters)/4)::float8 AS average_rating FROM checkins c WHERE c.user_id=$1 AND ${d.active} GROUP BY c.position_id`;
 const rows=(await pool.query(`WITH base AS (${base}),items AS (SELECT bp.id,r.name,b.id AS brewery_id,b.name AS brewery_name,r.style_id,st.name AS style_name,bp.status,base.checkins,base.average_rating,base.last_seen,catalog_normalize(r.name) AS sort_name
 FROM base JOIN beer_positions bp ON bp.id=base.position_id JOIN beer_releases r ON r.id=bp.release_id JOIN breweries b ON b.id=r.brewery_id LEFT JOIN styles st ON st.id=r.style_id)
 SELECT id,name,brewery_id AS "breweryId",brewery_name AS "breweryName",style_id AS "styleId",style_name AS "styleName",status,checkins,average_rating AS "averageRating",sort_name AS "sortName",to_char(last_seen AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "lastSeen"
 FROM items ${conditions.length?'WHERE '+conditions.join(' AND '):''} ORDER BY ${key} ${text?'ASC':'DESC'},id ${text?'ASC':'DESC'} LIMIT $${args.length}`,args)).rows;
 const items=rows.slice(0,limit),last=items.at(-1);
 return{items,nextCursor:rows.length>limit?d.encode(hash,text?last!.sortName:sort==='latest'?last!.lastSeen:last!.averageRating??-1,last!.id):null};
}
export async function wishlistChange(pool:pg.Pool,user:string,id:string,add:boolean) {
 // The row share lock serializes canonical resolution with P4 merges.
 const {transaction}=await import('../db/pool.js');
 return transaction(pool,async client=>{
 const source=(await client.query('SELECT id,status,merged_into_id FROM beer_positions WHERE id=$1 FOR SHARE',[id])).rows[0];
 if(!source)throw notFound();const canonical=source.status==='merged'?source.merged_into_id:id;
 if(add){const p=await client.query("SELECT id FROM beer_positions WHERE id=$1 AND status='verified' FOR SHARE",[canonical]);if(!p.rowCount)throw notFound();
 await client.query('INSERT INTO wishlist_items(user_id,position_id)VALUES($1,$2) ON CONFLICT DO NOTHING',[user,canonical]);}
 else await client.query('DELETE FROM wishlist_items WHERE user_id=$1 AND position_id=$2',[user,canonical]);
 return{positionId:canonical,wished:add};
 });
}
