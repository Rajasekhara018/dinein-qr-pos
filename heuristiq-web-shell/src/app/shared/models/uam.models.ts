// Mirrors 2026_RailCab_UAM's DTOs field-for-field. Keep in sync with the Java source
// under 2026_RailCab_UAM/src/main/java/com/railcab/uam/** if it changes.

// ── Envelopes ────────────────────────────────────────────────────────────────
// Every UAM write call must nest its payload in `requestObject` — unlike DMS,
// which takes raw bodies. See shared/services/uam-service.ts's `wrap()` helper.
export interface UamCommonRequest {
  requestObject?: unknown;
  doesInputHaveEncryptedData?: boolean;
  success?: boolean;
  pageNo?: number;
  pageSize?: number;
  sortField?: string;
  sortOrder?: 'ASC' | 'DESC';
  useOrOperator?: boolean;
}

export interface UamCommonResponse<T> {
  success: boolean;
  message: string;
  responseObject: T | null;
}

// Unverified against a live response — Spring Data's default Page<T> Jackson shape.
// Adjust if a real call shows a different structure (e.g. a nested `page` object).
export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface OperationResult {
  operation: string;
  resource: string;
  id: string | null;
  completed: boolean;
}

// ── Shared nested shapes ──────────────────────────────────────────────────────
export interface RPhoneNumber {
  countryCode: string | null;
  number: string | null;
}

export interface RName {
  firstName: string | null;
  middleName: string | null;
  lastName: string | null;
}

export interface RUpload {
  name?: string;
  fileType?: string;
  size: number;
  location?: string;
  title?: string;
  prefixFolder?: string;
  modelKey?: string;
  retentionPolicy?: 'RETAIN_SHORT' | 'RETAIN_NORMAL' | 'RETAIN_LONG' | 'RETAIN_EVER' | 'MERCHANT_DOCUMENTS';
  versionId?: string;
}

export interface AuditInfo {
  deleted?: boolean;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
  deletedAt?: string;
  deletedBy?: string;
  version?: number;
}

export type UamUserType = 'OPS' | 'SUPER_ADMIN' | 'ADMIN' | 'CUSTOMER' | 'DRIVER' | 'VENDOR';

/** Every user type UAM knows (UamEnums.UserType), so an existing user's type always maps to the form's select. */
export const USER_TYPES: readonly string[] = ['SUPER_ADMIN', 'ADMIN', 'OPS', 'BUSINESS', 'SECURITY', 'CUSTOMER', 'DRIVER', 'VENDOR'];
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'PENDING' | 'LOCKED' | 'SUSPENDED' | 'DISABLED';
export type LanguageEnum = 'TE' | 'EN' | 'HI' | 'TA' | 'ML' | 'OD' | 'KA' | 'AR';
export type Gender = 'MALE' | 'FEMALE';
export type AuthProvider = 'LOCAL' | 'KEYCLOAK' | 'FIREBASE' | 'GOOGLE' | 'AZURE_AD' | 'LDAP';

// ── Users ──────────────────────────────────────────────────────────────────
export interface UpsertUserRequest {
  username: string;
  password?: string | null;
  fullName?: string | null;
  userType?: UamUserType | null;
  email?: string | null;
  phoneNumber?: string | null;
  roles?: string[] | null;
  status?: UserStatus | null;
}

// Self-service profile update (/identity/me/profile) — deliberately excludes username/roles/status/userType,
// which only the SUPER_ADMIN-gated UpsertUserRequest above may change.
export interface UpdateMyProfileRequest {
  fullName?: string | null;
  email?: string | null;
  phoneNumber?: string | null;
  timezone?: string | null;
  preferredLanguage?: LanguageEnum | null;
  gender?: Gender | null;
}

export interface SetPasswordRequest {
  password: string;
  temporary?: boolean | null;
}

