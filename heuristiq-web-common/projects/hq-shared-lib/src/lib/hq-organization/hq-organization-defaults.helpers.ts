import { HQ_TENANT_CLAIM } from './hq-organization-defaults.constants';

/** The organization id in an access token (a JWT), or null when the token is missing or unreadable. */
export function hqTenantIdFromToken(token: string | null | undefined): string | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1] ?? '';
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    const value = (JSON.parse(json) as Record<string, unknown>)[HQ_TENANT_CLAIM];
    return typeof value === 'string' && value ? value : null;
  } catch {
    return null;
  }
}
