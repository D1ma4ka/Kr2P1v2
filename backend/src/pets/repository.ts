import {createHash} from 'node:crypto';
import type pg from 'pg';
import {transaction} from '../db/pool.js';
import {ApiError, conflict, notFound} from '../http/errors.js';

export const wearSlots = ['head','face','torso_base','torso_outer','neck','wrist','accessory','effect'];
export const decorSlots = ['shelf_slot_01','shelf_slot_02','shelf_slot_03','shelf_slot_04','wall_left',
  'wall_center','wall_right','table_top','floor_left','floor_right','lighting','wallpaper','flooring'];
type Economy = {version:number; reward:number; opening_balance:number; daily_limit:number|null; removal_policy:string};
type Definition = {id:string; kind:string; price:number|null; slots:string[]; compatible_pets:string[]; enabled:boolean};
type Queryable = pg.Pool | pg.PoolClient;
const missingPet = () => new ApiError(409,'PET_REQUIRED','Сначала выберите питомца');

async function config(db:Queryable) {
  return (await db.query<Economy>('SELECT * FROM kra_economy ORDER BY version DESC LIMIT 1')).rows[0]!;
}
async function lockAccount(db:pg.PoolClient,user:string) {
  await db.query('SELECT id FROM users WHERE id=$1 FOR NO KEY UPDATE',[user]);
}
async function active(db:Queryable,user:string) {
  return (await db.query<{id:string;definition_id:string;name:string}>(`SELECT p.id,p.definition_id,p.name
    FROM kra_rooms r JOIN kra_pets p ON p.id=r.active_pet_id WHERE r.user_id=$1`,[user])).rows[0];
}
async function requirePet(db:Queryable,user:string) {
  const pet=await active(db,user);
  if(!pet) throw missingPet();
  return pet;
}
async function wallet(db:pg.PoolClient,user:string) {
  const row=(await db.query<{balance:number}>('SELECT balance FROM kra_wallets WHERE user_id=$1 FOR UPDATE',[user])).rows[0];
  if(!row) throw missingPet();
  return row.balance;
}
async function entry(db:pg.PoolClient,user:string,reason:string,event:string,delta:number,version:number,actor?:string,note?:string) {
  const result=await db.query(`INSERT INTO kra_wallet_entries(user_id,reason,event_id,amount,config_version,actor_id,note)
    VALUES($1,$2,$3,$4,$5,$6,$7) ON CONFLICT(user_id,reason,event_id) DO NOTHING`,[user,reason,event,delta,version,actor,note]);
  if(result.rowCount) await db.query('UPDATE kra_wallets SET balance=balance+$2 WHERE user_id=$1',[user,delta]);
  return Boolean(result.rowCount);
}
export async function creditPublishedCheckin(db:pg.PoolClient,user:string,checkin:string) {
  await lockAccount(db,user);
  if(!await active(db,user)) return;
  await wallet(db,user);
  const settings=await config(db);
  let amount=settings.reward;
  if(settings.daily_limit!==null) {
    const {amount:today}=(await db.query<{amount:number}>(`SELECT coalesce(sum(amount),0)::integer AS amount
      FROM kra_wallet_entries WHERE user_id=$1 AND reason='checkin'
      AND created_at >= date_trunc('day', now(), 'UTC')`,[user])).rows[0]!;
    amount=Math.max(0,Math.min(amount,settings.daily_limit-today));
  }
  // Record a zero grant too, so a repeated event never benefits from a new config or day.
  await entry(db,user,'checkin',checkin,amount,settings.version);
}
export async function adopt(pool:pg.Pool,user:string,definition:string,name:string) {
  name=name.trim();
  if(!name || /[\p{Cc}\p{Cf}]/u.test(name)) throw new ApiError(400,'INVALID_PET_NAME','Проверьте имя питомца');
  await transaction(pool,async db=>{
    await lockAccount(db,user);
    const existing=(await db.query<{id:string;definition_id:string;name:string}>(
      "SELECT id,definition_id,name FROM kra_pets WHERE user_id=$1 AND origin='starter'",[user])).rows[0];
    if(existing) {
      if(existing.definition_id===definition && existing.name===name) return;
      throw conflict();
    }
    if(!(await db.query('SELECT id FROM kra_pet_definitions WHERE id=$1 AND starter AND enabled',[definition])).rowCount) throw notFound();
    const row=(await db.query<{id:string}>(`INSERT INTO kra_pets(user_id,definition_id,name,origin)
      VALUES($1,$2,$3,'starter') RETURNING id`,[user,definition,name])).rows[0]!;
    await db.query('INSERT INTO kra_rooms(user_id,active_pet_id) VALUES($1,$2)',[user,row.id]);
    await db.query('INSERT INTO kra_wallets(user_id) VALUES($1)',[user]);
    const settings=await config(db);
    await entry(db,user,'starter',row.id,settings.opening_balance,settings.version);
  });
}
export async function selectPet(pool:pg.Pool,user:string,id:string) {
  if(!(await pool.query(`UPDATE kra_rooms r SET active_pet_id=p.id
    FROM kra_pets p WHERE r.user_id=$1 AND p.user_id=r.user_id AND p.id=$2`,[user,id])).rowCount) throw notFound();
}
export async function petDefinitions(pool:pg.Pool) {
  return {items:(await pool.query(`SELECT id,name,personality,rarity,rig_version AS "rigVersion"
    FROM kra_pet_definitions WHERE starter AND enabled ORDER BY id`)).rows};
}
export async function state(pool:pg.Pool,user:string) {
  return (await pool.query(`SELECT
    (SELECT json_build_object('id',p.id,'definitionId',p.definition_id,'name',p.name)
     FROM kra_rooms r JOIN kra_pets p ON p.id=r.active_pet_id WHERE r.user_id=$1) AS pet,
    (SELECT coalesce(json_agg(json_build_object('id',id,'definitionId',definition_id,'name',name) ORDER BY acquired_at,id),'[]')
     FROM kra_pets WHERE user_id=$1) AS pets,
    (SELECT balance FROM kra_wallets WHERE user_id=$1) AS balance,
    (SELECT coalesce(json_agg(json_build_object('id',i.id,'definitionId',i.definition_id,'source',i.source) ORDER BY i.created_at,i.id),'[]')
     FROM kra_items i WHERE i.user_id=$1 AND i.consumed_at IS NULL) AS inventory,
    (SELECT coalesce(json_object_agg(e.slot,e.item_id),'{}') FROM kra_equipped e JOIN kra_rooms r ON r.active_pet_id=e.pet_id
     WHERE e.user_id=$1) AS equipped,
    (SELECT coalesce(json_object_agg(slot,item_id),'{}') FROM kra_room_decor WHERE user_id=$1) AS decor`,[user])).rows[0]!;
}
export async function catalog(pool:pg.Pool) {
  const {rows}=await pool.query(`SELECT id,name,kind,category,rarity,price,slots,render,
   compatible_pets AS "compatiblePets",asset_version AS "assetVersion",series,partner_id AS "partnerId",
   enabled AND (available_from IS NULL OR available_from<=now())
    AND (available_until IS NULL OR available_until>now()) AND price IS NOT NULL AS "forSale"
   FROM kra_item_definitions ORDER BY kind,price NULLS LAST,id`);
  return {items:rows};
}
export async function history(pool:pg.Pool,user:string,before?:string) {
  const {rows}=await pool.query(`SELECT id::text,reason,event_id AS "eventId",amount,
    config_version AS "configVersion",created_at AS "createdAt"
    FROM kra_wallet_entries WHERE user_id=$1 AND ($2::bigint IS NULL OR id<$2) ORDER BY id DESC LIMIT 51`,[user,before??null]);
  return {items:rows.slice(0,50),next:rows.length>50?rows[49]!.id:null};
}
async function oldAction(db:pg.PoolClient,user:string,key:string,operation:string,subject:string) {
  const old=(await db.query<{operation:string;subject:string;item_id:string}>(
    'SELECT operation,subject,item_id FROM kra_actions WHERE user_id=$1 AND request_id=$2',[user,key])).rows[0];
  if(old && (old.operation!==operation || old.subject!==subject)) throw conflict();
  return old?.item_id;
}
async function grantItem(db:pg.PoolClient,user:string,id:string,source:'purchase'|'partner') {
  const row=(await db.query<{id:string}>(`INSERT INTO kra_items(user_id,definition_id,source)
    VALUES($1,$2,$3) RETURNING id`,[user,id,source])).rows[0]!;
  await db.query('INSERT INTO kra_ownership(item_id,user_id,source) VALUES($1,$2,$3)',[row.id,user,source]);
  return row.id;
}
export async function purchase(pool:pg.Pool,user:string,key:string,definition:string) {
  return transaction(pool,async db=>{
    const balance=await wallet(db,user);
    const old=await oldAction(db,user,key,'purchase',definition);
    if(old) return {itemId:old};
    const row=(await db.query<Definition>(`SELECT * FROM kra_item_definitions WHERE id=$1
      AND enabled AND price IS NOT NULL AND (available_from IS NULL OR available_from<=now())
      AND (available_until IS NULL OR available_until>now())`,[definition])).rows[0];
    if(!row || row.price===null) throw notFound();
    if(balance<row.price) throw new ApiError(409,'INSUFFICIENT_KC','Недостаточно KrapiCoin');
    if(row.kind==='wear') {
      const {definition_id}=await requirePet(db,user);
      if(row.compatible_pets.length && !row.compatible_pets.includes(definition_id))
        throw new ApiError(409,'INCOMPATIBLE_ITEM','Предмет не подходит питомцу');
    }
    if(row.kind!=='food' && (await db.query(
      'SELECT id FROM kra_items WHERE user_id=$1 AND definition_id=$2 AND consumed_at IS NULL',[user,definition])).rowCount)
      throw new ApiError(409,'ITEM_OWNED','Предмет уже в коллекции');
    const itemId=await grantItem(db,user,definition,'purchase');
    await entry(db,user,'purchase',key,-row.price,(await config(db)).version);
    await db.query("INSERT INTO kra_actions VALUES($1,$2,'purchase',$3,$4)",[user,key,definition,itemId]);
    return {itemId};
  });
}
export async function consume(pool:pg.Pool,user:string,key:string,itemId:string) {
  return transaction(pool,async db=>{
    await wallet(db,user);
    const old=await oldAction(db,user,key,'consume',itemId);
    if(old) return {itemId:old};
    const {rowCount}=await db.query(`UPDATE kra_items i SET consumed_at=now() FROM kra_item_definitions d
      WHERE i.id=$2 AND i.user_id=$1 AND i.consumed_at IS NULL AND d.id=i.definition_id AND d.kind='food'`,[user,itemId]);
    if(!rowCount) throw notFound();
    await db.query("INSERT INTO kra_actions VALUES($1,$2,'consume',$3,$4)",[user,key,itemId,itemId]);
    return {itemId};
  });
}
export async function arrange(pool:pg.Pool,user:string,kind:'wear'|'decor',slot:string,item:string|null) {
  if(!(kind==='wear'?wearSlots:decorSlots).includes(slot)) throw notFound();
  await transaction(pool,async db=>{
    await wallet(db,user);
    const pet=await requirePet(db,user);
    if(item) {
      const row=(await db.query<Definition>(`SELECT d.* FROM kra_items i JOIN kra_item_definitions d ON d.id=i.definition_id
        WHERE i.user_id=$1 AND i.id=$2 AND i.consumed_at IS NULL AND d.enabled`,[user,item])).rows[0];
      if(!row) throw notFound();
      if(row.kind!==kind || !row.slots.includes(slot) || (row.compatible_pets.length && !row.compatible_pets.includes(pet.definition_id)))
        throw new ApiError(409,'INCOMPATIBLE_ITEM','Предмет несовместим с этим местом');
    }
    if(kind==='wear') {
      await db.query('DELETE FROM kra_equipped WHERE user_id=$1 AND ((pet_id=$2 AND slot=$3) OR item_id=$4)',[user,pet.id,slot,item]);
      if(item) await db.query('INSERT INTO kra_equipped(user_id,pet_id,slot,item_id) VALUES($1,$2,$3,$4)',[user,pet.id,slot,item]);
    } else {
      await db.query('DELETE FROM kra_room_decor WHERE user_id=$1 AND (slot=$2 OR item_id=$3)',[user,slot,item]);
      if(item) await db.query('INSERT INTO kra_room_decor(user_id,slot,item_id) VALUES($1,$2,$3)',[user,slot,item]);
    }
  });
}
export async function redeem(pool:pg.Pool,user:string,code:string,allowTest:boolean) {
  const hash=createHash('sha256').update(code).digest('hex');
  return transaction(pool,async db=>{
    await wallet(db,user);
    await db.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',[`kra-reward:${hash}`]);
    const row=(await db.query<{id:string;item_id:string;supply:number;active:boolean}>(`
      SELECT r.id,r.item_id,r.supply,r.starts_at<=now() AND r.ends_at>now() AND d.enabled AS active
      FROM kra_partner_rewards r JOIN kra_item_definitions d ON d.id=r.item_id
      WHERE r.code_hash=$1 AND r.enabled AND (NOT r.test_only OR $2)`,[hash,allowTest])).rows[0];
    if(!row) throw notFound();
    const old=(await db.query<{item_id:string}>('SELECT item_id FROM kra_redemptions WHERE reward_id=$1 AND user_id=$2',[row.id,user])).rows[0];
    if(old) return {itemId:old.item_id,replayed:true};
    const sold=(await db.query<{count:number}>('SELECT count(*)::integer AS count FROM kra_redemptions WHERE reward_id=$1',[row.id])).rows[0]!.count;
    if(!row.active || sold>=row.supply) throw new ApiError(409,'REWARD_UNAVAILABLE','Награда недоступна');
    const itemId=await grantItem(db,user,row.item_id,'partner');
    await db.query('INSERT INTO kra_redemptions(reward_id,user_id,item_id) VALUES($1,$2,$3)',[row.id,user,itemId]);
    return {itemId,replayed:false};
  });
}
