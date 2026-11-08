import type pg from 'pg';
import {transaction} from '../db/pool.js';
import {ApiError,conflict,notFound} from '../http/errors.js';
const columns=`p.user_id AS "userId",u.display_name AS "displayName",p.first_name AS "firstName",p.last_name AS "lastName",
 p.country,p.city,p.bio,p.gender,p.gender_visibility AS "genderVisibility",p.profile_visibility AS "profileVisibility",
 p.avatar_asset_id AS "avatarAssetId",p.cover_asset_id AS "coverAssetId",p.version,p.updated_at AS "updatedAt"`;
export interface ProfileUpdate {firstName:string|null;lastName:string|null;country:string|null;city:string|null;bio:string|null;
 gender:string|null;genderVisibility:string;profileVisibility:string;version:number;displayName?:string;
 avatarAssetId?:string|null;coverAssetId?:string|null;links?:{url:string;visibility:string}[];}
export async function profile(pool:pg.Pool|pg.PoolClient,owner:string,viewer=owner) {
 const result=await pool.query(`SELECT ${columns}, EXISTS(SELECT 1 FROM friendships f
  WHERE f.user_low=least($1::uuid,$2::uuid) AND f.user_high=greatest($1::uuid,$2::uuid)) AS friends
  FROM user_profiles p JOIN users u ON u.id=p.user_id WHERE p.user_id=$1 AND u.status='active'`,[owner,viewer]);
 const row=result.rows[0];
 if(!row||!(viewer===owner||row.profileVisibility==='public'||row.profileVisibility==='friends'&&row.friends)) throw notFound();
 const allowed=(visibility:string)=>viewer===owner||visibility==='public'||visibility==='friends'&&row.friends;
 if(!allowed(row.genderVisibility)) row.gender=null;
 row.links=(await pool.query('SELECT url,visibility FROM profile_links WHERE user_id=$1 ORDER BY slot',[owner])).rows.filter(link=>allowed(link.visibility));
 delete row.friends;
 return row;
}
export async function updateProfile(pool:pg.Pool,owner:string,b:ProfileUpdate) {
 if(b.links) for(const link of b.links) {
  try {const url=new URL(link.url);if(url.protocol!=='https:'||url.username||url.password||!url.hostname.includes('.')||/[\s\\]/.test(link.url)) throw Error();}
  catch {throw new ApiError(422,'INVALID_URL','Укажите полный HTTPS-адрес без логина и пароля');}
 }
 return transaction(pool,async client=>{
  const old=(await client.query('SELECT * FROM user_profiles WHERE user_id=$1 FOR UPDATE',[owner])).rows[0];
  if(old.version!==b.version) throw conflict();
  const avatar=b.avatarAssetId===undefined?old.avatar_asset_id:b.avatarAssetId,cover=b.coverAssetId===undefined?old.cover_asset_id:b.coverAssetId;
  const ids=[...new Set([avatar,cover].filter(Boolean))];
  const assets=await client.query("SELECT id FROM media_assets WHERE id=ANY($1::uuid[]) AND owner_id=$2 AND deleted_at IS NULL AND moderation_status<>'hidden' ORDER BY id FOR SHARE",[ids,owner]);
  if(assets.rowCount!==ids.length) throw notFound();
  if(b.displayName!==undefined) await client.query('UPDATE users SET display_name=$2,updated_at=now() WHERE id=$1',[owner,b.displayName.trim()]);
  await client.query(`UPDATE user_profiles SET first_name=$2,last_name=$3,country=$4,city=$5,bio=$6,gender=$7,
   gender_visibility=$8,profile_visibility=$9,avatar_asset_id=$10,cover_asset_id=$11,version=version+1,updated_at=now() WHERE user_id=$1`,
   [owner,b.firstName,b.lastName,b.country,b.city,b.bio,b.gender,b.genderVisibility,b.profileVisibility,avatar,cover]);
  if(b.links) {
   await client.query('DELETE FROM profile_links WHERE user_id=$1',[owner]);
   for(const [index,link] of b.links.entries()) await client.query('INSERT INTO profile_links(user_id,slot,url,visibility) VALUES($1,$2,$3,$4)',[owner,index+1,link.url,link.visibility]);
  }
  return profile(client,owner);
 });
}
