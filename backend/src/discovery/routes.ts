import type {FastifyInstance} from 'fastify';
import type pg from 'pg';
import * as s from '../http/schemas.js';
import {nearbyFields,venue} from '../venues/routes.js';
import type {NearbyQuery} from '../venues/repository.js';
import * as repo from './repository.js';

const distance={type:'number',minimum:0};
const place=s.object({...venue.properties,distanceKm:distance});
const window={startsAt:s.timestamp,endsAt:s.timestamp};
const event=s.object({
  id:s.uuid,title:{type:'string'},description:{type:'string'},...window,venueId:s.uuid,venueName:{type:'string'},
  city:{type:'string'},address:s.nullableText(1000),distanceKm:distance,badgeTitles:{type:'array',items:{type:'string'}},
});
const badge=s.object({
  id:s.uuid,title:{type:'string'},description:{type:'string'},...window,venueId:s.uuid,venueName:{type:'string'},
  eventId:{anyOf:[s.uuid,{type:'null'}]},eventTitle:s.nullableText(300),distanceKm:distance,
});
const response=s.object({id:s.uuid,version:s.version});
const entry={
  version:{type:'integer',minimum:0,maximum:2147483646},venueId:s.uuid,title:{type:'string',minLength:1,maxLength:300},
  description:{type:'string',minLength:1,maxLength:4000},...window,
};
type EventInput=Parameters<typeof repo.saveEvent>[3];
type BadgeInput=Parameters<typeof repo.saveBadge>[3];

export async function discoveryRoutes(app:FastifyInstance,pool:pg.Pool,allowTest:boolean) {
  const secure={security:[{bearerAuth:[]}]};
  app.post<{Body:NearbyQuery}>('/v1/discover/nearby',{
    schema:{...secure,body:s.object(nearbyFields),response:{...s.errors,200:s.object({
      places:{type:'array',items:place},events:{type:'array',items:event},badges:{type:'array',items:badge},
    })}},
  },req=>repo.nearbyDiscovery(pool,req.body,allowTest));
  app.put<{Params:{id:string};Body:EventInput}>('/v1/moderation/events/:id',{
    schema:{...secure,params:s.idParams,body:s.object({...entry,source:{type:'string',minLength:1,maxLength:2000},
      published:{type:'boolean'}}),response:{...s.errors,200:response}},
  },req=>repo.saveEvent(pool,req.userId,req.params.id,req.body,allowTest));
  app.put<{Params:{id:string};Body:BadgeInput}>('/v1/moderation/local-badges/:id',{
    schema:{...secure,params:s.idParams,body:s.object({...entry,eventId:{anyOf:[s.uuid,{type:'null'}]},active:{type:'boolean'}}),
      response:{...s.errors,200:response}},
  },req=>repo.saveBadge(pool,req.userId,req.params.id,req.body,allowTest));
  app.put<{Params:{id:string};Body:{checkinId:string;evidence:string}}>('/v1/moderation/local-badges/:id/attendance',{
    schema:{...secure,params:s.idParams,body:s.object({checkinId:s.uuid,evidence:{type:'string',minLength:1,maxLength:2000}}),
      response:{...s.errors,200:s.object({verified:{type:'boolean'}})}},
  },req=>repo.verifyAttendance(pool,req.userId,req.params.id,req.body.checkinId,req.body.evidence,allowTest));
}
