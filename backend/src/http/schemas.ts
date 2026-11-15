export const uuid = { type: "string", format: "uuid" };
export const timestamp = { type: "string", format: "date-time" };
export const nullableText = (maxLength: number) => ({ anyOf: [{ type: "string", maxLength }, { type: "null" }] });
export const visibility = { type: "string", enum: ["private", "friends", "public"] };
export function object(properties: Record<string, unknown>, required = Object.keys(properties)) {
  return { type: "object", additionalProperties: false, properties, required };
}
export const errorSchema = object({ error: object({ code: { type: "string" }, message: { type: "string" } }) });
export const errors = Object.fromEntries([400,401,403,404,409,413,415,422,429,500,503].map(status => [status, errorSchema]));
export const idParams = object({ id: uuid });
export const version = { type: "integer", minimum: 1, maximum: 2147483646 };
export const profileFields = {
  firstName: nullableText(80), lastName: nullableText(80), city: nullableText(120), country: nullableText(100),
  bio: nullableText(2000), gender: nullableText(80), genderVisibility: visibility, profileVisibility: visibility,
};
export const extraProfileFields = {
 displayName:{type:'string',minLength:1,maxLength:80,pattern:'\\S'},
 avatarAssetId:{anyOf:[uuid,{type:'null'}]},coverAssetId:{anyOf:[uuid,{type:'null'}]},
 links:{type:'array',maxItems:10,uniqueItems:true,items:object({url:{type:'string',maxLength:2048,pattern:'^https://'},visibility})},
};
export const handle = {type:['string','null']};
export const profileSchema = object({ userId: uuid, handle, ...profileFields, ...extraProfileFields, version, updatedAt: timestamp });
export const checkinFields = {
  positionId: uuid, ratingQuarters: { anyOf: [{ type: "integer", minimum: 1, maximum: 20 }, { type: "null" }] },
  body: { type: "string", maxLength: 5000 }, occurredAt: timestamp, visibility,
};
export const checkinSchema = object({ id: uuid, userId: uuid, ...checkinFields, version, createdAt: timestamp, updatedAt: timestamp });
export const pageQuery = object({ limit: { type: "string", pattern: "^(?:[1-9]|[1-4][0-9]|50)$" },
  cursor: { type: "string", maxLength: 256, pattern: "^[A-Za-z0-9_-]+$" } }, []);
export const page = (item: unknown) => object({ items: { type: "array", items: item }, nextCursor: { type: ["string","null"] } });
