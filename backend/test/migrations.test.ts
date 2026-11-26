import assert from "node:assert/strict";
import { test } from "node:test";
import { mkdtemp, cp, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { migrate } from "../src/db/migrate.js";
import { testDatabase } from "./database.js";

test("real PostgreSQL: concurrent/repeated migrations, checksums, rollback, relational invariants", async () => {
  const db = await testDatabase();
  const directory = await mkdtemp(join(tmpdir(), "krapiva-migrations-"));
  try {
    await Promise.all([migrate(db.pool), migrate(db.pool)]);
    await migrate(db.pool);
    assert.equal((await db.pool.query("SELECT * FROM schema_migrations")).rowCount, 16);
    assert.equal((await db.pool.query("SELECT * FROM users")).rowCount, 0);
    const user = (await db.pool.query("INSERT INTO users(email,display_name,adult_confirmed_at) VALUES('a@example.invalid','Synthetic A',now()) RETURNING id")).rows[0].id;
    await assert.rejects(db.pool.query("INSERT INTO users(email,display_name,adult_confirmed_at) VALUES('a@example.invalid','Duplicate',now())"), { code: "23505" });
    await assert.rejects(db.pool.query("INSERT INTO sessions(user_id,token_hash,expires_at) VALUES($1,repeat('a',64),now()-interval '1 day')", [user]), { code: "23514" });
    const brewery = (await db.pool.query("INSERT INTO breweries(name,source) VALUES('Synthetic','test') RETURNING id")).rows[0].id;
    const release = (await db.pool.query("INSERT INTO beer_releases(brewery_id,name) VALUES($1,'Synthetic') RETURNING id", [brewery])).rows[0].id;
    const position = (await db.pool.query("INSERT INTO beer_positions(release_id,source) VALUES($1,'test') RETURNING id", [release])).rows[0].id;
    for (const rating of [0,21]) await assert.rejects(db.pool.query("INSERT INTO checkins(user_id,position_id,occurred_at,rating_quarters) VALUES($1,$2,now(),$3)", [user,position,rating]), { code: "23514" });
    await db.pool.query("INSERT INTO checkins(user_id,position_id,occurred_at,rating_quarters) VALUES($1,$2,now(),NULL),($1,$2,now(),17)", [user,position]);
    await assert.rejects(db.pool.query("DELETE FROM beer_positions WHERE id=$1", [position]), { code: "23503" });
    await assert.rejects(db.pool.query("INSERT INTO friend_requests(from_user_id,to_user_id) VALUES($1,$1)", [user]), { code: "23514" });
    await assert.rejects(db.pool.query("INSERT INTO barcode_mappings(position_id,gtin,source) VALUES($1,'12345678901234','test')", [position]), { code: "23514" });
    assert.equal((await db.pool.query("SELECT valid_gtin('00012345600012') AS valid")).rows[0].valid, true);
    await cp("migrations", directory, { recursive: true });
    await writeFile(join(directory, "017_failure.sql"), "CREATE TABLE rollback_probe(id int); SELECT 1/0;");
    await assert.rejects(migrate(db.pool,directory), { code: "22012" });
    assert.equal((await db.pool.query("SELECT to_regclass('rollback_probe') AS name")).rows[0].name, null);
    assert.equal((await db.pool.query("SELECT * FROM schema_migrations")).rowCount, 16);
    await writeFile(join(directory, "001_foundation.sql"), "SELECT 1;");
    await assert.rejects(migrate(db.pool,directory), /checksum changed/);
  } finally { await db.close(); await rm(directory, { recursive: true, force: true }); }
});
