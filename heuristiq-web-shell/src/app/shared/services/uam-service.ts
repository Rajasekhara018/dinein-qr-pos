import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TranslateService } from '@ngx-translate/core';
import {
  UamCommonRequest, UamCommonResponse, SpringPage, OperationResult,
  UamUser, UpsertUserRequest, SetPasswordRequest, UpdateMyProfileRequest,
  UamRole, UpsertRoleRequest,
  Permission, UpsertPermissionRequest,
  Tenant, UpsertOrganizationRequest,
  Application, UpsertApplicationRequest,
  ApiClient, ApiClientCreated, UpsertClientRequest,
  SecurityPolicy, UpsertSecurityPolicyRequest,
  NotificationTemplate, UpsertNotificationTemplateRequest,
  UserMfa, UserSession, UserDevice, UserLoginSecurity, CredentialSummary,
  RefreshToken, PasswordHistory, UserPreference, UpsertUserPreferenceRequest, UpdateMyPreferencesRequest,
  CustomerRegisterRequest, RegistrationResponse, PhoneOtpRequest, EmailOtpRequest,
  VerifyPhoneOtpRequest, VerifyEmailOtpRequest, OtpSentResponse, AppLoginResponse,
  MfaEnrollTotpResponse,
} from '../models/uam.models';

/**
 * Talks to 2026_RailCab_UAM via the API Gateway's uam-api route
 * (/api/uam/** -> rewritten to UAM's /api/v1/uam/** — see 2026_RailCab_ApiGateway RouteConfig).
 * Every write request must be wrapped in a CommonRequestObject envelope (`wrap()`) —
 * unlike DMS, UAM does not accept raw request bodies.
 */
/** userType the web portal sends on its email OTP flows (sign-in OTP and forgot password).
 *  UAM only uses it to namespace the OTP key -- the account itself is looked up by email and must
 *  already exist -- so send and verify/reset must use the same value. */
const WEB_PORTAL_USER_TYPE = 'OPS';

@Injectable({ providedIn: 'root' })
export class UamService {
  private base = environment.uamApiUrl;

  constructor(private http: HttpClient, private translate: TranslateService) {}

  // ── Envelope helpers ─────────────────────────────────────────────────────
  private wrap<T>(payload: T): UamCommonRequest {
    return {
      requestObject: payload,
      doesInputHaveEncryptedData: false,
      success: false,
      pageNo: 0,
      pageSize: 0,
      useOrOperator: false,
    };
  }

  private unwrap<T>(res: UamCommonResponse<T>): T {
    if (!res.success) {
      const fallback = this.translate.instant('shared.errors.requestFailed');
      throw { error: { detail: res.message || fallback, message: res.message || fallback } };
    }
    return res.responseObject as T;
  }

  private pageParams(page: number, size: number): HttpParams {
    return new HttpParams().set('page', page).set('size', size);
  }

  // ── Generic CRUD (used by the 8 simple admin-managed entities) ──────────
  list<T>(path: string, page: number, size: number): Observable<SpringPage<T>> {
    return this.http.get<UamCommonResponse<SpringPage<T>>>(`${this.base}${path}`, { params: this.pageParams(page, size) })
      .pipe(map((res) => this.unwrap(res)));
  }

  /** GET of a resource path that returns its payload as it is (a list that is not paged, for example). */
  get<T>(path: string): Observable<T> {
    return this.http.get<UamCommonResponse<T>>(`${this.base}${path}`).pipe(map((res) => this.unwrap(res)));
  }

