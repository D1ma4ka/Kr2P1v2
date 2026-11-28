import type pg from 'pg';
import {transaction} from '../db/pool.js';
import {ApiError,conflict,notFound} from '../http/errors.js';
import {normalizeHandle,handleConflict} from '../identity/handles.js';
import {resolveLocation} from './locations.js';
import {linkForNetwork,networks,socialSlot,validateProfileLink,validateSocialLinks,
 type ProfileLink,type SocialLinks,type StoredLink} from './social-links.js';

const columns=`p.user_id AS "userId",u.display_name AS "displayName",u.handle,p.first_name AS "firstName",p.last_name AS "lastName",
 p.country,p.city,p.country_id AS "countryId",p.city_id AS "cityId",p.bio,p.gender,
 p.gender_visibility AS "genderVisibility",p.profile_visibility AS "profileVisibility",
 p.avatar_asset_id AS "avatarAssetId",p.cover_asset_id AS "coverAssetId",p.version,p.updated_at AS "updatedAt"`;
export interface ProfileUpdate {firstName:string|null;lastName:string|null;country:string|null;city:string|null;bio:string|null;
 gender:string|null;genderVisibility:string;profileVisibility:string;version:number;displayName?:string;handle?:string;
 avatarAssetId?:string|null;coverAssetId?:string|null;links?:ProfileLink[];
 countryId?:string|null;cityId?:number|null;socialLinks?:SocialLinks;}

export async function claimProfileHandle(db:pg.Pool|pg.PoolClient,owner:string,rawHandle:string) {
 const handle=normalizeHandle(rawHandle);
 const result=await db.query("UPDATE users SET handle=$2,updated_at=now() WHERE id=$1 AND (handle IS NULL OR handle=$2) RETURNING id",
  [owner,handle]).catch(handleConflict);
 if(!result.rowCount) throw new ApiError(409,'HANDLE_LOCKED','Никнейм уже выбран');
}

export async function profile(pool:pg.Pool|pg.PoolClient,owner:string,viewer=owner) {
 const result=await pool.query(`SELECT ${columns},
  coalesce((SELECT json_agg(json_build_object('slot',l.slot,'url',l.url,'visibility',l.visibility) ORDER BY l.slot)
    FROM profile_links l WHERE l.user_id=p.user_id),'[]') AS "storedLinks",
  EXISTS(SELECT 1 FROM friendships f
  WHERE f.user_low=least($1::uuid,$2::uuid) AND f.user_high=greatest($1::uuid,$2::uuid)) AS friends
  FROM user_profiles p JOIN users u ON u.id=p.user_id WHERE p.user_id=$1 AND u.status='active'`,[owner,viewer]);
 const row=result.rows[0];
 if(!row||!(viewer===owner||row.profileVisibility==='public'||row.profileVisibility==='friends'&&row.friends)) throw notFound();
 const allowed=(visibility:string)=>viewer===owner||visibility==='public'||visibility==='friends'&&row.friends;
 if(!allowed(row.genderVisibility)) row.gender=null;
 const storedLinks:StoredLink[]=row.storedLinks;
 row.links=storedLinks.filter(link=>link.slot<=10&&allowed(link.visibility)).map(({url,visibility})=>({url,visibility}));
 row.socialLinks=Object.fromEntries(networks.map(network=>{
  const link=linkForNetwork(storedLinks,network);
  return [network,link&&allowed(link.visibility)?{url:link.url,visibility:link.visibility}:null];
 }));
 delete row.storedLinks;
 delete row.friends;
 return row;
}
export async function updateProfile(pool:pg.Pool,owner:string,b:ProfileUpdate) {
 b.links?.forEach(validateProfileLink);
 if(b.socialLinks) validateSocialLinks(b.socialLinks);
 if([b.firstName,b.lastName,b.bio,b.gender,b.city,b.country,b.displayName].some(value=>value?.includes('\0')))
  throw new ApiError(422,'INVALID_PROFILE_TEXT','Проверьте текст в профиле');
 return transaction(pool,async client=>{
  const old=(await client.query('SELECT * FROM user_profiles WHERE user_id=$1 FOR UPDATE',[owner])).rows[0];
  if(!old) throw notFound();
  if(old.version!==b.version) throw conflict();
  const avatar=b.avatarAssetId===undefined?old.avatar_asset_id:b.avatarAssetId,cover=b.coverAssetId===undefined?old.cover_asset_id:b.coverAssetId;
  const ids=[...new Set([avatar,cover].filter(Boolean))];
  const assets=await client.query("SELECT id FROM media_assets WHERE id=ANY($1::uuid[]) AND owner_id=$2 AND deleted_at IS NULL AND moderation_status<>'hidden' ORDER BY id FOR SHARE",[ids,owner]);
  if(assets.rowCount!==ids.length) throw notFound();
  let country=b.country,city=b.city;
  let countryId:string|null=b.country===old.country?old.country_id:null;
  let cityId:number|null=b.city===old.city&&b.country===old.country?old.city_id:null;
  if(b.countryId!==undefined||b.cityId!==undefined) {
   countryId=b.countryId===undefined?old.country_id:b.countryId;
   cityId=b.cityId===undefined?(countryId===old.country_id?old.city_id:null):b.cityId;
   const location=await resolveLocation(countryId,cityId);
   country=location.country;
   city=location.city;
  }
  if(b.handle!==undefined) await claimProfileHandle(client,owner,b.handle);
  if(b.displayName!==undefined) await client.query('UPDATE users SET display_name=$2,updated_at=now() WHERE id=$1',[owner,b.displayName.trim()]);
  await client.query(`UPDATE user_profiles SET first_name=$2,last_name=$3,country=$4,city=$5,bio=$6,gender=$7,
   gender_visibility=$8,profile_visibility=$9,avatar_asset_id=$10,cover_asset_id=$11,country_id=$12,city_id=$13,
   version=version+1,updated_at=now() WHERE user_id=$1`,
   [owner,b.firstName,b.lastName,country,city,b.bio,b.gender,b.genderVisibility,b.profileVisibility,avatar,cover,countryId,cityId]);
  if(b.links) {
   await client.query('DELETE FROM profile_links WHERE user_id=$1 AND slot<=10',[owner]);
   for(const [index,link] of b.links.entries()) await client.query('INSERT INTO profile_links(user_id,slot,url,visibility) VALUES($1,$2,$3,$4)',[owner,index+1,link.url,link.visibility]);
  }
  if(b.socialLinks) {
   const stored=(await client.query<StoredLink>('SELECT slot,url,visibility FROM profile_links WHERE user_id=$1 ORDER BY slot',[owner])).rows;
   for(const network of networks) {
    const link=b.socialLinks[network];
    if(link===undefined) continue;
    const existing=linkForNetwork(stored,network);
    if(link===null) {
     if(existing) await client.query('DELETE FROM profile_links WHERE user_id=$1 AND slot=$2',[owner,existing.slot]);
    } else {
     await client.query(`INSERT INTO profile_links(user_id,slot,url,visibility) VALUES($1,$2,$3,$4)
      ON CONFLICT(user_id,slot) DO UPDATE SET url=excluded.url,visibility=excluded.visibility`,
      [owner,existing?.slot??socialSlot(network),link.url,link.visibility]);
    }
   }
  }
  return profile(client,owner);
 });
}
