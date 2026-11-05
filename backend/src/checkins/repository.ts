import type pg from "pg";
import { tokenHash } from "../identity/sessions.js";
import { transaction } from "../db/pool.js";
import { ApiError, conflict, notFound } from "../http/errors.js";
import { decodeCursor, encodeCursor } from "../http/pagination.js";
export interface CheckinInput { positionId: string; ratingQuarters: number | null; body: string; occurredAt: string; visibility: string; }
export interface Checkin extends CheckinInput { id: string; userId: string; version: number; createdAt: string; updatedAt: string; }
// Explicit microsecond UTC representation is also used for cursor boundaries.
export const columns = `id, user_id AS "userId", position_id AS "positionId", rating_quarters AS "ratingQuarters", body,
 to_char(occurred_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "occurredAt", visibility, version,
 to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "createdAt",
 to_char(updated_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "updatedAt"`;
const active = "deleted_at IS NULL AND hidden_at IS NULL";
export async function getCheckin(client: pg.Pool | pg.PoolClient, userId: string, id: string) {
  const result = await client.query<Checkin>(`SELECT ${columns} FROM checkins WHERE user_id=$1 AND id=$2 AND ${active}`,[userId,id]);
  if (!result.rows[0]) throw notFound();
  return result.rows[0];
}
export async function listCheckins(pool: pg.Pool, userId: string, limit: number, cursor?: string) {
  const after = decodeCursor(cursor);
  const result = await pool.query<Checkin>(`SELECT ${columns} FROM checkins WHERE user_id=$1 AND ${active}
    AND ($2::timestamptz IS NULL OR (created_at,id)<($2::timestamptz,$3::uuid)) ORDER BY created_at DESC,id DESC LIMIT $4`,[userId,after?.[0] ?? null,after?.[1] ?? null,limit+1]);
  const items = result.rows.slice(0,limit);
  return { items, nextCursor: result.rows.length > limit ? encodeCursor(items.at(-1)!) : null };
}
export async function createCheckin(pool: pg.Pool,userId: string,key: string,input: CheckinInput) {
  const normalized = { positionId: input.positionId.toLowerCase(), ratingQuarters: input.ratingQuarters,
    body: input.body, occurredAt: new Date(input.occurredAt).toISOString(), visibility: input.visibility };
  const hash = tokenHash(JSON.stringify(normalized));
  return transaction(pool,async client => {
    await client.query("SELECT pg_advisory_xact_lock(hashtextextended($1,0))",[`${userId}:${key.toLowerCase()}:create-checkin`]);
    const previous = await client.query("SELECT request_hash,resource_id FROM idempotency_keys WHERE user_id=$1 AND key=$2 AND operation='create-checkin'",[userId,key]);
    if (previous.rows[0]) {
      if (previous.rows[0].request_hash !== hash) throw conflict();
      const row = await getCheckin(client,userId,previous.rows[0].resource_id).catch(error => { if(error instanceof ApiError && error.statusCode===404) throw conflict(); throw error; });
      return { row, replay: true };
    }
    const position = await client.query("SELECT id FROM beer_positions WHERE id=$1 AND status='verified' FOR SHARE",[normalized.positionId]);
    if (!position.rowCount) throw new ApiError(422,"POSITION_UNAVAILABLE","Позиция недоступна");
    const result = await client.query<Checkin>(`INSERT INTO checkins(user_id,position_id,rating_quarters,body,occurred_at,visibility,created_at,updated_at)
      VALUES($1,$2,$3,$4,$5,$6,date_trunc('milliseconds',now()),date_trunc('milliseconds',now())) RETURNING ${columns}`,
      [userId,normalized.positionId,normalized.ratingQuarters,normalized.body,normalized.occurredAt,normalized.visibility]);
    const row = result.rows[0]!;
    await client.query("INSERT INTO idempotency_keys(user_id,key,operation,request_hash,resource_id) VALUES($1,$2,'create-checkin',$3,$4)",[userId,key,hash,row.id]);
    return { row,replay: false };
  });
}
export async function updateCheckin(pool: pg.Pool,userId: string,id: string,input: Omit<CheckinInput,'positionId'> & { version: number }) {
  const result = await pool.query<Checkin>(`UPDATE checkins SET rating_quarters=$4,body=$5,occurred_at=$6,visibility=$7,
    version=version+1,updated_at=date_trunc('milliseconds',now()) WHERE user_id=$1 AND id=$2 AND version=$3 AND ${active} RETURNING ${columns}`,
    [userId,id,input.version,input.ratingQuarters,input.body,input.occurredAt,input.visibility]);
  if (!result.rows[0]) { await getCheckin(pool,userId,id); throw conflict(); }
  return result.rows[0];
}
export async function deleteCheckin(pool: pg.Pool,userId: string,id: string,version: number) {
  const result = await pool.query(`UPDATE checkins SET deleted_at=now(),version=version+1,updated_at=now() WHERE user_id=$1 AND id=$2 AND version=$3 AND ${active}`,[userId,id,version]);
  if (!result.rowCount) { await getCheckin(pool,userId,id); throw conflict(); }
}
