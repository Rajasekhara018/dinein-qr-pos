/**
 * Canonical routes the core auth code redirects to. The admin and kitchen apps MUST expose these paths
 * (or change them here).
 */
export const ADMIN_PATHS = {
  home: '/admin',
  login: '/admin/login',
  changePassword: '/admin/change-password',
} as const;

export const KITCHEN_PATHS = {
  home: '/kitchen',
  login: '/kitchen/login',
} as const;
