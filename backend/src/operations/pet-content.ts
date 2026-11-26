import {createHash,randomBytes} from 'node:crypto';
import {readFile,realpath,stat} from 'node:fs/promises';
import {dirname,resolve,sep} from 'node:path';
import {pathToFileURL} from 'node:url';
import type pg from 'pg';
import sharp from 'sharp';
import {createPool,transaction} from '../db/pool.js';
import {wearSlots,decorSlots} from '../pets/repository.js';

const idPattern=/^[a-z][a-z0-9_]{2,63}$/;
const rarities=['common','uncommon','rare','epic','legendary','exclusive'];
type Item={
  id:string; name:string; kind:'wear'|'food'|'decor'; category:string; rarity:string; price:number|null;
  slots:string[]; compatiblePets:string[]; render:Record<string,number>; assetVersion:number; enabled?:boolean;
  asset:string; sha256:string; transferable?:boolean; series?:string; partnerId?:string;
  availableFrom?:string; availableUntil?:string;
};
type Package={version:number;items:Item[]};

export async function importPetContent(pool:pg.Pool,manifest:string) {
  const path=await realpath(manifest);
  const root=dirname(path);
  const document=JSON.parse(await readFile(path,'utf8')) as Package;
  if(document.version!==1 || !Array.isArray(document.items) || !document.items.length || document.items.length>1000)
    throw new Error('Invalid catalog version or item count');
  const knownPets=new Set((await pool.query<{id:string}>('SELECT id FROM kra_pet_definitions')).rows.map(row=>row.id));
  const ids=new Set<string>();
  const checked:{item:Item;bytes:Buffer}[]=[];
  for(const item of document.items) {
    if(!item || typeof item.id!=='string' || !idPattern.test(item.id) || ids.has(item.id))
      throw new Error('Invalid or duplicate item ID');
    ids.add(item.id);
    const allowed=item.kind==='wear'?wearSlots:item.kind==='decor'?decorSlots:[];
    const renderRules:Record<string,[number,number]>={displayWidth:[20,850],originX:[0,1],originY:[0,1],
      scale:[.15,3],dx:[-250,250],dy:[-250,250],rotation:[-180,180]};
    if(typeof item.name!=='string' || !item.name.trim() || item.name.length>80 ||
      !['wear','food','decor'].includes(item.kind) || !idPattern.test(item.category) || !rarities.includes(item.rarity) ||
      (item.price!==null && (!Number.isInteger(item.price) || item.price<0 || item.price>100000)) ||
      !Array.isArray(item.slots) || new Set(item.slots).size!==item.slots.length || item.slots.some(slot=>!allowed.includes(slot)) ||
      (item.kind!=='food' && !item.slots.length) ||
      !Array.isArray(item.compatiblePets) || item.compatiblePets.some(pet=>!knownPets.has(pet)) ||
      !Number.isSafeInteger(item.assetVersion) || item.assetVersion<1 || item.assetVersion>2147483647 ||
      !item.render || typeof item.render!=='object' || Array.isArray(item.render) ||
      Object.entries(item.render).some(([key,value])=>{
        const rule=renderRules[key];return !rule || !Number.isFinite(value) || value<rule[0] || value>rule[1];
      }) ||
      (item.enabled!==undefined && typeof item.enabled!=='boolean') ||
      (item.transferable!==undefined && typeof item.transferable!=='boolean') ||
      (item.series!==undefined && (typeof item.series!=='string' || item.series.length>80)) ||
      (item.partnerId!==undefined && !idPattern.test(item.partnerId)) ||
      [item.availableFrom,item.availableUntil].some(value=>value!==undefined &&
        (typeof value!=='string' || !value.endsWith('Z') || !Number.isFinite(Date.parse(value)))) ||
      (item.availableFrom && item.availableUntil && Date.parse(item.availableFrom)>=Date.parse(item.availableUntil)) ||
      typeof item.asset!=='string' || !/^art\/[a-z0-9_]+\.webp$/.test(item.asset) ||
      typeof item.sha256!=='string' || !/^[0-9a-f]{64}$/.test(item.sha256))
      throw new Error(`Invalid metadata: ${item.id}`);
    const file=await realpath(resolve(root,item.asset));
    if(!file.startsWith(root+sep) || (await stat(file)).size>1048576) throw new Error(`Invalid asset: ${item.id}`);
    const bytes=await readFile(file);
    const metadata=await sharp(bytes,{limitInputPixels:1024*1024}).metadata();
    if(metadata.format!=='webp' || (metadata.pages??1)!==1 || (metadata.width??0)>1024 || (metadata.height??0)>1024 ||
      createHash('sha256').update(bytes).digest('hex')!==item.sha256)
      throw new Error(`Asset verification failed: ${item.id}`);
    await sharp(bytes,{limitInputPixels:1024*1024}).raw().toBuffer();
    checked.push({item,bytes});
  }
  await transaction(pool,async db=>{
    await db.query('SELECT pg_advisory_xact_lock(220170)');
    for(const {item,bytes} of checked) {
      const previous=(await db.query<{asset_hash:string;asset_version:number}>(
        'SELECT asset_hash,asset_version FROM kra_item_definitions WHERE id=$1',[item.id])).rows[0];
      if(previous && (item.assetVersion<previous.asset_version ||
        (item.sha256!==previous.asset_hash && item.assetVersion===previous.asset_version)))
        throw new Error(`Increment assetVersion: ${item.id}`);
      await db.query('INSERT INTO kra_art VALUES($1,$2) ON CONFLICT DO NOTHING',[item.sha256,bytes]);
      await db.query(`INSERT INTO kra_item_definitions(id,name,kind,category,rarity,price,slots,compatible_pets,render,
        asset_version,enabled,transferable,series,partner_id,available_from,available_until,asset_hash)
        VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17)
        ON CONFLICT(id) DO UPDATE SET name=excluded.name,category=excluded.category,rarity=excluded.rarity,price=excluded.price,
          slots=excluded.slots,compatible_pets=excluded.compatible_pets,render=excluded.render,asset_version=excluded.asset_version,
          enabled=excluded.enabled,transferable=excluded.transferable,series=excluded.series,partner_id=excluded.partner_id,
          available_from=excluded.available_from,available_until=excluded.available_until,asset_hash=excluded.asset_hash
        WHERE kra_item_definitions.kind=excluded.kind
        RETURNING id`,[item.id,item.name,item.kind,item.category,item.rarity,item.price,item.slots,item.compatiblePets,item.render,
        item.assetVersion,item.enabled??true,item.transferable??false,item.series,item.partnerId,item.availableFrom,item.availableUntil,item.sha256])
        .then(result=>{if(!result.rowCount) throw new Error(`Cannot change item kind: ${item.id}`);});
    }
  });
  return checked.length;
}

