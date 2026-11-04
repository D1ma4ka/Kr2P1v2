export function databaseConfig(env:NodeJS.ProcessEnv=process.env) {
 if(!env.DATABASE_URL) throw new Error('DATABASE_URL required');
 let url:URL;
 try {url=new URL(env.DATABASE_URL);} catch {throw new Error('Invalid DATABASE_URL');}
 if(!['postgres:','postgresql:'].includes(url.protocol)||!url.hostname||url.pathname.length<2)
  throw new Error('Invalid database configuration');
 const text=env.DB_POOL_MAX??'10';
 if(!/^\d+$/.test(text)||Number(text)<1||Number(text)>50) throw new Error('DB_POOL_MAX must be 1..50');
 if(env.NODE_ENV==='production' && !['127.0.0.1','localhost','db'].includes(url.hostname) && url.searchParams.get('sslmode')!=='verify-full')
  throw new Error('Remote production database requires certificate-verified TLS');
 return {url:env.DATABASE_URL,max:Number(text)};
}
