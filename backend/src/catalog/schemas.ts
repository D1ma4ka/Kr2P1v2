import * as s from '../http/schemas.js';
const text=(max:number)=>({type:'string',minLength:1,maxLength:max,pattern:'\\S'});
const nullable=(schema:unknown)=>({anyOf:[schema,{type:'null'}]});
export const inputFields={
 name:text(200),breweryName:text(200),breweryId:nullable(s.uuid),breweryCountry:s.nullableText(100),breweryCity:s.nullableText(120),
 brewerySource:s.nullableText(2000),styleName:s.nullableText(120),brand:s.nullableText(200),
 abv:nullable({type:'number',minimum:0,maximum:100}),ibu:nullable({type:'number',minimum:0,maximum:1000}),
 packaging:s.nullableText(80),volumeMl:nullable({type:'integer',minimum:1,maximum:100000}),
 edition:s.nullableText(200),variant:s.nullableText(200),description:s.nullableText(5000),source:text(2000),
 gtin:nullable({type:'string',pattern:'^(?:[0-9]{8}|[0-9]{12,14})$'}),imageAssetId:nullable(s.uuid),imageSource:s.nullableText(2000),
 rightsConfirmed:{type:'boolean'},
};
export const input=s.object(inputFields,['name','breweryName','source']);
export const position=s.object({id:s.uuid,requestedId:s.uuid,canonicalId:s.uuid,releaseId:s.uuid,name:{type:'string'},
 breweryId:s.uuid,breweryName:{type:'string'},styleId:nullable(s.uuid),styleName:s.nullableText(120),brand:s.nullableText(200),
 abv:nullable({type:'number'}),ibu:nullable({type:'number'}),description:s.nullableText(5000),packaging:s.nullableText(80),
 volumeMl:nullable({type:'integer'}),edition:s.nullableText(200),variant:s.nullableText(200),source:{type:'string'},
 status:{type:'string',enum:['verified']},verifiedAt:s.timestamp,createdAt:s.timestamp,
 imageAssetId:nullable(s.uuid),imageSource:s.nullableText(2000)});
export const brewery=s.object({id:s.uuid,name:{type:'string'},country:s.nullableText(100),city:s.nullableText(120),description:s.nullableText(5000),
 source:{type:'string'},status:{type:'string',enum:['verified']},imageAssetId:nullable(s.uuid),imageSource:s.nullableText(2000),createdAt:s.timestamp});
export const proposal=s.object({id:s.uuid,status:{type:'string',enum:['pending','accepted','rejected']},version:s.version,
 proposedData:input,positionId:nullable(s.uuid),decisionReason:s.nullableText(2000),createdAt:s.timestamp,decidedAt:nullable(s.timestamp)});
export const similarCandidate=s.object({id:s.uuid,requestedId:s.uuid,canonicalId:s.uuid,name:{type:'string'},breweryName:{type:'string'},styleName:s.nullableText(120),abv:{type:['number','null']},packaging:s.nullableText(80),volumeMl:{type:['integer','null']},imageAssetId:nullable(s.uuid),match:{type:'string',enum:['barcode','name','brewery','similar']},score:{type:'number',minimum:0,maximum:1}});
export const similarQuery=s.object({name:text(200),breweryName:text(200),gtin:{type:'string',pattern:'^(?:[0-9]{8}|[0-9]{12,14})$'},abv:{type:'string',pattern:'^(?:100|[0-9]{1,2})(?:\\.[0-9]{1,2})?$'},packaging:s.nullableText(80),volumeMl:{type:'string',pattern:'^[1-9][0-9]{0,4}$'}},['name','breweryName']);
export const query=s.object({limit:s.pageQuery.properties.limit,cursor:{type:'string',maxLength:2048,pattern:'^[A-Za-z0-9_-]+$'},
 q:{type:'string',maxLength:120},breweryId:s.uuid,styleId:s.uuid,packaging:text(80),
 abvMin:{type:'string',pattern:'^(?:100|[0-9]{1,2})(?:\\.[0-9]{1,2})?$'},
 abvMax:{type:'string',pattern:'^(?:100|[0-9]{1,2})(?:\\.[0-9]{1,2})?$'},sort:{type:'string',enum:['newest','name','abv']}},[]);
