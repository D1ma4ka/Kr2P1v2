import assert from "node:assert/strict";
import { test } from "node:test";
import { buildApp } from "../src/app.js";
import { readConfig } from "../src/config.js";
const config = readConfig({ NODE_ENV: "test", LOG_LEVEL: "silent" });

test("configuration rejects malformed port, host and environment", () => {
  assert.equal(config.host, "127.0.0.1");
  for (const PORT of ["0", "-1", "65536", "3000x", "3.2", ""]) assert.throws(() => readConfig({ PORT }));
  assert.throws(() => readConfig({ HOST: "https://example.com" }));
  assert.throws(() => readConfig({ NODE_ENV: "prod" }));
  assert.throws(() => readConfig({ LOG_LEVEL: "unknown" }));
});
test("real HTTP liveness, 404, response headers and socket shutdown", async () => {
  const app = buildApp(config);
  const address = await app.listen({ host: "127.0.0.1", port: 0 });
  try {
    const health = await fetch(`${address}/health/live`);
    assert.equal(health.status, 200);
    assert.deepEqual(await health.json(), { status: "ok" });
    assert.equal(health.headers.get("cache-control"), "no-store");
    assert.equal(health.headers.get("x-content-type-options"), "nosniff");
    for (const path of ["/", "/health/ready", "/api/login", "/api/feed", "/private?token=do-not-reflect"]) {
      const response = await fetch(`${address}${path}`);
      assert.equal(response.status, 404);
      const body = await response.text();
      assert.equal(body.includes("do-not-reflect"), false);
      assert.equal(JSON.parse(body).error.code, "NOT_FOUND");
    }
    assert.equal((await fetch(`${address}/health/live`, { method: "POST" })).status, 404);
  } finally { await app.close(); }
  await assert.rejects(fetch(`${address}/health/live`, { signal: AbortSignal.timeout(1000) }));
});
test("invalid JSON and oversized bodies fail without reflecting content", async () => {
  const app = buildApp(config);
  const address = await app.listen({ host: "127.0.0.1", port: 0 });
  try {
    for (const [body, status] of [["{secret-value", 400], [JSON.stringify({ data: "x".repeat(20_000) }), 413]] as const) {
      const response = await fetch(`${address}/health/live`, {
        method: "POST", headers: { "content-type": "application/json" }, body });
      assert.equal(response.status, status);
      assert.equal((await response.text()).includes("secret-value"), false);
    }
  } finally { await app.close(); }
});
