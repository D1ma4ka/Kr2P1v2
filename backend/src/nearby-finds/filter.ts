import {ApiError} from '../http/errors.js';
import {object,nullableText} from '../http/schemas.js';
import {classify,normalize,styleVocabulary} from './styles.js';

const sorts = ['MENU','NAME','STYLE','BREWERY','ABV_ASC','ABV_DESC','IBU_ASC','IBU_DESC','RATING','PRICE','NEW_FIRST','RELEVANCE'] as const;
type MenuSort = typeof sorts[number];
type RangeKind = 'abv' | 'ibu' | 'volume' | 'price';
export type MenuFilter = {
 version: 1;
 query: string;
 families: string[];
 styles: string[];
 breweries: string[];
 countries: string[];
 formats: string[];
 tried: 'ALL' | 'YES' | 'NO';
 ratingMin: string;
 currency?: string | null;
 available: boolean;
 sort: MenuSort;
} & Record<`${Exclude<RangeKind,'volume'>}Min`,string> & Partial<Record<'volumeMin' | 'volumeMax',string>> &
 Record<`${Exclude<RangeKind,'volume'>}Max`,string>;

export interface Offer {
 positionId: string;
 name: string;
 breweryId: string;
 breweryName: string;
 styleId: string | null;
 styleName: string | null;
 country: string | null;
 packaging: string | null;
 servingFormat: string | null;
 available: boolean;
 abv: number | null;
 ibu: number | null;
 volumeMl: number | null;
 price: number | null;
 currency: string | null;
 averageRating: number | null;
 publicRating: number | null;
 tried: boolean;
 wished: boolean;
}

const values = (maxLength: number) => ({type:'array',maxItems:250,items:{type:'string',maxLength}});
const bound = {type:'string',maxLength:40};
const properties = {
 version:{type:'integer',enum:[1]},query:{type:'string',maxLength:250},
 families:values(80),styles:values(80),breweries:values(40),countries:values(120),formats:values(120),
 tried:{type:'string',enum:['ALL','YES','NO']},abvMin:bound,abvMax:bound,ibuMin:bound,ibuMax:bound,
 volumeMin:bound,volumeMax:bound,ratingMin:bound,priceMin:bound,priceMax:bound,currency:nullableText(3),
 available:{type:'boolean'},sort:{type:'string',enum:sorts},
};
export const filterSchema = object(properties,Object.keys(properties).filter(key => !['volumeMin','volumeMax','currency'].includes(key)));
const invalidFilter = () => new ApiError(422,'INVALID_MENU_FILTER','Проверьте условия фильтра Smart Bar');

function range(min: string | undefined,max: string | undefined,limit: number) {
 const parse = (raw: string | undefined, fallback: number) => {
  if (!raw?.trim()) return fallback;
  const value = raw.replace(',','.');
  if (!/^[+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?$/i.test(value)) throw invalidFilter();
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0 || number > limit) throw invalidFilter();
  return number;
 };
 const low = parse(min,0), high = parse(max,limit);
 if (low > high) throw invalidFilter();
 if (!min?.trim() && !max?.trim()) return () => true;
 return (value: number | null) => value !== null && value >= low && value <= high;
}

function format(value: string | null) {
 if (value === null) return null;
 const key = normalize(value);
 if (key === 'банка') return 'can';
 if (key === 'бутылка') return 'bottle';
 if (['кран','разливное','draught','tap','keg'].includes(key)) return 'draft';
 return key;
}
const families = new Set(styleVocabulary.map(([,family]) => family));
const styles = new Set(styleVocabulary.map(([key]) => key));

export function menuPredicate(filter: MenuFilter | undefined) {
 if (!filter) return () => true;
 if (filter.families.some(key => !families.has(key)) || filter.styles.some(key => !styles.has(key) && !key.startsWith('catalog:'))) throw invalidFilter();
 const abv = range(filter.abvMin,filter.abvMax,100), ibu = range(filter.ibuMin,filter.ibuMax,1000);
 const volume = range(filter.volumeMin,filter.volumeMax,100000), rating = range(filter.ratingMin,undefined,5);
 const price = range(filter.priceMin,filter.priceMax,9999999999.99);
 const priced = !!(filter.priceMin.trim() || filter.priceMax.trim());
 if (priced && !/^[A-Z]{3}$/.test(filter.currency ?? '')) throw invalidFilter();
 const words = normalize(filter.query).split(' ').filter(Boolean);
 return (row: Offer) => {
  const style = classify(row.styleName);
  const name = words.length ? normalize(row.name) : "";
  const key = style?.key ?? (row.styleId === null ? null : `catalog:${row.styleId}`);
  return ((!filter.families.length && !filter.styles.length) ||
    (style !== undefined && filter.families.includes(style.family)) || (key !== null && filter.styles.includes(key))) &&
   (!filter.breweries.length || filter.breweries.includes(row.breweryId)) &&
   (!filter.countries.length || (row.country !== null && filter.countries.includes(normalize(row.country)))) &&
   (!filter.formats.length || [format(row.packaging),format(row.servingFormat)].some(key => key !== null && filter.formats.includes(key))) &&
   (filter.tried === 'ALL' || row.tried === (filter.tried === 'YES')) && (!filter.available || row.available) &&
   abv(row.abv) && ibu(row.ibu) && volume(row.volumeMl) && rating(row.averageRating) &&
   (!priced || (row.currency === filter.currency && price(row.price))) &&
   words.every(word => name.includes(word));
 };
}
