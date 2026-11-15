import nodemailer from 'nodemailer';
import {ApiError} from '../http/errors.js';
export interface AccountMailer { send(to:string,subject:string,text:string):Promise<unknown>; close():void; }
// No external delivery in dev/test. Production requires explicit owner configuration.
export function accountMailer(env:NodeJS.ProcessEnv=process.env):AccountMailer {
 const production=env.NODE_ENV==='production';
 const host=env.SMTP_HOST??'127.0.0.1', port=Number(env.SMTP_PORT??51025);
 if(!Number.isInteger(port)||port<1||port>65535) throw Error('Invalid SMTP_PORT');
 if(!production&&!['localhost','127.0.0.1','mailpit'].includes(host)) throw Error('Local SMTP required');
 if(production&&(env.SMTP_DELIVERY_ENABLED!=='true'||!env.SMTP_FROM||!env.SMTP_USER||!env.SMTP_PASSWORD||![465,587].includes(port)))
  throw Error('Production SMTP needs explicit delivery approval configuration and TLS credentials');
 const transport=nodemailer.createTransport({host,port,secure:production&&port===465,requireTLS:production,
  ...(!production?{ignoreTLS:true}:{auth:{user:env.SMTP_USER!,pass:env.SMTP_PASSWORD!}}),
  connectionTimeout:3000,greetingTimeout:3000,socketTimeout:5000,disableFileAccess:true,disableUrlAccess:true});
 return {send(to,subject,text) {
  if(!production&&!/^[a-z0-9._+-]+@example\.invalid$/i.test(to))
   throw new ApiError(422,'LOCAL_MAILBOX_ONLY','Локальная почтовая среда: используйте адрес name@example.invalid и откройте письмо в Mailpit. Доставка во внешнюю почту не настроена.');
  return transport.sendMail({from:env.SMTP_FROM??'KraPiva <no-reply@example.invalid>',to,subject,text});
 },close:()=>transport.close()};
}
