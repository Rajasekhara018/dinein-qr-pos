import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../api/auth.api';
import { StaffInfo, StaffRole, TokenResponse } from '../api/models';

/**
 * Admin (OWNER / MANAGER) session.
 *
 * - The access JWT lives in memory only (a signal) — never in storage. The refresh token is an HttpOnly cookie
 *   (`dinein_rt`, path `/api/auth`) the browser sends to `/api/auth/refresh`.
 * - After a reload the session is restored with {@link ensureSession} (one silent refresh), which the admin guards
 *   call; `authInterceptor` also refreshes once on a 401.
 * - `refresh()` is single-flight: concurrent callers share one request.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);

  private readonly _accessToken = signal<string | null>(null);
  private readonly _user = signal<StaffInfo | null>(null);
  private readonly _expiresAt = signal<number | null>(null);

  readonly accessToken = this._accessToken.asReadonly();
  readonly user = this._user.asReadonly();
  /** Epoch millis when the access token expires. */
  readonly expiresAt = this._expiresAt.asReadonly();

  readonly isAuthenticated = computed(() => this._accessToken() !== null && this._user() !== null);
  readonly role = computed<StaffRole | null>(() => this._user()?.role ?? null);
  readonly isOwner = computed(() => this.role() === 'OWNER');
  /** The JWT only grants `/api/auth/change-password` until this is false. */
  readonly mustChangePassword = computed(() => this._user()?.mustChangePassword ?? false);

  private refreshInFlight: Promise<boolean> | null = null;

  async login(username: string, password: string): Promise<StaffInfo> {
    const response = await firstValueFrom(this.api.login({ username, password }));
    this.applyTokens(response);
    return response.user;
  }

  /**
   * Exchanges the refresh cookie for a new access token (rotating the cookie). Resolves `false` when there is no
   * valid refresh session. Fetches the CSRF cookie first if needed (handled by `csrfInterceptor`).
   */
  refresh(): Promise<boolean> {
    this.refreshInFlight ??= firstValueFrom(this.api.refresh())
      .then((response) => {
        this.applyTokens(response);
        return true;
      })
      .catch(() => {
        this.clearSession();
        return false;
      })
      .finally(() => {
        this.refreshInFlight = null;
      });
    return this.refreshInFlight;
  }

  /** True when a session exists or could be restored from the refresh cookie. */
  async ensureSession(): Promise<boolean> {
    if (this.isAuthenticated()) return true;
    return this.refresh();
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.logout());
    } catch {
      // Even if the server call fails, forget the local session.
    } finally {
      this.clearSession();
    }
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<StaffInfo> {
    const response = await firstValueFrom(
      this.api.changePassword({ currentPassword, newPassword }),
    );
    this.applyTokens(response);
    return response.user;
  }

  clearSession(): void {
    this._accessToken.set(null);
    this._user.set(null);
    this._expiresAt.set(null);
  }

  private applyTokens(response: TokenResponse): void {
    this._accessToken.set(response.accessToken);
    this._user.set(response.user);
    this._expiresAt.set(Date.now() + response.expiresIn * 1000);
  }
}
