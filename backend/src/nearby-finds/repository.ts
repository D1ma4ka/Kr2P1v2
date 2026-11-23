import {createHash} from 'node:crypto';
import type pg from 'pg';
import {advisorHistory} from '../checkins/repository.js';
import {active,visible} from '../checkins/queries.js';
import {transaction} from '../db/pool.js';
import {ApiError,notFound} from '../http/errors.js';
import {readMenu,verifiedPosition} from '../menus/repository.js';
import {distanceSql} from '../venues/repository.js';
import {advisorProfile,advisorScore,advisorTop,AdvisorWeights,type AdvisorProfile} from './advisor.js';
import {menuPredicate,type MenuFilter,type Offer} from './filter.js';
import {classify} from './styles.js';

export const modes = ['wishlist','untried','favorite','recommended','filter'] as const;
export const sorts = ['distance','matches','untried','wishlist','relevance','freshness'] as const;
export const statuses = ['current','stale','unpublished','empty'] as const;
export const reasons = ['wishlist','untried','favorite','advisor','neutral_advisor','advisor_brewery','advisor_style','advisor_public_rating','filter'] as const;
type Status = typeof statuses[number];
type Reason = typeof reasons[number];

export interface SearchConditions {
 latitude: number;
 longitude: number;
 radiusKm: number;
 mode: typeof modes[number];
 filter?: MenuFilter;
}
export interface SearchRequest extends SearchConditions {
 sort: typeof sorts[number];
 cursor?: string;
 limit?: number;
}

interface NearbyVenue {
 id: string;
 name: string;
 type: string;
 city: string;
 latitude: number;
 longitude: number;
 distanceKm: number;
 updatedAt: Date | null;
 status: Status;
}
interface VenueOffers extends NearbyVenue {offers: Offer[]}
interface Match extends Offer {
 favorite: boolean;
 score: number;
}
interface VenueResult {
 venue: NearbyVenue;
 matches: number;
 untried: number;
 wishlist: number;
 favorite: number;
 styles: string[];
 untriedStyle: {name: string; count: number} | null;
 relevance: number;
}
interface Evaluated {summary: VenueResult; rows: Match[]}

type PublishedOffer = Pick<Offer,'positionId'|'price'|'currency'|'volumeMl'|'servingFormat'|'available'> & {venueId: string};
type CatalogPosition = Omit<Offer,keyof PublishedOffer> & {positionId: string; catalogVolumeMl: number | null};

async function candidateMenus(db: pg.PoolClient,user: string,query: SearchConditions,allowTest: boolean,id: string | null) {
 // Use the latitude index for a conservative band. Haversine decides
 // inclusion and works across the antimeridian and at the poles.
 const delta = query.radiusKm / 110;
 const menus = await db.query<NearbyVenue>(`
  WITH nearby AS (
   SELECT v.id,v.name,v.type,v.city,v.latitude,v.longitude,${distanceSql('$1','$2')} AS "distanceKm"
   FROM venues v WHERE v.status='verified' AND v.coordinates_source IS NOT NULL
    AND v.latitude BETWEEN $6 AND $7 AND (NOT v.is_test OR $4)
    AND v.type IN ('bar','beer_shop') AND ($5::uuid IS NULL OR v.id=$5)
  )
  SELECT v.*,m.updated_at AS "updatedAt",
   CASE WHEN m.venue_id IS NULL THEN 'unpublished'
        WHEN m.updated_at<=now()-interval '24 hours' THEN 'stale' ELSE 'current' END AS status
  FROM nearby v LEFT JOIN venue_menus m ON m.venue_id=v.id AND m.state='published'
  WHERE v."distanceKm"<=$3
 `,[query.latitude,query.longitude,query.radiusKm,allowTest,id,query.latitude-delta,query.latitude+delta]);
 const venueIds = menus.rows.filter(row => row.status === 'current').map(row => row.id);
 const offers = (await db.query<PublishedOffer>(`
  SELECT venue_id AS "venueId",position_id AS "positionId",price::float8,currency,
   volume_ml AS "volumeMl",serving_format AS "servingFormat",available
  FROM venue_menu_items WHERE state='published' AND venue_id=ANY($1::uuid[])
 `,[venueIds])).rows;
 const positionIds = [...new Set(offers.map(row => row.positionId))];
 const positions = await db.query<CatalogPosition>(`
  WITH ratings AS (
   SELECT c.position_id,bool_or(c.user_id=$1) AS tried,(avg(c.rating_quarters)/4)::float8 AS rating,
    (avg(c.rating_quarters) FILTER(WHERE c.visibility='public' AND p.profile_visibility='public')/4)::float8 AS public_rating
   FROM checkins c JOIN users u ON u.id=c.user_id JOIN user_profiles p ON p.user_id=u.id
   WHERE c.position_id=ANY($2::uuid[]) AND ${active} AND ${visible}
   GROUP BY c.position_id
  )
  SELECT bp.id AS "positionId",r.name,b.id AS "breweryId",b.name AS "breweryName",
   r.style_id AS "styleId",st.name AS "styleName",b.country,bp.packaging,bp.volume_ml AS "catalogVolumeMl",
   r.abv::float8,r.ibu::float8,coalesce(a.tried,false) AS tried,
   a.rating AS "averageRating",a.public_rating AS "publicRating",w.position_id IS NOT NULL AS wished
  FROM beer_positions bp JOIN beer_releases r ON r.id=bp.release_id JOIN breweries b ON b.id=r.brewery_id
   LEFT JOIN styles st ON st.id=r.style_id LEFT JOIN ratings a ON a.position_id=bp.id
   LEFT JOIN wishlist_items w ON w.position_id=bp.id AND w.user_id=$1
  WHERE bp.id=ANY($2::uuid[]) AND ${verifiedPosition}
 `,[user,positionIds]);
 const catalog = new Map(positions.rows.map(row => [row.positionId,row]));
 const byVenue = Map.groupBy(offers,row => row.venueId);
 return menus.rows.map((venue): VenueOffers => {
  const verified = (byVenue.get(venue.id) ?? []).filter(row => catalog.has(row.positionId));
  return {
   ...venue,status:venue.status === 'current' && !verified.length ? 'empty' : venue.status,
   offers:verified.filter(row => row.available).map(row => {
    const data = catalog.get(row.positionId)!;
    return {...data,...row,volumeMl:row.volumeMl ?? data.catalogVolumeMl};
   }),
  };
 });
}

