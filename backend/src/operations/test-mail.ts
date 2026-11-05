import {localMailer} from '../mail/smtp.js';
const mail=localMailer(process.env.SMTP_HOST??'127.0.0.1',Number(process.env.SMTP_PORT??51025),process.env.NODE_ENV??'development');
try {
 await mail.send('tester@example.invalid','KraPiva SMTP foundation','Synthetic delivery over real SMTP. This is not an email verification workflow.');
 console.log('Synthetic test email accepted by local SMTP receiver');
} catch {console.error('Local SMTP delivery failed');process.exitCode=1;}
finally {mail.close();}