export interface UamUser {
  id: string;
  tenantId: string | null;
  userName: string;
  email: string | null;
  phoneNumber: RPhoneNumber | null;
  employeeCode: string | null;
  displayName: string | null;
  fullName: RName | null;
  status: UserStatus;
  departmentId: string | null;
  reportingManagerId: string | null;
  designationId: string | null;
  locationId: string | null;
  timezone: string | null;
  preferredLanguage: LanguageEnum | null;
  profileImage: RUpload | null;
  gender: Gender | null;
  joiningDate: string | null;
  relievingDate: string | null;
  active: boolean | null;
  apiUser: boolean | null;
  roles: string[] | null;
  /** Codes of the applications the user may open (direct grants plus their roles' grants), from the search index. */
  applications?: string[] | null;
  attributes: Record<string, unknown> | null;
  audit: AuditInfo | null;
}

// ── Roles ──────────────────────────────────────────────────────────────────
export type AssignmentType = 'DIRECT' | 'GROUP' | 'DEPARTMENT' | 'DESIGNATION' | 'TENANT_DEFAULT' | 'SYSTEM' | 'IMPORTED' | 'API';

export interface UpsertRoleRequest {
  code: string;
  name?: string | null;
  description?: string | null;
  permissions?: string[] | null;
  systemRole?: boolean;
}

export interface UamRole {
  id: string;
  tenantId: string | null;
  code: string;
  name: string | null;
  description: string | null;
  active: boolean | null;
  systemRole: boolean | null;
  editable: boolean | null;
  permissions: string[] | null;
  assignedBy: string | null;
  assignmentReason: string | null;
  assignmentType: AssignmentType | null;
  effectiveFrom: string | null;
  effectiveUntil: string | null;
  defaultRole: boolean | null;
  attributes: Record<string, unknown> | null;
  audit: AuditInfo | null;
}

// ── Permissions ────────────────────────────────────────────────────────────
export type ModuleCategory = 'ADMINISTRATION' | 'SECURITY' | 'BUSINESS' | 'REPORTING' | 'CONFIGURATION' | 'INTEGRATION' | 'MASTER_DATA' | 'WORKFLOW' | 'DASHBOARD' | 'SETTINGS';
export type ResourceType = 'MASTER' | 'TRANSACTION' | 'CONFIGURATION' | 'REPORT' | 'DASHBOARD' | 'WORKFLOW' | 'API' | 'DOCUMENT' | 'FILE' | 'SERVICE';
export type PermissionAction =
  | 'CREATE' | 'READ' | 'UPDATE' | 'DELETE' | 'VIEW' | 'LIST' | 'SEARCH' | 'FILTER' | 'APPROVE' | 'REJECT'
  | 'VERIFY' | 'AUTHORIZE' | 'REVIEW' | 'ENABLE' | 'DISABLE' | 'ACTIVATE' | 'DEACTIVATE' | 'LOCK' | 'UNLOCK'
  | 'IMPORT' | 'EXPORT' | 'DOWNLOAD' | 'UPLOAD' | 'PRINT' | 'ASSIGN' | 'UNASSIGN' | 'MAP' | 'UNMAP'
  | 'CONFIGURE' | 'MANAGE' | 'EXECUTE' | 'RESET' | 'CLONE' | 'LOGIN' | 'LOGOUT' | 'CHANGE_PASSWORD'
  | 'RESET_PASSWORD' | 'INITIATE' | 'CANCEL' | 'REFUND' | 'REVERSE' | 'SETTLE' | 'AUDIT' | 'AUDIT_VIEW' | 'CUSTOM';

export interface UpsertPermissionRequest {
  code: string;
  applicationCode?: string | null;
  module?: ModuleCategory | null;
  resource?: ResourceType | null;
  action?: PermissionAction | null;
  displayName?: string | null;
  description?: string | null;
}

export interface Permission {
  id: string;
  code: string;
  applicationCode: string | null;
  module: ModuleCategory | null;
  resource: ResourceType | null;
  action: PermissionAction | null;
  displayName: string | null;
  description: string | null;
  active: boolean | null;
  systemPermission: boolean | null;
  attributes: Record<string, unknown> | null;
  audit: AuditInfo | null;
}

