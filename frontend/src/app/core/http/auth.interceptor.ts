import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { API_BASE } from '../api/api-base';
import { ADMIN_PATHS, KITCHEN_PATHS } from '../auth/auth-paths';
import { AuthStore } from '../auth/auth.store';
import { DeviceAuthStore } from '../auth/device-auth.store';
import { AUTH_MODE, AuthMode, IS_RETRY, pathOf } from './http-context';

/** Resolves which credential a request needs (see {@link AuthMode}). */
export function resolveAuthMode(req: HttpRequest<unknown>): Exclude<AuthMode, 'auto'> {
  const mode = req.context.get(AUTH_MODE);
  if (mode !== 'auto') return mode;
  const path = pathOf(req.url);
  if (
    path.startsWith(`${API_BASE}/admin/`) ||
    path === `${API_BASE}/auth/me` ||
    path === `${API_BASE}/auth/change-password`
  ) {
    return 'admin';
  }
  if (path.startsWith(`${API_BASE}/kitchen/`)) return 'device';
  return 'none';
}

const withBearer = (req: HttpRequest<unknown>, token: string | null) =>
  token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

/**
 * Attaches `Authorization: Bearer …`:
 * - admin calls: the in-memory access JWT from {@link AuthStore}. On 401 it performs ONE single-flight
 *   `POST /api/v1/auth/refresh` (shared by all concurrent failures) and retries once; if refreshing fails the session
 *   is cleared and the user is sent to the admin login.
 * - kitchen calls: the persisted device token from {@link DeviceAuthStore}; on 401 the device is forgotten and the
 *   kitchen login is shown.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const mode = resolveAuthMode(req);
  if (mode === 'none') return next(req);

  const router = inject(Router);

  if (mode === 'device') {
    const devices = inject(DeviceAuthStore);
    return next(withBearer(req, devices.token())).pipe(
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          devices.clear();
          void router.navigateByUrl(KITCHEN_PATHS.login);
        }
        return throwError(() => error);
      }),
    );
  }

  const auth = inject(AuthStore);
  return next(withBearer(req, auth.accessToken())).pipe(
    catchError((error: unknown) => {
      if (
        !(error instanceof HttpErrorResponse) ||
        error.status !== 401 ||
        req.context.get(IS_RETRY)
      ) {
        return throwError(() => error);
      }
      return from(auth.refresh()).pipe(
        switchMap((ok) => {
          if (!ok) {
            auth.clearSession();
            void router.navigate([ADMIN_PATHS.login], { queryParams: { returnUrl: router.url } });
            return throwError(() => error);
          }
          const retry = req.clone({ context: req.context.set(IS_RETRY, true) });
          return next(withBearer(retry, auth.accessToken()));
        }),
      );
    }),
  );
};
