export function databaseConfig(env:NodeJS.ProcessEnv=process.env) {
 if(!env.DATABASE_URL) throw new Error('DATABASE_URL required');
 let url:URL;
 try {url=new URL(env.DATABASE_URL);} catch {throw new Error('Invalid DATABASE_URL');}
 if(!['postgres:','postgresql:'].includes(url.protocol)||!url.hostname||url.pathname.length<2||url.hash)
  throw new Error('Invalid database configuration');
 // libpq URL options must not override the reviewed host or TLS policy.
 for(const key of url.searchParams.keys()) {
  if(!['sslmode','sslrootcert'].includes(key)||url.searchParams.getAll(key).length!==1)
   throw new Error('Unsupported or duplicate database URL option');
 }
 const mode=url.searchParams.get('sslmode');
 if(mode!==null&&!['disable','verify-full'].includes(mode)) throw new Error('Use disabled local TLS or verify-full');
 const text=env.DB_POOL_MAX??'10';
 if(!/^\d+$/.test(text)||Number(text)<1||Number(text)>50) throw new Error('DB_POOL_MAX must be 1..50');
 if(env.NODE_ENV==='production' && !['127.0.0.1','localhost','db','[::1]'].includes(url.hostname) && mode!=='verify-full')
  throw new Error('Remote production database requires certificate-verified TLS');
 return {url:env.DATABASE_URL,max:Number(text)};
}
