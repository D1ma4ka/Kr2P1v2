export class ApiError extends Error {
  constructor(public statusCode: number, public code: string, message: string) { super(message); }
}
export const notFound = () => new ApiError(404,"NOT_FOUND","Ресурс не найден");
export const conflict = () => new ApiError(409,"CONFLICT","Данные изменились; обновите запись");
