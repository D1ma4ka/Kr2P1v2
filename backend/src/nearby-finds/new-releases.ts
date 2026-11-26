import type pg from 'pg';
import {active} from '../checkins/queries.js';

const dayMs = 86_400_000;
const initialDays = 14;
const maximumDays = 90;
const targetReleases = 3;

interface ReleaseHistoryRow {
 releaseId: string;
 venueId: string | null;
 lastVisit: Date | null;
}

export async function newReleaseHistory(db: pg.PoolClient,user: string) {
 const {rows} = await db.query<ReleaseHistoryRow>(`
  SELECT bp.release_id AS "releaseId",c.venue_id AS "venueId",
   max(c.occurred_at) FILTER (WHERE c.occurred_at<=now()) AS "lastVisit"
  FROM checkins c JOIN beer_positions bp ON bp.id=c.position_id
  WHERE c.user_id=$1 AND ${active}
  GROUP BY bp.release_id,c.venue_id
 `,[user]);
 const tried = new Set<string>();
 const visits = new Map<string,number>();
 let lastActivity: number | undefined;
 for (const {releaseId,venueId,lastVisit} of rows) {
  tried.add(releaseId);
  if (!lastVisit) continue;
  const visit = lastVisit.getTime();
  lastActivity = Math.max(lastActivity ?? visit,visit);
  if (venueId) visits.set(venueId,Math.max(visits.get(venueId) ?? visit,visit));
 }
 return {tried,visits,lastActivity,today:Math.floor(Date.now()/dayMs)};
}

export function newReleaseMatches<T extends {
 releaseId: string; releasedOn: string | null; positionId: string; score: number;
}>(rows: T[],venueId: string,history: Awaited<ReturnType<typeof newReleaseHistory>>) {
 const {today,tried,visits,lastActivity} = history;
 const last = visits.get(venueId) ?? lastActivity;
 const days = last === undefined ? initialDays : Math.min(maximumDays,Math.max(initialDays,today-Math.floor(last/dayMs)));
 const candidates = rows.flatMap(row => {
  if (row.releasedOn === null || tried.has(row.releaseId)) return [];
  const age = today - Date.parse(row.releasedOn)/dayMs;
  return age >= 0 && age <= maximumDays ? [{row,age}] : [];
 });

 // Use the same fallback for summaries and menu details. Count releases so
 // a single beer in several packages cannot prevent a wider search.
 let matches = candidates;
 for (const window of [days,Math.min(maximumDays,days+14),Math.min(maximumDays,days+30)]) {
  const recent = candidates.filter(candidate => candidate.age <= window);
  if (new Set(recent.map(candidate => candidate.row.releaseId)).size >= targetReleases) {
   matches = recent;
   break;
  }
 }
 return matches.sort((a,b) => b.row.score-a.row.score || a.age-b.age || a.row.positionId.localeCompare(b.row.positionId))
  .map(({row}) => row);
}
