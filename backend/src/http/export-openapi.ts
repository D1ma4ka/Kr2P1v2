import { mkdir,writeFile } from "node:fs/promises";
import { buildApp } from "../app.js";
import { readConfig } from "../config.js";
import { createPool } from "../db/pool.js";
const pool=createPool('postgresql://unused@127.0.0.1/unused'); // schema generation never connects
const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),pool);
try {
 await app.ready(); await mkdir('../docs/api',{recursive:true});
 await writeFile('../docs/api/openapi.yaml',app.swagger({yaml:true}));
 console.log('Generated OpenAPI from registered route schemas; no DB connection');
} finally { await app.close(); await pool.end(); }