async function profileForSearch(db: pg.PoolClient,user: string) {
 const {items} = await advisorHistory(db,user);
 const ids = [...new Set(items.flatMap(row => row.styleId === null ? [] : [row.styleId]))];
 const {rows} = await db.query<{id: string; name: string}>('SELECT id,name FROM styles WHERE id=ANY($1::uuid[])',[ids]);
 const names = new Map(rows.map(row => [row.id,row.name]));
 return advisorProfile(items.map(row => ({...row,styleName:row.styleId === null ? null : names.get(row.styleId) ?? null})));
}

function styleCounts(rows: Match[]) {
 return [...Map.groupBy(rows.filter(row => row.styleName !== null),row => row.styleName!).entries()]
  .map(([name,items]) => ({name,count:items.length}))
  .sort((a,b) => b.count - a.count || a.name.localeCompare(b.name,'ru'));
}

function summarize(venue: NearbyVenue,rows: Match[],profile: AdvisorProfile): VenueResult {
 // Keep volume of the menu out of relevance. The three best personal
 // signals contribute, with absent slots worth zero.
 const scores = rows.map(row => (row.wished ? 400 : 0) + (row.favorite ? 200 : 0) +
  (profile.personalized ? Math.max(0,row.score - Math.round((row.publicRating ?? 0)*AdvisorWeights.PUBLIC_RATING)) : 0));
 scores.sort((a,b) => b - a);
 const untried = rows.filter(row => !row.tried);
 return {
  venue,matches:rows.length,untried:untried.length,wishlist:rows.filter(row => row.wished).length,
  favorite:rows.filter(row => row.favorite).length,styles:styleCounts(rows).slice(0,3).map(row => row.name),
  untriedStyle:styleCounts(untried)[0] ?? null,relevance:scores.slice(0,3).reduce((sum,score) => sum+score,0),
 };
}

function evaluate(input: VenueOffers,mode: SearchConditions['mode'],profile: AdvisorProfile,predicate: (offer: Offer) => boolean): Evaluated {
 const {offers,...venue} = input;
 const eligible = offers.filter(predicate).map(row => {
  const family = classify(row.styleName)?.family;
  const favorite = (row.styleId !== null && profile.favorites.has(row.styleId)) ||
   (family !== undefined && profile.families.has(family) && (profile.styles.get(row.styleId)?.judgment ?? 0) >= 0);
  return {...row,favorite,score:advisorScore(row,profile)};
 });
 const rows = mode === 'recommended' ? advisorTop(eligible) : eligible.filter(row =>
  mode === 'filter' || (mode === 'wishlist' && row.wished) || (mode === 'untried' && !row.tried) ||
  (mode === 'favorite' && row.favorite));
 return {summary:summarize(venue,rows,profile),rows};
}

