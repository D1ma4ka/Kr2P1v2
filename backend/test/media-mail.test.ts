import assert from 'node:assert/strict';
import {test} from 'node:test';
import {randomUUID} from 'node:crypto';
import {mkdtemp,rm,readdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import sharp from 'sharp';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {FileStorage} from '../src/media/storage.js';
import {MediaService} from '../src/media/service.js';
import {localMailer} from '../src/mail/smtp.js';
import {testDatabase} from './database.js';
import {fixtures} from './fixtures.js';

test('private media: real image normalization/EXIF removal, attachment ACL, limits, restart and deletion',async()=>{
 const db=await testDatabase();await migrate(db.pool);const f=await fixtures(db.pool);
 const dir=await mkdtemp(join(tmpdir(),'krapiva-media-'));
 const storage=new FileStorage(dir);
 let app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,new MediaService(db.pool,storage));
 let address=await app.listen({host:'127.0.0.1',port:0});
 const request=(path:string,method='GET',body?:unknown,token=f.a.token)=>fetch(address+path,{method,
  headers:{authorization:`Bearer ${token}`,...(body===undefined?{}:{'content-type':'application/json'})},...(body===undefined?{}:{body:JSON.stringify(body)})});
 const upload=(bytes:Buffer,type='image/png',token=f.a.token)=>fetch(address+'/v1/media',{method:'POST',headers:{authorization:`Bearer ${token}`,'content-type':type},body:new Uint8Array(bytes)});
 try {
  const fixture=await sharp({create:{width:600,height:400,channels:3,background:'#aabbcc'}}).withExif({IFD0:{Artist:'synthetic-private-metadata'}}).png().toBuffer();
  assert.ok((await sharp(fixture).metadata()).exif);
  const response=await upload(fixture);assert.equal(response.status,201);
  const media=await response.json() as {id:string;contentType:string};assert.equal(media.contentType,'image/jpeg');
  const file=await request('/v1/media/'+media.id);assert.equal(file.status,200);
  const metadata=await sharp(Buffer.from(await file.arrayBuffer())).metadata();
  assert.equal(metadata.format,'jpeg');assert.equal(metadata.exif,undefined);assert.equal(metadata.width,600);
  const thumb=await request('/v1/media/'+media.id+'?size=thumbnail');
  assert.equal((await sharp(Buffer.from(await thumb.arrayBuffer())).metadata()).width,320);
  assert.equal((await request('/v1/media/'+media.id,'GET',undefined,f.b.token)).status,404);
  assert.equal((await request('/v1/media/'+media.id,'DELETE',undefined,f.b.token)).status,404);
  assert.equal((await fetch(address+'/v1/media/'+media.id)).status,401);
  assert.equal((await upload(Buffer.from('not an image'))).status,422);
  assert.equal((await upload(fixture,'image/jpeg')).status,422);
  assert.equal((await upload(fixture,'image/svg+xml')).status,415);
  const oversized=await upload(Buffer.alloc(8*1024*1024+1));assert.equal(oversized.status,413);await oversized.arrayBuffer();
  const huge=await sharp({create:{width:5000,height:4000,channels:3,background:'#fff'}}).png().toBuffer();
  assert.equal((await upload(huge)).status,422);
  const checkin=(await db.pool.query("INSERT INTO checkins(user_id,position_id,occurred_at) VALUES($1,$2,now()) RETURNING id",[f.a.id,f.position])).rows[0].id;
  assert.equal((await request(`/v1/checkins/${checkin}/media`,'PUT',{mediaIds:[media.id],version:1},f.b.token)).status,404);
  const foreign=(await db.pool.query("INSERT INTO checkins(user_id,position_id,occurred_at) VALUES($1,$2,now()) RETURNING id",[f.b.id,f.position])).rows[0].id;
  assert.equal((await request(`/v1/checkins/${foreign}/media`,'PUT',{mediaIds:[media.id],version:1},f.b.token)).status,404);
  assert.equal((await request(`/v1/checkins/${checkin}/media`,'PUT',{mediaIds:Array.from({length:5},()=>randomUUID()),version:1})).status,400);
  assert.equal((await request(`/v1/checkins/${checkin}/media`,'PUT',{mediaIds:[media.id],version:1})).status,200);
  assert.equal((await request('/v1/media/'+media.id,'DELETE')).status,409);
  await app.close();app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,new MediaService(db.pool,new FileStorage(dir)));
  address=await app.listen({host:'127.0.0.1',port:0});
  assert.deepEqual(await (await request(`/v1/checkins/${checkin}/media`)).json(),{mediaIds:[media.id],version:2});
  assert.equal((await request('/v1/media/'+media.id)).status,200);
  assert.equal((await request(`/v1/checkins/${checkin}/media`,'PUT',{mediaIds:[],version:2})).status,200);
  assert.equal((await request('/v1/media/'+media.id,'DELETE')).status,204);
  assert.equal((await request('/v1/media/'+media.id)).status,404);
  assert.equal((await request('/v1/media/'+media.id,'DELETE')).status,204);
  assert.deepEqual(await readdir(dir),[]);
 } finally {await app.close();await db.close();await rm(dir,{recursive:true,force:true});}
});

test('real SMTP delivers only synthetic messages into local Mailpit',async()=>{
 const host=process.env.SMTP_HOST??'127.0.0.1',port=Number(process.env.SMTP_PORT??51025);
 const api=process.env.MAILPIT_API_URL??'http://127.0.0.1:58025';
 const mail=localMailer(host,port,'test');
 const subject=`KraPiva synthetic ${randomUUID()}`;
 try {
  assert.throws(()=>localMailer(host,port,'production'));
  await assert.rejects(mail.send('external@example.com','no','no'));
  await mail.send('tester@example.invalid',subject,'Actual SMTP test; no external recipients.');
  const response=await fetch(api+'/api/v1/search?query='+encodeURIComponent(`subject:"${subject}"`));
  assert.equal(response.status,200);
  const body=await response.json() as {messages:{Subject:string}[]};
  assert.equal(body.messages[0]?.Subject,subject);
 } finally {mail.close();}
});