// ── Organizations (Tenants) ─────────────────────────────────────────────────
export type TenantType = 'INTERNAL' | 'CUSTOMER' | 'PARTNER' | 'VENDOR' | 'SUBSIDIARY';
export type TenantStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'ARCHIVED';

export interface UpsertOrganizationRequest {
  code: string;
  name?: string | null;
  description?: string | null;
  type?: TenantType | null;
  status?: TenantStatus | null;
  supportEmail?: string | null;
  supportPhone?: string | null;
  maxUsers?: number | null;
  maxApplications?: number | null;
}

export interface Tenant {
  id: string;
  code: string;
  name: string;
  legalName: string | null;
  description: string | null;
  type: TenantType | null;
  logo: RUpload | null;
  website: string | null;
  emailDomain: string | null;
  timeZone: string | null;
  defaultLanguage: LanguageEnum | null;
  country: string | null;
  currency: string | null;
  supportEmail: string | null;
  supportPhone: string | null;
  status: TenantStatus | null;
  maxUsers: number | null;
  maxApplications: number | null;
  active: boolean | null;
  systemTenant: boolean | null;
  settings: Record<string, unknown> | null;
  audit: AuditInfo | null;
}

// ── Applications ───────────────────────────────────────────────────────────
export type ApplicationType = 'WEB' | 'MOBILE' | 'DESKTOP' | 'API' | 'MICROSERVICE' | 'BATCH' | 'INTERNAL' | 'EXTERNAL';
export type AuthenticationMode = 'LOCAL' | 'LDAP' | 'OAUTH2' | 'OIDC' | 'SAML' | 'KEYCLOAK' | 'FIREBASE' | 'AZURE_AD' | 'CUSTOM';

export interface UpsertApplicationRequest {
  code: string;
  name?: string | null;
  description?: string | null;
  appVersion?: string | null;
  baseUrl?: string | null;
  landingPage?: string | null;
  ownerTeam?: string | null;
  supportEmail?: string | null;
  type?: ApplicationType | null;
  authenticationMode?: AuthenticationMode | null;
  ssoEnabled?: boolean | null;
  mfaRequired?: boolean | null;
  displayOrder?: number | null;
  /** Whether the application is active (optional: left out = unchanged on edit, active on create). */
  active?: boolean | null;
}

export interface Application {
  id: string;
  code: string;
  name: string;
  description: string | null;
  appVersion: string | null;
  baseUrl: string | null;
  icon: RUpload | null;
  landingPage: string | null;
  ownerTeam: string | null;
  supportEmail: string | null;
  type: ApplicationType | null;
  authenticationMode: AuthenticationMode | null;
  ssoEnabled: boolean | null;
  mfaRequired: boolean | null;
  displayOrder: number | null;
  active: boolean | null;
  systemApplication: boolean | null;
  attributes: Record<string, unknown> | null;
  audit: AuditInfo | null;
}

/** One entry of UAM's fixed application catalogue (GET /applications/catalogue); `created` = the session organization already has it. */
export interface ApplicationCatalogueItem {
  code: string;
  name: string;
  description: string | null;
  type: ApplicationType;
  displayOrder: number;
  iconHint: string | null;
  created: boolean;
}

// ── Clients ────────────────────────────────────────────────────────────────
export type ApiClientType = 'INTERNAL' | 'EXTERNAL' | 'MOBILE' | 'WEB' | 'MICROSERVICE' | 'BATCH' | 'INTEGRATION';
export type ClientAuthenticationMethod = 'CLIENT_SECRET' | 'PRIVATE_KEY_JWT' | 'MTLS' | 'JWT_ASSERTION';
export type GrantType = 'CLIENT_CREDENTIALS' | 'AUTHORIZATION_CODE' | 'REFRESH_TOKEN' | 'PASSWORD' | 'DEVICE_CODE';

