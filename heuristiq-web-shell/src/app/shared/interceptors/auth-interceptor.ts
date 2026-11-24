import { HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { catchError, filter, map, switchMap, take } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth-service';
import { HQ_PRIVILEGES_STORAGE_KEY } from 'hq-shared-lib';
import { carriesPlatformToken, platformOrigins } from './auth-interceptor.helpers';
import { viewAllOrganizationsHeaders } from '../services/organization-context.helpers';
import { VIEW_ALL_ORGANIZATIONS_STORAGE_KEY } from '../services/organization-context.constants';

// Module-level (not per-request) so concurrent 401s share one in-flight refresh instead of each
// triggering their own — the queued requests wait on this subject for the new token, then retry.
let isRefreshing = false;
const refreshedToken$ = new BehaviorSubject<string | null>(null);

// Where the person's access token may be sent: this site and the platform's own servers, never a third party (see carriesPlatformToken).
const PLATFORM_ORIGINS = platformOrigins(environment, window.location.origin);

/** The "All organizations" header for a read by an organization-wide person who chose it (see OrganizationContextService). */
function viewAllHeaders(method: string, url: string, token: string): Record<string, string> {
  let stored: string | null = null;
  try {
    stored = localStorage.getItem(VIEW_ALL_ORGANIZATIONS_STORAGE_KEY);
  } catch {
    stored = null;
  }
  return viewAllOrganizationsHeaders(method, url, token, stored);
}

export const authInterceptorFn: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const auth = inject(AuthService);
  const token = localStorage.getItem('access_token');

  const authReq = token && carriesPlatformToken(req.url, PLATFORM_ORIGINS)
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}`, ...viewAllHeaders(req.method, req.url, token) } })
    : req;

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      const url = req.url;
      // Note: environment.apiUrl already covers /api/dms/** (DMS is routed through the
      // gateway's apiUrl base — see shared/services/document-service.ts).
      const isAuthRoute = url.includes('/auth/login') || url.includes('/auth/refresh') || url.includes('/auth/google');
      const isAuthenticatedApiRequest = url.startsWith(environment.apiUrl) || url.startsWith(environment.uamApiUrl)
        || url.startsWith(environment.paymentApiUrl)
        // matrimony portal remote: its API calls go through this same HttpClient, so a 401 refreshes here too
        || url.startsWith(environment.matrimonyApiUrl);

      if (err.status === 401 && token && !isAuthRoute && isAuthenticatedApiRequest) {
        return handleUnauthorized(req, next, auth, router);
      }
      return throwError(() => err);
    }),
  );
};

function handleUnauthorized(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  auth: AuthService,
  router: Router,
): Observable<any> {
  if (!localStorage.getItem('refresh_token')) {
    forceLogout(auth, router);
    return throwError(() => new Error('Session expired'));
  }

  // Only a failed refresh ends the session. If the refresh works but the replayed call is still refused (401), the
  // person is signed in fine and this API just does not accept them (for example no access to that application),
  // so the error goes to the screen instead of sending them to the login page.
  return refreshAccessToken(auth).pipe(
    catchError((refreshErr) => {
      forceLogout(auth, router);
      return throwError(() => refreshErr);
    }),
    switchMap((newToken) => next(req.clone({ setHeaders: { Authorization: `Bearer ${newToken}`, ...viewAllHeaders(req.method, req.url, newToken) } }))),
  );
}

/**
 * Exchanges the refresh token for a new access token, sharing one in-flight refresh between every
 * caller: concurrent 401s here, and InAppNotificationService's socket reconnects. The refresh token
 * rotates, so two parallel refreshes would invalidate each other.
 */
export function refreshAccessToken(auth: AuthService): Observable<string> {
  if (!isRefreshing) {
    isRefreshing = true;
    refreshedToken$.next(null);
    return auth.refresh().pipe(
      map((data) => {
        isRefreshing = false;
        const newToken = data.accessToken || data.token || '';
        refreshedToken$.next(newToken);
        return newToken;
      }),
      catchError((refreshErr) => {
        isRefreshing = false;
        return throwError(() => refreshErr);
      }),
    );
  }

  // A refresh is already in flight -- wait for it instead of firing our own.
  return refreshedToken$.pipe(
    filter((newToken): newToken is string => newToken !== null),
    take(1),
  );
}

function forceLogout(auth: AuthService, router: Router): void {
  localStorage.removeItem('access_token');
  localStorage.removeItem('refresh_token');
  localStorage.removeItem('user_data');
  router.navigate(['/auth/login']);
}
