import { ApiError } from "./errors.js";
export function decodeCursor(cursor?: string): [string,string] | null {
  if (!cursor) return null;
  try {
    const value: unknown = JSON.parse(Buffer.from(cursor,"base64url").toString("utf8"));
    if (!Array.isArray(value) || value.length !== 2 || typeof value[0] !== "string" || typeof value[1] !== "string" ||
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3,6}Z$/.test(value[0]) || !Number.isFinite(Date.parse(value[0])) ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value[1])) throw Error();
    return [value[0],value[1]];
  } catch { throw new ApiError(400,"INVALID_CURSOR","Некорректный курсор"); }
}
export function encodeCursor(row: { createdAt: string; id: string }) {
  return Buffer.from(JSON.stringify([row.createdAt,row.id])).toString("base64url");
}
