import { mkdir, open, readFile, unlink, access } from 'node:fs/promises';
import {constants} from 'node:fs';
import { resolve, join } from 'node:path';
export interface Storage {
 put(key:string,data:Buffer):Promise<void>;
 get(key:string):Promise<Buffer>;
 delete(key:string):Promise<void>;
 check():Promise<void>;
}
export class FileStorage implements Storage {
 private readonly root:string;
 constructor(root:string) { this.root=resolve(root); }
 private path(key:string) {
  if(!/^[0-9a-f-]{36}-(?:original|thumbnail)\.jpg$/.test(key)) throw new Error('Invalid storage key');
  return join(this.root,key);
 }
 async initialize() {await mkdir(this.root,{recursive:true,mode:0o700});await this.check();}
 async check() {await access(this.root,constants.R_OK|constants.W_OK);}
 async syncDirectory() {
  const dir=await open(this.root,'r');
  try {await dir.sync();} finally {await dir.close();}
 }
 async put(key:string,data:Buffer) {
  await mkdir(this.root,{recursive:true,mode:0o700});
  const file=await open(this.path(key),'wx',0o600);
  try {await file.writeFile(data);await file.sync();} finally {await file.close();}
  await this.syncDirectory();
 }
 async get(key:string) {return readFile(this.path(key));}
 async delete(key:string) {
  try {await unlink(this.path(key));await this.syncDirectory();} catch(error) {if(!(error && typeof error==='object' && 'code' in error && error.code==='ENOENT')) throw error;}
 }
}
