import type pg from 'pg';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {test} from 'node:test';
import {mkdtemp,rm,cp} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import sharp from 'sharp';
import {testDatabase} from './database.js';
import {fixtures} from './fixtures.js';
import {migrate} from '../src/db/migrate.js';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {MediaService} from '../src/media/service.js';
import {FileStorage} from '../src/media/storage.js';

test('P6 real HTTP/PostgreSQL/SMTP: two registered accounts, friendship state machine, feed, comments, reactions, privacy and restart',async()=>{
 const db=await testDatabase(),dir=await mkdtemp(join(tmpdir(),'krapiva-social-'));
 // Exercise upgrade from P5, retaining existing rows.
 const migrations=join(dir,'migrations');await cp('migrations',migrations,{recursive:true});await rm(join(migrations,'008_social.sql'));await rm(join(migrations,'009_email_verification_codes.sql'));await rm(join(migrations,'010_places_badges_moderation.sql'));await rm(join(migrations,'011_password_reset_codes.sql'));await rm(join(migrations,'012_user_handles.sql'));await migrate(db.pool,migrations);
 const f=await fixtures(db.pool);await migrate(db.pool);
 assert.ok((await db.pool.query('SELECT id FROM users WHERE id=$1',[f.a.id])).rowCount);
 const media=new MediaService(db.pool,new FileStorage(join(dir,'media')));
 let app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,media),url=await app.listen({host:'127.0.0.1',port:0});
 const call=async(path:string,method='GET',body?:unknown,token?:string,status=200,key?:string)=>{
 const response=await fetch(url+path,{method,headers:{...(token?{authorization:`Bearer ${token}`} :{}),...(body===undefined?{}:{'content-type':'application/json'}),...(key?{'idempotency-key':key}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
 const text=await response.text();assert.equal(response.status,status,`${method} ${path}: ${text}`);return text?JSON.parse(text):null;
 };
 const register=async(name:string)=>{
 const email=`p6-${randomUUID()}@example.invalid`,password=`P6-${randomUUID()}-Test!`;
 await call('/v1/auth/register','POST',{email,password,displayName:name,adultConfirmed:true},undefined,202);
 const mailbox=process.env.MAILPIT_API_URL??'http://127.0.0.1:58025';
 const messages=await(await fetch(mailbox+'/api/v1/search?query='+encodeURIComponent('to:'+email))).json() as {messages:{ID:string}[]};
 const message=await(await fetch(mailbox+'/api/v1/message/'+messages.messages[0]!.ID)).json() as {Text:string};
 await call('/v1/auth/verify-code','POST',{email,code:message.Text.match(/Код подтверждения: ([0-9]{6})\b/)![1]},undefined,204);
 return call('/v1/auth/login','POST',{email,password});
 };
 try{
 const a=await register('P6 Alpha'),b=await register('P6 Beta'),ta=a.accessToken,tb=b.accessToken;
 const edit=async(token:string,visibility:string)=>{
 const p=await call('/v1/me','GET',undefined,token);
 return call('/v1/me/profile','PATCH',{firstName:null,lastName:null,country:null,city:null,bio:null,gender:'secret',genderVisibility:'private',profileVisibility:visibility,version:p.version},token);
 };
 await call('/v1/feed','GET',undefined,undefined,401);
 assert.equal((await call('/v1/users?q=P6','GET',undefined,ta)).items.length,0);
 await call('/v1/friend-requests/'+randomUUID(),'PUT',{userId:b.userId},ta,404);
 await edit(ta,'public');await edit(tb,'public');
 assert.equal((await call('/v1/users?q=Beta','GET',undefined,ta)).items[0].userId,b.userId);
 assert.ok(!JSON.stringify(await call('/v1/users?q=Beta','GET',undefined,ta)).includes('email'));
 await call('/v1/friend-requests/'+randomUUID(),'PUT',{userId:a.userId},ta,400);
 const requestId=randomUUID();
 const duplicates=await Promise.all([call('/v1/friend-requests/'+requestId,'PUT',{userId:b.userId},ta),call('/v1/friend-requests/'+requestId,'PUT',{userId:b.userId},ta)]);assert.equal(duplicates[0].id,duplicates[1].id);
 await call('/v1/friend-requests/'+randomUUID(),'PUT',{userId:a.userId},tb,409);
 await call('/v1/friend-requests/'+requestId+'/decision','PUT',{status:'accepted'},ta,404);
 assert.equal((await call('/v1/friend-requests','GET',undefined,tb)).items[0].fromUserId,a.userId);
 await Promise.all([call('/v1/friend-requests/'+requestId+'/decision','PUT',{status:'accepted'},tb),call('/v1/friend-requests/'+requestId+'/decision','PUT',{status:'accepted'},tb)]);
 assert.equal((await call('/v1/friends','GET',undefined,ta)).items[0].userId,b.userId);
 assert.equal((await call('/v1/friends','GET',undefined,tb)).items[0].userId,a.userId);
 await edit(ta,'friends');
 const bytes=await sharp({create:{width:32,height:32,channels:3,background:'#f5a623'}}).png().toBuffer();
 const upload=await fetch(url+'/v1/media',{method:'POST',headers:{authorization:'Bearer '+ta,'content-type':'image/png'},body:new Uint8Array(bytes)});assert.equal(upload.status,201);const photo=await upload.json() as {id:string};
 const input={positionId:f.position,ratingQuarters:17,body:'P6 shared checkin',occurredAt:'2026-11-07T10:00:00Z',visibility:'friends',mediaIds:[photo.id]};
 const {positionId:_,...mutableInput}=input;
 const first=await call('/v1/checkins','POST',input,ta,201,randomUUID());
 const second=await call('/v1/checkins','POST',{...input,mediaIds:[],body:'Second'},ta,201,randomUUID());
 await call('/v1/checkins','POST',{...input,visibility:'private',ratingQuarters:1},ta,201,randomUUID());
 const feed=await call('/v1/feed?limit=1','GET',undefined,tb);assert.equal(feed.items.length,1);assert.ok(feed.nextCursor);
 const next=await call('/v1/feed?limit=1&cursor='+feed.nextCursor,'GET',undefined,tb);assert.equal(next.items.length,1);assert.notEqual(next.items[0].id,feed.items[0].id);assert.equal(next.nextCursor,null);
 await call('/v1/feed?cursor='+feed.nextCursor,'GET',undefined,ta,400);
 await call('/v1/feed?limit=51','GET',undefined,tb,400);await call('/v1/feed?cursor=bad','GET',undefined,tb,400);
 assert.equal((await call('/v1/users/'+a.userId+'/statistics','GET',undefined,tb)).totalCheckins,2);
 assert.equal((await call('/v1/users/'+a.userId+'/statistics','GET',undefined,tb)).averageRating,4.25);
 assert.equal((await call('/v1/users/'+a.userId,'GET',undefined,tb)).gender,null);
 const path='/v1/social/checkins/'+first.id;
 assert.equal((await call(path,'GET',undefined,tb)).mediaIds[0],photo.id);
 assert.equal((await fetch(url+'/v1/media/'+photo.id,{headers:{authorization:'Bearer '+tb}})).status,200);
 await Promise.all([call(path+'/like','PUT',undefined,tb),call(path+'/like','PUT',undefined,tb)]);
 assert.equal((await call(path,'GET',undefined,ta)).likeCount,1);
 await call(path+'/like','DELETE',undefined,tb);assert.equal((await call(path,'GET',undefined,ta)).likeCount,0);
 await call(path+'/like','PUT',undefined,tb);
 const cid=randomUUID(),comment=path+'/comments/'+cid;
 await Promise.all([call(comment,'PUT',{body:'Настоящий комментарий'},tb),call(comment,'PUT',{body:'Настоящий комментарий'},tb)]);
 await call(comment,'PUT',{body:'Different payload'},tb,409);await call(comment,'DELETE',undefined,ta,404);
 await call(path+'/comments/'+randomUUID(),'PUT',{body:'   '},tb,400);
 assert.equal((await call(path+'/comments','GET',undefined,ta)).items.length,1);
 assert.equal((await call(path,'GET',undefined,ta)).commentCount,1);
 await app.close();app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,media);url=await app.listen({host:'127.0.0.1',port:0});
 assert.equal((await call(path,'GET',undefined,ta)).likeCount,1);assert.equal((await call(path+'/comments','GET',undefined,ta)).items[0].body,'Настоящий комментарий');
 await call(comment,'DELETE',undefined,tb,204);await call(comment,'DELETE',undefined,tb,204);await call(comment,'PUT',{body:'Настоящий комментарий'},tb,409);
 assert.equal((await call(path,'GET',undefined,ta)).commentCount,0);
 await call(path+'/comments/'+randomUUID(),'PUT',{body:'Still visible'},tb);
 // A6: all direct/indirect reads and writes lose access after privacy change.
 await call('/v1/checkins/'+first.id,'PATCH',{...mutableInput,visibility:'private',version:1},ta);
 for(const [suffix,method,body]of [['','GET',undefined],['/comments','GET',undefined],['/like','PUT',undefined],['/comments/'+randomUUID(),'PUT',{body:'Denied'}]] as const)await call(path+suffix,method,body,tb,404);
 assert.equal((await fetch(url+'/v1/media/'+photo.id,{headers:{authorization:'Bearer '+tb}})).status,404);
 assert.equal((await call('/v1/feed','GET',undefined,tb)).items.length,1);
 await call('/v1/checkins/'+first.id,'PATCH',{...mutableInput,version:2},ta);
 await edit(ta,'private');await call(path,'GET',undefined,tb,404);await call('/v1/users/'+a.userId+'/statistics','GET',undefined,tb,404);
 assert.equal((await call('/v1/feed','GET',undefined,tb)).items.length,0);
 await edit(ta,'friends');await call('/v1/friends/'+a.userId,'DELETE',undefined,tb,204);
 await call(path,'GET',undefined,tb,404);assert.equal((await call('/v1/feed','GET',undefined,tb)).items.length,0);
 await call('/v1/friend-requests/'+requestId+'/decision','PUT',{status:'accepted'},tb);
 assert.equal((await call('/v1/users/'+a.userId+'/relationship','GET',undefined,tb)).friends,false);
 await edit(ta,'public');
 const rejected=randomUUID();await call('/v1/friend-requests/'+rejected,'PUT',{userId:a.userId},tb);await call('/v1/friend-requests/'+rejected+'/decision','PUT',{status:'rejected'},ta);await call('/v1/friend-requests/'+rejected+'/decision','PUT',{status:'accepted'},ta,409);
 const cancelled=randomUUID();await call('/v1/friend-requests/'+cancelled,'PUT',{userId:a.userId},tb);await call('/v1/friend-requests/'+cancelled+'/decision','PUT',{status:'cancelled'},tb);
 const again=randomUUID();await call('/v1/friend-requests/'+again,'PUT',{userId:a.userId},tb);await call('/v1/friend-requests/'+again+'/decision','PUT',{status:'accepted'},ta);
 await db.pool.query('UPDATE checkins SET hidden_at=now() WHERE id=$1',[first.id]);await call(path,'GET',undefined,tb,404);await call(path+'/comments','GET',undefined,tb,404);
 await call('/v1/checkins/'+second.id,'DELETE',{version:1},ta,204);assert.equal((await call('/v1/feed','GET',undefined,tb)).items.length,0);
 await db.pool.query('UPDATE checkins SET hidden_at=NULL WHERE id=$1',[first.id]);
 await call('/v1/me/deletion-request','POST',{},ta,202);await call(path,'GET',undefined,tb,404);assert.equal((await call('/v1/friends','GET',undefined,tb)).items.length,0);
 assert.equal((await db.pool.query('SELECT count(*)::int AS n FROM reactions WHERE checkin_id=$1',[first.id])).rows[0].n,1);
 }finally{await app.close();await db.close();await rm(dir,{recursive:true,force:true});}
});

test('P6 concurrent privacy/unfriend serializes against social writes; comment cursors and suspension',async()=>{
 const db=await testDatabase();await migrate(db.pool);const f=await fixtures(db.pool);
 const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool),url=await app.listen({host:'127.0.0.1',port:0});
 const call=async(path:string,method='GET',body?:unknown,token=f.b.token)=>{
 const response=await fetch(url+path,{method,headers:{authorization:'Bearer '+token,...(body===undefined?{}:{'content-type':'application/json'}),'idempotency-key':randomUUID()},...(body===undefined?{}:{body:JSON.stringify(body)})});
 const text=await response.text();return {status:response.status,data:text?JSON.parse(text):null};
 };
 const blockers:pg.PoolClient[]=[];
 try{
 await db.pool.query("UPDATE user_profiles SET profile_visibility='public'");
 await db.pool.query('INSERT INTO friendships(user_low,user_high)VALUES(least($1::uuid,$2::uuid),greatest($1::uuid,$2::uuid))',[f.a.id,f.b.id]);
 const created=await call('/v1/checkins','POST',{positionId:f.position,body:'Race checkin',visibility:'friends',ratingQuarters:17,occurredAt:'2026-11-07T12:00:00Z'},f.a.token);assert.equal(created.status,201);
 const id=created.data.id,path='/v1/social/checkins/'+id;
 // Two writes with the same server timestamp must still page exactly once each.
 const ids=[randomUUID(),randomUUID(),randomUUID()];
 for(const cid of ids)assert.equal((await call(path+'/comments/'+cid,'PUT',{body:'Original'})).status,200);
 await db.pool.query("UPDATE comments SET created_at='2026-11-07T12:00:00Z' WHERE checkin_id=$1",[id]);
 const seen=new Set<string>();let cursor:string|null=null;
 do{const result=await call(path+'/comments?limit=1'+(cursor?'&cursor='+cursor:''));assert.equal(result.status,200);for(const item of result.data.items){assert.ok(!seen.has(item.id));seen.add(item.id);}cursor=result.data.nextCursor;}while(cursor);
 assert.equal(seen.size,3);
 const firstPage=(await call(path+'/comments?limit=1')).data;
 assert.equal((await call(path+'/comments?cursor='+firstPage.nextCursor,'GET',undefined,f.a.token)).status,400);
 const malformed=JSON.parse(Buffer.from(firstPage.nextCursor,'base64url').toString());malformed[1]='2026-02-31T12:00:00Z';
 assert.equal((await call(path+'/comments?cursor='+Buffer.from(JSON.stringify(malformed)).toString('base64url'))).status,400);
 // Do not send uncontrolled sleeps: observe a real PostgreSQL lock wait before releasing it.
 const waitForLock=async()=>{const deadline=Date.now()+1200;while(Date.now()<deadline){if((await db.pool.query("SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'")).rowCount)return;await new Promise(resolve=>setTimeout(resolve,10));}throw Error('Expected actual ACL row lock wait');};
 let lock=await db.pool.connect();blockers.push(lock);await lock.query('BEGIN');await lock.query("UPDATE user_profiles SET profile_visibility='private' WHERE user_id=$1",[f.a.id]);
 const pending=call(path+'/like','PUT');await waitForLock();await lock.query('COMMIT');assert.equal((await pending).status,404);
 assert.equal((await db.pool.query('SELECT count(*)::int n FROM reactions WHERE checkin_id=$1',[id])).rows[0].n,0);
 await db.pool.query("UPDATE user_profiles SET profile_visibility='friends' WHERE user_id=$1",[f.a.id]);
 await lock.query('BEGIN');await lock.query('DELETE FROM friendships WHERE user_low=least($1::uuid,$2::uuid) AND user_high=greatest($1::uuid,$2::uuid)',[f.a.id,f.b.id]);
 const deniedId=randomUUID(),pendingComment=call(path+'/comments/'+deniedId,'PUT',{body:'Must not be saved'});await waitForLock();await lock.query('COMMIT');assert.equal((await pendingComment).status,404);
 assert.equal((await db.pool.query('SELECT id FROM comments WHERE id=$1',[deniedId])).rowCount,0);
 await db.pool.query("UPDATE user_profiles SET profile_visibility='public' WHERE user_id=$1",[f.a.id]);
 await db.pool.query("UPDATE checkins SET visibility='public' WHERE id=$1",[id]);
 assert.equal((await call(path)).status,200);assert.equal((await call('/v1/feed')).data.items.length,0); // public stranger is not in friends feed
 await db.pool.query("UPDATE users SET status='suspended' WHERE id=$1",[f.b.id]);
 assert.equal((await call(path,'GET',undefined,f.a.token)).data.commentCount,0);
 assert.equal((await call(path+'/comments','GET',undefined,f.a.token)).data.items.length,0);
 }finally{for(const lock of blockers){await lock.query('ROLLBACK');lock.release();}await app.close();await db.close();}
});
