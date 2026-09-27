import { HttpContext, HttpContextToken } from '@angular/common/http';

/**
 * Which credential `authInterceptor` attaches.
 * - `auto` (default): staff JWT for `/api/v1/admin/**`, `/api/v1/waiter/**`, `/api/v1/auth/me`,
 *   `/api/v1/auth/change-password`;
 *   kitchen device token for `/api/v1/kitchen/**`; nothing otherwise.
 * - `admin` / `device`: force one (e.g. the kitchen app calling `/api/v1/auth/me` with its device token).
 * - `none`: never attach.
 */
export type AuthMode = 'auto' | 'admin' | 'device' | 'none';

export const AUTH_MODE = new HttpContextToken<AuthMode>(() => 'auto');

/** Set by callers that handle failures themselves; `errorInterceptor` then shows no toast even for 5xx/network. */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

/** Internal: marks a request already retried after a token refresh / CSRF refetch (prevents loops). */
export const IS_RETRY = new HttpContextToken<boolean>(() => false);

export function withAuth(mode: AuthMode, context = new HttpContext()): HttpContext {
  return context.set(AUTH_MODE, mode);
}

export function silentErrors(context = new HttpContext()): HttpContext {
  return context.set(SKIP_ERROR_TOAST, true);
}

/** Path of a (possibly relative) URL, without query string. */
export function pathOf(url: string): string {
  try {
    return new URL(url, 'http://local').pathname;
  } catch {
    return url.split('?')[0];
  }
}
