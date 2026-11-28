import {ApiError} from '../http/errors.js';

export const networks=['telegram','youtube','vk','untappd'] as const;
type Network=typeof networks[number];
export type ProfileLink={url:string;visibility:string};
export type SocialLinks=Partial<Record<Network,ProfileLink|null>>;
export type StoredLink=ProfileLink&{slot:number};

const hosts:Record<Network,readonly string[]>={
 telegram:['t.me','telegram.me'],
 youtube:['youtube.com','m.youtube.com','youtu.be'],
 vk:['vk.com','m.vk.com','vk.ru','m.vk.ru'],
 untappd:['untappd.com'],
};

function httpsUrl(value:string) {
 try {
  const url=new URL(value);
  if(url.protocol==='https:'&&!url.username&&!url.password&&url.hostname.includes('.')&&!/[\s\\]/.test(value)) return url;
 } catch {
  // The same error is returned for malformed and unsupported addresses.
 }
 throw new ApiError(422,'INVALID_URL','Укажите полный HTTPS-адрес без логина и пароля');
}

export function validateProfileLink(link:ProfileLink) { httpsUrl(link.url); }

export function networkForLink(value:string) {
 try {
  const url=httpsUrl(value);
  if(url.port||!url.pathname.split('/').some(Boolean)) return undefined;
  const host=url.hostname.replace(/^www\./,'');
  return networks.find(network=>hosts[network].includes(host));
 } catch {
  return undefined;
 }
}

export function validateSocialLinks(links:SocialLinks) {
 for(const network of networks) {
  const link=links[network];
  if(link&&networkForLink(link.url)!==network)
   throw new ApiError(422,'INVALID_SOCIAL_LINK','Проверьте адрес и название социальной сети');
 }
}

export function socialSlot(network:Network) { return 11+networks.indexOf(network); }

export function linkForNetwork(links:StoredLink[],network:Network) {
 return links.find(link=>link.slot===socialSlot(network))??
  links.find(link=>link.slot<=10&&networkForLink(link.url)===network);
}
