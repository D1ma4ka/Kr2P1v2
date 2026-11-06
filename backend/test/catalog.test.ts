import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {test} from 'node:test';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import sharp from 'sharp';
import {testDatabase} from './database.js';
import {migrate} from '../src/db/migrate.js';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {issueSession} from '../src/identity/sessions.js';
import {MediaService} from '../src/media/service.js';
import {FileStorage} from '../src/media/storage.js';
import {normalizeGtin} from '../src/catalog/gtin.js';

test('GTIN checksum, EAN8/UPC/EAN13/GTIN14 padding and QR separation',()=>{
 for(const code of ['96385074','012345600012','4006381333931','00012345600012']) assert.equal(normalizeGtin(code).length,14);
 assert.equal(normalizeGtin('012345600012'),'00012345600012');
 for(const code of ['12345678901234','4006381333932','https://example.invalid','1234567','１２３４５６７８']) assert.throws(()=>normalizeGtin(code));
});

test('P4 real HTTP/PostgreSQL: empty catalog, proposals, ACL, search, GTIN, duplicates, merge, media and rollback',async()=>{
 const db=await testDatabase(),root=await mkdtemp(join(tmpdir(),'krapiva-catalog-'));
 const media=new MediaService(db.pool,new FileStorage(root));
 const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool,media);
 try {
  await migrate(db.pool);
  const users=[];
  for(const role of ['user','user','moderator']) {
   const id=(await db.pool.query('INSERT INTO users(email,display_name,adult_confirmed_at,email_verified_at,role) VALUES($1,$2,now(),now(),$3) RETURNING id',[`${randomUUID()}@example.invalid`,'Synthetic P4',role])).rows[0].id as string;
   await db.pool.query('INSERT INTO user_profiles(user_id) VALUES($1)',[id]);users.push({id,token:await issueSession(db.pool,id)});
  }
  const [a,b,mod]=users as [{id:string;token:string},{id:string;token:string},{id:string;token:string}];
  const base=await app.listen({host:'127.0.0.1',port:0});
  async function call(path:string,method='GET',body?:unknown,who=a,expected=200) {
   const response=await fetch(base+path,{method,headers:{authorization:'Bearer '+who.token,...(body?{'content-type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});
   const value=response.status===204?null:await response.json() as any;
   assert.equal(response.status,expected,JSON.stringify({path,value}));return value;
  }
  const data={name:'Synthetic Тёмный Ёж',breweryName:'Synthetic Пивоварня',breweryCity:'Тула',styleName:'Synthetic Stout',abv:6.5,ibu:null,packaging:'can',volumeMl:500,source:'Synthetic test evidence',gtin:'4006381333931'};
  const proposal=async(input:unknown=data)=>call('/v1/position-proposals','POST',{...input as object,requestId:randomUUID()},a,201);
  const decision=async(id:string,extra:object={},expected=200)=>call(`/v1/moderation/position-proposals/${id}/decision`,'POST',{version:1,action:'accept',reason:'Synthetic review',...extra},mod,expected);
  assert.deepEqual((await call('/v1/positions')).items,[]);
  assert.equal((await call('/v1/barcodes/lookup?kind=gtin&code=4006381333931')).state,'not_found');
  assert.equal((await call('/v1/barcodes/lookup?kind=qr&code=4006381333931')).state,'unsupported_qr');
  await call('/v1/barcodes/lookup?kind=gtin&code=4006381333932','GET',undefined,a,422);
  await call('/v1/positions?limit=51','GET',undefined,a,400);
  await call('/v1/moderation/position-proposals','GET',undefined,a,403);
  const longText='Synthetic '+('я'.repeat(4990));
  const longProposal=await proposal({...data,description:longText,source:'Synthetic '+('я'.repeat(1990)),brewerySource:'Synthetic '+('я'.repeat(1990)),gtin:null});
  await decision(longProposal.id,{action:'reject'});
  const key=randomUUID(),request={...data,requestId:key};
  const first=await call('/v1/position-proposals','POST',request,a,201);
  assert.equal((await call('/v1/position-proposals','POST',request,a,201)).id,first.id);
  await call('/v1/position-proposals','POST',{...request,name:'Changed'},a,409);
  assert.equal((await call('/v1/me/position-proposals','GET',undefined,b)).items.length,0);
  assert.equal((await call('/v1/positions')).items.length,0);
  await call(`/v1/position-proposals/${first.id}`,'PATCH',{...data,version:1},b,404);
  await call(`/v1/moderation/position-proposals/${first.id}/decision`,'POST',{version:1,action:'accept',reason:'x'},a,403);
  assert.equal((await call('/v1/moderation/position-proposals','GET',undefined,mod)).items[0].id,first.id);
  const accepted=await decision(first.id),id=accepted.positionId;
  await decision(first.id,{},409);
  const card=await call('/v1/positions/'+id);
  assert.equal(card.ibu,null);assert.equal(card.volumeMl,500);assert.equal(card.imageAssetId,null);
  assert.equal((await call('/v1/barcodes/lookup?kind=gtin&code=4006381333931')).position.id,id);
  assert.equal((await call('/v1/positions?q='+encodeURIComponent('темный ЕЖ stout ПИВОВАРНЯ'))).items[0].id,id);
  assert.equal((await call('/v1/positions?q=absent')).items.length,0);
  assert.equal((await call('/v1/positions?abvMin=7')).items.length,0);
  await call('/v1/positions?abvMin=10&abvMax=2','GET',undefined,a,422);
  assert.equal((await call('/v1/breweries/'+card.breweryId)).city,'Тула');
  assert.equal((await call('/v1/breweries/'+card.breweryId+'/positions')).items[0].id,id);
  assert.equal((await call('/v1/styles')).items[0].name,data.styleName);
  // A second package is a distinct position; same-name candidates require explicit review.
  const second=await proposal({...data,breweryId:card.breweryId,volumeMl:330,gtin:'012345600012'});
  assert.equal((await call(`/v1/moderation/position-proposals/${second.id}/duplicates`,'GET',undefined,mod)).items[0].id,id);
  await decision(second.id,{breweryId:card.breweryId},409);
  const secondId=(await decision(second.id,{breweryId:card.breweryId,releaseId:card.releaseId,allowDuplicate:true})).positionId;
  const all=new Set([id,secondId]);
  for(const sort of ['newest','name','abv']) {
   const q=`/v1/positions?limit=1&sort=${sort}`,page=await call(q);assert.ok(page.nextCursor);
   const next=await call(q+'&cursor='+page.nextCursor);assert.notEqual(next.items[0].id,page.items[0].id);assert.equal(next.nextCursor,null);
   assert.deepEqual(new Set([page.items[0].id,next.items[0].id]),all);
   if(sort==='newest') {
    const invalid=JSON.parse(Buffer.from(page.nextCursor,'base64url').toString()); invalid[1]='2026-02-31T00:00:00.000Z';
    await call(q+'&cursor='+Buffer.from(JSON.stringify(invalid)).toString('base64url'),'GET',undefined,a,400);
   }
   await call(q+'&q=changed&cursor='+page.nextCursor,'GET',undefined,a,400);
  }
  // No stale projection after edits to referenced names/style or verified visibility.
  await db.pool.query("UPDATE breweries SET name='Synthetic Новая' WHERE id=$1",[card.breweryId]);
  assert.equal((await call('/v1/positions?q='+encodeURIComponent('новая'))).items.length,2);
  assert.equal((await call('/v1/positions?q='+encodeURIComponent('пивоварня'))).items.length,0);
  await db.pool.query("UPDATE styles SET name='Synthetic Porter' WHERE id=$1",[card.styleId]);
  assert.equal((await call('/v1/positions?q=porter')).items.length,2);
  // Barcode conflict rolls back every catalog write.
  const conflicting=await proposal({...data,name:'Synthetic different',breweryId:card.breweryId});
  await decision(conflicting.id,{breweryId:card.breweryId},409);
  assert.equal((await call('/v1/positions')).items.length,2);
  const reject=await decision(conflicting.id,{action:'reject'});assert.equal(reject.status,'rejected');
  const edit=await proposal({...data,gtin:null,name:'Synthetic editable'});
  const edited=await call(`/v1/position-proposals/${edit.id}`,'PATCH',{...data,gtin:null,name:'Synthetic edited',version:1});assert.equal(edited.version,2);
  assert.equal((await db.pool.query('SELECT * FROM proposal_revisions WHERE proposal_id=$1',[edit.id])).rowCount,1);
  await call(`/v1/position-proposals/${edit.id}`,'PATCH',{...data,version:1},a,409);
  const link=await proposal({...data,gtin:null});await decision(link.id,{action:'link',positionId:id});
  await call('/v1/moderation/barcode-exclusions','PUT',{code:'96385074',source:'Synthetic non-beer proof'},mod);
  assert.equal((await call('/v1/barcodes/lookup?kind=gtin&code=96385074')).state,'non_beer');
  await call('/v1/moderation/barcode-exclusions','PUT',{code:data.gtin,source:'Synthetic'},mod,409);
  await call('/v1/moderation/barcode-exclusions/96385074','DELETE',{reason:'Synthetic correction'},mod,204);
  assert.equal((await call('/v1/barcodes/lookup?kind=gtin&code=96385074')).state,'not_found');
  // Merge preserves identifiers and moves existing P2 checkins/wishlist/barcodes.
  await db.pool.query('INSERT INTO checkins(user_id,position_id,occurred_at) VALUES($1,$2,now())',[a.id,secondId]);
  await db.pool.query('INSERT INTO wishlist_items(user_id,position_id) VALUES($1,$2),($1,$3)',[a.id,id,secondId]);
  await call(`/v1/moderation/positions/${secondId}/merge`,'POST',{targetId:id,reason:'Synthetic duplicate resolution'},a,403);
  const merged=await call(`/v1/moderation/positions/${secondId}/merge`,'POST',{targetId:id,reason:'Synthetic duplicate resolution'},mod);
  assert.equal(merged.canonicalId,id);assert.equal(merged.requestedId,secondId);
  assert.equal((await call('/v1/positions/'+secondId)).id,id);
  assert.equal((await call('/v1/barcodes/lookup?kind=gtin&code=012345600012')).position.id,id);
  assert.equal((await db.pool.query('SELECT * FROM wishlist_items WHERE user_id=$1',[a.id])).rowCount,1);
  assert.equal((await db.pool.query('SELECT position_id FROM checkins WHERE user_id=$1',[a.id])).rows[0].position_id,id);
  await call(`/v1/moderation/positions/${id}/merge`,'POST',{targetId:secondId,reason:'Synthetic cycle'},mod,422);
  assert.ok((await db.pool.query('SELECT * FROM audit_events')).rowCount!>=7);
  // Sanitized photo remains private until a rights-confirmed proposal is accepted.
  const image=await media.upload(a.id,await sharp({create:{width:16,height:16,channels:3,background:'#f5a623'}}).png().toBuffer(),'image/png');
  await assert.rejects(media.get(b.id,image.id));
  await call('/v1/position-proposals','POST',{...data,requestId:randomUUID(),gtin:null,imageAssetId:image.id,imageSource:'Synthetic own image',rightsConfirmed:false},a,422);
  const imageProposal=await proposal({...data,breweryId:card.breweryId,name:'Synthetic photo',gtin:null,imageAssetId:image.id,imageSource:'Synthetic own image',rightsConfirmed:true});
  const photoPosition=(await decision(imageProposal.id,{breweryId:card.breweryId})).positionId;assert.ok((await media.get(b.id,image.id)).length>0);
  await assert.rejects(media.remove(a.id,image.id));
  await db.pool.query("UPDATE media_assets SET moderation_status='hidden' WHERE id=$1",[image.id]);await assert.rejects(media.get(b.id,image.id));
  const breweryImage=await media.upload(mod.id,await sharp({create:{width:12,height:12,channels:3,background:'#78b936'}}).png().toBuffer(),'image/png');
  await call(`/v1/moderation/breweries/${card.breweryId}/image`,'PUT',{imageAssetId:breweryImage.id,imageSource:'Synthetic own brewery image',rightsConfirmed:true},a,403);
  const breweryCard=await call(`/v1/moderation/breweries/${card.breweryId}/image`,'PUT',{imageAssetId:breweryImage.id,imageSource:'Synthetic own brewery image',rightsConfirmed:true},mod);
  assert.equal(breweryCard.imageAssetId,breweryImage.id);assert.ok((await media.get(b.id,breweryImage.id)).length>0);await assert.rejects(media.remove(mod.id,breweryImage.id));
  // A hidden photo must not prevent a moderator from rejecting the proposal.
  const pendingImage=await media.upload(a.id,await sharp({create:{width:10,height:10,channels:3,background:'#1d2125'}}).png().toBuffer(),'image/png');
  const hiddenPending=await proposal({...data,breweryId:card.breweryId,name:'Synthetic hidden pending',gtin:null,imageAssetId:pendingImage.id,imageSource:'Synthetic own image',rightsConfirmed:true});
  await db.pool.query("UPDATE media_assets SET moderation_status='hidden' WHERE id=$1",[pendingImage.id]);
  await decision(hiddenPending.id,{action:'reject'});
  // Two concurrent moderator decisions: exactly one wins, the other sees a version conflict.
  const racing=await proposal({...data,breweryId:card.breweryId,name:'Synthetic concurrent',gtin:null});
  const statuses=await Promise.all(['accept','reject'].map(async action=>{
   const response=await fetch(base+`/v1/moderation/position-proposals/${racing.id}/decision`,{method:'POST',headers:{authorization:'Bearer '+mod.token,'content-type':'application/json'},body:JSON.stringify({version:1,action,breweryId:card.breweryId,reason:'Synthetic concurrent review'})});
   await response.arrayBuffer();return response.status;
  }));assert.deepEqual(statuses.sort(),[200,409]);
  await call(`/v1/moderation/positions/${id}/merge`,'POST',{targetId:photoPosition,reason:'Synthetic transitive alias'},mod);
  assert.equal((await call('/v1/positions/'+secondId)).canonicalId,photoPosition);
  assert.equal((await call('/v1/barcodes/lookup?kind=gtin&code=012345600012')).position.id,photoPosition);
  await db.pool.query("UPDATE breweries SET verification_status='rejected' WHERE id=$1",[card.breweryId]);
  assert.equal((await call('/v1/positions')).items.length,0);await call('/v1/positions/'+id,'GET',undefined,a,404);
 } finally {await app.close();await db.close();await rm(root,{recursive:true,force:true});}
});

test('P3 → P4 additive migration retains stable catalog IDs and rebuilds searchable projection',async()=>{
 const {cp,readFile,writeFile}=await import('node:fs/promises');
 const {fixtures}=await import('./fixtures.js');
 const db=await testDatabase(),directory=await mkdtemp(join(tmpdir(),'krapiva-upgrade-'));
 try {
  for(const name of ['001_foundation.sql','002_request_budgets.sql','003_accounts.sql']) await cp('migrations/'+name,join(directory,name));
  await migrate(db.pool,directory);const old=await fixtures(db.pool);
  const before=(await db.pool.query('SELECT * FROM beer_positions WHERE id=$1',[old.position])).rows[0];
  await writeFile(join(directory,'004_catalog.sql'),await readFile('migrations/004_catalog.sql'));
  await migrate(db.pool,directory);
  await cp('migrations/005_catalog_batch_projection.sql',join(directory,'005_catalog_batch_projection.sql'));
  await migrate(db.pool,directory);await migrate(db.pool,directory);
  const after=(await db.pool.query('SELECT * FROM beer_positions WHERE id=$1',[old.position])).rows[0];
  for(const key of Object.keys(before)) assert.deepEqual(after[key],before[key]);
  assert.equal((await db.pool.query('SELECT position_id FROM catalog_search')).rows[0].position_id,old.position);
 } finally {await db.close();await rm(directory,{recursive:true,force:true});}
});


test('cursor calendar validation rejects normalized invalid dates before SQL',async()=>{
 const {decodeCursor,validCursorTimestamp}=await import('../src/http/pagination.js');
 assert.equal(validCursorTimestamp('2024-02-29T12:30:01.123456Z'),true);
 for(const timestamp of ['2026-02-31T00:00:00.000Z','2026-02-29T00:00:00.000Z','0000-01-01T00:00:00.000Z','2026-01-01T24:00:00.000Z']) {
  assert.equal(validCursorTimestamp(timestamp),false);
  assert.throws(()=>decodeCursor(Buffer.from(JSON.stringify([timestamp,'11111111-1111-1111-1111-111111111111'])).toString('base64url')));
 }
});