export interface UpsertClientRequest {
  clientId: string;
  tenantId?: string | null;
  applicationId?: string | null;
  name?: string | null;
  description?: string | null;
  clientType?: ApiClientType | null;
  authenticationMethod?: ClientAuthenticationMethod | null;
  grantTypes?: GrantType[] | null;
  scopes?: string[] | null;
  redirectUris?: string[] | null;
  active?: boolean | null;
}

export interface ApiClient {
  id: string;
  tenantId: string | null;
  applicationId: string | null;
  clientId: string;
  name: string;
  description: string | null;
  clientType: ApiClientType | null;
  authenticationMethod: ClientAuthenticationMethod | null;
  grantTypes: GrantType[] | null;
  scopes: string[] | null;
  roleIds: string[] | null;
  allowedIpAddresses: string[] | null;
  redirectUris: string[] | null;
  accessTokenValiditySeconds: number | null;
  refreshTokenValiditySeconds: number | null;
  active: boolean | null;
  audit: AuditInfo | null;
}

export interface ApiClientCreated {
  client: ApiClient;
  clientSecret: string;
}

// ── Security Policies ────────────────────────────────────────────────────────
export interface PasswordPolicy {
  minimumLength?: number | null;
  maximumLength?: number | null;
  requireUppercase?: boolean | null;
  requireLowercase?: boolean | null;
  requireNumber?: boolean | null;
  requireSpecialCharacter?: boolean | null;
  passwordHistoryCount?: number | null;
  expiryDays?: number | null;
  minimumAgeDays?: number | null;
  preventUsernameInPassword?: boolean | null;
}

export interface LoginPolicy {
  maxFailedAttempts?: number | null;
  lockDurationMinutes?: number | null;
  captchaAfterAttempts?: number | null;
  allowConcurrentSessions?: boolean | null;
  maxConcurrentSessions?: number | null;
  rememberMeAllowed?: boolean | null;
}

export interface SessionPolicy {
  idleTimeoutMinutes?: number | null;
  absoluteTimeoutMinutes?: number | null;
  singleSessionOnly?: boolean | null;
  invalidateOnPasswordChange?: boolean | null;
  invalidateOnRoleChange?: boolean | null;
}

export interface MfaPolicy {
  enabled?: boolean | null;
  mandatory?: boolean | null;
  emailOtpAllowed?: boolean | null;
  smsOtpAllowed?: boolean | null;
  authenticatorAppAllowed?: boolean | null;
  backupCodesAllowed?: boolean | null;
}

export interface ApiSecurityPolicy {
  apiKeyAllowed?: boolean | null;
  jwtAllowed?: boolean | null;
  oauthAllowed?: boolean | null;
  rateLimitPerMinute?: number | null;
  ipWhitelistEnabled?: boolean | null;
}

export interface AccountPolicy {
  selfRegistrationAllowed?: boolean | null;
  emailVerificationRequired?: boolean | null;
  phoneVerificationRequired?: boolean | null;
  adminApprovalRequired?: boolean | null;
  autoUnlockEnabled?: boolean | null;
  autoUnlockAfterMinutes?: number | null;
}

export interface UpsertSecurityPolicyRequest {
  version?: number | null;
  active?: boolean | null;
  effectiveFrom?: string | null;
  effectiveUntil?: string | null;
  tenantId?: string | null;
  applicationId?: string | null;
  name: string;
  description?: string | null;
  passwordPolicy?: PasswordPolicy | null;
  loginPolicy?: LoginPolicy | null;
  sessionPolicy?: SessionPolicy | null;
  mfaPolicy?: MfaPolicy | null;
  apiSecurityPolicy?: ApiSecurityPolicy | null;
  accountPolicy?: AccountPolicy | null;
}

