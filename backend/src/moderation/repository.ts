import type pg from 'pg';
import {transaction} from '../db/pool.js';
import {ApiError,conflict,notFound} from '../http/errors.js';
import {requireModerator} from '../catalog/proposals.js';
import {profile} from '../profiles/repository.js';
import {position} from '../catalog/repository.js';
import {checkin} from '../social/repository.js';
import {decodeCursor,encodeCursor} from '../http/pagination.js';
import {detail as venue} from '../venues/repository.js';
export type Subject='user'|'checkin'|'comment'|'venue'|'position';
export interface ReportInput {subjectType:Subject;subjectId:string;reason:string;}
const reportCols=`id,subject_type AS "subjectType",subject_id AS "subjectId",reason,status,version,
 to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "createdAt"`;
async function visible(pool:pg.Pool,user:string,input:ReportInput,allowTest:boolean) {
 switch(input.subjectType) {
  case 'user': {const p=await profile(pool,input.subjectId,user);if(p.profileVisibility!=='public')throw notFound();return;}
  case 'checkin': {const c=await checkin(pool,user,input.subjectId);const p=await profile(pool,c.userId,user);if(c.visibility!=='public'||p.profileVisibility!=='public')throw notFound();return;}
  case 'comment': {
   if(!(await pool.query(`SELECT co.id FROM comments co JOIN checkins c ON c.id=co.checkin_id JOIN users u ON u.id=c.user_id JOIN user_profiles p ON p.user_id=u.id JOIN users author ON author.id=co.author_user_id
    WHERE co.id=$1 AND co.deleted_at IS NULL AND co.hidden_at IS NULL AND c.deleted_at IS NULL AND c.hidden_at IS NULL AND c.visibility='public' AND p.profile_visibility='public' AND u.status='active' AND author.status='active'`,[input.subjectId])).rowCount)throw notFound();return;
  }
  case 'venue': await venue(pool,user,input.subjectId,allowTest);return;
  case 'position': await position(pool,input.subjectId);return;
 }
}
export async function report(pool:pg.Pool,user:string,id:string,input:ReportInput,allowTest:boolean) {
 if(!input.reason.trim())throw new ApiError(422,'REASON_REQUIRED','Опишите причину жалобы');
 const clean={...input,subjectId:input.subjectId.toLowerCase(),reason:input.reason.trim()};
 return transaction(pool,async db=>{
  await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,220029))',[id.toLowerCase()]);
  const old=(await db.query(`SELECT ${reportCols},reporter_id FROM reports WHERE id=$1`,[id])).rows[0];
  if(old) {
   if(old.reporter_id!==user)throw notFound();
   if(old.subjectType!==clean.subjectType||old.subjectId!==clean.subjectId||old.reason!==clean.reason)throw conflict();
   return old;
  }
  await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,220030))',[user]);
  const budget=(await db.query("SELECT count(*)::int AS count FROM reports WHERE reporter_id=$1 AND created_at>now()-interval '1 hour'",[user])).rows[0];
  if(budget.count>=20)throw new ApiError(429,'RATE_LIMITED','Слишком много жалоб; повторите позже');
  await visible(pool,user,clean,allowTest);
  return (await db.query(`INSERT INTO reports(id,reporter_id,subject_type,subject_id,reason)VALUES($1,$2,$3,$4,$5)RETURNING ${reportCols}`,[id,user,clean.subjectType,clean.subjectId,clean.reason])).rows[0];
 });
}
export async function reports(pool:pg.Pool,actor:string,q:{limit?:string;cursor?:string;status?:string}) {
 await requireModerator(pool,actor);const after=decodeCursor(q.cursor),limit=Number(q.limit??20);
 const rows=(await pool.query(`SELECT ${reportCols},reporter_id AS "reporterId" FROM reports WHERE ($1::text IS NULL OR status=$1)
  AND ($2::timestamptz IS NULL OR (created_at,id)>($2,$3::uuid)) ORDER BY created_at,id LIMIT $4`,[q.status??null,after?.[0]??null,after?.[1]??null,limit+1])).rows;
 return {items:rows.slice(0,limit),nextCursor:rows.length>limit?encodeCursor(rows[limit-1]):null};
}
const subjects:Record<Subject,{table:string;state:string;normal:string;hidden:string}>= {
 user:{table:'users',state:'status',normal:'active',hidden:'suspended'},
 venue:{table:'venues',state:'status',normal:'verified',hidden:'hidden'},
 position:{table:'beer_positions',state:'status',normal:'verified',hidden:'rejected'},
 checkin:{table:'checkins',state:'hidden_at',normal:'visible',hidden:'hidden'},
 comment:{table:'comments',state:'hidden_at',normal:'visible',hidden:'hidden'},
};
async function change(db:pg.PoolClient,actor:string,type:Subject,id:string,action:'hide'|'restore') {
 const cfg=subjects[type];if(!cfg)throw new ApiError(422,'INVALID_SUBJECT','Неизвестный тип контента');
 await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,220028))',[`${type}:${id}`]);
 const row=(await db.query(`SELECT * FROM ${cfg.table} WHERE id=$1 FOR UPDATE`,[id])).rows[0];
 if(!row)throw notFound();
 if(row.deleted_at||row.status==='deleted')throw new ApiError(422,'DELETED_CONTENT','Удалённый контент не восстанавливается');
 if(type==='user') {
  if(id===actor)throw new ApiError(422,'SELF_MODERATION','Нельзя скрыть собственный аккаунт');
  const role=(await db.query('SELECT role FROM users WHERE id=$1',[actor])).rows[0]?.role;
  if(row.role!=='user'&&role!=='admin')throw new ApiError(403,'FORBIDDEN','Требуется администратор');
  if((await db.query('SELECT 1 FROM account_deletion_requests WHERE user_id=$1',[id])).rowCount)throw new ApiError(422,'DELETION_REQUESTED','Нельзя отменить запрос удаления модерацией');
 }
 const hold=(await db.query('SELECT previous_state FROM moderation_holds WHERE subject_type=$1 AND subject_id=$2 FOR UPDATE',[type,id])).rows[0];
 if(action==='hide') {
  if(hold)return;
  if(cfg.state==='status'&&row.status!==cfg.normal||cfg.state==='hidden_at'&&row.hidden_at)throw conflict();
  await db.query('INSERT INTO moderation_holds(subject_type,subject_id,previous_state)VALUES($1,$2,$3)',[type,id,cfg.normal]);
  await db.query(`UPDATE ${cfg.table} SET ${cfg.state}=${cfg.state==='hidden_at'?'now()':"'"+cfg.hidden+"'"}${type==='venue'||type==='checkin'?',version=version+1':''} WHERE id=$1`,[id]);
  if(type==='user')await db.query('UPDATE sessions SET revoked_at=coalesce(revoked_at,now()) WHERE user_id=$1',[id]);
 } else {
  if(!hold)throw conflict();
  if(cfg.state==='status'&&row.status!==cfg.hidden||cfg.state==='hidden_at'&&!row.hidden_at)throw conflict();
  await db.query(`UPDATE ${cfg.table} SET ${cfg.state}=${cfg.state==='hidden_at'?'NULL':'$2'}${type==='venue'||type==='checkin'?',version=version+1':''} WHERE id=$1`,cfg.state==='hidden_at'?[id]:[id,hold.previous_state]);
  await db.query('DELETE FROM moderation_holds WHERE subject_type=$1 AND subject_id=$2',[type,id]);
 }
}
export async function decide(pool:pg.Pool,actor:string,id:string,input:{version:number;action:'hide'|'restore'|'dismiss';reason:string}) {
 return transaction(pool,async db=>{
  await requireModerator(db,actor);
  const row=(await db.query('SELECT * FROM reports WHERE id=$1 FOR UPDATE',[id])).rows[0];if(!row)throw notFound();if(row.version!==input.version)throw conflict();
  if(input.action!=='dismiss')await change(db,actor,row.subject_type,row.subject_id,input.action);
  await db.query('UPDATE reports SET status=$2,version=version+1,resolved_at=now() WHERE id=$1',[id,input.action==='dismiss'?'rejected':'resolved']);
  await db.query('INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason)VALUES($1,$2,$3,$4,$5)',[actor,'moderation.'+input.action,row.subject_type,row.subject_id,input.reason.trim()]);
  return (await db.query(`SELECT ${reportCols},reporter_id AS "reporterId" FROM reports WHERE id=$1`,[id])).rows[0];
 });
}
export async function audit(pool:pg.Pool,actor:string,q:{limit?:string;cursor?:string;entityId?:string}) {
 await requireModerator(pool,actor);const after=decodeCursor(q.cursor),limit=Number(q.limit??20);
 const rows=(await pool.query(`SELECT id,actor_id AS "actorId",action,entity_type AS "entityType",entity_id AS "entityId",reason,
 to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "createdAt"
 FROM audit_events WHERE ($1::uuid IS NULL OR entity_id=$1) AND ($2::timestamptz IS NULL OR (created_at,id)>($2,$3::uuid)) ORDER BY created_at,id LIMIT $4`,[q.entityId??null,after?.[0]??null,after?.[1]??null,limit+1])).rows;
 return {items:rows.slice(0,limit),nextCursor:rows.length>limit?encodeCursor(rows[limit-1]):null};
}
export async function role(pool:pg.Pool,actor:string,id:string,input:{expectedRole:string;role:string;reason:string}) {
 return transaction(pool,async db=>{
  const users=(await db.query("SELECT id,role FROM users WHERE id=ANY($1::uuid[]) AND status='active' ORDER BY id FOR UPDATE",[[actor,id]])).rows;
  if(users.find(u=>u.id===actor)?.role!=='admin')throw new ApiError(403,'FORBIDDEN','Требуется администратор');
  if(actor===id)throw new ApiError(422,'SELF_ROLE_CHANGE','Нельзя менять собственную роль');
  const target=users.find(u=>u.id===id);if(!target)throw notFound();if(target.role!==input.expectedRole)throw conflict();
  await db.query('UPDATE users SET role=$2,updated_at=now() WHERE id=$1',[id,input.role]);
  await db.query("INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason)VALUES($1,'role.'||$2,'user',$3,$4)",[actor,input.role,id,input.reason.trim()]);
  return {userId:id,role:input.role};
 });
}
