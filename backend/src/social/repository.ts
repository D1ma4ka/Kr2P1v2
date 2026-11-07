import type pg from 'pg';
import {transaction} from '../db/pool.js';
import {ApiError,conflict,notFound} from '../http/errors.js';
import {tokenHash} from '../identity/sessions.js';
import * as d from '../checkins/queries.js';

type DB=pg.Pool|pg.PoolClient;
export interface PageQuery {limit?:string;cursor?:string;q?:string;}
const friends=`EXISTS(SELECT 1 FROM friendships f WHERE f.user_low=least(u.id,$1::uuid) AND f.user_high=greatest(u.id,$1::uuid))`;
const profileVisible=`u.status='active' AND (u.id=$1 OR p.profile_visibility='public' OR p.profile_visibility='friends' AND ${friends})`;
const person=`u.id AS "userId",u.display_name AS "displayName"`;
const time=(column:string)=>`to_char(${column} AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"')`;
const requestCols=`id,from_user_id AS "fromUserId",to_user_id AS "toUserId",status,${time('created_at')} AS "createdAt"`;
const commentCols=`co.id,co.author_user_id AS "userId",u.display_name AS "displayName",co.checkin_id AS "checkinId",co.body,${time('co.created_at')} AS "createdAt"`;
const socialCols=`${d.cols},u.display_name AS "displayName",
 (SELECT count(*)::int FROM reactions rx JOIN users ru ON ru.id=rx.actor_user_id WHERE rx.checkin_id=c.id AND ru.status='active') AS "likeCount",
 EXISTS(SELECT 1 FROM reactions rx WHERE rx.checkin_id=c.id AND rx.actor_user_id=$1) AS liked,
 (SELECT count(*)::int FROM comments co JOIN users cu ON cu.id=co.author_user_id WHERE co.checkin_id=c.id AND co.deleted_at IS NULL AND co.hidden_at IS NULL AND cu.status='active') AS "commentCount"`;
