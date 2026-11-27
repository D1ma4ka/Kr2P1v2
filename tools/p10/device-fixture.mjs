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

if (process.argv[2] === '--fund-starters') {
  const { starterAccounts } = JSON.parse(await readFile('work/p10-device-fixture.json', 'utf8'))
  const emails = starterAccounts.map(({ email }) => email)
  if (emails.length !== 5 || emails.some(email => !/^pet-[0-9a-f]{12}@example\.invalid$/.test(email))) {
    throw new Error('Five synthetic starter accounts are required for the visual fixture')
  }
  const pool = createPool(process.env.DATABASE_URL)
  try {
    const result = await pool.query(`
      WITH credits AS (
        INSERT INTO kra_wallet_entries(user_id, reason, event_id, amount, config_version, note)
        SELECT u.id, 'admin', u.id, 1000, (SELECT max(version) FROM kra_economy), 'P10 visual fixture'
        FROM users u JOIN kra_wallets w ON w.user_id = u.id
        WHERE u.email = ANY($1::text[])
        ON CONFLICT (user_id, reason, event_id) DO NOTHING
        RETURNING user_id, amount
      )
      UPDATE kra_wallets w SET balance = w.balance + c.amount
      FROM credits c WHERE w.user_id = c.user_id
    `, [emails])
    if (result.rowCount !== emails.length) throw new Error('Starter adoption must succeed before funding the visual fixture')
  } finally {
    await pool.end()
  }
  console.log('Funded five synthetic wallets for visual acceptance')
  process.exit(0)
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
