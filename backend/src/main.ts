import {databaseConfig} from "./db/config.js";
import {MediaService} from "./media/service.js";
import {FileStorage} from "./media/storage.js";
import { createPool } from "./db/pool.js";
import { buildApp } from "./app.js";
import { readConfig } from "./config.js";
async function main() {
  const config = readConfig();
  const database=databaseConfig();
  const pool = createPool(database.url,database.max);
  const storage=new FileStorage(process.env.MEDIA_ROOT ?? "../work/media");
  await storage.initialize();
  const app = buildApp(config,pool,new MediaService(pool,storage));
  pool.on("error",()=>app.log.error("database connection lost"));
  app.addHook("onClose",async()=>{await pool.end();});
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
  console.error("Backend startup failed; check HOST, PORT, NODE_ENV, LOG_LEVEL and DATABASE_URL");
  process.exitCode = 1;
});
