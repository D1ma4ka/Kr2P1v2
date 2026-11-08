import {randomUUID} from 'node:crypto';
import {access,readFile,writeFile} from 'node:fs/promises';
import {createPool} from '../backend/dist/src/db/pool.js';
import {hashPassword} from '../backend/dist/src/identity/passwords.js';
import {propose,decide} from '../backend/dist/src/catalog/proposals.js';
if(process.env.NODE_ENV!=='development'||new URL(process.env.DATABASE_URL).hostname!=='127.0.0.1') throw Error('Local development fixtures only');
if(!process.env.P61_FIXTURE_PHOTO) throw Error('Set P61_FIXTURE_PHOTO to a local test-only PNG');
try { await access('work/p61-android.json'); throw Error('Fixture already exists; reuse it'); } catch(e) { if(e.code!=='ENOENT') throw e; }
const photo=await readFile(process.env.P61_FIXTURE_PHOTO);
const db=createPool(process.env.DATABASE_URL);
const request=async(path,token,method='GET',body)=>{
 const r=await fetch('http://127.0.0.1:53000/'+path,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{}),'Idempotency-Key':randomUUID()},body:body?JSON.stringify(body):undefined});
 if(!r.ok)throw Error(`${path}: ${r.status} ${await r.text()}`);
 const text=await r.text();return text?JSON.parse(text):{};
};
try {
 const password='P61-'+randomUUID()+'!';
 async function user(name){
  const email='p61-'+randomUUID()+'@example.invalid';
  const id=(await db.query('INSERT INTO users(email,display_name,password_hash,adult_confirmed_at,email_verified_at) VALUES($1,$2,$3,now(),now()) RETURNING id',[email,name,await hashPassword(password)])).rows[0].id;
  await db.query('INSERT INTO user_profiles(user_id) VALUES($1)',[id]);
  const session=await request('v1/auth/login',null,'POST',{email,password});
  const p=await request('v1/me',session.accessToken);
  await request('v1/me/profile',session.accessToken,'PATCH',{version:p.version,firstName:name.split(' ')[0],lastName:null,country:null,city:null,bio:null,gender:null,profileVisibility:'public',genderVisibility:'private'});
  return {id,email,token:session.accessToken};
 }
 const a=await user('Саша');const b=await user('Мария Лебедева');
 const mod=(await db.query("INSERT INTO users(email,display_name,adult_confirmed_at,email_verified_at,role) VALUES($1,'P61 fixture moderator',now(),now(),'moderator') RETURNING id",['p61-mod-'+randomUUID()+'@example.invalid'])).rows[0].id;
 const p=await propose(db,a.id,randomUUID(),{name:'Солнечный лагер',breweryName:'Крафтовый клуб',styleName:'Lager',source:'Local P6.1 synthetic acceptance fixture',abv:4.8,packaging:'can',volumeMl:500});
 const accepted=await decide(db,mod,p.id,{version:1,action:'accept',reason:'Local P6.1 synthetic acceptance fixture',allowDuplicate:true});
 const f=randomUUID();await request('v1/friend-requests/'+f,a.token,'PUT',{userId:b.id});await request('v1/friend-requests/'+f+'/decision',b.token,'PUT',{status:'accepted'});
 async function checkin(u,body,visibility){
  const uploaded=await fetch('http://127.0.0.1:53000/v1/media',{method:'POST',headers:{Authorization:'Bearer '+u.token,'Content-Type':'image/png'},body:photo});
  if(!uploaded.ok)throw Error('Upload '+uploaded.status+' '+await uploaded.text());
  const media=await uploaded.json();
  return await request('v1/checkins',u.token,'POST',{positionId:accepted.positionId,ratingQuarters:17,body,occurredAt:new Date().toISOString(),visibility,mediaIds:[media.id]});
 }
 const own=await checkin(a,'Лёгкий солодовый вкус и мягкая горечь','private');
 const friend=await checkin(b,'Тёплый вечер в хорошей компании','friends');
 await writeFile('work/p61-android.json',JSON.stringify({email:a.email,password,positionId:accepted.positionId,ownCheckinId:own.id,friendCheckinId:friend.id}),{flag:'wx',mode:0o600});
 console.log('P6.1 local real-API fixture saved in ignored work/p61-android.json');
}finally{await db.end();}
