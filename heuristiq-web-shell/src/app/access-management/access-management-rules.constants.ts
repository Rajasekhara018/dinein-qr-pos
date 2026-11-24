/**
 * Input rules of the identity forms (API clients, security policies, users, roles). The server enforces the same rules; the forms check
 * them first so a bad value is explained before anything is sent. Keep both sides identical.
 */

/** An API client id: starts with a letter or digit, then letters, digits, dot, underscore or hyphen; 3 to 64 characters in all. */
export const CLIENT_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$/;
export const CLIENT_ID_MAX_LENGTH = 64;

/** Redirect URIs of an API client. */
export const REDIRECT_URI_MAX_COUNT = 20;
export const REDIRECT_URI_MAX_LENGTH = 255;
/** The only schemes a redirect URI may use. */
export const REDIRECT_URI_SCHEMES: readonly string[] = ['https:', 'http:'];
/** Hosts that may use plain http (local development); every other host needs https. */
export const REDIRECT_URI_HTTP_HOSTS: readonly string[] = ['localhost', '127.0.0.1'];

/** Password policy bounds. */
export const POLICY_PASSWORD_MIN_LENGTH_FLOOR = 8;
export const POLICY_PASSWORD_LENGTH_CEILING = 128;
/** Failed sign-in attempts before a lock. */
export const POLICY_MAX_FAILED_ATTEMPTS_FLOOR = 1;
export const POLICY_MAX_FAILED_ATTEMPTS_CEILING = 20;
/** Idle timeout of a session, in minutes. */
export const POLICY_IDLE_TIMEOUT_MIN_MINUTES = 1;
/** Every other numeric value of a policy (expiry days, lock minutes, session counts, absolute timeout) may not be negative. */
export const POLICY_NON_NEGATIVE_MIN = 0;

/** A user's email address. */
export const USER_EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
export const USER_EMAIL_MAX_LENGTH = 254;
/** A user's password: 8 to 128 characters with at least one letter and one digit. */
export const USER_PASSWORD_MIN_LENGTH = 8;
export const USER_PASSWORD_MAX_LENGTH = 128;
export const USER_PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,128}$/;

/** A role code: starts with a capital letter, then capitals, digits or underscores; 3 to 50 characters in all. */
export const ROLE_CODE_PATTERN = /^[A-Z][A-Z0-9_]{2,49}$/;
export const ROLE_CODE_MAX_LENGTH = 50;

/** Translation keys of the errors above, one place so the configs and the specs agree. */
export const RULE_ERROR_KEYS = {
  clientId: 'accessManagement.clients.errors.clientId',
  redirectUrisInvalid: 'accessManagement.clients.errors.redirectUrisInvalid',
  redirectUrisHttps: 'accessManagement.clients.errors.redirectUrisHttps',
  redirectUrisTooMany: 'accessManagement.clients.errors.redirectUrisTooMany',
  redirectUrisRequired: 'accessManagement.clients.errors.redirectUrisRequired',
  passwordMinLength: 'accessManagement.securityPolicies.errors.passwordMinLength',
  passwordLengthOrder: 'accessManagement.securityPolicies.errors.passwordLengthOrder',
  maxFailedAttempts: 'accessManagement.securityPolicies.errors.maxFailedAttempts',
  idleTimeout: 'accessManagement.securityPolicies.errors.idleTimeout',
  notNegative: 'accessManagement.securityPolicies.errors.notNegative',
  userEmail: 'accessManagement.users.errors.email',
  userPassword: 'accessManagement.users.errors.password',
  roleCode: 'accessManagement.roles.errors.code',
} as const;

/** Translation key of the message under the role code when the server says a role with that code exists (HTTP 409). */
export const ROLE_CODE_TAKEN_KEY = 'accessManagement.roles.form.codeTaken';

/** HTTP status for a clash with something that already exists. */
export const HTTP_CONFLICT = 409;

/** The platform administrator role: it is never edited from the screen. */
export const PLATFORM_ADMIN_ROLE_CODE = 'SUPER_ADMIN';
