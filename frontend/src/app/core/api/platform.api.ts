import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { withAuth } from '../http/http-context';
import { API_BASE } from './api-base';
import { OnboardRestaurantRequest, OnboardRestaurantResponse, RestaurantSummary } from './models';

const KEY_HEADER = 'X-Platform-Admin-Key';

/**
 * `restaurant.PlatformOnboardingController`. Authenticated by a shared secret the operator enters once
 * (see `PlatformPrefs`), sent as `X-Platform-Admin-Key` on every call — never the staff JWT/cookie, so every
 * request explicitly opts out of `authInterceptor` (`withAuth('none')`; it would resolve to 'none' by default
 * anyway, since `/platform/**` matches none of its prefixes, but this makes the intent explicit).
 */
@Injectable({ providedIn: 'root' })
export class PlatformApi {
  private readonly http = inject(HttpClient);

  onboard(key: string, request: OnboardRestaurantRequest): Observable<OnboardRestaurantResponse> {
    return this.http.post<OnboardRestaurantResponse>(`${API_BASE}/platform/restaurants`, request, {
      headers: { [KEY_HEADER]: key },
      context: withAuth('none'),
    });
  }

  list(key: string): Observable<RestaurantSummary[]> {
    return this.http.get<RestaurantSummary[]>(`${API_BASE}/platform/restaurants`, {
      headers: { [KEY_HEADER]: key },
      context: withAuth('none'),
    });
  }
}