export interface SecurityPolicy extends UpsertSecurityPolicyRequest {
  id: string;
  active: boolean | null;
  systemPolicy: boolean | null;
  effectiveFrom: string | null;
  effectiveUntil: string | null;
  audit: AuditInfo | null;
}

// ── Notification Templates ──────────────────────────────────────────────────
export type NotificationChannel = 'EMAIL' | 'SMS' | 'PUSH' | 'WHATSAPP' | 'IN_APP';
export type TemplateEngine = 'PLAIN_TEXT' | 'HTML' | 'THYMELEAF' | 'FREEMARKER' | 'MUSTACHE';

export interface UpsertNotificationTemplateRequest {
  tenantId?: string | null;
  applicationId?: string | null;
  code: string;
  name?: string | null;
  description?: string | null;
  channel?: NotificationChannel | null;
  language?: LanguageEnum | null;
  subject?: string | null;
  body?: string | null;
  engine?: TemplateEngine | null;
  variables?: string[] | null;
}

export interface NotificationTemplate {
  id: string;
  tenantId: string | null;
  applicationId: string | null;
  code: string;
  name: string;
  description: string | null;
  channel: NotificationChannel | null;
  language: LanguageEnum | null;
  subject: string | null;
  body: string | null;
  engine: TemplateEngine | null;
  variables: string[] | null;
  version: number | null;
  active: boolean | null;
  systemTemplate: boolean | null;
  audit: AuditInfo | null;
}

// ── MFA ────────────────────────────────────────────────────────────────────
export type MfaType = 'TOTP' | 'EMAIL_OTP' | 'SMS_OTP' | 'PUSH_NOTIFICATION' | 'PASSKEY' | 'WEBAUTHN' | 'SECURITY_KEY';

export interface UserMfa {
  id: string;
  userId: string;
  mfaType: MfaType;
  phoneNumber: RPhoneNumber | null;
  email: string | null;
  enabled: boolean | null;
  primary: boolean | null;
  verified: boolean | null;
  lastVerifiedAt: string | null;
  failedAttempts: number | null;
  locked: boolean | null;
  lockedUntil: string | null;
  deviceId: string | null;
  metadata: Record<string, unknown> | null;
  active: boolean | null;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string;
  updatedBy: string | null;
  version: number | null;
}

// ── Sessions ───────────────────────────────────────────────────────────────
export type LogoutReason =
  | 'USER_LOGOUT' | 'SESSION_EXPIRED' | 'TOKEN_EXPIRED' | 'ADMIN_LOGOUT' | 'PASSWORD_CHANGED'
  | 'ACCOUNT_LOCKED' | 'ACCOUNT_DISABLED' | 'SECURITY_POLICY' | 'CONCURRENT_LOGIN' | 'SYSTEM_RESTART';

export interface UserSession {
  id: string;
  sessionId: string;
  userId: string;
  credentialId: string | null;
  deviceId: string | null;
  accessTokenId: string | null;
  provider: AuthProvider | null;
  ipAddress: string | null;
  country: string | null;
  state: string | null;
  city: string | null;
  browser: string | null;
  operatingSystem: string | null;
  deviceType: string | null;
  userAgent: string | null;
  loginAt: string | null;
  lastActivityAt: string | null;
  expiresAt: string | null;
  logoutAt: string | null;
  logoutReason: LogoutReason | null;
  active: boolean | null;
  rememberMe: boolean | null;
  revoked: boolean | null;
  forcedLogout: boolean | null;
  mfaVerified: boolean | null;
  metadata: Record<string, unknown> | null;
  createdAt: string | null;
  updatedAt: string | null;
  version: number | null;
}

// ── Devices ────────────────────────────────────────────────────────────────
export type DeviceType = 'WEB' | 'MOBILE' | 'TABLET' | 'DESKTOP' | 'SMART_TV' | 'UNKNOWN';
export type Platform = 'ANDROID' | 'IOS' | 'WINDOWS' | 'LINUX' | 'MACOS' | 'WEB';

