import assert from 'node:assert/strict';
import {test} from 'node:test';
import {mkdtemp,rm,readdir,rename} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import sharp from 'sharp';
import {databaseConfig} from '../src/db/config.js';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {FileStorage} from '../src/media/storage.js';
import {MediaService} from '../src/media/service.js';
import {testDatabase} from './database.js';
import {fixtures} from './fixtures.js';

test('database config bounds and TLS cannot be bypassed through URL parameters',()=>{
 const remote='postgresql://u:private-value@db.example.invalid/krapiva';
 for(const url of ['', 'invalid', 'https://localhost/db', remote+'#fragment',remote+'?host=127.0.0.1',remote+'?sslmode=verify-full&sslmode=disable',remote+'?sslmode=verify-full&ssl=false',remote+'?sslmode=no-verify'])
  assert.throws(()=>databaseConfig({DATABASE_URL:url,NODE_ENV:'production'}));
 assert.throws(()=>databaseConfig({DATABASE_URL:remote,NODE_ENV:'production'}));
 assert.equal(databaseConfig({DATABASE_URL:remote+'?sslmode=verify-full',NODE_ENV:'production'}).max,10);
 for(const DB_POOL_MAX of ['0','51','1.5','x']) assert.throws(()=>databaseConfig({DATABASE_URL:remote,DB_POOL_MAX}));
 try {databaseConfig({DATABASE_URL:remote+'?password=private-value'});} catch(error) {assert.equal(String(error).includes('private-value'),false);}
});

test('readiness rejects missing schema, drift and inaccessible storage; media failures preserve recoverability',async()=>{
 const db=await testDatabase();await migrate(db.pool);const f=await fixtures(db.pool);
 const dir=await mkdtemp(join(tmpdir(),'krapiva-failure-'));
 const storage=new FileStorage(dir),media=new MediaService(db.pool,storage);
 const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,media);
 const address=await app.listen({host:'127.0.0.1',port:0});
 const ready=async()=>{const response=await fetch(address+'/health/ready');await response.arrayBuffer();return response.status;};
 try {
  assert.equal(await ready(),200);
  await db.pool.query('ALTER TABLE sessions RENAME TO absent_sessions');
  assert.equal(await ready(),503);await db.pool.query('ALTER TABLE absent_sessions RENAME TO sessions');
  const old=(await db.pool.query("UPDATE schema_migrations SET sha256=repeat('0',64) WHERE name='002_request_budgets.sql' RETURNING name")).rows[0];
  assert.ok(old);assert.equal(await ready(),503);
  // Recovery uses exact checked-in migration bytes, not a new/faked schema.
  const {createHash}=await import('node:crypto');const {readFile}=await import('node:fs/promises');
  await db.pool.query("UPDATE schema_migrations SET sha256=$1 WHERE name='002_request_budgets.sql'",[createHash('sha256').update(await readFile('migrations/002_request_budgets.sql')).digest('hex')]);
  await rename(dir,dir+'-offline');assert.equal(await ready(),503);await rename(dir+'-offline',dir);
  assert.equal(await ready(),200);
  const bytes=await sharp({create:{width:8,height:8,channels:3,background:'#fff'}}).png().toBuffer();
  class BrokenWrite extends FileStorage {override async put(key:string,data:Buffer) {if(key.endsWith('-thumbnail.jpg'))throw Error('disk fault');await super.put(key,data);}}
  await assert.rejects(new MediaService(db.pool,new BrokenWrite(dir)).upload(f.a.id,bytes,'image/png'));
  assert.equal((await db.pool.query('SELECT * FROM media_assets')).rowCount,0);assert.deepEqual(await readdir(dir),[]);
  const saved=await media.upload(f.a.id,bytes,'image/png');
  class BrokenDelete extends FileStorage {override async delete(_key:string):Promise<void> {throw Error('disk fault');}}
  await assert.rejects(new MediaService(db.pool,new BrokenDelete(dir)).remove(f.a.id,saved.id));
  await assert.rejects(media.get(f.a.id,saved.id),{statusCode:404});
  assert.ok((await db.pool.query('SELECT deleted_at FROM media_assets WHERE id=$1',[saved.id])).rows[0].deleted_at);
  await media.remove(f.a.id,saved.id);assert.deepEqual(await readdir(dir),[]);
  await assert.rejects(storage.get('../../outside-original.jpg'));
 } finally {await app.close();await db.close();await rm(dir,{recursive:true,force:true});await rm(dir+'-offline',{recursive:true,force:true});}
});
