import {MediaService} from "../src/media/service.js";
import {FileStorage} from "../src/media/storage.js";
import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFile} from 'node:fs/promises';
import SwaggerParser from '@apidevtools/swagger-parser';
import {buildApp} from '../src/app.js';
import {readConfig} from '../src/config.js';
import {createPool} from '../src/db/pool.js';
test('OpenAPI is valid and exactly matches registered HTTP schemas',async()=>{
 const pool=createPool('postgresql://unused@127.0.0.1/unused');
 const app=buildApp(readConfig({NODE_ENV:'test',LOG_LEVEL:'silent'}),pool,new MediaService(pool,new FileStorage('../work/media')));
 try {
  await app.ready();
  assert.equal(await readFile('../docs/api/openapi.yaml','utf8'),app.swagger({yaml:true}));
  await SwaggerParser.validate('../docs/api/openapi.yaml');
 } finally {await app.close();await pool.end();}
});