export interface UserDevice {
  id: string;
  userId: string;
  deviceFingerprint: string;
  deviceUuid: string | null;
  deviceName: string | null;
  deviceType: DeviceType | null;
  operatingSystem: string | null;
  operatingSystemVersion: string | null;
  browser: string | null;
  browserVersion: string | null;
  manufacturer: string | null;
  model: string | null;
  pushNotificationToken: string | null;
  platform: Platform | null;
  trusted: boolean | null;
  verified: boolean | null;
  blocked: boolean | null;
  blockReason: string | null;
  lastLoginAt: string | null;
  lastLogoutAt: string | null;
  lastIpAddress: string | null;
  country: string | null;
  state: string | null;
  city: string | null;
  registeredAt: string | null;
  lastActiveAt: string | null;
  active: boolean | null;
  metadata: Record<string, unknown> | null;
  createdAt: string | null;
  createdBy: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
  version: number | null;
}

// ── Login security ───────────────────────────────────────────────────────────
export interface UserLoginSecurity {
  id: string;
  userId: string;
  failedLoginAttempts: number | null;
  successfulLoginCount: number | null;
  failedLoginCount: number | null;
  accountLocked: boolean | null;
  lockReason: string | null;
  lockedAt: string | null;
  unlockAt: string | null;
  lastSuccessfulLoginAt: string | null;
  lastFailedLoginAt: string | null;
  previousSuccessfulLoginAt: string | null;
  lastLoginIp: string | null;
  lastFailedLoginIp: string | null;
  lastLoginLocation: string | null;
  lastDeviceId: string | null;
  forcePasswordChange: boolean | null;
  passwordExpiryAt: string | null;
  firstLogin: boolean | null;
  otpVerificationRequired: boolean | null;
  captchaRequired: boolean | null;
  mfaRequired: boolean | null;
  suspiciousActivity: boolean | null;
  riskScore: number | null;
  lastPasswordResetRequestAt: string | null;
  active: boolean | null;
  createdAt: string | null;
  createdBy: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
  version: number | null;
}

// ── Credentials ────────────────────────────────────────────────────────────
export interface CredentialSummary {
  id: string;
  provider: AuthProvider;
  providerUserId: string | null;
  verified: boolean | null;
  primaryCredential: boolean | null;
  active: boolean | null;
  lastAuthenticatedAt: string | null;
}

// ── Refresh tokens / password history ───────────────────────────────────────
export type PrincipalType = 'USER' | 'API_CLIENT';

export interface RefreshToken {
  id: string;
  principalId: string;
  principalType: PrincipalType | null;
  clientId: string | null;
  tokenId: string | null;
  jwtId: string | null;
  sessionId: string | null;
  deviceId: string | null;
  issuedAt: string | null;
  expiresAt: string | null;
  lastUsedAt: string | null;
  revoked: boolean | null;
  revokedReason: string | null;
  rotationCount: number | null;
  ipAddress: string | null;
  userAgent: string | null;
  audit: AuditInfo | null;
}

export type PasswordChangeReason = 'USER_CHANGED' | 'ADMIN_RESET' | 'PASSWORD_EXPIRED' | 'FIRST_LOGIN' | 'FORGOT_PASSWORD' | 'MIGRATION' | 'SYSTEM_RESET';

export interface PasswordHistory {
  id: string;
  userId: string;
  credentialId: string | null;
  algorithm: string | null;
  passwordVersion: number | null;
  changeReason: PasswordChangeReason | null;
  changedBy: string | null;
  passwordExpiryAt: string | null;
  audit: AuditInfo | null;
}

// ── User preferences ─────────────────────────────────────────────────────────
export interface UiPreference {
  theme?: 'LIGHT' | 'DARK' | 'DEFAULT' | null;
  accentColor?: string | null;
  compactMode?: boolean | null;
  animationsEnabled?: boolean | null;
  pageSize?: number | null;
  showSidebar?: boolean | null;
  darkMode?: boolean | null;
  landingPage?: string | null;
  /** The web theme the user chose (a theme id such as 'ocean'); empty follows the deployment's default. */
  themeId?: string | null;
  /** The text size the user chose: small, normal, large or xlarge. */
  fontScale?: string | null;
}

