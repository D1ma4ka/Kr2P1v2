import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';
import { ApiError } from '../http/errors.js';
const options={N:32768,r:8,p:1,maxmem:64*1024*1024};
let active=0;
async function derive(password:string,salt:string):Promise<Buffer> {
 if(active>=4) throw new ApiError(429,'AUTH_BUSY','Повторите попытку позже');
 active++;
 try {return await new Promise((resolve,reject)=>scrypt(password,salt,32,options,(error,key)=>error?reject(error):resolve(key)));}
 finally {active--;}
}
export async function hashPassword(password:string) {
 const salt=randomBytes(16).toString('hex');
 return `scrypt-v1$${salt}$${(await derive(password,salt)).toString('hex')}`;
}
export async function checkPassword(password:string,hash:string|null) {
 // Same KDF cost for unknown accounts; the synthetic verifier never authenticates.
 const parts=(hash??'').split('$');
 const valid=parts.length===3&&parts[0]==='scrypt-v1'&&/^[a-f0-9]{32}$/.test(parts[1]!)&&/^[a-f0-9]{64}$/.test(parts[2]!);
 const key=await derive(password,valid?parts[1]!:'00000000000000000000000000000000');
 return valid&&timingSafeEqual(key,Buffer.from(parts[2]!,'hex'));
}
