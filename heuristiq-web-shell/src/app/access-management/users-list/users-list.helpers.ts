import { USER_TYPES } from '../../shared/models/uam.models';

/** Staff accounts of the web portal have no role named after a type; they are signed in as this type. */
export const DEFAULT_USER_TYPE: string = 'OPS';

/**
 * The user type to show for an existing user. UAM keeps no separate type on the account: the type shows up as a role named after
 * it (SUPER_ADMIN, DRIVER, ...). The first match in {@link USER_TYPES} order wins; a user with none of those roles (for
 * example an organization's own administrator role) is a staff account.
 */
export function userTypeOf(user: Record<string, unknown>): string {
  const roles = Array.isArray(user['roles']) ? user['roles'].map((r) => String(r).trim().toUpperCase()) : [];
  return USER_TYPES.find((type) => roles.includes(type)) ?? DEFAULT_USER_TYPE;
}
