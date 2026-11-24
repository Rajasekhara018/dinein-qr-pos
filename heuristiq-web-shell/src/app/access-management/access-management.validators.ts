import { HqAppDialogFormValues } from 'hq-shared-lib';
import { ADMIN_FORM_TAGS_SEPARATOR } from './admin-form.constants';
import {
  REDIRECT_URI_HTTP_HOSTS,
  REDIRECT_URI_MAX_COUNT,
  REDIRECT_URI_MAX_LENGTH,
  REDIRECT_URI_SCHEMES,
  RULE_ERROR_KEYS,
} from './access-management-rules.constants';

/*
 * Pure rules of the identity forms that need more than a pattern: a list of redirect URIs, and the minimum / maximum password length of
 * a security policy. They return the translation key of the error, or null when the value is fine.
 */

function textOf(value: unknown): string {
  return value === null || value === undefined ? '' : String(value).trim();
}

/** The URIs typed into a comma-separated field, trimmed, without blanks. */
export function splitUris(value: unknown): string[] {
  return textOf(value).split(ADMIN_FORM_TAGS_SEPARATOR).map((uri) => uri.trim()).filter(Boolean);
}

/** The error of one redirect URI: an absolute http(s) URL with a host, no user info, no fragment, within the length limit; https unless local. */
export function redirectUriError(uri: string): string | null {
  if (uri.length > REDIRECT_URI_MAX_LENGTH || uri.includes('#')) return RULE_ERROR_KEYS.redirectUrisInvalid;
  let url: URL;
  try {
    url = new URL(uri);
  } catch {
    return RULE_ERROR_KEYS.redirectUrisInvalid;
  }
  if (!REDIRECT_URI_SCHEMES.includes(url.protocol) || !url.hostname || url.username || url.password) {
    return RULE_ERROR_KEYS.redirectUrisInvalid;
  }
  if (url.protocol === 'http:' && !REDIRECT_URI_HTTP_HOSTS.includes(url.hostname)) return RULE_ERROR_KEYS.redirectUrisHttps;
  return null;
}

/** The error of a field holding redirect URIs (at most 20, each valid), or null. */
export function redirectUrisError(values: HqAppDialogFormValues, key = 'redirectUris'): string | null {
  const uris = splitUris(values[key]);
  if (uris.length > REDIRECT_URI_MAX_COUNT) return RULE_ERROR_KEYS.redirectUrisTooMany;
  for (const uri of uris) {
    const error = redirectUriError(uri);
    if (error) return error;
  }
  return null;
}

/** A browser sign-in (authorization code) sends the user back to a redirect URI, so that grant needs at least one. */
export function authorizationCodeRedirectError(values: HqAppDialogFormValues): string | null {
  const grants = values['grantTypes'];
  const wantsCode = Array.isArray(grants) && grants.includes('AUTHORIZATION_CODE');
  return wantsCode && splitUris(values['redirectUris']).length === 0 ? RULE_ERROR_KEYS.redirectUrisRequired : null;
}

/** The maximum password length of a policy may not be below its minimum, when both are typed. */
export function passwordLengthOrderError(values: HqAppDialogFormValues): string | null {
  const min = textOf(values['passwordPolicy.minimumLength']);
  const max = textOf(values['passwordPolicy.maximumLength']);
  if (min === '' || max === '') return null;
  return Number(max) < Number(min) ? RULE_ERROR_KEYS.passwordLengthOrder : null;
}
