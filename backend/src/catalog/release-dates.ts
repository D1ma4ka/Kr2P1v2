import type pg from 'pg';
import {validDate} from '../checkins/queries.js';
import {transaction} from '../db/pool.js';
import {ApiError,notFound} from '../http/errors.js';
import {requireModerator} from './proposals.js';

export interface ReleaseDateInput {
 releasedOn: string;
 source: string;
}

export async function verifyReleaseDate(pool: pg.Pool,user: string,id: string,input: ReleaseDateInput) {
 const {releasedOn} = input;
 const source = input.source.trim();
 return transaction(pool,async db => {
  await requireModerator(db,user);
  const {rows:[clock]} = await db.query<{today: string}>("SELECT (now() AT TIME ZONE 'UTC')::date::text AS today");
  if (!validDate(releasedOn+'T00:00:00Z') || releasedOn > clock!.today || !source) {
   throw new ApiError(422,'INVALID_RELEASE_DATE','Укажите прошедшую дату выпуска или сегодня и источник');
  }
  const {rows:[previous]} = await db.query<{releasedOn: string | null; source: string | null}>(`
   SELECT r.released_on::text AS "releasedOn",r.release_date_source AS source
   FROM beer_releases r JOIN breweries b ON b.id=r.brewery_id
   WHERE r.id=$1 AND b.verification_status='verified' AND EXISTS(
    SELECT 1 FROM beer_positions bp WHERE bp.release_id=r.id AND bp.status='verified'
   ) FOR UPDATE OF r
  `,[id]);
  if (!previous) throw notFound();
  if (previous.releasedOn !== releasedOn || previous.source !== source) {
   await db.query('UPDATE beer_releases SET released_on=$2,release_date_source=$3 WHERE id=$1',[id,releasedOn,source]);
   await db.query(`
    INSERT INTO audit_events(actor_id,action,entity_type,entity_id,reason)
    VALUES($1,'catalog.verify_release_date','beer_release',$2,$3)
   `,[user,id,JSON.stringify({previous,releasedOn,source})]);
  }
  return {releaseId:id,releasedOn,source};
 });
}
