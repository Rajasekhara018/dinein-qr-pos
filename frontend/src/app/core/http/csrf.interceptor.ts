import {
  HttpClient,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
  HttpXsrfTokenExtractor,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, defer, map, Observable, of, shareReplay, switchMap, throwError } from 'rxjs';
import { IS_RETRY, pathOf } from './http-context';

export const XSRF_COOKIE = 'XSRF-TOKEN';
export const XSRF_HEADER = 'X-XSRF-TOKEN';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);
const PROVIDER_CALLBACK = /^\/api\/public\/payments\/[^/]+\/callback$/;

/** Endpoints authenticated by cookies, where Spring Security enforces CSRF (see backend SecurityConfig). */
export function requiresCsrf(req: HttpRequest<unknown>): boolean {
  if (SAFE_METHODS.has(req.method)) return false;
  const path = pathOf(req.url);
  if (path === '/api/auth/refresh' || path === '/api/auth/logout') return true;
  return path.startsWith('/api/public/') && !PROVIDER_CALLBACK.test(path);
}

/** Shared in-flight `GET /api/auth/csrf` so concurrent mutations fetch the cookie only once. */
let csrfFetch: Observable<unknown> | null = null;

/**
 * Makes sure the `XSRF-TOKEN` cookie exists before a cookie-authenticated mutation (calls `GET /api/auth/csrf` once
 * when it is missing) and echoes it in `X-XSRF-TOKEN`. On `403 CSRF_INVALID` it refetches the token and retries
 * once. Angular's built-in XSRF support (withXsrfConfiguration) covers the normal case; this covers first visits
 * and expired cookies.
 */
export const csrfInterceptor: HttpInterceptorFn = (req, next) => {
  if (!requiresCsrf(req)) return next(req);
  const extractor = inject(HttpXsrfTokenExtractor);
  const http = inject(HttpClient);

  const ensureToken = (force: boolean): Observable<string | null> =>
    defer(() => {
      const existing = extractor.getToken();
      if (existing && !force) return of(existing);
      csrfFetch ??= http.get('/api/auth/csrf').pipe(shareReplay({ bufferSize: 1, refCount: false }));
      return csrfFetch.pipe(
        map(() => {
          csrfFetch = null;
          return extractor.getToken();
        }),
        catchError((e: unknown) => {
          csrfFetch = null;
          return throwError(() => e);
        }),
      );
    });

  const withToken = (request: HttpRequest<unknown>, token: string | null) =>
    token ? request.clone({ setHeaders: { [XSRF_HEADER]: token } }) : request;

  return ensureToken(false).pipe(
    switchMap((token) => next(withToken(req, token))),
    catchError((error: unknown) => {
      const csrfRejected =
        error instanceof HttpErrorResponse &&
        error.status === 403 &&
        (error.error as { code?: string } | null)?.code === 'CSRF_INVALID';
      if (!csrfRejected || req.context.get(IS_RETRY)) {
        return throwError(() => error);
      }
      const retry = req.clone({ context: req.context.set(IS_RETRY, true) });
      return ensureToken(true).pipe(switchMap((token) => next(withToken(retry, token))));
    }),
  );
};

/** Test hook. */
export function resetCsrfState(): void {
  csrfFetch = null;
}
