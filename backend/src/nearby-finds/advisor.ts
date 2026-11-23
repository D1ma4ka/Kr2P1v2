import type {AdvisorHistoryRow} from '../checkins/repository.js';
import {classify} from './styles.js';

// Server evaluation of LocalDiscovery Advisor's MY_CHOICE mode. Keep the
// integer arithmetic and the first three diversity picks identical to P8.6.
export const AdvisorWeights = {
 WINDOW:100, MIN_HISTORY:3, TOP:3, NEUTRAL_RATING:12, RATING_SMOOTHING:200,
 REPEAT_WEIGHTS:[100,25,10], BREWERY_UNIQUE:18, BREWERY_REPEAT:6, STYLE_UNIQUE:11,
 BREWERY_RATING:3, STYLE_RATING:1, POSITION_RATING:2, MAX_POSITIVE_RATING_BONUS:600,
 NOVELTY:50, NOVELTY_HABIT:50, PUBLIC_RATING:10, DIVERSIFY_BREWERY:700, DIVERSIFY_STYLE:100,
} as const;

type Recent = AdvisorHistoryRow & {recency: number; styleName: string | null};
const W = AdvisorWeights;
const empty = {unique:0,repeats:0,recent:0,judgment:0};

function affinity(rows: Recent[]) {
 const positions = Map.groupBy(rows, row => row.positionId);
 let ratedWeight = 0, ratedSum = 0, repeats = 0;
 for (const position of positions.values()) {
  repeats += Math.min(position.length - 1,W.REPEAT_WEIGHTS.length - 1);
  for (const [index,row] of position.slice(0,W.REPEAT_WEIGHTS.length).entries()) {
   if (row.ratingQuarters === null) continue;
   const weight = Math.trunc(row.recency * W.REPEAT_WEIGHTS[index]! / 100);
   ratedWeight += weight;
   ratedSum += weight * (row.ratingQuarters - W.NEUTRAL_RATING);
  }
 }
 return {unique:positions.size,repeats,recent:rows[0]!.recency,
  judgment:Math.trunc(100 * ratedSum / (ratedWeight + W.RATING_SMOOTHING))};
}

export function advisorProfile(history: (AdvisorHistoryRow & {styleName: string | null})[]) {
 const rows = history.map((row,index) => ({...row,recency:W.WINDOW - Math.floor(index / 2)}));
 const by = (key: (row: Recent) => string | null) =>
  new Map([...Map.groupBy(rows,key)].map(([id,group]) => [id,affinity(group)]));
 const styles = by(row => row.styleId);
 const favorites = new Set(rows.filter(row => (row.ratingQuarters ?? 0) >= 16 && row.styleId !== null &&
  (styles.get(row.styleId)?.judgment ?? 0) > 0).map(row => row.styleId!));
 const families = new Set(rows.filter(row => row.styleId !== null && favorites.has(row.styleId))
  .map(row => classify(row.styleName)?.family).filter(family => family !== undefined));
 return {
  size:rows.length, personalized:rows.length >= W.MIN_HISTORY,
  novelty:W.NOVELTY + Math.trunc(W.NOVELTY_HABIT * rows.filter(row => row.firstForPosition).length / Math.max(1,rows.length)),
  breweries:by(row => row.breweryId), styles, positions:by(row => row.positionId), favorites, families,
 };
}
export type AdvisorProfile = ReturnType<typeof advisorProfile>;
interface Candidate {
 positionId: string;
 breweryId: string;
 styleId: string | null;
 tried: boolean;
 publicRating: number | null;
 score: number;
}

export function advisorScore(row: Omit<Candidate,'score'>, profile: AdvisorProfile) {
 const rating = Math.round((row.publicRating ?? 0) * W.PUBLIC_RATING);
 if (!profile.personalized) return rating;
 const brewery = profile.breweries.get(row.breweryId) ?? empty;
 const style = row.styleId === null ? empty : (profile.styles.get(row.styleId) ?? empty);
 const position = profile.positions.get(row.positionId) ?? empty;
 const judgment = W.BREWERY_RATING * brewery.judgment + W.STYLE_RATING * style.judgment + W.POSITION_RATING * position.judgment;
 const habit = Math.min(brewery.unique,30) * W.BREWERY_UNIQUE + Math.min(brewery.repeats,12) * W.BREWERY_REPEAT +
  Math.trunc(brewery.recent / 2) + Math.min(style.unique,15) * W.STYLE_UNIQUE + Math.trunc(style.recent / 3);
 return rating + (row.tried ? 0 : profile.novelty) + habit + Math.min(judgment,W.MAX_POSITIVE_RATING_BONUS);
}

export function advisorTop<T extends Candidate>(rows: T[]): T[] {
 const remaining = [...rows].sort((a,b) => b.score - a.score || a.positionId.localeCompare(b.positionId,'en'));
 const top: T[] = [];
 while (top.length < W.TOP && remaining.length) {
  const adjusted = (row: T) => row.score - top.filter(pick => pick.breweryId === row.breweryId).length * W.DIVERSIFY_BREWERY -
   top.filter(pick => row.styleId !== null && pick.styleId === row.styleId).length * W.DIVERSIFY_STYLE;
  const best = remaining.reduce((a,b) => adjusted(b) > adjusted(a) ? b : a);
  top.push(best);
  remaining.splice(remaining.indexOf(best),1);
 }
 return top;
}
