// Explicit local-only fixture. Never called by startup/migrations, no real company seed.
import {access,writeFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {createPool} from '../backend/dist/src/db/pool.js';
import {transaction} from '../backend/dist/src/db/pool.js';
import {hashPassword} from '../backend/dist/src/identity/passwords.js';
if(process.env.NODE_ENV!=='development'||new URL(process.env.DATABASE_URL).hostname!=='127.0.0.1')throw Error('Local development fixtures only');
try {await access('work/p7/android-fixture.json');throw Error('Fixture exists; reuse it');}catch(e){if(e.code!=='ENOENT')throw e;}
const db=createPool(process.env.MIGRATION_DATABASE_URL);
try {
 const result=await transaction(db,async client=>{
  const password='P7-'+randomUUID()+'!',email='p7-'+randomUUID()+'@example.invalid';
  const uid=(await client.query("INSERT INTO users(email,display_name,password_hash,adult_confirmed_at,email_verified_at)VALUES($1,'P7 Synthetic tester',$2,now(),now())RETURNING id",[email,await hashPassword(password)])).rows[0].id;
  await client.query('INSERT INTO user_profiles(user_id)VALUES($1)',[uid]);
  const places=[];
  for(let i=0;i<12;i++) {
   const row=(await client.query(`INSERT INTO venues(name,type,city,address,source,latitude,longitude,coordinates_source,coordinates_verified_at,status,is_test)
    VALUES($1,$2,'P7 Synthetic city','Synthetic address — not a real business','Isolated P7 fixture; not a real business',$3,$4,'Synthetic test coordinates; not surveyed',now(),'verified',true)RETURNING id`,
    ['P7 Synthetic '+i,i%2?'beer_shop':'bar',55.75+(i%3)*0.001,37.61+Math.floor(i/3)*0.001])).rows[0];places.push(row.id);
  }
  await client.query(`INSERT INTO discovery_events(id,venue_id,title,description,source,starts_at,ends_at,published)
    VALUES($1,$2,'Synthetic P10.3 event','Isolated device acceptance event','Synthetic fixture',
      now(),now()+interval '1 day',true)`,[randomUUID(),places[0]]);
  // Exact example supplied in FR-109, NOT externally confirmed. Deliberately no coordinates/photo.
  const craft=(await client.query(`INSERT INTO venues(name,type,city,address,source,status,is_test)VALUES('Craft Republic','bar','Москва','Малый Гнездниковский переулок, 9с7, Москва, 125009','TEST ONLY: example from FR-109; current existence/address not externally confirmed','verified',true)RETURNING id`)).rows[0].id;
  const positions=[];
  for(let i=0;i<5;i++) {
   const br=(await client.query("INSERT INTO breweries(name,source,verification_status)VALUES($1,'Isolated test','verified')RETURNING id",['P7 Synthetic brewery '+i])).rows[0].id;
   const st=(await client.query('INSERT INTO styles(name)VALUES($1)RETURNING id',['P7 Synthetic style '+randomUUID()])).rows[0].id;
   const rel=(await client.query("INSERT INTO beer_releases(brewery_id,name,style_id)VALUES($1,$2,$3)RETURNING id",[br,'P7 Synthetic position '+i,st])).rows[0].id;
   positions.push((await client.query("INSERT INTO beer_positions(release_id,source,status,verified_at)VALUES($1,'Isolated P7 fixture','verified',now())RETURNING id",[rel])).rows[0].id);
  }
  return {email,password,userId:uid,venueIds:places,craftExampleId:craft,positionId:positions[0],positionIds:positions,requestIds:positions.map(()=>randomUUID()),occurredAt:new Date().toISOString()};
 });
 await writeFile('work/p7/android-fixture.json',JSON.stringify(result),{flag:'wx',mode:0o600});
 console.log('Local synthetic fixture saved to ignored work/p7/android-fixture.json; credentials not printed');
}finally {await db.end();}
