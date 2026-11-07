// Explicit local acceptance drill; never runs as part of production startup.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomUUID,createHash} from 'node:crypto';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import sharp from 'sharp';
import {createPool} from '../dist/src/db/pool.js';
import {fixtures} from '../dist/src/operations/synthetic.js';
import {buildApp} from '../dist/src/app.js';
import {readConfig} from '../dist/src/config.js';
import {FileStorage} from '../dist/src/media/storage.js';
import {MediaService} from '../dist/src/media/service.js';
import {localMailer} from '../dist/src/mail/smtp.js';
import {testDatabase} from '../dist/test/database.js';
if(process.env.NODE_ENV!=='development'||new URL(process.env.DATABASE_URL).hostname!=='127.0.0.1') throw Error('Local development only');
const root=resolve('..'),work=join(root,'work');
const accountsFile=join(work,`runtime-accounts-${randomUUID()}.json`);
await mkdir(work,{recursive:true,mode:0o700});
const env={...process.env};
for(const key of ['DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH']) delete env[key];
function dc(args,input) {return execFileSync('docker',['--host=unix:///var/run/docker.sock','compose',...args],{cwd:root,env,input,timeout:60_000,maxBuffer:16*1024*1024,stdio:['pipe','pipe','pipe']});}
const pool=createPool(process.env.DATABASE_URL);
let f;
try {
 f=await fixtures(pool,randomUUID().replaceAll('-',''));
 await writeFile(accountsFile,JSON.stringify(f),{flag:'wx',mode:0o600});
 // Runtime role cannot perform DDL, even though CRUD is allowed.
 await assert.rejects(pool.query('CREATE TABLE must_not_create(id int)'),{code:'42501'});
} finally {await pool.end();}
const api='http://127.0.0.1:53000';
async function call(base,path,method='GET',body,token=f.a.token,key) {
 const result=await fetch(base+path,{method,headers:{authorization:`Bearer ${token}`,...(body===undefined?{}:{'content-type':'application/json'}),...(key?{'idempotency-key':key}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
 const text=await result.text();return {status:result.status,body:text?JSON.parse(text):null};
}
const input={positionId:f.position,ratingQuarters:17,body:'Synthetic runtime persistence check',occurredAt:new Date().toISOString(),visibility:'private'};
const made=await call(api,'/v1/checkins','POST',input,f.a.token,randomUUID());assert.equal(made.status,201);
const id=made.body.id;
const bytes=await sharp({create:{width:64,height:64,channels:3,background:'#e0aa30'}}).png().toBuffer();
const upload=await fetch(api+'/v1/media',{method:'POST',headers:{authorization:`Bearer ${f.a.token}`,'content-type':'image/png'},body:bytes});assert.equal(upload.status,201);
const media=await upload.json();
assert.equal((await call(api,`/v1/checkins/${id}/media`,'PUT',{mediaIds:[media.id],version:1})).status,200);
async function photoHash(base) {
 const response=await fetch(base+'/v1/media/'+media.id,{headers:{authorization:`Bearer ${f.a.token}`}});
 assert.equal(response.status,200);return createHash('sha256').update(Buffer.from(await response.arrayBuffer())).digest('hex');
}
const hash=await photoHash(api);
// Keep the private-owner fixture above; add a separate friends-only social record.
for(const actor of [f.a,f.b]) {
 const profile=(await call(api,'/v1/me','GET',undefined,actor.token)).body;
 const patch={version:profile.version,profileVisibility:'public',genderVisibility:'private',firstName:null,lastName:null,country:null,city:null,bio:null,gender:null};
 assert.equal((await call(api,'/v1/me/profile','PATCH',patch,actor.token)).status,200);
}
const requestId=randomUUID(),commentId=randomUUID();
assert.equal((await call(api,'/v1/friend-requests/'+requestId,'PUT',{userId:f.b.id})).status,200);
assert.equal((await call(api,'/v1/friend-requests/'+requestId+'/decision','PUT',{status:'accepted'},f.b.token)).status,200);
const social=await call(api,'/v1/checkins','POST',{...input,visibility:'friends'},f.a.token,randomUUID());assert.equal(social.status,201);
const socialPath='/v1/social/checkins/'+social.body.id;
assert.equal((await call(api,socialPath+'/like','PUT',{},f.b.token)).status,200);
assert.equal((await call(api,socialPath+'/comments/'+commentId,'PUT',{body:'Persistent P6 comment'},f.b.token)).status,200);
async function socialRestored(base) {
 assert.equal((await call(base,'/v1/users/'+f.a.id+'/relationship','GET',undefined,f.b.token)).body.friends,true);
 const detail=await call(base,socialPath,'GET',undefined,f.b.token);
 assert.equal(detail.status,200);assert.equal(detail.body.likeCount,1);assert.equal(detail.body.commentCount,1);assert.equal(detail.body.liked,true);
 assert.ok((await call(base,'/v1/feed','GET',undefined,f.b.token)).body.items.some(row=>row.id===social.body.id));
 assert.equal((await call(base,socialPath+'/comments','GET',undefined,f.b.token)).body.items[0].id,commentId);
}

const subject=`Persistent mail ${randomUUID()}`;
const mail=localMailer('127.0.0.1',51025,'development');
try {await mail.send('tester@example.invalid',subject,'Synthetic persisted mail');} finally {mail.close();}
const backup=join(work,'backups',randomUUID());await mkdir(backup,{recursive:true,mode:0o700});
// Quiesce API writes while taking coordinated SQL+media snapshots. No volume deletion.
dc(['stop','api']);
try {
 const dump=dc(['exec','-T','db','pg_dump','-U','krapiva_owner','-d','krapiva','-Fc','--no-owner','--no-acl']);
 await writeFile(join(backup,'database.dump'),dump,{mode:0o600});
 // API is stopped; Docker copies the private media volume through its container filesystem.
 const container=dc(['ps','-a','-q','api']).toString().trim();
 execFileSync('docker',['--host=unix:///var/run/docker.sock','cp',container+':/data/media',join(backup,'media')],{cwd:root,env,stdio:'pipe',timeout:10_000});
 dc(['restart','db','mailpit']);
} finally {dc(['up','-d','--wait','db','mailpit','api']);}
assert.equal((await call(api,'/v1/checkins/'+id)).status,200);
assert.equal((await call(api,'/v1/checkins/'+id,'GET',undefined,f.b.token)).status,404);
assert.equal(await photoHash(api),hash);
await socialRestored(api);
const inbox=await (await fetch('http://127.0.0.1:58025/api/v1/search?query='+encodeURIComponent(`subject:"${subject}"`))).json();
assert.equal(inbox.messages[0]?.Subject,subject);
console.log('PASS: runtime role has no DDL; API+DB+Mailpit restart preserved accounts, sessions, checkin, private photo, mail and P6 social graph/reactions/comments');
const restored=await testDatabase();
let app;
try {
 const name=new URL(restored.url).pathname.slice(1);
 dc(['exec','-T','db','pg_restore','-U','krapiva_owner','-d',name,'--no-owner','--no-acl','--exit-on-error'],await readFile(join(backup,'database.dump')));
 app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),restored.pool,new MediaService(restored.pool,new FileStorage(join(backup,'media'))));
 const address=await app.listen({host:'127.0.0.1',port:0});
 assert.equal((await call(address,'/v1/checkins/'+id)).status,200);
 assert.equal((await call(address,'/v1/checkins/'+id,'GET',undefined,f.b.token)).status,404);
 assert.equal(await photoHash(address),hash);
 await socialRestored(address);
 assert.deepEqual((await call(address,`/v1/checkins/${id}/media`)).body,{mediaIds:[media.id],version:2});
 console.log('PASS: isolated pg_restore + media backup serves identical bytes and enforces original session/owner ACL and preserves P6 social data');
 await writeFile(join(backup,'RESULT.json'),JSON.stringify({result:'PASS',checkinId:id,mediaId:media.id,sha256:hash},null,2),{mode:0o600});
} finally {if(app)await app.close();await restored.close();}
console.log('Synthetic data/backups retained only in ignored work/ and local Docker volumes. No production or capacity claim.');
