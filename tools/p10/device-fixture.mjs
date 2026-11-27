import {writeFile,readFile,mkdir} from 'node:fs/promises'
import {randomUUID} from 'node:crypto'
import {createPool} from '../../backend/dist/src/db/pool.js'

if (process.env.NODE_ENV !== 'development' ||
    !['127.0.0.1','localhost'].includes(new URL(process.env.DATABASE_URL).hostname)) {
  throw new Error('This fixture requires a development backend and a loopback database')
}
const api = 'http://127.0.0.1:53000'
const mailbox = 'http://127.0.0.1:58025'
async function post(path,body,status) {
  const response = await fetch(api+path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body)})
  if (response.status !== status) throw new Error(path+' returned '+response.status)
}

async function createAccount() {
  const suffix = randomUUID().replaceAll('-','').slice(0,12)
  const email = 'pet-'+suffix+'@example.invalid'
  const password = randomUUID()+'Aa!'
  await post('/v1/auth/register',{email,password,displayName:'KraPet QA',handle:'petqa_'+suffix,adultConfirmed:true},202)
  const mail = await (await fetch(mailbox+'/api/v1/search?query='+encodeURIComponent('to:'+email))).json()
  const message = await (await fetch(mailbox+'/api/v1/message/'+mail.messages[0].ID)).json()
  const code = message.Text.match(/^Код подтверждения: ([0-9]{6})$/m)[1]
  await post('/v1/auth/verify-code',{email,code},204)
  return {email,password}
}

const qr = (await readFile(process.argv[2],'utf8')).trim()
if (!/^krapiva:reward:[A-Za-z0-9_-]{16,180}$/.test(qr)) throw new Error('Provide the local test reward code file')
const account = await createAccount()
const starterAccounts = []
if (process.argv.includes('--all-starters')) {
  for (let index = 0; index < 5; index++) starterAccounts.push(await createAccount())
}

const pool = createPool(process.env.DATABASE_URL)
let position
try {
  const brewery = (await pool.query("INSERT INTO breweries(name,source,verification_status) VALUES('P10 QA brewery','test fixture','verified') RETURNING id")).rows[0].id
  const release = (await pool.query("INSERT INTO beer_releases(brewery_id,name,abv) VALUES($1,'P10 QA zero',0) RETURNING id",[brewery])).rows[0].id
  position = (await pool.query("INSERT INTO beer_positions(release_id,source,status,verified_at) VALUES($1,'test fixture','verified',now()) RETURNING id",[release])).rows[0].id
} finally {
  await pool.end()
}

await mkdir('work',{recursive:true})
await writeFile('work/p10-device-fixture.json',JSON.stringify({...account,position,qr,starterAccounts}),{mode:0o600,flag:'wx'})
console.log('Synthetic account confirmed and fixture written to work/p10-device-fixture.json')
