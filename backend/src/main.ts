import { buildApp } from "./app.js";
import { readConfig } from "./config.js";
async function main() {
  const config = readConfig();
  const app = buildApp(config);
  let stopping = false;
  const stop = async () => {
    if (stopping) return;
    stopping = true;
    const deadline = setTimeout(() => process.exit(1), 10_000).unref();
    try { await app.close(); app.log.info("server stopped"); }
    catch { process.exitCode = 1; }
    finally { clearTimeout(deadline); }
  };
  process.once("SIGINT", () => { void stop(); });
  process.once("SIGTERM", () => { void stop(); });
  try { await app.listen({ host: config.host, port: config.port }); }
  catch { await app.close(); throw new Error("Server failed to start"); }
}
main().catch(() => {
  // Do not print environment values in failure diagnostics.
  console.error("Backend startup failed; check HOST, PORT, NODE_ENV and LOG_LEVEL");
  process.exitCode = 1;
});
