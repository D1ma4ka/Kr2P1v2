import assert from 'node:assert/strict';
import {test} from 'node:test';
import {cp,mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {migrate} from '../src/db/migrate.js';
import {testDatabase} from './database.js';
import {fixtures} from './fixtures.js';

test('P10.2 profile: Unicode, GeoNames choices, four networks, privacy and upgrade from migration 019',async()=>{
 const db=await testDatabase();
 const migrations=await mkdtemp(join(tmpdir(),'krapiva-profile-'));
 const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),db.pool);
 try {
  await cp('migrations',migrations,{recursive:true,filter:path=>!path.endsWith('020_profile_locations_and_links.sql')});
  await migrate(db.pool,migrations);
  const {a,b}=await fixtures(db.pool);
  const telegram={url:'https://t.me/kra_profile',visibility:'private'};
  const legacy={url:'https://example.invalid/keep',visibility:'public'};
  await db.pool.query("UPDATE user_profiles SET country='Россия',city='Тверь',gender='Женский',profile_visibility='public' WHERE user_id=$1",[a.id]);
  await db.pool.query('INSERT INTO profile_links VALUES($1,1,$2,$3),($1,2,$4,$5)',[a.id,telegram.url,telegram.visibility,legacy.url,legacy.visibility]);
  await db.pool.query("INSERT INTO profile_links SELECT $1,slot,'https://example.invalid/keep/'||slot,'private' FROM generate_series(3,10) slot",[a.id]);
  await migrate(db.pool);

  const call=(url:string,token=a.token,body?:unknown)=>app.inject({url,method:body===undefined?'GET':'PATCH',
   headers:{authorization:'Bearer '+token},...(body===undefined?{}:{payload:JSON.stringify(body),headers:{authorization:'Bearer '+token,'content-type':'application/json'}})});
  const me=await call('/v1/me');
  assert.equal(me.statusCode,200);
  const before=me.json();
  assert.equal(before.city,'Тверь');
  assert.equal(before.countryId,null);
  assert.equal(before.cityId,null);
  assert.deepEqual(before.links.slice(0,2),[telegram,legacy]);
  assert.equal(before.links.length,10);
  assert.deepEqual(before.socialLinks.telegram,telegram);
  const peer=async()=>(await call('/v1/users/'+a.id,b.token));
  assert.equal((await peer()).json().gender,null);
  assert.equal((await peer()).json().socialLinks.telegram,null);

  const places='/v1/me/profile/locations/';
  for(const query of ['Рос','Russia','Rossiya']) {
   const response=await call(places+'countries?q='+encodeURIComponent(query));
   assert.deepEqual(response.json().items[0],{id:'RU',name:'Russia'});
  }
  for(const [query,id,name] of [
   ['Мос',524901,'Moscow'],['Moskva',524901,'Moscow'],['Санкт',498817,'Saint Petersburg'],
   ['Sankt Peterburg',498817,'Saint Petersburg'],
  ] as const) {
   const response=await call(places+'cities?countryId=RU&q='+encodeURIComponent(query));
   assert.equal(response.statusCode,200);
   assert.deepEqual(response.json().items[0],{id,name,countryId:'RU',regionCode:id===524901?'48':'66'});
  }
  const america=await call(places+'cities?countryId=US&q=Moscow');
  assert.ok(america.json().items.every((city:{id:number;countryId:string})=>city.countryId==='US'&&city.id!==524901));

  const base={firstName:'Дарья 李',lastName:'Стрельцова',bio:'Крафт 🍻\nمرحبا',gender:'Женский',
   genderVisibility:'private',profileVisibility:'public',country:'Russia',city:'Moscow',
   countryId:'RU',cityId:524901,version:before.version};
  const video={url:'https://www.youtube.com/@kra_profile',visibility:'public'};
  const vk={url:'https://vk.com/kra_profile',visibility:'friends'};
  const untappd={url:'https://untappd.com/user/kra_profile',visibility:'public'};
  assert.equal((await call('/v1/me/profile',a.token,{...base,handle:'кириллица'})).statusCode,422);
  assert.equal((await call('/v1/me/profile',a.token,{...base,countryId:'US'})).statusCode,422);
  assert.equal((await call('/v1/me/profile',a.token,{...base,socialLinks:{vk:video}})).statusCode,422);
  const saved=await call('/v1/me/profile',a.token,{...base,handle:'kra_unicode',socialLinks:{youtube:video,vk,untappd}});
  assert.equal(saved.statusCode,200,saved.body);
  const after=saved.json();
  assert.equal(after.handle,'kra_unicode');
  assert.equal(after.firstName,base.firstName);
  assert.equal(after.lastName,base.lastName);
  assert.equal(after.bio,base.bio);
  assert.equal(after.countryId,'RU');
  assert.equal(after.cityId,524901);
  assert.deepEqual(after.links,before.links);
  assert.deepEqual(after.socialLinks,{telegram,youtube:video,vk,untappd});
  const visible=(await peer()).json();
  assert.equal(visible.firstName,base.firstName);
  assert.equal(visible.gender,null);
  assert.deepEqual(visible.links,[legacy]);
  assert.deepEqual(visible.socialLinks,{telegram:null,youtube:video,vk:null,untappd});
  // An old client can write its original payload without clearing IDs or typed links.
  const {countryId:_countryId,cityId:_cityId,...oldPayload}=base;
  const oldWrite=await call('/v1/me/profile',a.token,{...oldPayload,profileVisibility:'private',version:after.version,links:after.links});
  assert.equal(oldWrite.statusCode,200);
  assert.equal(oldWrite.json().cityId,524901);
  assert.deepEqual(oldWrite.json().socialLinks,after.socialLinks);
  assert.equal((await peer()).statusCode,404);
 } finally {
  await app.close();
  await db.close();
  await rm(migrations,{recursive:true,force:true});
 }
});
