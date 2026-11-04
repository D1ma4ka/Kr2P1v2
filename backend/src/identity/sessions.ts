import { createHash, randomBytes } from "node:crypto";
import type pg from "pg";
import { ApiError } from "../http/errors.js";
export const tokenHash = (value: string) => createHash("sha256").update(value).digest("hex");
export async function issueSession(client: pg.Pool | pg.PoolClient, userId: string) {
  const token = randomBytes(32).toString("base64url");
  await client.query("INSERT INTO sessions(user_id,token_hash,expires_at) VALUES($1,$2,now()+interval '1 day')",[userId,tokenHash(token)]);
  return token;
}
export async function authenticate(pool: pg.Pool, header?: string) {
  if (!header || !/^Bearer [A-Za-z0-9_-]{43}$/.test(header)) throw new ApiError(401,"UNAUTHORIZED","Требуется вход");
  const result = await pool.query<{ user_id: string }>(`SELECT s.user_id FROM sessions s JOIN users u ON u.id=s.user_id
    WHERE s.token_hash=$1 AND s.revoked_at IS NULL AND s.expires_at>now() AND u.status='active'`,[tokenHash(header.slice(7))]);
  const userId = result.rows[0]?.user_id;
  if (!userId) throw new ApiError(401,"UNAUTHORIZED","Требуется вход");
  const budget = await pool.query<{ count: number }>(`INSERT INTO request_budgets(user_id,window_start,count) VALUES($1,date_trunc('minute',now()),1)
    ON CONFLICT(user_id) DO UPDATE SET
    count=CASE WHEN request_budgets.window_start=date_trunc('minute',now()) THEN least(request_budgets.count+1,121) ELSE 1 END,
    window_start=date_trunc('minute',now()) RETURNING count`,[userId]);
  if (budget.rows[0]!.count > 120) throw new ApiError(429,"RATE_LIMITED","Слишком много запросов; повторите через минуту");
  return userId;
}
