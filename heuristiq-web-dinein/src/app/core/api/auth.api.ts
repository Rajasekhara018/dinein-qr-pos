import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { silentErrors, withAuth } from '../http/http-context';
import { API_BASE } from './api-base';
import {
  ChangePasswordRequest,
  DeviceTokenResponse,
  KitchenDeviceRequest,
  LoginRequest,
  MeResponse,
  TokenResponse,
} from './models';

/** `/api/v1/auth/**` */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  /** No-op that makes the server set the `XSRF-TOKEN` cookie. */
  csrf(): Observable<void> {
    return this.http.get<void>(`${API_BASE}/auth/csrf`);
  }

  login(body: LoginRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${API_BASE}/auth/login`, body);
  }

  /** Uses the HttpOnly `dinein_rt` cookie (+ CSRF). Errors are silent: callers decide what a failure means. */
  refresh(): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${API_BASE}/auth/refresh`, null, { context: silentErrors() });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${API_BASE}/auth/logout`, null, { context: silentErrors() });
  }

  changePassword(body: ChangePasswordRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${API_BASE}/auth/change-password`, body);
  }

  registerKitchenDevice(body: KitchenDeviceRequest): Observable<DeviceTokenResponse> {
    return this.http.post<DeviceTokenResponse>(`${API_BASE}/auth/kitchen-device`, body);
  }

  /** Current principal; pass `'device'` from the kitchen app. */
  me(as: 'admin' | 'device' = 'admin', context?: HttpContext): Observable<MeResponse> {
    return this.http.get<MeResponse>(`${API_BASE}/auth/me`, { context: withAuth(as, context) });
  }
}
