/**
 * Centralizes every REST path this app calls, grouped by the microservice/module that owns it,
 * mirroring the pattern already used by Rider_CustomerApp/Rider_DriverApp's lib/core/api/
 * api_endpoints.dart. Each service's *base* URL (host + gateway prefix + version) lives in
 * environment.ts/environment.development.ts -- that's the part that changes per environment.
 * The path segment below is what's appended to that base.
 *
 * Unlike Rider_AdminWeb (which calls each backend service directly), this app goes through
 * Rider_ApiGateway for everything except bookingApiUrl (direct to Rider_BookingsAPI). The version
 * is now baked into each relevant environment.* base (uamApiUrl, paymentApiUrl, and the
 * rideApiUrl/dispatchApiUrl/locationApiUrl/riderOnboardingApiUrl/workflowApiUrl bases added this
 * session but not yet consumed by any service here) rather than left to the gateway's implicit
 * v1 default, or baked inline per-path for apiUrl-based calls that mix multiple services under
 * one base (dms, nms, admin, vendors).
 *
 * This file is documentation-first: it records every endpoint this app is CURRENTLY calling (as
 * of this audit), so a future version bump or path change has one place to look, rather than a
 * grep across ~50 component/service files. It does not itself change any runtime behavior --
 * existing services still build their own request URLs; migrating them to import from here is a
 * separate, larger follow-up if wanted.
 *
 * Two known mismatches are flagged inline below rather than "fixed" by guessing at intent -- see
 * PaymentEndpoints and AuthEndpoints.changePassword.
 */

/** document-service.ts (environment.apiUrl + "/v1/dms/documents") — Rider_DMS's DocumentController,
 *  via the gateway's dms-api route. Version made explicit this session (was missing entirely). */
export class DmsEndpoints {
  static documents() { return '/v1/dms/documents'; }
  static metadata(id: string) { return `/v1/dms/documents/metadata/${id}`; }
  static reupload(id: string) { return `/v1/dms/documents/metadata/${id}/rupload`; }
  static downloadUrl(id: string) { return `/v1/dms/documents/metadata/${id}/download-url`; }
  static delete(id: string) { return `/v1/dms/documents/metadata/${id}`; }
}

/** document-url-service.ts (environment.apiUrl) — resolves a stored document's public/relative
 *  URL for use as an <img>/link target; not itself an HttpClient call site. */
export class DmsUrlResolver {
  /** Pass the "/documents/..." (public token) or "documents/..." value the backend already
   *  returned; this just prefixes environment.apiUrl. No version segment of its own. */
  static resolve(relativeUrl: string) { return relativeUrl; }
}

/** fcm-service.ts (environment.apiUrl + "/v1/nms/devices/fcm-token") — Rider_NMS's
 *  DeviceTokenController, via the gateway's nms-api-versioned route. Version made explicit this
 *  session (was missing entirely, which meant this call 404'd until nms-api-versioned was added
 *  to RouteConfig). */
export class NmsDeviceEndpoints {
  static fcmToken() { return '/v1/nms/devices/fcm-token'; }
}

/** in-app-notification-service.ts (environment.apiUrl + "/v1/nms/in-app/notifications") —
 *  Rider_NMS's InAppNotificationController, via the gateway's nms-api-versioned route (accepts
 *  this ordering, same as the legacy nms-api route's shape, or "/nms/v1/..." -- this app uses
 *  the "/v1/<service>" ordering consistently, matching every environment.* base). */
export class NmsInAppNotificationEndpoints {
  static list() { return '/v1/nms/in-app/notifications'; }
  static unreadCount() { return '/v1/nms/in-app/notifications/unread-count'; }
  static markRead(id: string) { return `/v1/nms/in-app/notifications/${id}/read`; }
  static markAllRead() { return '/v1/nms/in-app/notifications/read-all'; }
}

/** nms-templates.service.ts (environment.apiUrl + "/v1/nms/templates" or "/v1/nms/events") —
 *  Rider_NMS's TemplateController / EventController. Same ordering as above. */
export class NmsTemplateEndpoints {
  static templates() { return '/v1/nms/templates'; }
  static template(id: string) { return `/v1/nms/templates/${id}`; }
  static templateByEvent(event: string) { return `/v1/nms/templates/event/${event}`; }
  static templateByEventChannel(event: string, channel: string) {
    return `/v1/nms/templates/event/${event}/channel/${channel}`;
  }
}
export class NmsEventEndpoints {
  static events() { return '/v1/nms/events'; }
  static event(id: string) { return `/v1/nms/events/${id}`; }
  static resetDefaults() { return '/v1/nms/events/reset-defaults'; }
  static attributes(event: string) { return `/v1/nms/events/${event}/attributes`; }
}

/** vendor-register.ts / vendor-documents.ts (environment.apiUrl) — Rider_AdminAPI's
 *  PublicVendorController, via the gateway's admin-api route. Versioned explicitly inline
 *  (verified this session: RouteConfig's RequestVersioning correctly decomposes "/v1/admin/..."
 *  into service="admin" + backendPath="/api/v1/vendors/...", matching PublicVendorController
 *  exactly -- same as every other apiUrl-based path in this file, "/v1" comes first). */
export class PublicVendorEndpoints {
  static register() { return '/v1/admin/vendors/register'; }
}

/** change-password.ts: UAM's self-service identity endpoint (authenticated only, not in the privilege catalog). */
export class AuthEndpoints {
  static changePassword() { return '/v1/uam/identity/account/change-password'; }
}

