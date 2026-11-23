import type pg from 'pg';
import {transaction} from '../db/pool.js';
import {active, visible} from '../checkins/queries.js';
import {requireModerator} from '../catalog/proposals.js';
import {ApiError, notFound} from '../http/errors.js';

type State = 'draft' | 'published';
export interface Offer {
 sectionId: string;
 ordinal: number;
 price: number | null;
 currency: string | null;
 volumeMl: number | null;
 servingFormat: string | null;
 available: boolean;
}
interface Item extends Offer { positionId: string }
export interface Draft {
 expectedRevision: number;
 source: string;
 sections: {id: string; name: string}[];
 items: Item[];
}

const currencies = new Set(Intl.supportedValuesOf('currency'));
const conflict = () => new ApiError(409, 'MENU_REVISION', 'Меню изменилось. Загрузите текущую версию.');
const invalid = (message: string) => new ApiError(422, 'INVALID_MENU', message);
export const verifiedPosition = "bp.status='verified' AND b.verification_status='verified'";
const catalogJoin = `JOIN beer_positions bp ON bp.id=i.position_id
 JOIN beer_releases r ON r.id=bp.release_id JOIN breweries b ON b.id=r.brewery_id`;

// The snapshot and its enrichment come from one statement. No per-position HTTP calls.
export async function readMenu(
 pool: pg.Pool | pg.PoolClient,user: string,venue: string,allowTest: boolean,state: State,positionIds: string[] | null = null,
) {
 const result = await pool.query(`
  WITH menu AS (
   SELECT m.* FROM venue_menus m WHERE m.venue_id=$2 AND m.state=$4
  ), items AS MATERIALIZED (
   SELECT i.*, r.name, r.abv, r.ibu, r.description, r.style_id, r.id AS release_id, b.id AS brewery_id,
    b.name AS brewery_name, b.country, bp.packaging, bp.volume_ml AS catalog_volume_ml, st.name AS style_name
   FROM menu m JOIN venue_menu_items i USING (venue_id,state)
   ${catalogJoin} LEFT JOIN styles st ON st.id=r.style_id
   WHERE ($4='draft' OR (${verifiedPosition})) AND ($5::uuid[] IS NULL OR i.position_id=ANY($5))
  ), ratings AS (
   SELECT c.position_id,(avg(c.rating_quarters)/4)::float8 AS rating,
    (avg(c.rating_quarters) FILTER (WHERE c.visibility='public' AND p.profile_visibility='public')/4)::float8 AS public_rating,
    count(c.rating_quarters)::int AS rated,
    count(*) FILTER (WHERE c.user_id=$1)::int AS personal_checkins,
    (avg(c.rating_quarters) FILTER (WHERE c.user_id=$1)/4)::float8 AS personal_rating
   FROM items i JOIN checkins c ON c.position_id=i.position_id
    JOIN users u ON u.id=c.user_id JOIN user_profiles p ON p.user_id=u.id
   WHERE ${active} AND ${visible}
   GROUP BY c.position_id
  )
  SELECT v.id AS "venueId", (
   SELECT jsonb_build_object('state',m.state,'revision',m.revision,'source',m.source,'updatedAt',m.updated_at,
    'sections',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',s.id,'name',s.name) ORDER BY s.ordinal)
     FROM venue_menu_sections s WHERE (s.venue_id,s.state)=(m.venue_id,m.state)
      AND ($5::uuid[] IS NULL OR EXISTS(SELECT 1 FROM items i WHERE i.section_id=s.id))),'[]'::jsonb),
    'items',COALESCE((SELECT jsonb_agg(jsonb_build_object(
     'positionId',i.position_id,'sectionId',i.section_id,'ordinal',i.ordinal,
     'name',i.name,'breweryId',i.brewery_id,'breweryName',i.brewery_name,'country',i.country,
     'styleId',i.style_id,'styleName',i.style_name,'releaseId',i.release_id,
     'abv',i.abv,'ibu',i.ibu,'packaging',i.packaging,'catalogVolumeMl',i.catalog_volume_ml,
     'price',i.price,'currency',i.currency,'volumeMl',i.volume_ml,'servingFormat',i.serving_format,
     'available',i.available,'averageRating',a.rating,'publicAverageRating',a.public_rating,'ratedCheckins',COALESCE(a.rated,0),
     'personalCheckins',COALESCE(a.personal_checkins,0),'personalAverageRating',a.personal_rating,
     'tried',COALESCE(a.personal_checkins>0,false),'description',i.description
    ) ORDER BY s.ordinal,i.ordinal,i.position_id)
     FROM items i JOIN venue_menu_sections s ON (s.venue_id,s.state,s.id)=(i.venue_id,i.state,i.section_id)
     LEFT JOIN ratings a USING(position_id)),'[]'::jsonb)
   ) FROM menu m
  ) AS menu FROM venues v
  WHERE v.id=$2 AND v.status='verified' AND ($3 OR NOT v.is_test)
  AND ($4='published' OR EXISTS (SELECT 1 FROM venue_menu_operators o WHERE o.venue_id=v.id AND o.user_id=$1)
   OR EXISTS (SELECT 1 FROM users WHERE id=$1 AND status='active' AND role IN ('moderator','admin')))
 `, [user, venue, allowTest, state, positionIds]);
 if (!result.rowCount) {
  if (state === 'draft') throw new ApiError(403, 'FORBIDDEN', 'Нет доступа к черновику меню');
  throw notFound();
 }
 return result.rows[0]!;
}