const socialFrom=`FROM checkins c ${d.joins} JOIN users u ON u.id=c.user_id JOIN user_profiles p ON p.user_id=u.id`;
const socialVisible=`${d.active} AND ${d.visible}`;
function paging(q:PageQuery,scope:string) {
 const limit=Number(q.limit??20),hash=d.fingerprint(q,scope),after=d.cursor(q.cursor,hash,'date');
 return {limit,hash,after};
}
function page(rows:any[],limit:number,hash:string,key='createdAt',id='id') {
 const items=rows.slice(0,limit),last=items.at(-1);
 return {items,nextCursor:rows.length>limit?d.encode(hash,last[key],last[id]):null};
}
export async function search(pool:pg.Pool,user:string,q:PageQuery) {
 const {limit,hash,after}=paging(q,`people:${user}`);
 const rows=(await pool.query(`SELECT ${person},${time('u.created_at')} AS "createdAt" FROM users u JOIN user_profiles p ON p.user_id=u.id
 WHERE u.id<>$1 AND ${profileVisible} AND length(catalog_normalize($2))>=2 AND catalog_normalize(u.display_name) LIKE '%'||catalog_normalize($2)||'%'
 AND ($3::timestamptz IS NULL OR (u.created_at,u.id)<($3,$4::uuid)) ORDER BY u.created_at DESC,u.id DESC LIMIT $5`,[user,q.q??'',after?.[0]??null,after?.[1]??null,limit+1])).rows;
 return page(rows,limit,hash,'createdAt','userId');
}
async function pairLock(client:pg.PoolClient,a:string,b:string) {
 await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1,26))',[[a.toLowerCase(),b.toLowerCase()].sort().join(':')]);
}
export async function relationship(db:DB,user:string,target:string) {
 const result=await db.query(`SELECT EXISTS(SELECT 1 FROM friendships WHERE user_low=least($1::uuid,$2::uuid) AND user_high=greatest($1::uuid,$2::uuid)) AS friends,
 (SELECT id FROM friend_requests WHERE from_user_id=$1 AND to_user_id=$2 AND status='pending') AS "outgoingId",
 (SELECT id FROM friend_requests WHERE from_user_id=$2 AND to_user_id=$1 AND status='pending') AS "incomingId"`,[user,target]);
 return result.rows[0];
}
export async function requestFriend(pool:pg.Pool,user:string,id:string,target:string) {
 if(user===target.toLowerCase())throw new ApiError(400,'SELF_FRIEND','Нельзя добавить себя');
 return transaction(pool,async client=>{
 await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1,28))',[id.toLowerCase()]);
 await pairLock(client,user,target);
 const old=(await client.query(`SELECT ${requestCols} FROM friend_requests WHERE id=$1`,[id])).rows[0];
 if(old){if(old.fromUserId!==user||old.toUserId!==target.toLowerCase())throw conflict();return old;}
 await client.query('SELECT user_id FROM user_profiles WHERE user_id=$1 FOR SHARE',[target]);
 const participants=await client.query("SELECT id FROM users WHERE id=ANY($1::uuid[]) AND status='active' ORDER BY id FOR SHARE",[[user,target]]);
 if(participants.rowCount!==2)throw notFound();
 const allowed=await client.query(`SELECT u.id FROM users u JOIN user_profiles p ON p.user_id=u.id WHERE u.id=$2 AND ${profileVisible} FOR SHARE OF u,p`,[user,target]);
 if(!allowed.rowCount)throw notFound();
 const rel=await relationship(client,user,target);
 if(rel.friends||rel.incomingId||rel.outgoingId)throw conflict();
 return (await client.query(`INSERT INTO friend_requests(id,from_user_id,to_user_id)VALUES($1,$2,$3) RETURNING ${requestCols}`,[id,user,target])).rows[0];
 });
}
export async function decide(pool:pg.Pool,user:string,id:string,status:'accepted'|'rejected'|'cancelled') {
 return transaction(pool,async client=>{
 const hint=(await client.query('SELECT from_user_id,to_user_id FROM friend_requests WHERE id=$1 AND (from_user_id=$2 OR to_user_id=$2)',[id,user])).rows[0];if(!hint)throw notFound();
 await pairLock(client,hint.from_user_id,hint.to_user_id);
 const row=(await client.query(`SELECT ${requestCols} FROM friend_requests WHERE id=$1 FOR UPDATE`,[id])).rows[0];
 if((status==='cancelled'?row.fromUserId:row.toUserId)!==user)throw notFound();
 if(row.status===status)return row; // Replay never re-creates a removed friendship.
 if(row.status!=='pending')throw conflict();
 const users=await client.query("SELECT id FROM users WHERE id=ANY($1::uuid[]) AND status='active' ORDER BY id FOR SHARE",[[row.fromUserId,row.toUserId]]);
 if(users.rowCount!==2)throw notFound();
 if(status==='accepted')await client.query('INSERT INTO friendships(user_low,user_high)VALUES(least($1::uuid,$2::uuid),greatest($1::uuid,$2::uuid)) ON CONFLICT DO NOTHING',[row.fromUserId,row.toUserId]);
 return (await client.query(`UPDATE friend_requests SET status=$2,updated_at=now() WHERE id=$1 RETURNING ${requestCols}`,[id,status])).rows[0];
 });
}
export async function removeFriend(pool:pg.Pool,user:string,target:string) {
 await transaction(pool,async client=>{await pairLock(client,user,target);await client.query('DELETE FROM friendships WHERE user_low=least($1::uuid,$2::uuid) AND user_high=greatest($1::uuid,$2::uuid)',[user,target]);});
}
export async function connections(pool:pg.Pool,user:string,q:PageQuery,requests=false) {
 const {limit,hash,after}=paging(q,`${requests?'requests':'friends'}:${user}`);
 if(requests){
 const rows=(await pool.query(`SELECT fr.id,fr.from_user_id AS "fromUserId",fr.to_user_id AS "toUserId",fr.status,${person},${time('fr.created_at')} AS "createdAt"
 FROM friend_requests fr JOIN users u ON u.id=CASE WHEN fr.from_user_id=$1 THEN fr.to_user_id ELSE fr.from_user_id END
 WHERE (fr.from_user_id=$1 OR fr.to_user_id=$1) AND fr.status='pending' AND u.status='active'
 AND ($2::timestamptz IS NULL OR (fr.created_at,fr.id)<($2,$3::uuid)) ORDER BY fr.created_at DESC,fr.id DESC LIMIT $4`,[user,after?.[0]??null,after?.[1]??null,limit+1])).rows;return page(rows,limit,hash);
 }
 // Membership reveals only identity needed to manage the relationship; no private profile fields.
 const rows=(await pool.query(`SELECT ${person},${time('f.accepted_at')} AS "createdAt" FROM friendships f JOIN users u ON u.id=CASE WHEN f.user_low=$1 THEN f.user_high ELSE f.user_low END
 WHERE (f.user_low=$1 OR f.user_high=$1) AND u.status='active' AND ($2::timestamptz IS NULL OR (f.accepted_at,u.id)<($2,$3::uuid)) ORDER BY f.accepted_at DESC,u.id DESC LIMIT $4`,[user,after?.[0]??null,after?.[1]??null,limit+1])).rows;
 return page(rows,limit,hash,'createdAt','userId');
}
export async function feed(pool:pg.Pool,user:string,q:PageQuery,owner?:string) {
 const {limit,hash,after}=paging(q,`feed:${user}:${owner??'friends'}`);
 const rows=(await pool.query(`SELECT ${socialCols} ${socialFrom} WHERE ${socialVisible}
 AND (${owner?'c.user_id=$5':friends}) AND ($2::timestamptz IS NULL OR (c.created_at,c.id)<($2,$3::uuid)) ORDER BY c.created_at DESC,c.id DESC LIMIT $4`,[user,after?.[0]??null,after?.[1]??null,limit+1,...(owner?[owner]:[])])).rows;
 return page(rows,limit,hash);
}
export async function checkin(db:DB,user:string,id:string) {
 const row=(await db.query(`SELECT ${socialCols} ${socialFrom} WHERE c.id=$2 AND ${socialVisible}`,[user,id])).rows[0];if(!row)throw notFound();return row;
}
export async function publicStats(pool:pg.Pool,user:string,owner:string) {
 const row=(await pool.query(`SELECT count(c.id)::int AS "totalCheckins",count(DISTINCT c.position_id)::int AS "uniquePositions",count(c.rating_quarters)::int AS "ratedCheckins",(avg(c.rating_quarters)/4)::float8 AS "averageRating"
 FROM users u JOIN user_profiles p ON p.user_id=u.id LEFT JOIN checkins c ON c.user_id=u.id AND ${d.active} AND ${d.visible}
 WHERE u.id=$2 AND ${profileVisible} GROUP BY u.id`,[user,owner])).rows[0];if(!row)throw notFound();return {...row,scope:'visible_to_viewer'};
}
// Lock the actual ACL rows before testing visibility. Concurrent privacy changes,
// unfriend, checkin deletion and suspension wait until the write has committed.
async function lockCheckin(client:pg.PoolClient,user:string,id:string) {
 const c=(await client.query('SELECT user_id FROM checkins WHERE id=$1',[id])).rows[0];if(!c)throw notFound();
 await client.query('SELECT user_low FROM friendships WHERE user_low=least($1::uuid,$2::uuid) AND user_high=greatest($1::uuid,$2::uuid) FOR SHARE',[user,c.user_id]);
 // Profile updates lock profile before display-name user row: keep the same order.
 await client.query('SELECT user_id FROM user_profiles WHERE user_id=$1 FOR SHARE',[c.user_id]);
 const participants=await client.query("SELECT id FROM users WHERE id=ANY($1::uuid[]) AND status='active' ORDER BY id FOR SHARE",[[user,c.user_id]]);
 if(participants.rowCount!==new Set([user,c.user_id]).size)throw notFound();
 await client.query('SELECT id FROM checkins WHERE id=$1 FOR SHARE',[id]);
 await checkin(client,user,id);
}
export async function like(pool:pg.Pool,user:string,id:string,value:boolean) {
 return transaction(pool,async client=>{await lockCheckin(client,user,id);
 if(value)await client.query('INSERT INTO reactions(actor_user_id,checkin_id)VALUES($1,$2) ON CONFLICT DO NOTHING',[user,id]);
 else await client.query('DELETE FROM reactions WHERE actor_user_id=$1 AND checkin_id=$2',[user,id]);
 return checkin(client,user,id);});
}
export async function comments(pool:pg.Pool,user:string,id:string,q:PageQuery) {
 const {limit,hash,after}=paging(q,`comments:${user}:${id}`);
 // One statement gates both existence and comments, preventing a split-read ACL race.
 const result=await pool.query(`SELECT (${socialVisible}) AS allowed,
 coalesce((SELECT jsonb_agg(x ORDER BY x."createdAt" DESC,x.id DESC) FROM (SELECT ${commentCols} FROM comments co JOIN users u ON u.id=co.author_user_id
 WHERE co.checkin_id=c.id AND co.deleted_at IS NULL AND co.hidden_at IS NULL AND u.status='active'
 AND ($3::timestamptz IS NULL OR (co.created_at,co.id)<($3,$4::uuid)) ORDER BY co.created_at DESC,co.id DESC LIMIT $5) x),'[]'::jsonb) AS items
 ${socialFrom} WHERE c.id=$2`,[user,id,after?.[0]??null,after?.[1]??null,limit+1]);
 const row=result.rows[0];if(!row?.allowed)throw notFound();return page(row.items,limit,hash);
}
export async function addComment(pool:pg.Pool,user:string,id:string,commentId:string,body:string) {
 const trimmed=body.trim();if(!trimmed)throw new ApiError(400,'EMPTY_COMMENT','Введите комментарий');
 return transaction(pool,async client=>{
 await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1,27))',[commentId.toLowerCase()]);
 await lockCheckin(client,user,id);
 const hash=tokenHash(JSON.stringify([id.toLowerCase(),trimmed]));
 const old=(await client.query('SELECT * FROM comments WHERE id=$1',[commentId])).rows[0];
 if(old){if(old.author_user_id!==user||old.request_hash!==hash||old.deleted_at||old.hidden_at)throw conflict();}
 else await client.query('INSERT INTO comments(id,author_user_id,checkin_id,body,request_hash)VALUES($1,$2,$3,$4,$5)',[commentId,user,id,trimmed,hash]);
 return (await client.query(`SELECT ${commentCols} FROM comments co JOIN users u ON u.id=co.author_user_id WHERE co.id=$1`,[commentId])).rows[0];
 });
}
export async function deleteComment(pool:pg.Pool,user:string,id:string,commentId:string) {
 await transaction(pool,async client=>{await lockCheckin(client,user,id);
 const result=await client.query('UPDATE comments SET deleted_at=coalesce(deleted_at,now()),updated_at=now() WHERE id=$1 AND checkin_id=$2 AND author_user_id=$3 RETURNING id',[commentId,id,user]);if(!result.rowCount)throw notFound();});
}
