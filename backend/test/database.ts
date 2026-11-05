import { randomUUID } from "node:crypto";
import { createPool } from "../src/db/pool.js";
// Creates and drops only a uniquely named synthetic DB, never the configured database.
export async function testDatabase() {
  if (!process.env.TEST_DATABASE_URL) throw new Error("TEST_DATABASE_URL required for real PostgreSQL tests");
  const admin = createPool(process.env.TEST_DATABASE_URL, 1);
  const name = `krapiva_test_${randomUUID().replaceAll('-', '')}`;
  await admin.query(`CREATE DATABASE ${name}`);
  const url = new URL(process.env.TEST_DATABASE_URL); url.pathname = `/${name}`;
  const pool = createPool(url.toString(), 5);
  pool.on("error", () => { /* failure is asserted by the next DB operation */ });
  return { pool, url: url.toString(), async close() {
    await pool.end();
    await admin.query(`DROP DATABASE ${name} WITH (FORCE)`);
    await admin.end();
  } };
}
