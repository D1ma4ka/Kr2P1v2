import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import type pg from 'pg';
// Relative to both src/db and dist/src/db: migrations live at backend/migrations.
const root=new URL(import.meta.url.includes('/dist/')?'../../../migrations/':'../../migrations/',import.meta.url);
const expected=Promise.all(['001_foundation.sql','002_request_budgets.sql','003_accounts.sql','004_catalog.sql','005_catalog_batch_projection.sql','006_proposal_media_review.sql','007_diary.sql','008_social.sql','009_email_verification_codes.sql','010_places_badges_moderation.sql','011_password_reset_codes.sql','012_user_handles.sql','013_local_discovery.sql','014_saved_venues.sql','015_venue_menus.sql','016_verified_release_dates.sql','017_krapet.sql','018_krapet_content.sql'].map(async name=>({name,
 sha256:createHash('sha256').update(await readFile(new URL(name,root))).digest('hex')})));
export async function databaseReady(pool:pg.Pool) {
 const result=await pool.query<{name:string;sha256:string}>('SELECT name,sha256 FROM schema_migrations ORDER BY name');
 const wanted=await expected;
 if(JSON.stringify(result.rows)!==JSON.stringify(wanted)) throw new Error('Migration history differs');
 // Resolve actual relations and privileges, not only the ledger. LIMIT 0 does not scan user rows.
 await pool.query('SELECT u.id FROM users u, user_profiles p, sessions s, checkins c, media_assets m, request_budgets b, catalog_search cs, position_proposals pp, email_verification_codes ev, password_reset_codes pr, moderation_holds mh, discovery_events de, local_badge_targets lbt, local_badge_attendance lba, user_saved_venues sv, kra_pet_definitions kp, kra_wallets kw, kra_item_definitions ki, kra_art ka LIMIT 0');
}
