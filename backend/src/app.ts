import Fastify, { LogController } from "fastify";
import type { Config } from "./config.js";
export function buildApp(config: Config) {
  const app = Fastify({
    logger: { level: config.logLevel,
      redact: ["req.headers.authorization", "req.headers.cookie", "res.headers.set-cookie"] },
    logController: new LogController({ disableRequestLogging: true }),
    trustProxy: false,
    bodyLimit: 16 * 1024,
    requestTimeout: 10_000,
    connectionTimeout: 10_000,
    keepAliveTimeout: 5_000,
  });
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
  return app;
}
