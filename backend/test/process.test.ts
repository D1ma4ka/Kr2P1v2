import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer, type AddressInfo } from "node:net";
import { test } from "node:test";

test("entrypoint serves HTTP, omits sensitive input from logs and exits on SIGTERM", { timeout: 10_000 }, async () => {
  const reservation = createServer();
  reservation.listen(0, "127.0.0.1");
  await once(reservation, "listening");
  const port = (reservation.address() as AddressInfo).port;
  await new Promise<void>((resolve, reject) => reservation.close(error => error ? reject(error) : resolve()));
  const child = spawn(process.execPath, ["dist/src/main.js"], {
    env: { ...process.env, HOST: "127.0.0.1", PORT: String(port), NODE_ENV: "test", LOG_LEVEL: "info" },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let output = "";
  child.stdout.on("data", data => { output += data.toString(); });
  child.stderr.on("data", data => { output += data.toString(); });
  const exited = once(child, "exit");
  try {
    await new Promise<void>((resolve, reject) => {
      const deadline = setTimeout(() => reject(new Error("server startup timeout")), 5000);
      child.stdout.on("data", () => {
        if (output.includes("Server listening")) { clearTimeout(deadline); resolve(); }
      });
      child.once("error", error => { clearTimeout(deadline); reject(error); });
      child.once("exit", code => { clearTimeout(deadline); reject(new Error(`early exit ${code}`)); });
    });
    const sentinel = "p1-sensitive-input-sentinel";
    const response = await fetch(`http://127.0.0.1:${port}/health/live?token=${sentinel}`, {
      headers: { authorization: `Bearer ${sentinel}`, cookie: `session=${sentinel}` },
    });
    assert.equal(response.status, 200);
    await response.arrayBuffer();
    child.kill("SIGTERM");
    const [code, signal] = await exited;
    assert.equal(code, 0);
    assert.equal(signal, null);
    assert.ok(output.includes("server stopped"));
    assert.equal(output.includes(sentinel), false);
  } finally { if (child.exitCode === null) child.kill("SIGKILL"); }
});
