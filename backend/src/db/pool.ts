import pg from "pg";
export function createPool(connectionString: string, max = 10) {
  return new pg.Pool({ connectionString, max, connectionTimeoutMillis: 3000,
    idleTimeoutMillis: 30_000, statement_timeout: 5000, lock_timeout: 2000,
    idle_in_transaction_session_timeout: 10_000, application_name: "krapiva-api" });
}
export async function transaction<T>(pool: pg.Pool, action: (client: pg.PoolClient) => Promise<T>): Promise<T> {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const result = await action(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw error;
  } finally { client.release(); }
}
