import nodemailer from 'nodemailer';
// P2 local SMTP adapter only. Production delivery/verification/reset workflow is P3.
export function localMailer(host:string,port:number,environment:string) {
 if(environment==='production' || !['127.0.0.1','localhost','mailpit'].includes(host)) throw new Error('Local test SMTP required');
 if(!Number.isInteger(port)||port<1||port>65535) throw new Error('Invalid SMTP_PORT');
 const transport=nodemailer.createTransport({host,port,secure:false,ignoreTLS:true,
  connectionTimeout:3000,greetingTimeout:3000,socketTimeout:5000,
  disableFileAccess:true,disableUrlAccess:true});
 return {async send(to:string,subject:string,text:string) {
  if(!/^[a-z0-9._+-]+@example\.invalid$/i.test(to)) throw new Error('Only synthetic example.invalid recipients allowed');
  if(subject.length>200||text.length>10000) throw new Error('Mail exceeds test limits');
  return transport.sendMail({from:'KraPiva Test <no-reply@example.invalid>',to,subject,text});
 },close:()=>transport.close()};
}
