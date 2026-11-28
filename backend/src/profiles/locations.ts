import {readFile} from 'node:fs/promises';
import isoCountries from 'i18n-iso-countries';
import {ApiError} from '../http/errors.js';

const english=new Intl.DisplayNames('en',{type:'region'});
const russian=new Intl.DisplayNames('ru',{type:'region'});
const countryCodes=Object.keys(isoCountries.getAlpha2Codes());
const countries=new Map(countryCodes.map(id=>[id,{id,name:english.of(id)!}]));
const transliteration:Record<string,string>={
 а:'a',б:'b',в:'v',г:'g',д:'d',е:'e',ж:'zh',з:'z',и:'i',й:'y',к:'k',л:'l',м:'m',н:'n',
 о:'o',п:'p',р:'r',с:'s',т:'t',у:'u',ф:'f',х:'kh',ц:'ts',ч:'ch',ш:'sh',щ:'shch',
 ъ:'',ы:'y',ь:'',э:'e',ю:'yu',я:'ya',
};
const key=(value:string)=>value.toLowerCase().normalize('NFKD').replace(/\p{Mark}/gu,'')
 .replace(/[а-я]/g,letter=>transliteration[letter]??letter).replace(/[^\p{Letter}\p{Number}]/gu,'');

function match(name:string,aliases:readonly string[],query:string) {
 if(!query) return 3;
 if(name===query) return 0;
 if(aliases.includes(query)) return 1;
 if(name.startsWith(query)||aliases.some(alias=>alias.startsWith(query))) return 2;
 if(name.includes(query)||aliases.some(alias=>alias.includes(query))) return 3;
 return 4;
}

const countryIndex=Array.from(countries.values(),country=>({
 ...country,key:key(country.name),aliases:[key(country.id),key(russian.of(country.id)!)],
})).sort((a,b)=>a.name.localeCompare(b.name,'en'));

type City={latitude:number;longitude:number;cyrillicNames:string[];id:number;name:string;countryId:string;key:string;aliases:string[];population:number;regionCode:string|null};
type CityIndex={byId:Map<number,City>;byCountry:Map<string,City[]>};
let index:Promise<CityIndex>|undefined;

function cityIndex():Promise<CityIndex> {
 // The pinned package bundles the GeoNames cities1000 UTF-8 dump. Read the TSV
 // in one operation to avoid the package iterator's byte-at-a-time system calls.
 index??=readFile(new URL('cities1000.txt',import.meta.resolve('cities-1000-structured')),'utf8').then(raw=>{
  const byId=new Map<number,City>();
  const byCountry=new Map<string,City[]>();
  for(const line of raw.split('\n')) {
   const [rawId,name,ascii,aliases,lat,lon,,,countryId,,regionCode,,,,population]=line.split('\t');
   if(!rawId||!name||!countryId||!countries.has(countryId)) continue;
   const id=Number(rawId);
   const names=aliases?.split(',')??[];
   const entry={id,name,latitude:Number(lat),longitude:Number(lon),
    cyrillicNames:countryId==='RU'?names.filter(alias=>/^[А-Яа-яЁё -]+$/.test(alias)):[],countryId,key:key(name),regionCode:regionCode||null,population:Number(population)||0,
    aliases:[...new Set([ascii??'',...names].map(key).filter(Boolean))]};
   byId.set(id,entry);
   const group=byCountry.get(countryId)??[];
   if(!byCountry.has(countryId)) byCountry.set(countryId,group);
   group.push(entry);
  }
  for(const group of byCountry.values()) group.sort((a,b)=>b.population-a.population||a.id-b.id);
  return {byId,byCountry};
 }).catch((error:unknown)=>{index=undefined;throw error;});
 return index;
}

export function searchCountries(query:string) {
 const normalized=key(query);
 if(query.trim()&&!normalized) return [];
 return countryIndex.map(entry=>({entry,rank:match(entry.key,entry.aliases,normalized)}))
  .filter(result=>result.rank<4).sort((a,b)=>a.rank-b.rank).slice(0,20)
  .map(({entry})=>({id:entry.id,name:entry.name}));
}

async function matchingCities(countryId:string,query:string) {
 if(!countries.has(countryId)) throw new ApiError(422,'INVALID_COUNTRY','Выберите страну из списка');
 const normalized=key(query);
 if(query.trim()&&!normalized) return [];
 const group=(await cityIndex()).byCountry.get(countryId)??[];
 return group.map(entry=>({entry,rank:match(entry.key,entry.aliases,normalized)}))
  .filter(result=>result.rank<4).sort((a,b)=>a.rank-b.rank).slice(0,20)
  .map(({entry})=>entry);
}

export async function searchCities(countryId:string,query:string) {
 return (await matchingCities(countryId,query)).map(({id,name,countryId,regionCode})=>({id,name,countryId,regionCode}));
}

export async function russianCityCenters(query:string) {
 const prefix=query.trim().toLowerCase();
 if(prefix.length<2) return [];
 return (await matchingCities('RU',query)).map(entry=>({
  id:entry.id,city:entry.cyrillicNames.find(alias=>alias.toLowerCase()===prefix)
    ??entry.cyrillicNames.find(alias=>alias.toLowerCase().startsWith(prefix))??entry.name,
  regionCode:entry.regionCode,latitude:entry.latitude,longitude:entry.longitude,
 }));
}

export async function resolveLocation(countryId:string|null,cityId:number|null) {
 const country=countryId===null?null:countries.get(countryId);
 if(country===undefined) throw new ApiError(422,'INVALID_COUNTRY','Выберите страну из списка');
 const city=cityId===null?null:(await cityIndex()).byId.get(cityId);
 if(city===undefined||city&&city.countryId!==countryId)
  throw new ApiError(422,'INVALID_CITY','Выберите город в указанной стране');
 return {country:country?.name??null,city:city?.name??null,countryId,cityId};
}
