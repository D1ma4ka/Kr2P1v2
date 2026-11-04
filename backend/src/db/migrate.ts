import { createHash } from "node:crypto";
import { readdir, readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import type pg from "pg";
import { createPool } from "./pool.js";
export async function migrate(pool: pg.Pool, directory = resolve("migrations")) {
  const files = (await readdir(directory)).filter(name => /^\d{3}_[a-z0-9_]+\.sql$/.test(name)).sort();
  if (files.length === 0 || new Set(files.map(name => name.slice(0, 3))).size !== files.length)
    throw new Error("Missing or duplicate migration sequence");
  const client = await pool.connect();
  let locked = false;
  try {
    await client.query("SELECT pg_advisory_lock(220020)"); locked = true;
    await client.query(`CREATE TABLE IF NOT EXISTS schema_migrations (
      name text PRIMARY KEY, sha256 char(64) NOT NULL, applied_at timestamptz NOT NULL DEFAULT now())`);
    const applied = await client.query<{ name: string; sha256: string }>("SELECT name,sha256 FROM schema_migrations ORDER BY name");
    if (applied.rows.some((row, i) => row.name !== files[i])) throw new Error("Database migration history differs");
    for (const [i, name] of files.entries()) {
      const sql = await readFile(resolve(directory, name), "utf8");
      const hash = createHash("sha256").update(sql).digest("hex");
      const old = applied.rows[i];
      if (old) { if (old.sha256 !== hash) throw new Error(`Migration checksum changed: ${name}`); continue; }
      await client.query("BEGIN");
      try {
        await client.query(sql);
        await client.query("INSERT INTO schema_migrations(name,sha256) VALUES($1,$2)", [name, hash]);
        await client.query("COMMIT");
      } catch (error) { await client.query("ROLLBACK"); throw error; }
    }
  } finally {
    if (locked) await client.query("SELECT pg_advisory_unlock(220020)").catch(() => undefined);
    client.release();
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const url = process.env.MIGRATION_DATABASE_URL;
  if (!url) throw new Error("MIGRATION_DATABASE_URL required");
  const pool = createPool(url, 1);
  try { await migrate(pool); console.log("Migrations applied/verified"); }
  catch { console.error("Migration failed; schema unchanged for failed step. Check migration history/configuration."); process.exitCode = 1; }
  finally { await pool.end(); }
}
