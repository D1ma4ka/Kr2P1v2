// Explicit disposable synthetic benchmark. Never connects to or populates the app DB.
import {performance} from 'node:perf_hooks';
import {cpus,totalmem} from 'node:os';
import {mkdir,writeFile} from 'node:fs/promises';
import {testDatabase} from '../../test/database.js';
import {migrate} from '../db/migrate.js';
import {positions} from '../catalog/repository.js';
if(process.env.NODE_ENV!=='development'||!process.env.TEST_DATABASE_URL||new URL(process.env.TEST_DATABASE_URL).hostname!=='127.0.0.1') throw Error('Local disposable benchmark only');
const count=Number(process.env.CATALOG_BENCH_POSITIONS??50000),codes=Number(process.env.CATALOG_BENCH_CODES??3000000);
if(!Number.isInteger(count)||count<100||count>100000||!Number.isInteger(codes)||codes<100||codes>3000000) throw Error('Invalid synthetic sizes');
const db=await testDatabase();
try {
 await migrate(db.pool);
 const client=await db.pool.connect();
 try {
  await client.query("SET statement_timeout='180s'");
  const start=performance.now();
  const brewery=(await client.query("INSERT INTO breweries(name,source,verification_status) VALUES('Synthetic benchmark brewery','disposable synthetic benchmark','verified') RETURNING id")).rows[0].id;
  const style=(await client.query("INSERT INTO styles(name) VALUES('Synthetic benchmark IPA') RETURNING id")).rows[0].id;
  await client.query(`INSERT INTO beer_releases(id,brewery_id,style_id,name,abv) SELECT md5('release'||n)::uuid,$1,$2,'Synthetic beer '||n,(n%1500)/100.0 FROM generate_series(1,$3::int) n`,[brewery,style,count]);
  await client.query(`INSERT INTO beer_positions(id,release_id,packaging,volume_ml,status,source,verified_at) SELECT md5('position'||n)::uuid,md5('release'||n)::uuid,'can',500,'verified','disposable synthetic benchmark',now() FROM generate_series(1,$1::int) n`,[count]);
  for (let offset=0;offset<codes;offset+=100000) {
  await client.query(`INSERT INTO barcode_mappings(position_id,gtin,source,status)
  SELECT md5('position'||(n%$1::int+1))::uuid,payload||((10-(SELECT sum(substr(payload,i,1)::int*CASE WHEN i%2=1 THEN 3 ELSE 1 END) FROM generate_series(1,13) i)%10)%10)::text,'disposable synthetic benchmark','verified'
  FROM (SELECT n,('2900000000000'::bigint+n)::text AS payload FROM generate_series($2::int+1,least($2::int+100000,$3::int)) n) data`,[count,offset,codes]);
  console.log('Generated synthetic barcodes: '+Math.min(offset+100000,codes));
  }
  await client.query('ANALYZE');
  const generationMs=performance.now()-start;
  const code=(await client.query("SELECT gtin FROM barcode_mappings ORDER BY gtin LIMIT 1 OFFSET $1",[Math.floor(codes/2)])).rows[0].gtin as string;
  const plans:Record<string,unknown>={};
  for(const [name,sql,values] of [
   ['barcode',"SELECT position_id FROM barcode_mappings WHERE gtin=$1 AND status='verified'",[code]],
   ['search',"SELECT position_id FROM catalog_search WHERE search_text LIKE $1 ORDER BY created_at DESC,position_id DESC LIMIT 21",['%49999%']],
   ['namePage','SELECT position_id FROM catalog_search ORDER BY name_key COLLATE "C",position_id LIMIT 21',[]],
  ] as const) plans[name]=(await client.query('EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) '+sql,[...values])).rows[0]['QUERY PLAN'];
  const timings:Record<string,number[]>={barcode:[],search:[],newest:[],name:[],abv:[]};
  for(let i=0;i<30;i++) for(const kind of Object.keys(timings)) {
   const before=performance.now();
   if(kind==='barcode') await client.query("SELECT position_id FROM barcode_mappings WHERE gtin=$1 AND status='verified'",[code]);
   else await positions(client,kind==='search'?{q:String(Math.min(count,49999)),limit:'20'}:{sort:kind as 'name'|'abv'|'newest',limit:'20'});
   timings[kind]!.push(performance.now()-before);
  }
  const summary=Object.fromEntries(Object.entries(timings).map(([kind,values])=>{values.sort((a,b)=>a-b);return [kind,{samples:values.length,p50Ms:values[14],p95Ms:values[28],maxMs:values.at(-1)}];}));
  const result={recordedAt:new Date().toISOString(),scope:'disposable synthetic SQL/repository smoke, sequential, warm cache; no production capacity claim',positions:count,barcodes:codes,
   environment:{node:process.version,postgres:(await client.query('SHOW server_version')).rows[0].server_version,cpu:cpus()[0]?.model,visibleCpuCount:cpus().length,visibleMemoryBytes:totalmem(),containerCpuQuota:'see /sys/fs/cgroup/cpu.max; this run: 4 CPU',containerMemoryLimit:'16 GiB'},generationMs,summary,plans};
  await mkdir('../docs/performance',{recursive:true});await writeFile('../docs/performance/P4-catalog-benchmark.json',JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify({positions:count,barcodes:codes,generationMs,summary}));
 } finally {client.release();}
} finally {await db.close();}
