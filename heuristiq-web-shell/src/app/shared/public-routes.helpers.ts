import { PUBLIC_ROUTE_PREFIXES } from './public-routes.constants';

/** True when [url] (path, optionally with query or fragment) is one of the public pages. */
export function isPublicPath(url: string | null | undefined): boolean {
  const path = (url ?? '').split(/[?#]/)[0];
  return PUBLIC_ROUTE_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`));
}
