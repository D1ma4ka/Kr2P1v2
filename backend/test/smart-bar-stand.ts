import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {hashPassword} from '../src/identity/passwords.js';
import {testDatabase} from './database.js';
import {fixtures} from './fixtures.js';

// Host-side Android integration fixture. The parent closes stdin to stop the stand.
const target = new URL(process.env.TEST_DATABASE_URL ?? '');
assert.ok(['localhost', '127.0.0.1'].includes(target.hostname), 'local test database required');
const db = await testDatabase();
const app = buildApp(readConfig({NODE_ENV: 'test', LOG_LEVEL: 'silent'}), db.pool);
try {
  await migrate(db.pool);
  const {a, b} = await fixtures(db.pool);
  await db.pool.query("UPDATE users SET role='moderator' WHERE id=$1", [a.id]);
  await db.pool.query('UPDATE users SET password_hash=$2, email_verified_at=now() WHERE id=$1',
    [b.id, await hashPassword('Isolated Smart Bar QA!')]);
  const {rows: styles} = await db.pool.query("INSERT INTO styles(name) SELECT unnest($1::text[]) RETURNING id,name",
    [['Tripel', 'IPA', 'Porter', 'Gose', 'APA']]);
  const offers: string[] = [];
  for (const {id, name} of styles) {
    const release = await db.pool.query(`INSERT INTO beer_releases(brewery_id,name,style_id,abv)
      SELECT id,$1,$2,6 FROM breweries RETURNING id`, ['Тестовое длинное название '.repeat(5) + name, id]);
    const positions = await db.pool.query(`INSERT INTO beer_positions(release_id,source,status,packaging,volume_ml)
      SELECT $1,'Isolated QA','verified',CASE WHEN n%2=0 THEN 'can' ELSE 'bottle' END,250+n
      FROM generate_series(1,51) n RETURNING id`, [release.rows[0].id]);
    offers.push(...positions.rows.map(row => row.id as string));
  }

  const address = await app.listen({host: '127.0.0.1', port: 0});
  const write = async (path: string, method: string, body: object) => {
    const res = await fetch(address + path, {method, headers: {
      authorization: 'Bearer ' + a.token, 'content-type': 'application/json',
    }, body: JSON.stringify(body)});
    assert.equal(res.status, 200, method + ' ' + path);
  };
  for (const [name, positions] of [['A', offers.slice(0, 250)], ['B', offers.slice(250)]] as const) {
    const venue = randomUUID(), section = randomUUID();
    await write('/v1/moderation/venues/' + venue, 'PUT', {
      version: 0, name, type: 'bar', city: 'QA', source: 'Isolated QA',
      country: null, address: null, latitude: 55.7, longitude: 37.6, coordinatesSource: 'Isolated QA',
      imageAssetId: null, imageSource: null, rightsConfirmed: false, verified: true, testOnly: true,
    });
    const path = '/v1/venues/' + venue + '/menu';
    await write(path + '/draft', 'PUT', {expectedRevision: 0, source: 'Isolated QA',
      sections: [{id: section, name: 'QA'}], items: positions.map((positionId, ordinal) => ({
        positionId, sectionId: section, ordinal, price: 5, currency: 'EUR', available: true,
        servingFormat: null, volumeMl: null,
      }))});
    await write(path + '/publish', 'POST', {expectedRevision: 1});
  }
  await db.pool.query(`INSERT INTO checkins(user_id,position_id,rating_quarters,body,visibility,occurred_at)
    SELECT $1,p,16,'','private',now()-interval '1 day' FROM unnest($2::uuid[]) p`, [b.id, offers.slice(0, 100)]);
  process.stdout.write(address + '\n');
  for await (const _ of process.stdin) { /* the parent owns the lifetime */ }
} finally {
  await app.close();
  await db.close();
}
