import type pg from 'pg';
import {transaction} from '../db/pool.js';
import {ApiError} from '../http/errors.js';
import {profile} from '../profiles/repository.js';
type DB=pg.Pool|pg.PoolClient;
export async function reconcile(db:DB,user:string) {await db.query('SELECT reconcile_badges($1)',[user]);}
const columns=`r.id,r.metric,r.target,r.title,r.description,r.icon_key AS "iconKey",r.version,
 b.progress,b.earned_at AS "earnedAt",p.slot AS "pinSlot"`;
async function rows(db:DB,user:string,pinned=false) {
 return {items:(await db.query(`SELECT ${columns} FROM badge_rules r JOIN user_badges b ON b.rule_id=r.id AND b.user_id=$1
 LEFT JOIN badge_pins p ON p.user_id=b.user_id AND p.rule_id=b.rule_id WHERE r.active ${pinned?'AND p.slot IS NOT NULL AND b.earned_at IS NOT NULL':''}
 ORDER BY p.slot NULLS LAST,r.id`,[user])).rows};
}
export async function badges(pool:pg.Pool,user:string,viewer=user) {
 if(user!==viewer)await profile(pool,user,viewer);
 return transaction(pool,async db=>{await reconcile(db,user);return rows(db,user,user!==viewer);});
}
export async function pin(pool:pg.Pool,user:string,ids:string[]) {
 return transaction(pool,async db=>{
  await reconcile(db,user);
  const selected=await db.query('SELECT b.rule_id FROM user_badges b JOIN badge_rules r ON r.id=b.rule_id WHERE b.user_id=$1 AND b.rule_id=ANY($2::uuid[]) AND b.earned_at IS NOT NULL AND r.active',[user,ids]);
  if(ids.length>3||new Set(ids.map(x=>x.toLowerCase())).size!==ids.length||selected.rowCount!==ids.length)throw new ApiError(422,'INVALID_PINS','Можно закрепить не более трёх полученных значков');
  await db.query('DELETE FROM badge_pins WHERE user_id=$1',[user]);
  for(const [i,id]of ids.entries())await db.query('INSERT INTO badge_pins(user_id,rule_id,slot)VALUES($1,$2,$3)',[user,id,i+1]);
  return rows(db,user);
 });
}
