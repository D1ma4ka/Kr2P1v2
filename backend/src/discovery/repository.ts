import type pg from 'pg';
import {requireModerator} from '../catalog/proposals.js';
import {transaction} from '../db/pool.js';
import {ApiError,conflict,notFound} from '../http/errors.js';
import {validDate} from '../checkins/queries.js';
import {distanceSql,nearby,type NearbyQuery} from '../venues/repository.js';

interface EventInput {
  version:number; venueId:string; title:string; description:string; source:string;
  startsAt:string; endsAt:string; published:boolean;
}
interface BadgeInput {
  version:number; venueId:string; eventId:string|null; title:string; description:string;
  startsAt:string; endsAt:string; active:boolean;
}

function verifyWindow(startsAt:string,endsAt:string) {
  if(!validDate(startsAt)||!validDate(endsAt)||Date.parse(startsAt)>=Date.parse(endsAt))
    throw new ApiError(422,'INVALID_WINDOW','Укажите начало и более позднее окончание');
}

async function verifyVenue(db:pg.PoolClient,id:string,allowTest:boolean) {
  const result=await db.query(
    "SELECT 1 FROM venues WHERE id=$1 AND status='verified' AND (NOT is_test OR $2) FOR SHARE",
    [id,allowTest],
  );
  if(!result.rowCount) throw notFound();
}

export async function nearbyDiscovery(pool:pg.Pool,q:NearbyQuery,allowTest:boolean) {
  const args=[q.latitude,q.longitude,q.radiusKm,allowTest];
  const scope=`SELECT v.id,v.name,v.city,v.address,${distanceSql('$1','$2')} AS distance
   FROM venues v WHERE v.status='verified' AND v.coordinates_source IS NOT NULL AND (NOT v.is_test OR $4)
   AND abs(v.latitude-$1::float8) <= $3::float8 / 110`;
  const [places,events,badges]=await Promise.all([
    nearby(pool,{...q,limit:4},allowTest),
    pool.query(`WITH places AS (${scope}) SELECT e.id,e.title,e.description,e.starts_at AS "startsAt",e.ends_at AS "endsAt",
       p.id AS "venueId",p.name AS "venueName",p.city,p.address,p.distance AS "distanceKm",
       ARRAY(SELECT r.title FROM local_badge_targets t JOIN badge_rules r ON r.id=t.rule_id
         WHERE t.event_id=e.id AND r.active ORDER BY r.title,r.id) AS "badgeTitles"
       FROM discovery_events e JOIN places p ON p.id=e.venue_id
       WHERE e.published AND e.ends_at>now() AND p.distance<=$3 ORDER BY e.starts_at,p.distance,e.id LIMIT 30`,args),
    pool.query(`WITH places AS (${scope}) SELECT r.id,r.title,r.description,t.event_id AS "eventId",e.title AS "eventTitle",
       t.starts_at AS "startsAt",t.ends_at AS "endsAt",p.id AS "venueId",p.name AS "venueName",p.distance AS "distanceKm"
       FROM local_badge_targets t JOIN places p ON p.id=t.venue_id JOIN badge_rules r ON r.id=t.rule_id
       LEFT JOIN discovery_events e ON e.id=t.event_id
       WHERE r.active AND t.ends_at>now() AND (t.event_id IS NULL OR e.published AND e.ends_at>now())
       AND p.distance<=$3 ORDER BY p.distance,t.starts_at,r.id LIMIT 30`,args),
  ]);
  return {places:places.items,events:events.rows,badges:badges.rows};
}

export async function saveEvent(pool:pg.Pool,actor:string,id:string,input:EventInput,allowTest:boolean) {
  return transaction(pool,async db=>{
    await requireModerator(db,actor);
    verifyWindow(input.startsAt,input.endsAt);
    await verifyVenue(db,input.venueId,allowTest);
    await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,220030))',[id.toLowerCase()]);
    const previous=(await db.query('SELECT version,venue_id FROM discovery_events WHERE id=$1 FOR UPDATE',[id])).rows[0];
    if(previous?previous.version!==input.version||previous.venue_id!==input.venueId.toLowerCase():input.version!==0) throw conflict();
    if(!input.title.trim()||!input.description.trim()||!input.source.trim())
      throw new ApiError(422,'SOURCE_REQUIRED','Укажите название, описание и источник события');
    const result=await db.query(`INSERT INTO discovery_events(id,venue_id,title,description,source,starts_at,ends_at,published)
      VALUES($1,$2,$3,$4,$5,$6,$7,$8)
      ON CONFLICT(id) DO UPDATE SET title=excluded.title,description=excluded.description,source=excluded.source,
       starts_at=excluded.starts_at,ends_at=excluded.ends_at,published=excluded.published,version=discovery_events.version+1
      RETURNING id,version`,
      [id,input.venueId,input.title.trim(),input.description.trim(),input.source.trim(),input.startsAt,input.endsAt,input.published]);
    await db.query("INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason) VALUES($1,'discovery.event.save','event',$2,$3)",
      [actor,id,input.source.trim()]);
    return result.rows[0];
  });
}

