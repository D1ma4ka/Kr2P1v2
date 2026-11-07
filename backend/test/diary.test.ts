import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {test} from 'node:test';
import {mkdtemp,rm} from 'node:fs/promises';
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

test('P5 real HTTP/SQL: A/A/B counts, A13/A14 charts, filters/cursors, own media/tags, wishlist, ACL, versions and merge',async()=>{
 const db=await testDatabase(),dir=await mkdtemp(join(tmpdir(),'krapiva-diary-'));
 await migrate(db.pool);const f=await fixtures(db.pool);
 const media=new MediaService(db.pool,new FileStorage(dir));const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,media);
 const url=await app.listen({host:'127.0.0.1',port:0});
 const call=async(path:string,method='GET',body?:unknown,token=f.a.token,status=200,key?:string)=>{
 const response=await fetch(url+path,{method,headers:{authorization:`Bearer ${token}`,...(body===undefined?{}:{'content-type':'application/json'}),...(key?{'idempotency-key':key}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
 const text=await response.text();assert.equal(response.status,status,`${method} ${path}: ${text}`);return text?JSON.parse(text):null;
 };
 const create=async(position=f.position,rating:number|null=17,extra:Record<string,unknown>={},token=f.a.token,key=randomUUID(),status=201)=>call('/v1/checkins','POST',{positionId:position,ratingQuarters:rating,body:'Synthetic заметка Ёж',occurredAt:'2026-11-07T10:00:00Z',visibility:'private',...extra},token,status,key);
 try{
 const empty=await call('/v1/me/home');assert.equal(empty.statistics.totalCheckins,0);assert.equal(empty.statistics.averageRating,null);assert.equal(empty.statistics.histogram.length,20);assert.equal(empty.recent.items.length,0);
 const release=(await db.pool.query('SELECT release_id FROM beer_positions WHERE id=$1',[f.position])).rows[0].release_id;
 const styles=(await db.pool.query("INSERT INTO styles(name)VALUES('Synthetic X'),('Synthetic Y')RETURNING id")).rows;
 await db.pool.query('UPDATE beer_releases SET style_id=$1 WHERE id=$2',[styles[0].id,release]);
 const second=(await db.pool.query("INSERT INTO beer_positions(release_id,packaging,volume_ml,status,source)VALUES($1,'can',330,'verified','synthetic')RETURNING id",[release])).rows[0].id;
 const thirdRelease=(await db.pool.query("INSERT INTO beer_releases(brewery_id,name,style_id) SELECT brewery_id,'Synthetic third',$2 FROM beer_releases WHERE id=$1 RETURNING id",[release,styles[1].id])).rows[0].id;
 const third=(await db.pool.query("INSERT INTO beer_positions(release_id,status,source)VALUES($1,'verified','synthetic')RETURNING id",[thirdRelease])).rows[0].id;
 const tags=(await call('/v1/taste-tags')).items;assert.equal(tags.length,12);
 const photo=await media.upload(f.a.id,await sharp({create:{width:32,height:32,channels:3,background:'#ffad0a'}}).png().toBuffer(),'image/png');
 const venue=(await db.pool.query("INSERT INTO venues(name,type,city,source,status)VALUES('Synthetic bar','bar','Synthetic city','fixture','verified')RETURNING id")).rows[0].id;
 const key=randomUUID(),extras={tagIds:[tags[0].id],mediaIds:[photo.id],servingFormat:'Кран',venueId:venue};
 const first=await create(f.position,17,extras,f.a.token,key);assert.equal(first.mediaIds[0],photo.id);assert.equal(first.tagLabels[0],tags[0].label);assert.equal(first.venueName,'Synthetic bar');
 assert.equal((await call('/v1/me/checkin-requests/'+key)).checkinId,first.id);assert.equal((await call('/v1/me/checkin-requests/'+key,'GET',undefined,f.b.token)).checkinId,null);
 assert.equal((await create(f.position,17,extras,f.a.token,key,200)).id,first.id);
 await create(f.position,18,extras,f.a.token,key,409);
 const repeated=await create(f.position,19,{venueText:'Дом',occurredAt:'2026-11-07T11:00:00Z'});
 const s3=await call('/v1/me/statistics');assert.equal(s3.totalCheckins,2);assert.equal(s3.uniquePositions,1);
 const secondC=await create(second,null);const aab=await call('/v1/me/statistics');assert.equal(aab.totalCheckins,3);assert.equal(aab.uniquePositions,2);const thirdC=await create(third,17);
 let stats=await call('/v1/me/statistics');assert.equal(stats.totalCheckins,4);assert.equal(stats.uniquePositions,3);assert.equal(stats.ratedCheckins,3);assert.equal(stats.unratedCheckins,1);assert.equal(stats.ratingSumQuarters,'53');assert.ok(Math.abs(stats.averageRating-13.25/3)<1e-12);
 assert.equal(stats.histogram.find((x:any)=>x.ratingQuarters===17).count,2);assert.deepEqual(stats.styles.map((x:any)=>x.count).sort(),[1,2]);
 await db.pool.query("UPDATE beer_positions SET status='rejected' WHERE id=$1",[third]);await call('/v1/positions/'+third+'/statistics','GET',undefined,f.a.token,404);assert.equal((await call('/v1/me/statistics')).uniquePositions,3);
 await db.pool.query("UPDATE beer_positions SET status='verified' WHERE id=$1",[third]);
 const drill=await call('/v1/me/unique-positions?styleId='+styles[0].id);assert.equal(drill.items.length,2);
 for(const sort of ['date_desc','date_asc','rating_desc','rating_asc']){
 let cursor:string|null=null;const seen=new Set<string>();do{const page=await call('/v1/checkins?limit=1&sort='+sort+(cursor?'&cursor='+cursor:''));for(const item of page.items){assert.ok(!seen.has(item.id));seen.add(item.id);}cursor=page.nextCursor;}while(cursor);assert.equal(seen.size,4);
 }
 const ratingPage=await call('/v1/checkins?limit=1&sort=rating_desc');
 for(const [badKey,badId]of [[1.5,first.id],[1e100,first.id],[17,'a'.repeat(36)]]){const c=JSON.parse(Buffer.from(ratingPage.nextCursor,'base64url').toString());c[1]=badKey;c[2]=badId;await call('/v1/checkins?limit=1&sort=rating_desc&cursor='+Buffer.from(JSON.stringify(c)).toString('base64url'),'GET',undefined,f.a.token,400);}
 const page=await call('/v1/checkins?limit=1');await call('/v1/checkins?limit=1&q=changed&cursor='+page.nextCursor,'GET',undefined,f.a.token,400);
 assert.equal((await call('/v1/checkins?q=%D0%B5%D0%B6&hasPhoto=true')).items.length,1);
 assert.equal((await call('/v1/checkins?unrated=true')).items[0].id,secondC.id);
 assert.equal((await call('/v1/checkins?ratingMin=19&ratingMax=20')).items.length,1);
 assert.equal((await call('/v1/checkins?venueId='+venue)).items.length,1);
 assert.equal((await call('/v1/checkins?venue=%D0%94%D0%BE%D0%BC')).items.length,1);
 assert.equal((await call('/v1/checkins?from=2026-11-07T10:30:00Z')).items.length,1);
 await call('/v1/checkins?from=2026-02-31T00:00:00Z','GET',undefined,f.a.token,400);
 await create(f.position,17,{occurredAt:'2026-02-31T00:00:00Z'},f.a.token,randomUUID(),400);
 await create(f.position,17,{tagIds:[randomUUID()]},f.a.token,randomUUID(),422);
 await create(f.position,17,{mediaIds:[photo.id]},f.b.token,randomUUID(),404);
 await create(f.position,17,{mediaIds:Array.from({length:5},()=>randomUUID())},f.a.token,randomUUID(),400);
 await call('/v1/checkins/'+first.id,'GET',undefined,f.b.token,404);
 await call('/v1/media/'+photo.id,'GET',undefined,f.b.token,404);
 const editBody={ratingQuarters:20,body:'Edited',occurredAt:repeated.occurredAt,visibility:'private',version:1};
 const updates=await Promise.all([fetch(url+'/v1/checkins/'+repeated.id,{method:'PATCH',headers:{authorization:'Bearer '+f.a.token,'content-type':'application/json'},body:JSON.stringify(editBody)}),fetch(url+'/v1/checkins/'+repeated.id,{method:'PATCH',headers:{authorization:'Bearer '+f.a.token,'content-type':'application/json'},body:JSON.stringify(editBody)})]);assert.deepEqual(updates.map(x=>x.status).sort(),[200,409]);
 stats=await call('/v1/me/statistics');assert.equal(stats.ratingSumQuarters,'54');assert.equal(stats.averageRating,4.5);
 // Own stats do not disclose another user's private counts; visible aggregates require both privacy gates.
 await create(f.position,1,{},f.b.token);let visible=await call('/v1/positions/'+f.position+'/statistics');assert.equal(visible.totalCheckins,2);assert.equal(visible.personalCheckins,2);
 await db.pool.query("UPDATE user_profiles SET profile_visibility='public' WHERE user_id=$1",[f.a.id]);
 await call('/v1/checkins/'+first.id,'PATCH',{ratingQuarters:17,body:first.body,occurredAt:first.occurredAt,visibility:'public',version:1});
 // Profile and checkin friends gates are independent, and direct media follows them.
 await db.pool.query("UPDATE user_profiles SET profile_visibility='friends' WHERE user_id=$1",[f.a.id]);
 await call('/v1/media/'+photo.id,'GET',undefined,f.b.token,404);
 await db.pool.query('INSERT INTO friendships(user_low,user_high)VALUES(least($1::uuid,$2::uuid),greatest($1::uuid,$2::uuid))',[f.a.id,f.b.id]);
 await db.pool.query("UPDATE checkins SET visibility='friends' WHERE id=$1",[first.id]);
 const imageResponse=await fetch(url+'/v1/media/'+photo.id,{headers:{authorization:'Bearer '+f.b.token}});assert.equal(imageResponse.status,200);assert.ok((await imageResponse.arrayBuffer()).byteLength>0);
 visible=await call('/v1/positions/'+f.position+'/statistics','GET',undefined,f.b.token);assert.equal(visible.totalCheckins,2);assert.equal(visible.users,2);
 await db.pool.query("UPDATE users SET status='suspended' WHERE id=$1",[f.a.id]);await call('/v1/media/'+photo.id,'GET',undefined,f.b.token,404);
 await db.pool.query("UPDATE users SET status='active' WHERE id=$1",[f.a.id]);
 await db.pool.query('UPDATE checkins SET hidden_at=now() WHERE id=$1',[first.id]);
 await call('/v1/media/'+photo.id,'GET',undefined,f.b.token,404);
 assert.equal((await call('/v1/me/statistics')).totalCheckins,3);await db.pool.query('UPDATE checkins SET hidden_at=NULL WHERE id=$1',[first.id]);
 for(let i=0;i<2;i++)await call('/v1/me/wishlist/'+second,'PUT');assert.equal((await call('/v1/me/wishlist')).items.length,1);assert.equal((await call('/v1/me/wishlist')).items[0].checkins,1);assert.equal((await call('/v1/me/wishlist','GET',undefined,f.b.token)).items.length,0);
 // Real moderator merge: positions collapse but checkin identities/history remain.
 await db.pool.query("UPDATE users SET role='moderator' WHERE id=$1",[f.b.id]);await call('/v1/moderation/positions/'+second+'/merge','POST',{targetId:f.position,reason:'Synthetic duplicate resolution'},f.b.token);
 stats=await call('/v1/me/statistics');assert.equal(stats.totalCheckins,4);assert.equal(stats.uniquePositions,2);assert.equal(stats.styles.find((x:any)=>x.id===styles[0].id).count,1);
 assert.equal((await call('/v1/me/wishlist')).items[0].id,f.position);assert.equal((await call('/v1/me/wishlist')).items[0].checkins,3);
 const mergedCheckin=await call('/v1/checkins/'+secondC.id);assert.equal(mergedCheckin.positionId,f.position);
 await call('/v1/checkins/'+thirdC.id,'DELETE',{version:1},f.a.token,204);assert.equal((await call('/v1/me/statistics')).uniquePositions,1);
 await call('/v1/checkins/'+repeated.id,'DELETE',{version:2},f.a.token,204);assert.equal((await call('/v1/me/statistics')).uniquePositions,1);
 await call('/v1/me/wishlist/'+second,'DELETE');assert.equal((await call('/v1/me/wishlist')).items.length,0);
 await db.pool.query('UPDATE beer_releases SET style_id=NULL WHERE id=$1',[release]);const unknown=await call('/v1/me/statistics');assert.equal(unknown.styles.find((x:any)=>x.id===null).count,1);assert.equal((await call('/v1/me/unique-positions?styleId=unknown')).items.length,1);
 await db.pool.query('UPDATE beer_releases SET style_id=$1 WHERE id=$2',[styles[0].id,release]);
 const current=await call('/v1/checkins/'+first.id);await call('/v1/checkins/'+first.id,'PATCH',{...editBody,version:current.version,mediaIds:[],tagIds:[]});
 assert.equal((await call('/v1/checkins?hasPhoto=true')).items.length,0);await call('/v1/media/'+photo.id,'DELETE',undefined,f.a.token,204);
 }finally{await app.close();await db.close();await rm(dir,{recursive:true,force:true});}
});

test('P4 → P5 migration preserves historical checkins and legacy idempotency acknowledgements',async()=>{
 const {cp}=await import('node:fs/promises');const {tokenHash}=await import('../src/identity/sessions.js');const {createCheckin}=await import('../src/checkins/repository.js');const {statistics}=await import('../src/checkins/statistics.js');
 const db=await testDatabase(),dir=await mkdtemp(join(tmpdir(),'krapiva-p4-upgrade-'));
 try {
  for(const name of ['001_foundation.sql','002_request_budgets.sql','003_accounts.sql','004_catalog.sql','005_catalog_batch_projection.sql','006_proposal_media_review.sql'])await cp('migrations/'+name,join(dir,name));
  await migrate(db.pool,dir);const f=await fixtures(db.pool),key=randomUUID();
  const input={positionId:f.position,ratingQuarters:17,body:'Persisted P4 history',occurredAt:'2026-11-07T10:00:00.000Z',visibility:'private'};
  const id=(await db.pool.query('INSERT INTO checkins(user_id,position_id,rating_quarters,body,occurred_at,visibility)VALUES($1,$2,$3,$4,$5,$6)RETURNING id',[f.a.id,input.positionId,input.ratingQuarters,input.body,input.occurredAt,input.visibility])).rows[0].id;
  await db.pool.query("INSERT INTO idempotency_keys(user_id,key,operation,request_hash,resource_id)VALUES($1,$2,'create-checkin',$3,$4)",[f.a.id,key,tokenHash(JSON.stringify(input)),id]);
  await migrate(db.pool);const replay=await createCheckin(db.pool,f.a.id,key,input);assert.equal(replay.replay,true);assert.equal(replay.row.id,id);
  assert.equal((await statistics(db.pool,f.a.id)).totalCheckins,1);assert.equal((await db.pool.query('SELECT * FROM taste_tags')).rowCount,12);assert.equal((await db.pool.query('SELECT * FROM beer_positions')).rowCount,1);
 }finally{await db.close();await rm(dir,{recursive:true,force:true});}
});