export type NotificationCategory = 'SECURITY' | 'SYSTEM' | 'ACCOUNT' | 'BILLING' | 'PAYMENT' | 'MARKETING' | 'PROMOTION' | 'REPORT' | 'ALERT' | 'APPROVAL';

export interface NotificationCategoryPreference {
  category?: NotificationCategory | null;
  enabled?: boolean | null;
  preferredChannels?: NotificationChannel[] | null;
}

export interface NotificationPreference {
  emailEnabled?: boolean | null;
  smsEnabled?: boolean | null;
  pushEnabled?: boolean | null;
  whatsappEnabled?: boolean | null;
  inAppEnabled?: boolean | null;
  doNotDisturb?: boolean | null;
  dndStartTime?: string | null;
  dndEndTime?: string | null;
  categories?: NotificationCategoryPreference[] | null;
}

export interface DashboardPreference {
  favoriteModules?: string[] | null;
  pinnedMenus?: string[] | null;
  hiddenWidgets?: string[] | null;
  defaultPageSize?: number | null;
  defaultDashboard?: string | null;
}

export interface AccessibilityPreference {
  highContrast?: boolean | null;
  screenReader?: boolean | null;
  fontScale?: number | null;
  reducedMotion?: boolean | null;
}

export interface RegionalPreference {
  language?: LanguageEnum | null;
  timezone?: string | null;
  currency?: string | null;
  dateFormat?: string | null;
  timeFormat?: string | null;
  numberFormat?: string | null;
  firstDayOfWeek?: string | null;
}

// Self-service preferences update (/identity/me/preferences) — no userId/tenantId, unlike
// UpsertUserPreferenceRequest below; the backend fills in the caller's own id server-side.
export interface UpdateMyPreferencesRequest {
  applicationId?: string | null;
  ui?: UiPreference | null;
  notification?: NotificationPreference | null;
  dashboard?: DashboardPreference | null;
  accessibility?: AccessibilityPreference | null;
  regional?: RegionalPreference | null;
}

export interface UpsertUserPreferenceRequest {
  tenantId?: string | null;
  userId: string;
  applicationId?: string | null;
  ui?: UiPreference | null;
  notification?: NotificationPreference | null;
  dashboard?: DashboardPreference | null;
  accessibility?: AccessibilityPreference | null;
  regional?: RegionalPreference | null;
}

export interface UserPreference extends UpsertUserPreferenceRequest {
  id: string;
  active: boolean | null;
  version: number | null;
  createdAt: string | null;
  updatedAt: string | null;
}

// ── Audit logs ─────────────────────────────────────────────────────────────
export type AuditAction = string;
export type AuditStatus = string;

export interface FieldChange {
  field?: string;
  oldValue?: unknown;
  newValue?: unknown;
}

export interface AuditLog {
  id: string;
  tenantId: string | null;
  applicationId: string | null;
  userId: string | null;
  username: string | null;
  entityType: string | null;
  entityId: string | null;
  action: AuditAction | null;
  status: AuditStatus | null;
  oldValue: Record<string, unknown> | null;
  newValue: Record<string, unknown> | null;
  changes: FieldChange[] | null;
  remarks: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  device: string | null;
  browser: string | null;
  operatingSystem: string | null;
  correlationId: string | null;
  requestId: string | null;
  executionTimeMs: number | null;
  createdAt: string;
}

// ── Customer registration + OTP (CustomerAuthController) ───────────────────
export interface CustomerRegisterRequest {
  name: string;
  mobile: string;
  email: string;
  city?: string | null;
  preferredPaymentMethod?: string | null;
  emergencyContactName?: string | null;
  emergencyContactPhone?: string | null;
  homeAddress?: string | null;
  workAddress?: string | null;
  stationAddress?: string | null;
  notificationsEnabled?: string | null;
}

