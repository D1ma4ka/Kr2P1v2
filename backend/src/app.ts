import {databaseReady} from "./db/readiness.js";
import type { MediaService } from "./media/service.js";
import swagger from "@fastify/swagger";
import type pg from "pg";
import { foundationRoutes } from "./http/routes.js";
import { ApiError } from "./http/errors.js";
import { errors, object } from "./http/schemas.js";
import Fastify, { LogController } from "fastify";
import type { Config } from "./config.js";
import {accountRoutes} from './identity/routes.js';
import {accountMailer, type AccountMailer} from './mail/accounts.js';
export function buildApp(config: Config, pool?: pg.Pool, media?: MediaService, mail?:AccountMailer) {
  const app = Fastify({
    logger: { level: config.logLevel,
      redact: ["req.headers.authorization", "req.headers.cookie", "res.headers.set-cookie"] },
    logController: new LogController({ disableRequestLogging: true }),
    trustProxy: false,
    ajv: { customOptions: { removeAdditional: false, coerceTypes: false } },
    bodyLimit: 16 * 1024,
    requestTimeout: 10_000,
    connectionTimeout: 10_000,
    keepAliveTimeout: 5_000,
  });
  app.register(swagger,{openapi:{openapi:"3.0.3",info:{title:"KraPiva API",version:"1.0.0"},
    servers:[{url:"/"}],components:{securitySchemes:{bearerAuth:{type:"http",scheme:"bearer"}}}}});
  app.addHook("onSend", async (_request, reply, payload) => {
    reply.header("x-content-type-options", "nosniff");
    reply.header("cache-control", "no-store");
    return payload;
  });
  app.addHook("onResponse", async (request, reply) => {
    app.log.info({ requestId: request.id, method: request.method,
      route: request.routeOptions.url ?? "unmatched", statusCode: reply.statusCode }, "request completed");
  });
  app.setNotFoundHandler((_request, reply) => {
    return reply.code(404).send({ error: { code: "NOT_FOUND", message: "Ресурс не найден" } });
  });
  app.setErrorHandler((error, request, reply) => {
    if (error instanceof ApiError) {
      if (error.statusCode === 429) reply.header("retry-after","60");
      return reply.code(error.statusCode).send({error:{code:error.code,message:error.message}});
    }
    if (error && typeof error === 'object' && 'code' in error &&
      ['55P03','57014','53300','57P01','ECONNREFUSED'].includes(String(error.code)))
      return reply.code(503).send({error:{code:'UNAVAILABLE',message:'Сервис временно недоступен'}});
    const candidate = error !== null && typeof error === "object" && "statusCode" in error
      ? error.statusCode : undefined;
    const status = typeof candidate === "number" && Number.isInteger(candidate) && candidate >= 400 && candidate < 500
      ? candidate : 500;
    // No raw request bodies, query strings, credentials or exception messages in logs.
    app.log.error({ requestId: request.id, statusCode: status }, "request failed");
    return reply.code(status).send({ error: {
      code: status === 500 ? "INTERNAL_ERROR" : "INVALID_REQUEST",
      message: status === 500 ? "Внутренняя ошибка сервера" : "Некорректный запрос" } });
  });
  app.get("/health/live", { schema: { response: { 200: {
    type: "object", additionalProperties: false, required: ["status"],
    properties: { status: { type: "string", const: "ok" } } } } } }, async () => ({ status: "ok" }));
  if (pool) {
    const sender=mail??accountMailer({...process.env,NODE_ENV:config.environment});
    if(!mail) app.addHook('onClose',async()=>sender.close());
    app.register(async auth=>accountRoutes(auth,pool,sender));
    app.register(async api => {
      api.get("/health/ready",{schema:{response:{...errors,200:object({status:{type:'string',enum:['ready']}})}}},async()=>{
        try {
          await databaseReady(pool);
          if(media) await media.ready();
        }
        catch { throw new ApiError(503,"UNAVAILABLE","Зависимости сервера недоступны"); }
        return {status:"ready"};
      });
      api.register(async secured => foundationRoutes(secured,pool,media));
    });
  }
  return app;
}