async function lockVenue(db: pg.PoolClient, venue: string, allowTest: boolean) {
 const result = await db.query("SELECT id FROM venues WHERE id=$1 AND status='verified' AND ($2 OR NOT is_test) FOR UPDATE", [venue, allowTest]);
 if (!result.rowCount) throw notFound();
}

export async function setOperator(pool: pg.Pool, actor: string, venue: string, user: string, role: 'owner' | 'operator' | null, allowTest: boolean) {
 return transaction(pool, async db => {
  await requireModerator(db, actor);
  await lockVenue(db, venue, allowTest);
  if (role === null) {
   await db.query('DELETE FROM venue_menu_operators WHERE venue_id=$1 AND user_id=$2', [venue, user]);
  } else {
   const result = await db.query(`INSERT INTO venue_menu_operators(venue_id,user_id,role,granted_by)
    SELECT $1,id,$3,$4 FROM users WHERE id=$2 AND status='active'
    ON CONFLICT (venue_id,user_id) DO UPDATE SET role=EXCLUDED.role,granted_by=EXCLUDED.granted_by`, [venue, user, role, actor]);
   if (!result.rowCount) throw notFound();
  }
  return {role};
 });
}

async function edit<T>(pool: pg.Pool, actor: string, venue: string, allowTest: boolean, expected: number, change: (db: pg.PoolClient) => Promise<T>) {
 return transaction(pool, async db => {
  await lockVenue(db, venue, allowTest);
  const {rowCount} = await db.query('SELECT 1 FROM venue_menu_operators WHERE venue_id=$1 AND user_id=$2', [venue, actor]);
  if (!rowCount) await requireModerator(db, actor);
  const draft = await db.query("SELECT revision FROM venue_menus WHERE venue_id=$1 AND state='draft'", [venue]);
  if ((draft.rows[0]?.revision ?? 0) !== expected) throw conflict();
  return change(db);
 });
}

function validateOffer(offer: Offer) {
 if ((offer.price === null) !== (offer.currency === null) || (offer.currency !== null && !currencies.has(offer.currency))) {
  throw invalid('Для цены укажите поддерживаемую валюту');
 }
 if (offer.price !== null && !/^\d+(\.\d{1,2})?$/.test(String(offer.price))) throw invalid('Цена должна иметь до двух десятичных знаков');
}

async function verifyPositions(db: pg.PoolClient, ids: string[]) {
 if (!ids.length) return;
 const result = await db.query(`SELECT bp.id FROM beer_positions bp JOIN beer_releases r ON r.id=bp.release_id
  JOIN breweries b ON b.id=r.brewery_id WHERE bp.id=ANY($1::uuid[]) AND ${verifiedPosition}`, [ids]);
 if (result.rowCount !== ids.length) throw invalid('Укажите проверенные позиции каталога по их действующим ID');
}

async function putItem(db: pg.PoolClient, venue: string, id: string, item: Offer) {
 await db.query(`INSERT INTO venue_menu_items(venue_id,state,position_id,section_id,ordinal,price,currency,volume_ml,serving_format,available)
  VALUES ($1,'draft',$2,$3,$4,$5,$6,$7,$8,$9) ON CONFLICT (venue_id,state,position_id) DO UPDATE SET
  section_id=EXCLUDED.section_id,ordinal=EXCLUDED.ordinal,price=EXCLUDED.price,currency=EXCLUDED.currency,
  volume_ml=EXCLUDED.volume_ml,serving_format=EXCLUDED.serving_format,available=EXCLUDED.available`,
 [venue,id,item.sectionId,item.ordinal,item.price,item.currency,item.volumeMl,item.servingFormat?.trim() ?? null,item.available]);
}