  getById<T>(path: string, id: string): Observable<T> {
    return this.http.get<UamCommonResponse<T>>(`${this.base}${path}/${id}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  create<Req, Res>(path: string, body: Req): Observable<Res> {
    return this.http.post<UamCommonResponse<Res>>(`${this.base}${path}`, this.wrap(body))
      .pipe(map((res) => this.unwrap(res)));
  }

  update<Req, Res>(path: string, id: string, body: Req): Observable<Res> {
    return this.http.put<UamCommonResponse<Res>>(`${this.base}${path}/${id}`, this.wrap(body))
      .pipe(map((res) => this.unwrap(res)));
  }

  remove(path: string, id: string): Observable<OperationResult> {
    return this.http.delete<UamCommonResponse<OperationResult>>(`${this.base}${path}/${id}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  // ── Users ─────────────────────────────────────────────────────────────────
  listUsers(page: number, size: number): Observable<SpringPage<UamUser>> {
    return this.list<UamUser>('/users', page, size);
  }

  getUser(id: string): Observable<UamUser> {
    return this.getById<UamUser>('/users', id);
  }

  createUser(body: UpsertUserRequest): Observable<UamUser> {
    return this.create<UpsertUserRequest, UamUser>('/users', body);
  }

  updateUser(id: string, body: UpsertUserRequest): Observable<UamUser> {
    return this.update<UpsertUserRequest, UamUser>('/users', id, body);
  }

  deleteUser(id: string): Observable<OperationResult> {
    return this.remove('/users', id);
  }

  setUserStatus(id: string, status: string): Observable<UamUser> {
    return this.http.patch<UamCommonResponse<UamUser>>(`${this.base}/users/${id}/status`, null, { params: { status } })
      .pipe(map((res) => this.unwrap(res)));
  }

  setUserPassword(id: string, body: SetPasswordRequest): Observable<UamUser> {
    return this.http.patch<UamCommonResponse<UamUser>>(`${this.base}/users/${id}/password`, this.wrap(body))
      .pipe(map((res) => this.unwrap(res)));
  }

  setTotpRequired(id: string, required: boolean): Observable<UamUser> {
    return this.http.patch<UamCommonResponse<UamUser>>(`${this.base}/users/${id}/totp-required`, null, { params: { required } })
      .pipe(map((res) => this.unwrap(res)));
  }

  // ── Self-service ("My Profile") ──────────────────────────────────────────
  // All of these hit /identity/me/... -- the backend resolves the caller's own id from the JWT subject
  // server-side, so any authenticated user can call them for themselves. They must NOT use the older
  // /users/me/... aliases: the gateway matches those against the admin privilege entries
  // (/users/{userId}/sessions, /users/** ...) and answers 403 to anyone without user-management
  // privileges (franchise owners, for example). /identity/** matches no privilege entry. See
  // UserProfileController and its siblings in Rider_UAM, which answer on both paths.
  getMyProfile(): Observable<UamUser> {
    return this.http.get<UamCommonResponse<UamUser>>(`${this.base}/identity/me/profile`)
      .pipe(map((res) => this.unwrap(res)));
  }

  updateMyProfile(body: UpdateMyProfileRequest): Observable<UamUser> {
    return this.http.put<UamCommonResponse<UamUser>>(`${this.base}/identity/me/profile`, this.wrap(body))
      .pipe(map((res) => this.unwrap(res)));
  }

  getMyPreferences(applicationId?: string): Observable<UserPreference> {
    const params = applicationId ? { applicationId } : undefined;
    return this.http.get<UamCommonResponse<UserPreference>>(`${this.base}/identity/me/preferences`, { params })
      .pipe(map((res) => this.unwrap(res)));
  }

  updateMyPreferences(body: UpdateMyPreferencesRequest): Observable<UserPreference> {
    return this.http.put<UamCommonResponse<UserPreference>>(`${this.base}/identity/me/preferences`, this.wrap(body))
      .pipe(map((res) => this.unwrap(res)));
  }

  getMySessions(): Observable<UserSession[]> {
    return this.http.get<UamCommonResponse<UserSession[]>>(`${this.base}/identity/me/sessions`)
      .pipe(map((res) => this.unwrap(res)));
  }

  revokeMySession(sessionId: string): Observable<OperationResult> {
    return this.http.delete<UamCommonResponse<OperationResult>>(`${this.base}/identity/me/sessions/${sessionId}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  revokeAllMySessions(): Observable<OperationResult> {
    return this.http.delete<UamCommonResponse<OperationResult>>(`${this.base}/identity/me/sessions`)
      .pipe(map((res) => this.unwrap(res)));
  }

  getMyDevices(): Observable<UserDevice[]> {
    return this.http.get<UamCommonResponse<UserDevice[]>>(`${this.base}/identity/me/devices`)
      .pipe(map((res) => this.unwrap(res)));
  }

  trustMyDevice(id: string, trusted: boolean): Observable<UserDevice> {
    return this.http.patch<UamCommonResponse<UserDevice>>(`${this.base}/identity/me/devices/${id}/trust`, null, { params: { trusted } })
      .pipe(map((res) => this.unwrap(res)));
  }

  blockMyDevice(id: string, blocked: boolean, reason?: string): Observable<UserDevice> {
    const params: Record<string, string> = { blocked: String(blocked) };
    if (reason) params['reason'] = reason;
    return this.http.patch<UamCommonResponse<UserDevice>>(`${this.base}/identity/me/devices/${id}/block`, null, { params })
      .pipe(map((res) => this.unwrap(res)));
  }

  deleteMyDevice(id: string): Observable<OperationResult> {
    return this.http.delete<UamCommonResponse<OperationResult>>(`${this.base}/identity/me/devices/${id}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  getMyMfa(): Observable<UserMfa[]> {
    return this.http.get<UamCommonResponse<UserMfa[]>>(`${this.base}/identity/me/mfa`)
      .pipe(map((res) => this.unwrap(res)));
  }

  enrollMyTotp(): Observable<MfaEnrollTotpResponse> {
    return this.http.post<UamCommonResponse<MfaEnrollTotpResponse>>(`${this.base}/identity/me/mfa/totp/enroll`, null)
      .pipe(map((res) => this.unwrap(res)));
  }

  verifyMyMfaEnrollment(id: string, code: string): Observable<UserMfa> {
    return this.http.post<UamCommonResponse<UserMfa>>(`${this.base}/identity/me/mfa/${id}/verify`, this.wrap({ code }))
      .pipe(map((res) => this.unwrap(res)));
  }

  disableMyMfa(id: string): Observable<UserMfa> {
    return this.http.patch<UamCommonResponse<UserMfa>>(`${this.base}/identity/me/mfa/${id}/disable`, null)
      .pipe(map((res) => this.unwrap(res)));
  }

  deleteMyMfa(id: string): Observable<OperationResult> {
    return this.http.delete<UamCommonResponse<OperationResult>>(`${this.base}/identity/me/mfa/${id}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  getMyLoginSecurity(): Observable<UserLoginSecurity> {
    return this.http.get<UamCommonResponse<UserLoginSecurity>>(`${this.base}/identity/me/login-security`)
      .pipe(map((res) => this.unwrap(res)));
  }

  // ── MFA ───────────────────────────────────────────────────────────────────
  getUserMfa(userId: string): Observable<UserMfa[]> {
    return this.http.get<UamCommonResponse<UserMfa[]>>(`${this.base}/users/${userId}/mfa`)
      .pipe(map((res) => this.unwrap(res)));
  }

  deleteMfa(id: string): Observable<OperationResult> {
    return this.remove('/mfa', id);
  }

  disableMfa(id: string): Observable<UserMfa> {
    return this.http.patch<UamCommonResponse<UserMfa>>(`${this.base}/mfa/${id}/disable`, null)
      .pipe(map((res) => this.unwrap(res)));
  }

  // ── Sessions ──────────────────────────────────────────────────────────────
  getUserSessions(userId: string): Observable<UserSession[]> {
    return this.http.get<UamCommonResponse<UserSession[]>>(`${this.base}/users/${userId}/sessions`)
      .pipe(map((res) => this.unwrap(res)));
  }

  revokeSession(sessionId: string): Observable<OperationResult> {
    return this.http.delete<UamCommonResponse<OperationResult>>(`${this.base}/sessions/${sessionId}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  revokeAllSessions(userId: string): Observable<OperationResult> {
    return this.http.delete<UamCommonResponse<OperationResult>>(`${this.base}/users/${userId}/sessions`)
      .pipe(map((res) => this.unwrap(res)));
  }

  // ── Devices ───────────────────────────────────────────────────────────────
  getUserDevices(userId: string): Observable<UserDevice[]> {
    return this.http.get<UamCommonResponse<UserDevice[]>>(`${this.base}/users/${userId}/devices`)
      .pipe(map((res) => this.unwrap(res)));
  }

  deleteDevice(id: string): Observable<OperationResult> {
    return this.remove('/devices', id);
  }

  trustDevice(id: string, trusted: boolean): Observable<UserDevice> {
    return this.http.patch<UamCommonResponse<UserDevice>>(`${this.base}/devices/${id}/trust`, null, { params: { trusted } })
      .pipe(map((res) => this.unwrap(res)));
  }

  blockDevice(id: string, blocked: boolean, reason?: string): Observable<UserDevice> {
    const params: Record<string, string> = { blocked: String(blocked) };
    if (reason) params['reason'] = reason;
    return this.http.patch<UamCommonResponse<UserDevice>>(`${this.base}/devices/${id}/block`, null, { params })
      .pipe(map((res) => this.unwrap(res)));
  }

  // ── Login security ────────────────────────────────────────────────────────
  getLoginSecurity(userId: string): Observable<UserLoginSecurity> {
    return this.http.get<UamCommonResponse<UserLoginSecurity>>(`${this.base}/users/${userId}/login-security`)
      .pipe(map((res) => this.unwrap(res)));
  }

  unlockAccount(userId: string): Observable<UserLoginSecurity> {
    return this.http.post<UamCommonResponse<UserLoginSecurity>>(`${this.base}/users/${userId}/login-security/unlock`, null)
      .pipe(map((res) => this.unwrap(res)));
  }

  // ── Credentials ───────────────────────────────────────────────────────────
  getUserCredentials(userId: string): Observable<CredentialSummary[]> {
    return this.http.get<UamCommonResponse<CredentialSummary[]>>(`${this.base}/users/${userId}/credentials`)
      .pipe(map((res) => this.unwrap(res)));
  }

  deleteCredential(id: string): Observable<OperationResult> {
    return this.remove('/credentials', id);
  }

  // ── Refresh tokens ────────────────────────────────────────────────────────
  getRefreshTokens(principalId: string): Observable<RefreshToken[]> {
    return this.http.get<UamCommonResponse<RefreshToken[]>>(`${this.base}/principals/${principalId}/refresh-tokens`)
      .pipe(map((res) => this.unwrap(res)));
  }

  revokeRefreshToken(tokenId: string): Observable<OperationResult> {
    return this.http.delete<UamCommonResponse<OperationResult>>(`${this.base}/refresh-tokens/${tokenId}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  revokeAllRefreshTokens(principalId: string): Observable<OperationResult> {
    return this.http.delete<UamCommonResponse<OperationResult>>(`${this.base}/principals/${principalId}/refresh-tokens`)
      .pipe(map((res) => this.unwrap(res)));
  }

  // ── Password history ─────────────────────────────────────────────────────
  getPasswordHistory(userId: string, page: number, size: number): Observable<SpringPage<PasswordHistory>> {
    return this.http.get<UamCommonResponse<SpringPage<PasswordHistory>>>(`${this.base}/users/${userId}/password-history`, { params: this.pageParams(page, size) })
      .pipe(map((res) => this.unwrap(res)));
  }

  // ── User preferences ──────────────────────────────────────────────────────
  getUserPreference(id: string): Observable<UserPreference> {
    return this.getById<UserPreference>('/user-preferences', id);
  }

  getUserPreferenceByUser(userId: string, applicationId?: string): Observable<UserPreference> {
    const params = applicationId ? { applicationId } : undefined;
    return this.http.get<UamCommonResponse<UserPreference>>(`${this.base}/user-preferences/by-user/${userId}`, { params })
      .pipe(map((res) => this.unwrap(res)));
  }

  upsertUserPreference(body: UpsertUserPreferenceRequest): Observable<UserPreference> {
    return this.create<UpsertUserPreferenceRequest, UserPreference>('/user-preferences', body);
  }

  deleteUserPreference(id: string): Observable<OperationResult> {
    return this.remove('/user-preferences', id);
  }

  // ── Clients: rotate secret (no body, raw string responseObject) ─────────
  rotateClientSecret(id: string): Observable<string> {
    return this.http.post<UamCommonResponse<string>>(`${this.base}/clients/${id}/rotate-secret`, null)
      .pipe(map((res) => this.unwrap(res)));
  }

  createClient(body: UpsertClientRequest): Observable<ApiClientCreated> {
    return this.create<UpsertClientRequest, ApiClientCreated>('/clients', body);
  }

  // ── Customer self-service registration + OTP ─────────────────────────────
  // The old /customer/auth/** controller was deleted from UAM — every client (including the
  // mobile apps) now goes through the generic /identity/auth/** surface, with userType in body.
  registerCustomer(body: CustomerRegisterRequest): Observable<RegistrationResponse> {
    return this.create<CustomerRegisterRequest & { userType: string }, RegistrationResponse>(
      '/identity/auth/register', { ...body, userType: 'CUSTOMER' });
  }

  sendPhoneOtp(body: PhoneOtpRequest): Observable<OtpSentResponse> {
    return this.create<PhoneOtpRequest & { userType: string }, OtpSentResponse>(
      '/identity/auth/phone/send-otp', { ...body, userType: 'CUSTOMER' });
  }

  verifyPhoneOtp(body: VerifyPhoneOtpRequest): Observable<AppLoginResponse> {
    return this.create<VerifyPhoneOtpRequest & { userType: string }, AppLoginResponse>(
      '/identity/auth/phone/verify-otp', { ...body, userType: 'CUSTOMER' });
  }

  sendEmailOtp(body: EmailOtpRequest): Observable<OtpSentResponse> {
    return this.create<EmailOtpRequest & { userType: string }, OtpSentResponse>(
      '/identity/auth/email/send-otp', { ...body, userType: WEB_PORTAL_USER_TYPE });
  }

  verifyEmailOtp(body: VerifyEmailOtpRequest): Observable<AppLoginResponse> {
    return this.create<VerifyEmailOtpRequest & { userType: string }, AppLoginResponse>(
      '/identity/auth/email/verify-otp', { ...body, userType: WEB_PORTAL_USER_TYPE });
  }

  // ── Forgot password (shared by customer + admin accounts on this login page) ────────────────
  // userType is purely an OTP-key namespace, not an account filter — UAM's findByEmail() looks up
  // by email alone (email is globally unique across every UserType), so this resolves the right
  // account whether it's a customer or an OPS user.
  sendPasswordResetOtp(email: string): Observable<OtpSentResponse> {
    return this.create<{ email: string; userType: string }, OtpSentResponse>(
      '/identity/auth/email/send-otp', { email, userType: WEB_PORTAL_USER_TYPE });
  }

  resetPassword(email: string, otp: string, newPassword: string): Observable<void> {
    return this.create<{ email: string; otp: string; newPassword: string; userType: string }, void>(
      '/identity/auth/reset-password', { email, otp, newPassword, userType: WEB_PORTAL_USER_TYPE });
  }
}

// Re-export entity response/request types callers commonly need alongside the service,
// so feature modules can `import { UamRole, ... } from '.../uam-service'` in a pinch.
export type {
  UamUser, UamRole, Permission, Tenant, Application, ApiClient, SecurityPolicy, NotificationTemplate,
};