export async function saveBadge(pool:pg.Pool,actor:string,id:string,input:BadgeInput,allowTest:boolean) {
  return transaction(pool,async db=>{
    await requireModerator(db,actor);
    verifyWindow(input.startsAt,input.endsAt);
    await verifyVenue(db,input.venueId,allowTest);
    await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,220031))',[id.toLowerCase()]);
    if(input.eventId) {
      const event=await db.query('SELECT 1 FROM discovery_events WHERE id=$1 AND venue_id=$2 AND starts_at<=$3 AND ends_at>=$4',
        [input.eventId,input.venueId,input.startsAt,input.endsAt]);
      if(!event.rowCount) throw new ApiError(422,'INVALID_EVENT','Период значка должен входить в период события в этом месте');
    }
    if(!input.title.trim()||!input.description.trim())
      throw new ApiError(422,'INVALID_BADGE','Укажите название и условия получения значка');
    const existing=(await db.query(`SELECT r.version,r.metric,t.venue_id,t.event_id,t.starts_at,t.ends_at FROM badge_rules r
      LEFT JOIN local_badge_targets t ON t.rule_id=r.id WHERE r.id=$1 FOR UPDATE OF r`,[id])).rows[0];
    if(existing&&(existing.version!==input.version||existing.metric!=='local_attendance'||existing.venue_id!==input.venueId.toLowerCase()||
       existing.event_id!==(input.eventId?.toLowerCase()??null)||existing.starts_at.getTime()!==Date.parse(input.startsAt)||existing.ends_at.getTime()!==Date.parse(input.endsAt))) throw conflict();
    if(!existing&&input.version!==0) throw conflict();
    const result=await db.query(`INSERT INTO badge_rules(id,metric,target,title,description,icon_key,active)
      VALUES($1,'local_attendance',1,$2,$3,'people',$4)
      ON CONFLICT(id) DO UPDATE SET title=excluded.title,description=excluded.description,active=excluded.active,version=badge_rules.version+1
      RETURNING id,version`,[id,input.title.trim(),input.description.trim(),input.active]);
    if(!existing) await db.query('INSERT INTO local_badge_targets(rule_id,venue_id,event_id,starts_at,ends_at) VALUES($1,$2,$3,$4,$5)',
      [id,input.venueId,input.eventId,input.startsAt,input.endsAt]);
    await db.query("INSERT INTO audit_events(actor_id,action,entity_type,entity_id) VALUES($1,'discovery.badge.save','badge',$2)",[actor,id]);
    return result.rows[0];
  });
}

export async function verifyAttendance(pool:pg.Pool,actor:string,ruleId:string,checkinId:string,evidence:string,allowTest:boolean) {
  return transaction(pool,async db=>{
    await requireModerator(db,actor);
    if(!evidence.trim()) throw new ApiError(422,'EVIDENCE_REQUIRED','Укажите основание подтверждения посещения');
    const result=await db.query(`SELECT e.user_id FROM eligible_local_badge_checkins e
      JOIN badge_rules r ON r.id=e.rule_id AND r.active
      JOIN local_badge_targets t ON t.rule_id=r.id
      JOIN venues v ON v.id=t.venue_id AND (NOT v.is_test OR $3)
      WHERE e.rule_id=$1 AND e.checkin_id=$2`,[ruleId,checkinId,allowTest]);
    const userId=result.rows[0]?.user_id;
    if(!userId) throw new ApiError(422,'ATTENDANCE_INELIGIBLE','Чекин не подходит по месту, времени или текущему статусу');
    if(userId===actor) throw new ApiError(403,'SELF_VERIFICATION','Собственное посещение подтверждает другой модератор');
    const changed=await db.query(`INSERT INTO local_badge_attendance(rule_id,user_id,checkin_id,verified_by)
      VALUES($1,$2,$3,$4) ON CONFLICT(rule_id,user_id) DO UPDATE SET checkin_id=excluded.checkin_id,
      verified_by=excluded.verified_by,verified_at=now() WHERE local_badge_attendance.checkin_id<>excluded.checkin_id`,
      [ruleId,userId,checkinId,actor]);
    if(changed.rowCount) await db.query("INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason) VALUES($1,'discovery.attendance.verify','checkin',$2,$3)",
      [actor,checkinId,evidence.trim()]);
    return {verified:true};
  });
}