function validate(query: SearchConditions) {
 if ((query.mode === 'filter') !== (query.filter !== undefined)) {
  throw new ApiError(422,'INVALID_MENU_FILTER','Выберите сохранённый фильтр для этого режима');
 }
 return menuPredicate(query.filter);
}

function sortKey(row: VenueResult,sort: SearchRequest['sort']) {
 switch (sort) {
  case 'matches': return -row.matches;
  case 'untried': return -row.untried;
  case 'wishlist': return -row.wishlist;
  case 'relevance': return -row.relevance;
  case 'freshness': return -(row.venue.updatedAt?.getTime() ?? 0);
  case 'distance': return row.venue.distanceKm;
 }
}

type Order = [number,number,string];
const compare = (a: Order,b: Order) => a[0]-b[0] || a[1]-b[1] || (a[2] < b[2] ? -1 : Number(a[2] > b[2]));
function cursorScope(user: string,query: SearchRequest,allowTest: boolean) {
 const {cursor:_,limit:__,...conditions} = query;
 return createHash('sha256').update(JSON.stringify([user,allowTest,conditions],(_key,value) =>
  value && typeof value === 'object' && !Array.isArray(value) ? Object.fromEntries(Object.entries(value).sort()) : value)).digest('hex');
}

function cursorOrder(cursor: string,scope: string): Order {
 try {
  const [hash,group,key,id] = JSON.parse(Buffer.from(cursor,'base64url').toString());
  if (hash === scope && (group === 0 || group === 1) && Number.isFinite(key) && typeof id === 'string' &&
   /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(id)) return [group,key,id];
 } catch {
  // Same error for malformed and foreign cursors.
 }
 throw new ApiError(400,'INVALID_CURSOR','Курсор не соответствует условиям поиска');
}

export async function searchFinds(pool: pg.Pool,user: string,query: SearchRequest,allowTest: boolean) {
 const predicate = validate(query);
 const hash = cursorScope(user,query,allowTest);
 const after = query.cursor === undefined ? null : cursorOrder(query.cursor,hash);
 const limit = query.limit ?? 30;
 return transaction(pool,async db => {
  await db.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY');
  const menus = await candidateMenus(db,user,query,allowTest,null);
  const profile = await profileForSearch(db,user);
  const totals = {current:0,stale:0,unpublished:0,empty:0,withMatches:0};
  const order = (summary: VenueResult): Order =>
   [summary.matches ? 0 : 1,sortKey(summary,query.sort),summary.venue.id];
  const result = [];
  for (const menu of menus) {
   totals[menu.status]++;
   const {summary} = evaluate(menu,query.mode,profile,predicate);
   if (summary.matches) totals.withMatches++;
   if (!summary.matches && menu.status === 'current') continue;
   const key = order(summary);
   if (after === null || compare(key,after) > 0) result.push({summary,key});
  }
  result.sort((a,b) => compare(a.key,b.key));
  const page = result.slice(0,limit);
  return {
   items:page.map(row => row.summary),totals,historySize:profile.size,personalized:profile.personalized,
   nextCursor:result.length > limit ? Buffer.from(JSON.stringify([hash,...page.at(-1)!.key])).toString('base64url') : null,
  };
 });
}

function matchReasons(row: Match,mode: SearchConditions['mode'],profile: AdvisorProfile): Reason[] {
 const result: Reason[] = [];
 if (row.wished) result.push('wishlist');
 if (!row.tried) result.push('untried');
 if (row.favorite) result.push('favorite');
 if (mode === 'recommended') {
  result.push(profile.personalized ? 'advisor' : 'neutral_advisor');
  if (profile.personalized && profile.breweries.has(row.breweryId)) result.push('advisor_brewery');
  if (profile.personalized && row.styleId !== null && profile.styles.has(row.styleId)) result.push('advisor_style');
  if (row.publicRating !== null) result.push('advisor_public_rating');
 }
 if (mode === 'filter') result.push('filter');
 return result;
}

export async function venueFinds(pool: pg.Pool,user: string,query: SearchConditions,allowTest: boolean,id: string) {
 const predicate = validate(query);
 return transaction(pool,async db => {
  await db.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY');
  const [candidate] = await candidateMenus(db,user,query,allowTest,id);
  if (!candidate) throw notFound();
  const profile = await profileForSearch(db,user);
  const {summary,rows} = evaluate(candidate,query.mode,profile,predicate);
  const matches = rows.map(row => ({positionId:row.positionId,wished:row.wished,reasons:matchReasons(row,query.mode,profile)}));
  const {menu} = summary.venue.status === 'current' ?
   await readMenu(db,user,id,allowTest,'published',matches.map(row => row.positionId)) : {menu:null};
  return {summary,menu,matches};
 });
}
