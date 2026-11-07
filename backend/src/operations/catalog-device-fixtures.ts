// Explicit test/dev fixture preparation, never called by server/migrations.
import {randomUUID} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import {createPool} from '../db/pool.js';
import {hashPassword} from '../identity/passwords.js';
import {propose,decide} from '../catalog/proposals.js';
if(process.env.NODE_ENV!=='development'||!process.env.DATABASE_URL||new URL(process.env.DATABASE_URL).hostname!=='127.0.0.1') throw Error('Local Android synthetic fixtures only');
const db=createPool(process.env.DATABASE_URL);
try {
 const password='Synthetic Android P4 password!';const email=`p4${randomUUID()}@example.invalid`;
 const owner=(await db.query(`INSERT INTO users(email,display_name,password_hash,adult_confirmed_at,email_verified_at) VALUES($1,'Synthetic catalog Android',$2,now(),now()) RETURNING id`,[email,await hashPassword(password)])).rows[0].id as string;
 await db.query('INSERT INTO user_profiles(user_id) VALUES($1)',[owner]);
 const mod=(await db.query("INSERT INTO users(email,display_name,adult_confirmed_at,email_verified_at,role) VALUES($1,'Synthetic catalog reviewer',now(),now(),'moderator') RETURNING id",[`p4mod${randomUUID()}@example.invalid`])).rows[0].id as string;
 const gtin='2900000000018';
 const proposal=await propose(db,owner,randomUUID(),{name:'Synthetic Android Тёмный Ёж',breweryName:'Synthetic Android Brewery',styleName:'Synthetic Android Stout',source:'Synthetic Android acceptance fixture',gtin,abv:6.5,packaging:'can',volumeMl:500});
 const accepted=await decide(db,mod,proposal.id,{version:1,action:'accept',reason:'Synthetic Android acceptance fixture'});
 const additional:string[]=[];
 for(const [name,styleName] of [['Synthetic Android Второе','Synthetic Android Stout'],['Synthetic Android Третье','Synthetic Android Pale']]) {
  const proposal=await propose(db,owner,randomUUID(),{name:name!,styleName:styleName!,breweryName:'Synthetic Android Brewery',source:'Synthetic Android acceptance fixture',packaging:'can',volumeMl:330});
  const result=await decide(db,mod,proposal.id,{version:1,action:'accept',reason:'Synthetic Android acceptance fixture',allowDuplicate:true});additional.push(result.positionId!);
 }
 await mkdir('../work',{recursive:true});
 await writeFile('../work/p4-android.json',JSON.stringify({email,password,gtin,positionId:accepted.positionId,secondPositionId:additional[0],thirdPositionId:additional[1]}),{flag:'wx',mode:0o600});
 console.log('Synthetic Android fixture prepared; credentials are only in ignored work/p4-android.json');
} finally {await db.end();}