/** auth-service.ts / vendor-auth-service.ts / register.ts / forgot-password.ts
 *  (environment.uamApiUrl, now "/api/v1/uam") — Rider_UAM's IdentityAuthController, via the
 *  gateway's uam-auth-api route. Version made explicit this session; uam-auth-api's route
 *  predicate in RouteConfig accepts the version before "uam" (this app's ordering), between
 *  "uam" and "identity/auth" (a second, equally valid ordering added at the same time), or
 *  absent entirely -- three patterns total, since this route has a fixed segment after the
 *  service name unlike every "plain" route. */
export class UamAuthEndpoints {
  static login() { return '/identity/auth/login'; }
  static register() { return '/identity/auth/register'; }
  static refresh() { return '/identity/auth/refresh'; }
  static logout() { return '/identity/auth/logout'; }
  static sendPhoneOtp() { return '/identity/auth/phone/send-otp'; }
  static verifyPhoneOtp() { return '/identity/auth/phone/verify-otp'; }
  /** Present in login.ts's flow via forgot-password.ts; exact backend path unverified against
   *  IdentityAuthController's current source in this audit -- confirm before relying on it. */
  static sendPasswordResetOtp() { return '/identity/auth/password/send-otp'; }
  /** Unverified, see note above. */
  static resetPassword() { return '/identity/auth/password/reset'; }
}

/** uam-service.ts (environment.uamApiUrl, now "/api/v1/uam") — the bulk of Rider_UAM's admin
 *  surface (users, roles, groups, permissions, organizations, applications, clients,
 *  security-policies, notification-templates, sessions, devices, mfa, login-security,
 *  credentials, refresh-tokens, password-history). Generic CRUD resourcePath values
 *  used via list()/create()/update()/remove()/getById(): /organizations, /applications,
 *  /clients, /security-policies, /notification-templates, /groups, /users, /roles, /permissions. */
export class UamEndpoints {
  static resource(path: string) { return path; }
  static resourceById(path: string, id: string) { return `${path}/${id}`; }

  static userStatus(id: string) { return `/users/${id}/status`; }
  static userPassword(id: string) { return `/users/${id}/password`; }
  static userTotpRequired(id: string) { return `/users/${id}/totp-required`; }

  static myProfile() { return '/identity/me/profile'; }
  static myPreferences() { return '/identity/me/preferences'; }
  static mySessions() { return '/identity/me/sessions'; }
  static mySession(id: string) { return `/identity/me/sessions/${id}`; }
  static myDevices() { return '/identity/me/devices'; }
  static myDeviceTrust(id: string) { return `/identity/me/devices/${id}/trust`; }
  static myDeviceBlock(id: string) { return `/identity/me/devices/${id}/block`; }
  static myDevice(id: string) { return `/identity/me/devices/${id}`; }
  static myMfa() { return '/identity/me/mfa'; }
  static myMfaTotpEnroll() { return '/identity/me/mfa/totp/enroll'; }
  static myMfaVerify(id: string) { return `/identity/me/mfa/${id}/verify`; }
  static myMfaDisable(id: string) { return `/identity/me/mfa/${id}/disable`; }
  static myMfaById(id: string) { return `/identity/me/mfa/${id}`; }
  static myLoginSecurity() { return '/identity/me/login-security'; }

  static userMfa(userId: string) { return `/users/${userId}/mfa`; }
  static mfaDisable(id: string) { return `/mfa/${id}/disable`; }
  static userSessions(userId: string) { return `/users/${userId}/sessions`; }
  static session(id: string) { return `/sessions/${id}`; }
  static userDevices(userId: string) { return `/users/${userId}/devices`; }
  static deviceTrust(id: string) { return `/devices/${id}/trust`; }
  static deviceBlock(id: string) { return `/devices/${id}/block`; }
  static userLoginSecurity(userId: string) { return `/users/${userId}/login-security`; }
  static unlockAccount(userId: string) { return `/users/${userId}/login-security/unlock`; }
  static userCredentials(userId: string) { return `/users/${userId}/credentials`; }
  static refreshTokens(principalId: string) { return `/principals/${principalId}/refresh-tokens`; }
  static refreshToken(tokenId: string) { return `/refresh-tokens/${tokenId}`; }
  static passwordHistory(userId: string) { return `/users/${userId}/password-history`; }
  static preferencesByUser(userId: string) { return `/user-preferences/by-user/${userId}`; }
  static rotateClientSecret(id: string) { return `/clients/${id}/rotate-secret`; }
}

/**
 * payment-service.ts (environment.paymentApiUrl, now "/api/v1/rider/payment") — KNOWN MISMATCH,
 * flagged not fixed. createSession()/checkStatus() call "/create-session" and "/check-status",
 * neither of which exist on Rider_PaymentService's real PaymentController (which has POST
 * /payments, GET /payments/{id}, POST /payments/webhook, GET /payments/{id}/receipt, POST
 * /payments/{id}/refund -- no session-based flow at all). This looks like it was written
 * against the legacy, non-existent-in-this-repo PaymentAPI's shape (which really did have
 * /create-session and /check-status). Needs a product decision on the intended payment flow,
 * not a path rename.
 */
export class PaymentEndpoints {
  /** MISMATCH -- see class doc comment. */
  static createSession() { return '/create-session'; }
  static byId(paymentId: string) { return `/${paymentId}`; }
  /** MISMATCH -- see class doc comment. */
  static checkStatus() { return '/check-status'; }
}
