import {ApiError} from '../http/errors.js';
// Versioned local policy; explicit denylist is not a claim of exhaustive language moderation.
const reserved=new Set(['admin','administrator','support','system','root','krapiva','moderator','api','auth','login','register','help','official','null','undefined']);
const abusive=/(?:fuck|shit|nazi|hitler|pidor|pizd|blyad|suka)/;
export function normalizeHandle(value:string):string {
 if(/[^A-Za-z0-9_]/.test(value)||!/^[A-Za-z][A-Za-z0-9_]{1,22}[A-Za-z0-9]$/.test(value))
  throw new ApiError(422,'INVALID_HANDLE','Никнейм: только латинские буквы, цифры и _; длина 3–24');
 const handle=value.toLowerCase();
 if(!/^[a-z][a-z0-9_]{1,22}[a-z0-9]$/.test(handle)||handle.includes('__'))
  throw new ApiError(422,'INVALID_HANDLE','Никнейм: 3–24 символа a-z, 0-9 и _, первая — буква; без __ и _ в конце');
 if(reserved.has(handle)||abusive.test(handle)) throw new ApiError(422,'RESERVED_HANDLE','Этот никнейм недоступен. Выбери другой');
 return handle;
}
export function handleConflict(error:unknown):never {
 if(error&&typeof error==='object'&&'code' in error&&error.code==='23505'&&'constraint' in error&&error.constraint==='users_handle_unique')
  throw new ApiError(409,'HANDLE_TAKEN','Этот никнейм уже занят. Попробуй другой');
 throw error;
}
