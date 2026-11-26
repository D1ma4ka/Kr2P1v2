import assert from 'node:assert/strict';
import {createHash, randomUUID} from 'node:crypto';
import {test} from 'node:test';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {testDatabase} from './database.js';
import {fixtures} from './fixtures.js';

test('KraPet real PostgreSQL: account isolation, verified publication, wallet, concurrent purchases and partner rewards',async()=>{
  const db=await testDatabase();
  const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
  try {
    await migrate(db.pool);
    const f=await fixtures(db.pool);
    const call=(path:string,token:string,method='GET',body?:unknown,key?:string)=>app.inject({
      url:path,method:method as 'GET',headers:{authorization:'Bearer '+token,...(key?{'idempotency-key':key}:{})},
      ...(body===undefined?{}:{payload:JSON.stringify(body),headers:{authorization:'Bearer '+token,
        'content-type':'application/json',...(key?{'idempotency-key':key}:{})}})
    });
    const pet=(path:string,method='GET',body?:unknown,token=f.a.token)=>call('/v1/pets/'+path,token,method,body);
    const balance=async()=>(await pet('state')).json().balance;
    const entry={positionId:f.position,ratingQuarters:null,body:'',visibility:'private',occurredAt:'2026-11-01T00:00:00Z'};
    const checkin=(key:string,body=entry)=>call('/v1/checkins',f.a.token,'POST',body,key);
    assert.equal((await pet('definitions')).json().items.length,5);
    assert.equal((await checkin(randomUUID())).statusCode,201);
    assert.equal(await balance(),null);

    const starter={definitionId:'starter_hop',name:'Финик'};
    const competing=await Promise.all([pet('adopt','POST',starter),pet('adopt','POST',{...starter,name:'Лист'})]);
    assert.deepEqual(competing.map(r=>r.statusCode).sort(),[200,409]);
    const selected=competing.find(r=>r.statusCode===200)!.json().pet;
    assert.equal((await pet('adopt','POST',{definitionId:selected.definitionId,name:selected.name})).json().pet.id,selected.id);
    assert.equal((await pet('active','PUT',{petId:selected.id},f.b.token)).statusCode,404);
    assert.equal(await balance(),0);
    assert.equal((await checkin(randomUUID(),{...entry,positionId:randomUUID()})).statusCode,422);
    const key=randomUUID();
    const published=await checkin(key);
    assert.equal(published.statusCode,201);
    assert.equal((await checkin(key)).json().id,published.json().id);
    const {positionId:_,...edit}=entry;
    assert.equal((await call('/v1/checkins/'+published.json().id,f.a.token,'PATCH',{...edit,body:'changed',version:1})).statusCode,200);
    assert.equal(await balance(),2);

    await db.pool.query(`INSERT INTO kra_item_definitions(id,name,kind,category,rarity,price,slots,render)
      VALUES('pretzel','Крендель','food','snack','common',1,'{}','{}'),
      ('glasses','Очки','wear','glasses','common',1,'{face}','{}'),
      ('reward_plant','Растение','decor','plant','exclusive',NULL,'{shelf_slot_01}','{}')`);
    assert.equal((await pet('purchase','POST',{definitionId:'pretzel',requestId:randomUUID(),price:0})).statusCode,400);
    const buy=(id:string='pretzel',requestId:string=randomUUID())=>pet('purchase','POST',{definitionId:id,requestId});
    const buyKey=randomUUID();
    const results=await Promise.all([buy('pretzel',buyKey),buy('glasses'),buy(),buy()]);
    assert.deepEqual(results.map(r=>r.statusCode).sort(),[200,200,409,409]);
    assert.equal(await balance(),0);
    if(results[0]!.statusCode===200) {
      const itemId=results[0]!.json().itemId;
      assert.equal((await buy('pretzel',buyKey)).json().itemId,itemId);
      const requestId=randomUUID();
      assert.equal((await pet('consume','POST',{itemId,requestId})).statusCode,200);
      assert.equal((await pet('consume','POST',{itemId,requestId})).statusCode,200);
    }

    const qr='krapiva:reward:synthetic-test-token-one';
    await db.pool.query(`INSERT INTO kra_partner_rewards VALUES
      ('reward_1','test','event','campaign','reward_plant',$1,now()-interval '1 minute',now()+interval '1 hour',1,true,true)`,
      [createHash('sha256').update(qr).digest('hex')]);
    const reward=(await pet('redeem','POST',{code:qr})).json();
    assert.equal(reward.replayed,false);
    assert.equal((await pet('redeem','POST',{code:qr})).json().itemId,reward.itemId);
    assert.equal((await pet('decor/shelf_slot_01','PUT',{itemId:reward.itemId})).json().decor.shelf_slot_01,reward.itemId);
    assert.equal((await pet('decor/shelf_slot_02','PUT',{itemId:reward.itemId})).statusCode,409);
    assert.equal((await pet('adopt','POST',{definitionId:'starter_tap',name:'Краник'},f.b.token)).statusCode,200);
    assert.equal((await pet('redeem','POST',{code:qr},f.b.token)).statusCode,409);
    assert.equal((await pet('decor/shelf_slot_01','PUT',{itemId:reward.itemId},f.b.token)).statusCode,404);
    assert.equal((await pet('wallet')).json().items.filter((e:{reason:string})=>e.reason==='checkin').length,1);

    await db.pool.query("UPDATE kra_wallet_entries SET created_at=now()-interval '1 day' WHERE user_id=$1 AND reason='checkin'",[f.a.id]);
    await db.pool.query("INSERT INTO kra_economy(version,reward,opening_balance,daily_limit,removal_policy) VALUES(2,2,0,3,'recover_available')");
    const first=(await checkin(randomUUID())).json();
    await checkin(randomUUID());
    assert.equal(await balance(),3);
    await db.pool.query('UPDATE checkins SET hidden_at=now() WHERE id=$1',[first.id]);
    assert.equal(await balance(),1);
    await db.pool.query('UPDATE checkins SET hidden_at=NULL WHERE id=$1',[first.id]);
    await db.pool.query('UPDATE checkins SET hidden_at=now() WHERE id=$1',[first.id]);
    assert.equal(await balance(),1);
  } finally { await app.close(); await db.close(); }
});

