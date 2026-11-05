import {mkdir,open,unlink} from 'node:fs/promises';
import {resolve,dirname} from 'node:path';
import {createPool} from '../db/pool.js';
import {fixtures} from './synthetic.js';
if(process.env.NODE_ENV!=='development' || !process.env.DATABASE_URL ||
 !['127.0.0.1','localhost','db'].includes(new URL(process.env.DATABASE_URL).hostname))
 throw new Error('Explicit development environment and local database required');
const output=resolve(process.env.DEV_ACCOUNTS_FILE??'../work/dev-accounts.json');
await mkdir(dirname(output),{recursive:true,mode:0o700});
const file=await open(output,'wx',0o600); // never overwrite existing access tokens
const pool=createPool(process.env.DATABASE_URL);
try {await file.writeFile(JSON.stringify(await fixtures(pool),null,2));await file.sync();console.log('Synthetic accounts saved to protected local file (not logged).');}
catch {await unlink(output);console.error('Provision failed; inspect local DB/account conflicts.');process.exitCode=1;}
finally {await file.close();await pool.end();}
