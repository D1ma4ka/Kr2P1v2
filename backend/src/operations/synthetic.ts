import type pg from "pg";
import { transaction } from "../db/pool.js";
import { issueSession } from "../identity/sessions.js";
// Synthetic only; never automatically called by migrations or server startup.
export async function fixtures(pool: pg.Pool) {
  return transaction(pool,async client=>{
    const accounts=[];
    for(const name of ['Alpha','Beta']) {
      const user=(await client.query("INSERT INTO users(email,display_name,adult_confirmed_at) VALUES($1,$2,now()) RETURNING id",[`${name.toLowerCase()}@example.invalid`,`Synthetic ${name}`])).rows[0];
      await client.query("INSERT INTO user_profiles(user_id) VALUES($1)",[user.id]);
      accounts.push({id:user.id as string,token:await issueSession(client,user.id)});
    }
    const brewery=(await client.query("INSERT INTO breweries(name,source,verification_status) VALUES('Synthetic brewery','test fixture','verified') RETURNING id")).rows[0].id;
    const release=(await client.query("INSERT INTO beer_releases(brewery_id,name) VALUES($1,'Synthetic position') RETURNING id",[brewery])).rows[0].id;
    const position=(await client.query("INSERT INTO beer_positions(release_id,source,status,verified_at) VALUES($1,'test fixture','verified',now()) RETURNING id",[release])).rows[0].id as string;
    return {a:accounts[0]!,b:accounts[1]!,position};
  });
}