export async function createTestReward(pool:pg.Pool) {
  const token=randomBytes(24).toString('base64url');
  const code='krapiva:reward:'+token;
  await pool.query(`INSERT INTO kra_partner_rewards(id,partner_id,event_id,campaign_id,item_id,code_hash,
    starts_at,ends_at,supply,test_only,enabled) VALUES($1,'local_partner','local_event','local_campaign','partner_badge',
    $2,now(),now()+interval '1 day',10,true,true)`,['local_'+token.slice(0,12),createHash('sha256').update(code).digest('hex')]);
  return code;
}

if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href) {
  const url=process.env.MIGRATION_DATABASE_URL;
  const target=process.argv[2];
  if(!url || !target) throw new Error('Set MIGRATION_DATABASE_URL and pass a catalog path or --test-reward');
  const pool=createPool(url,1);
  try {
    if(target==='--test-reward') {
      if(process.env.NODE_ENV!=='development') throw new Error('Test QR requires the local development environment');
      const local=new URL(url);
      if(!['127.0.0.1','localhost','[::1]'].includes(local.hostname)) throw new Error('Test QR requires loopback database');
      console.log(await createTestReward(pool));
    } else console.log(`Imported ${await importPetContent(pool,target)} pet items`);
  } finally { await pool.end(); }
}