export interface RegistrationResponse {
  message: string;
  userId: string;
  driverId: string;
  onboardingStatus: string;
  preferredPaymentMethod: string;
  isDriverOnline: boolean;
}

export interface PhoneOtpRequest {
  phone: string;
}

export interface EmailOtpRequest {
  email: string;
}

export interface VerifyPhoneOtpRequest {
  phone: string;
  otp: string;
}

export interface VerifyEmailOtpRequest {
  email: string;
  otp: string;
}

export interface OtpSentResponse {
  maskedEmail: string | null;
  otp: string | null;
}

// Field-for-field identical to LoginResponse in models.ts, but a separate wire type in UAM.
export interface AppLoginResponse {
  token: string | null;
  accessToken: string;
  userId: string;
  username: string | null;
  email: string;
  fullName: string;
  userType: UamUserType | null;
  role: string;
  roles: string[] | null;
  /** The user's saved IANA timezone, null when none (absent on older servers). */
  timezone?: string | null;
  /** The session organization's IANA timezone, null when none (absent on older servers). */
  organizationTimeZone?: string | null;
  /** The session organization's id / code / name; null when none, absent on older servers. */
  organizationId?: string | null;
  organizationCode?: string | null;
  organizationName?: string | null;
}

// Returned instead of AppLoginResponse when the account has an enabled MFA method — resubmit the
// same login request with totpCode populated to complete sign-in.
export interface MfaChallengeRequiredResponse {
  mfaRequired: true;
  mfaType?: MfaType;
  mfaEnrollmentRequired?: boolean;
  enrollmentToken?: string;
  message: string;
}

export interface MfaEnrollTotpResponse {
  id: string;
  secret: string;
  qrCodeImageBase64: string;
  otpAuthUrl: string;
}

/** Whether an organization currently uses an application. A cancelled subscription is kept for history. */
export type TenantApplicationStatus = 'ACTIVE' | 'SUSPENDED' | 'CANCELLED';

/** One application an organization uses, as UAM lists it for the organization's Applications section. */
export interface TenantApplicationView {
  subscription: {
    id: string;
    tenantId: string;
    applicationCode: string;
    status: TenantApplicationStatus;
    maxUsers: number | null;
    validUntil: string | null;
    note: string | null;
    subscribedBy: string | null;
    subscribedAt: string | null;
  };
  applicationName: string | null;
}

/** A person's standing in one organization. A removed membership is kept for history. */
export type TenantMembershipStatus = 'ACTIVE' | 'SUSPENDED' | 'REMOVED';

/** One member of an organization, as UAM lists it for the organization's Members section. */
export interface TenantMemberView {
  membership: {
    id: string;
    tenantId: string;
    userId: string;
    status: TenantMembershipStatus;
    joinedAt: string | null;
  };
  userName: string | null;
  email: string | null;
  displayName: string | null;
  employeeCode: string | null;
  /**
   * The account's type as UAM resolves it from its roles (DRIVER, VENDOR, CUSTOMER, or OPS for staff); null when the account
   * no longer exists or the server is older. A backend code: shown through a translated label, never translated itself.
   */
  userType?: string | null;
}

/** An organization the signed-in account may work in (GET /identity/me/organizations). */
export interface OrganizationChoice {
  tenantId: string;
  code: string;
  name: string;
  status: TenantMembershipStatus;
  /** IANA timezone of the organization, when the API supplies it (display only). */
  timeZone?: string | null;
  /** The organization's currency (ISO 4217), the default for amount fields. */
  currency?: string | null;
  /** The organization's country (ISO 3166 alpha-2), the default for phone numbers. */
  country?: string | null;
  /** The organization's default language (LanguageEnum code, e.g. 'TE'). */
  defaultLanguage?: string | null;
}
