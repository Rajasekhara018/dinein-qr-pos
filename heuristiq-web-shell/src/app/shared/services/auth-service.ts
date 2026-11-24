import { FRANCHISE_PARTNER_PERMISSIONS, ORGANIZATION_HEADER } from './organization-context.constants';
import { LOGIN_OTP_USER_TYPE } from '../../auth/login/login.constants';
import { franchiseIdsFromToken } from './organization-context.helpers';
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { User, LoginResponse } from '../models/models';
import { AppLoginResponse, MfaChallengeRequiredResponse, MfaEnrollTotpResponse, OtpSentResponse } from '../models/uam.models';
import { environment } from '../../../environments/environment';
import { TranslateService } from '@ngx-translate/core';
import { FcmService } from './fcm-service';
import { PrivilegeService } from './privilege-service';
import { HQ_PERMISSION_PRIVILEGES, HqTimezoneService } from 'hq-shared-lib';

interface UamResponse<T> {
  success?: boolean;
  responseObject?: T;
  message?: string;
}

const ROLES = {
  CUSTOMER: 'CUSTOMER',
} as const;

// Customers don't have admin-style RBAC today. canAccess() is the single place every permission check goes
// through (permission-guard, sidebar isVisible(), ...). It decides only from the user's privileges
// (hq-shared-lib privilege map), never from fixed role names: what a role may do is whatever privileges it was
// granted in UAM. A permission with no privilege mapping is open to everyone signed in.

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly hqTimezone = inject(HqTimezoneService);
  private userSubject = new BehaviorSubject<User | null>(null);
  user$: Observable<User | null> = this.userSubject.asObservable();

  private readonly fcm = inject(FcmService);
  private readonly privileges = inject(PrivilegeService);

  constructor(private http: HttpClient, private router: Router, private translate: TranslateService) {
    this.restoreSession();
  }

  get currentUser(): User | null {
    return this.userSubject.value;
  }

  private restoreSession(): void {
    const token = localStorage.getItem('access_token');
    const raw = localStorage.getItem('user_data');
    if (token && raw) {
      try { this.userSubject.next(JSON.parse(raw)); } catch { /* ignore */ }
      // Use the privileges saved by the last visit until fresh ones arrive (see refreshPrivileges()).
      this.privileges.restore();
    }
  }

  /**
   * Re-reads the signed-in user's privileges from UAM. Called once at app start (not from the constructor:
   * the HTTP interceptor injects this service, so a request cannot start while it is being constructed).
   */
  refreshPrivileges(): void {
    if (this.isAuthenticated()) {
      this.privileges.load().subscribe();
    }
  }

  /** Completes once the privileges are known (fetching them if needed); route guards wait on this. */
  ensurePrivileges(): Observable<void> {
    return this.privileges.isPending && this.isAuthenticated() ? this.privileges.load() : of(undefined);
  }

  login(credentials: { email: string; password: string; totpCode?: string }): Observable<LoginResponse | MfaChallengeRequiredResponse> {
    // UAM requires every write request wrapped in a CommonRequestObject envelope —
    // see shared/services/uam-service.ts's wrap() for the full shape used elsewhere.
    // If the account has 2FA enabled, the server returns MfaChallengeRequiredResponse instead of
    // a token — the caller resubmits with totpCode populated to complete sign-in.
    // Uses the generic /identity/auth surface (same one CustomerApp/DriverApp call) rather than
    // /auth/login -- LoginRequest carries no userType, so this behaves identically either way,
    // but staying on one surface keeps every client on the same unified login API.
    const body = {
      requestObject: {
        username: credentials.email,
        password: credentials.password,
        totpCode: credentials.totpCode || undefined,
      },
    };
    return this.http.post<UamResponse<LoginResponse | MfaChallengeRequiredResponse>>(`${environment.uamApiUrl}/identity/auth/login`, body).pipe(
      map((response) => this.unwrapUamResponse(response)),
      tap((data) => {
        if (!('mfaRequired' in data)) this.applySession(data);
      }),
    );
  }

  /** Exchanges a Firebase ID token (obtained via Google sign-in) for a RailCab session. */
  loginWithGoogle(idToken: string, totpCode?: string): Observable<LoginResponse | MfaChallengeRequiredResponse> {
    // userType only matters for a brand-new Google sign-in (decides the role assigned on first
    // login) -- web sign-in is for OPS (back-office) users; an existing account keeps its own roles.
    // /identity/auth/login is now the single entry point for every login mechanism (local, Google,
    // external IdP) -- which one runs is decided server-side by which fields are populated, so
    // there's no separate /google endpoint anymore.
    const body = { requestObject: { idToken, userType: 'OPS', ...(totpCode ? { totpCode } : {}) } };
    return this.http.post<UamResponse<LoginResponse | MfaChallengeRequiredResponse>>(`${environment.uamApiUrl}/identity/auth/login`, body).pipe(
      map((response) => this.unwrapUamResponse(response)),
      tap((data) => { if (!('mfaRequired' in data)) this.applySession(data); }),
    );
  }

  beginMfaEnrollment(enrollmentToken: string): Observable<MfaEnrollTotpResponse> {
    return this.http.post<UamResponse<MfaEnrollTotpResponse>>(`${environment.uamApiUrl}/identity/auth/mfa-enrollment/begin`,
      { requestObject: { enrollmentToken } }).pipe(map(response => this.unwrapUamResponse(response)));
  }

  completeMfaEnrollment(enrollmentToken: string, mfaId: string, code: string): Observable<LoginResponse> {
    return this.http.post<UamResponse<LoginResponse>>(`${environment.uamApiUrl}/identity/auth/mfa-enrollment/complete`,
      { requestObject: { enrollmentToken, mfaId, code } }).pipe(
        map(response => this.unwrapUamResponse(response)), tap(data => this.applySession(data)));
  }

  /** Emails a sign-in code to verify the account's email address (asked for when the security policy needs a verified email). */
  sendEmailVerificationCode(email: string): Observable<OtpSentResponse> {
    return this.http.post<UamResponse<OtpSentResponse>>(`${environment.uamApiUrl}/identity/auth/email/send-otp`,
      { requestObject: { email, userType: LOGIN_OTP_USER_TYPE } }).pipe(map(response => this.unwrapUamResponse(response)));
  }

  /**
   * Checks the emailed code: UAM marks the email verified and signs in. An account with an authenticator also needs its code; without it
   * the answer is an MFA challenge and the caller asks for it.
   */
  verifyEmailAndSignIn(email: string, otp: string, totpCode?: string): Observable<LoginResponse | MfaChallengeRequiredResponse> {
    const requestObject = { email, otp, userType: LOGIN_OTP_USER_TYPE, ...(totpCode ? { totpCode } : {}) };
    return this.http.post<UamResponse<LoginResponse | MfaChallengeRequiredResponse>>(`${environment.uamApiUrl}/identity/auth/email/verify-otp`,
      { requestObject }).pipe(
        map((response) => this.unwrapUamResponse(response)),
        tap((data) => { if (!('mfaRequired' in data)) this.applySession(data); }),
      );
  }

  /**
   * Rotating refresh token: exchanges the current one for a fresh access + refresh token pair. Naming an organization
   * moves the session into it (the account must belong to it); without one the session stays where it is.
   */
  refresh(organization?: string): Observable<LoginResponse> {
    const refreshToken = localStorage.getItem('refresh_token');
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }
    const body = { requestObject: { refreshToken } };
    const headers = organization ? { [ORGANIZATION_HEADER]: organization } : undefined;
    return this.http.post<UamResponse<LoginResponse>>(`${environment.uamApiUrl}/identity/auth/refresh`, body, { headers }).pipe(
      map((response) => this.unwrapUamResponse(response) as LoginResponse),
      tap((data) => this.applySession(data)),
    );
  }

  /** Shared by login() and the registration/OTP-verify flow (auth/register). */
  applySession(data: LoginResponse | AppLoginResponse): void {
    const token = data.accessToken || data.token;
    if (!token) {
      throw new Error('Login response did not include an access token');
    }
    localStorage.setItem('access_token', token);
    if ('refreshToken' in data && data.refreshToken) {
      localStorage.setItem('refresh_token', data.refreshToken);
    }
    const user: User = {
      id: data.userId,
      email: data.email ?? data.username ?? undefined,
      name: data.fullName ?? data.username ?? undefined,
      role: data.role ?? data.roles?.[0] ?? ROLES.CUSTOMER,
      userType: data.userType ?? undefined,
      mustChangePassword: 'forcePasswordChange' in data ? !!data.forcePasswordChange : false,
      totpEnabled: false,
    };
    localStorage.setItem('user_data', JSON.stringify(user));
    this.userSubject.next(user);
    this.applyTimezones(data);
    // A new sign-in or a token refresh: (re)load what this user may open.
    this.privileges.load().subscribe();
  }

  /**
   * Hands the zones UAM returned with the session to the display layer. Absent (older server) or empty leaves the
   * device timezone untouched. The organization zone is replaced whenever the field is present, so an organization
   * switch to one without a zone drops the previous one; it is only used while the opt-in flag is on (off by default).
   */
  private applyTimezones(data: LoginResponse | AppLoginResponse): void {
    if (data.timezone) {
      this.hqTimezone.setUserTimezone(data.timezone);
    }
    if (data.organizationTimeZone !== undefined) {
      this.hqTimezone.setOrganizationTimezone(data.organizationTimeZone);
    }
  }

  private unwrapUamResponse<T>(response: UamResponse<T>): T {
    if (!response.success || !response.responseObject) {
      const fallback = this.translate.instant('login.messages.loginFailed');
      throw {
        error: {
          detail: response.message || fallback,
          message: response.message || fallback,
        },
      };
    }
    return response.responseObject;
  }

  logout(): void {
    // Unregister this browser's push token while the session token still exists (see FcmService).
    this.fcm.unregisterBeforeLogout();
    // Was previously routed through AdminAPI's ApiService (`/api/auth/logout`), a gateway path
    // that matches no real route -- call UAM's self-service logout directly instead, same base
    // used by login/refresh above. The auth interceptor already attaches the bearer token.
    this.http.post(`${environment.uamApiUrl}/identity/auth/logout`, {}).subscribe({ error: () => {} });
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('user_data');
    this.privileges.clear();
    this.hqTimezone.reset();
    this.userSubject.next(null);
    this.router.navigate(['/auth/login']);
  }

  isAuthenticated(): boolean {
    return !!this.userSubject.value && !!localStorage.getItem('access_token');
  }

  /**
   * Whether the user may open the screen guarded by {@code permission}. Decided by the user's privileges when
   * the permission has a privilege mapping (hq-shared-lib privilege map) and they are known; hidden while they
   * are still loading; and by the role-based hints when UAM could not supply them.
   */
  canAccess(permission: string): boolean {
    if (!permission) return true;
    // the partner's own franchise pages are for people in a franchise, however many privileges they hold
    if (FRANCHISE_PARTNER_PERMISSIONS.includes(permission) && franchiseIdsFromToken(localStorage.getItem('access_token')).length === 0) return false;
    const required = HQ_PERMISSION_PRIVILEGES[permission];
    // While privileges are loading (or could not be loaded) nothing mapped opens: fail closed.
    return required ? this.privileges.hasAny(required) : true;
  }

  hasRole(...roles: string[]): boolean {
    return roles.includes(this.currentUser?.role ?? '');
  }
}
