import {ApiError} from '../http/errors.js';
export function normalizeGtin(input:string):string {
 const value=input.trim();
 if(!/^(?:[0-9]{8}|[0-9]{12,14})$/.test(value)) throw new ApiError(422,'INVALID_GTIN','Некорректный EAN/UPC/GTIN');
 const gtin=value.padStart(14,'0');
 if([...gtin].reduce((sum,n,i)=>sum+Number(n)*(i%2===0?3:1),0)%10!==0)
  throw new ApiError(422,'INVALID_GTIN','Неверная контрольная цифра штрихкода');
 return gtin;
}
