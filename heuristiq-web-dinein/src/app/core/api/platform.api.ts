import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { withAuth } from '../http/http-context';
import { API_BASE } from './api-base';
import { OnboardRestaurantRequest, OnboardRestaurantResponse, RestaurantSummary } from './models';

const KEY_HEADER = 'X-Platform-Admin-Key';

/**
 * `restaurant.PlatformOnboardingController`. Two ways in, matching the backend:
 * - `key` given: the shared secret the operator enters once (see `PlatformPrefs`), sent as `X-Platform-Admin-Key`
 *   and explicitly opting out of `authInterceptor` (`withAuth('none')`) — for use outside any staff login.
 * - `key` omitted (`null`): the caller is already logged into the admin panel as a `platformAdmin` account, so
 *   the request goes through `authInterceptor` with the staff JWT instead (`withAuth('admin')`, forced since
 *   `/platform/**` doesn't match any of its URL-based auto-detection prefixes).
 */
@Injectable({ providedIn: 'root' })
export class PlatformApi {
  private readonly http = inject(HttpClient);

  onboard(key: string | null, request: OnboardRestaurantRequest): Observable<OnboardRestaurantResponse> {
    return this.http.post<OnboardRestaurantResponse>(`${API_BASE}/platform/restaurants`, request, {
      headers: key ? { [KEY_HEADER]: key } : {},
      context: withAuth(key ? 'none' : 'admin'),
    });
  }

  list(key: string | null): Observable<RestaurantSummary[]> {
    return this.http.get<RestaurantSummary[]>(`${API_BASE}/platform/restaurants`, {
      headers: key ? { [KEY_HEADER]: key } : {},
      context: withAuth(key ? 'none' : 'admin'),
    });
  }
}