test('KraPet content import serves checked WebP and supports audited adjustments',async()=>{
  const {importPetContent,createTestReward}=await import('../src/operations/pet-content.js');
  const db=await testDatabase();
  const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
  try {
    await migrate(db.pool);
    const f=await fixtures(db.pool);
    const manifest='../assets/pets/items/catalog.json';
    assert.equal(await importPetContent(db.pool,manifest),51);
    assert.equal(await importPetContent(db.pool,manifest),51);
    const call=(path:string,token=f.a.token,body?:unknown)=>app.inject({url:'/v1/pets/'+path,
      method:body?'POST':'GET',headers:{authorization:'Bearer '+token,...(body?{'content-type':'application/json'}:{})},
      ...(body?{payload:JSON.stringify(body)}:{})});
    const {items}=(await call('catalog')).json();
    for(const [kind,count] of [['wear',25],['food',10],['decor',15]]) {
      assert.equal(items.filter((r:{kind:string;forSale:boolean})=>r.kind===kind && r.forSale).length,count);
    }
    const art=await call('art/'+items[0].assetHash);
    assert.equal(art.headers['content-type'],'image/webp');
    assert.equal(createHash('sha256').update(art.rawPayload).digest('hex'),items[0].assetHash);
    for(const [index,definitionId] of ['starter_hop','starter_cap','starter_bottle','starter_tap'].entries()) {
      const temporary=await fixtures(db.pool,`starter${index}`);
      const picked=await call('adopt',temporary.a.token,{definitionId,name:'Тест'});
      assert.equal(picked.json().pet.definitionId,definitionId);
    }
    assert.equal((await call('adopt',f.a.token,{definitionId:'starter_nettle',name:'Мята'})).statusCode,200);
    assert.equal((await call('animations')).json().items.length,21);
    for(let i=0;i<2;i++) assert.equal((await call('visit',f.a.token,{view:'room'})).statusCode,204);
    await db.pool.query("UPDATE kra_rooms SET last_visited_at=now()-interval '1 hour' WHERE user_id=$1",[f.a.id]);
    assert.equal((await call('visit',f.a.token,{view:'room'})).statusCode,204);
    assert.equal((await call('visit',f.a.token,{view:'wardrobe'})).statusCode,204);
    assert.deepEqual((await db.pool.query(`SELECT event,count::integer FROM kra_metrics
      WHERE definition_id='starter_nettle' ORDER BY event`)).rows,[
      {event:'adopt',count:1},{event:'first_visit',count:1},{event:'return_visit',count:1},{event:'wardrobe',count:1}
    ]);
    const adjust={userId:f.a.id,requestId:randomUUID(),delta:25,reason:'Synthetic adjustment'};
    assert.equal((await call('admin/adjust',f.b.token,adjust)).statusCode,403);
    await db.pool.query("UPDATE users SET role='admin' WHERE id=$1",[f.b.id]);
    assert.equal((await call('admin/adjust',f.b.token,adjust)).json().balance,25);
    assert.equal((await call('admin/adjust',f.b.token,adjust)).json().balance,25);
    assert.equal((await call('admin/adjust',f.b.token,{...adjust,delta:1})).statusCode,409);
    const reward=await createTestReward(db.pool);
    const accepted=await call('redeem',f.a.token,{code:reward});
    assert.equal(accepted.statusCode,200);
    assert.equal((await call('redeem',f.a.token,{code:reward})).json().replayed,true);
  } finally { await app.close(); await db.close(); }
});