export async function saveDraft(pool: pg.Pool, actor: string, venue: string, allowTest: boolean, draft: Draft) {
 return edit(pool, actor, venue, allowTest, draft.expectedRevision, async db => {
  const sections = new Set(draft.sections.map(s => s.id.toLowerCase()));
  const ids = [...new Set(draft.items.map(item => item.positionId.toLowerCase()))];
  if (sections.size !== draft.sections.length || ids.length !== draft.items.length) throw invalid('В меню есть дубликаты');
  for (const item of draft.items) {
   validateOffer(item);
   if (!sections.has(item.sectionId.toLowerCase())) throw invalid('Раздел отсутствует в этом меню');
  }
  await verifyPositions(db, ids);
  const revision = draft.expectedRevision + 1;
  await db.query(`INSERT INTO venue_menus(venue_id,state,revision,source,updated_by) VALUES ($1,'draft',$2,$3,$4)
   ON CONFLICT (venue_id,state) DO UPDATE SET revision=EXCLUDED.revision,source=EXCLUDED.source,updated_by=EXCLUDED.updated_by,updated_at=now()`,
  [venue,revision,draft.source.trim(),actor]);
  await db.query("DELETE FROM venue_menu_sections WHERE venue_id=$1 AND state='draft'", [venue]);
  for (const [ordinal, section] of draft.sections.entries()) {
   await db.query("INSERT INTO venue_menu_sections VALUES ($1,'draft',$2,$3,$4)", [venue,section.id,section.name.trim(),ordinal]);
  }
  for (const item of draft.items) await putItem(db,venue,item.positionId,item);
  return {revision};
 });
}

export async function changeItem(pool: pg.Pool, actor: string, venue: string, allowTest: boolean, expected: number, id: string, offer: Offer | null) {
 return edit(pool, actor, venue, allowTest, expected, async db => {
  if (expected === 0) throw notFound();
  if (offer) {
   validateOffer(offer);
   await verifyPositions(db, [id]);
   const section = await db.query("SELECT 1 FROM venue_menu_sections WHERE venue_id=$1 AND state='draft' AND id=$2", [venue,offer.sectionId]);
   if (!section.rowCount) throw invalid('Раздел отсутствует в этом меню');
   const size = await db.query("SELECT count(*)::int AS size FROM venue_menu_items WHERE venue_id=$1 AND state='draft' AND position_id<>$2", [venue,id]);
   if (size.rows[0].size >= 250) throw invalid('В меню может быть до 250 позиций');
   await putItem(db, venue, id, offer);
  } else {
   const result = await db.query("DELETE FROM venue_menu_items WHERE venue_id=$1 AND state='draft' AND position_id=$2", [venue,id]);
   if (!result.rowCount) throw notFound();
  }
  const revision = expected + 1;
  await db.query("UPDATE venue_menus SET revision=$2,updated_by=$3,updated_at=now() WHERE venue_id=$1 AND state='draft'", [venue,revision,actor]);
  return {revision};
 });
}

export async function publish(pool: pg.Pool, actor: string, venue: string, allowTest: boolean, revision: number) {
 return edit(pool, actor, venue, allowTest, revision, async db => {
  const existing = await db.query("SELECT revision FROM venue_menus WHERE venue_id=$1 AND state='published'", [venue]);
  if (existing.rows[0]?.revision === revision) return {revision};
  const positions = await db.query("SELECT position_id FROM venue_menu_items WHERE venue_id=$1 AND state='draft'", [venue]);
  await verifyPositions(db, positions.rows.map(row => row.position_id));
  await db.query(`INSERT INTO venue_menus(venue_id,state,revision,source,updated_by)
   SELECT venue_id,'published',revision,source,$2 FROM venue_menus WHERE venue_id=$1 AND state='draft'
   ON CONFLICT (venue_id,state) DO UPDATE SET revision=EXCLUDED.revision,source=EXCLUDED.source,updated_by=EXCLUDED.updated_by,updated_at=now()`, [venue,actor]);
  await db.query("DELETE FROM venue_menu_sections WHERE venue_id=$1 AND state='published'", [venue]);
  await db.query(`INSERT INTO venue_menu_sections SELECT venue_id,'published',id,name,ordinal
   FROM venue_menu_sections WHERE venue_id=$1 AND state='draft'`, [venue]);
  await db.query(`INSERT INTO venue_menu_items SELECT venue_id,'published',section_id,position_id,ordinal,price,currency,volume_ml,serving_format,available
   FROM venue_menu_items WHERE venue_id=$1 AND state='draft'`, [venue]);
  return {revision};
 });
}
