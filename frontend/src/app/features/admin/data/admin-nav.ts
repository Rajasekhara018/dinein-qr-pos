/** Lucide icon key for a nav item — rendered via a `@switch` in `admin-shell.html` (see `shared/icons.ts`). */
export type AdminNavIcon =
  | 'dashboard'
  | 'orders'
  | 'categories'
  | 'items'
  | 'tables'
  | 'reports'
  | 'settings'
  | 'staff'
  | 'kiosk'
  | 'appearance'
  | 'platform';

export interface AdminNavItem {
  label: string;
  /** Absolute router link. */
  path: string;
  icon: AdminNavIcon;
  ownerOnly?: boolean;
  /** Only for accounts with `StaffInfo.platformAdmin` — restaurant onboarding, not restaurant management. */
  platformAdminOnly?: boolean;
  exact?: boolean;
}

export interface AdminNavGroup {
  /** Small-caps section label shown above the group. */
  label: string;
  items: readonly AdminNavItem[];
}

/** Sidebar / drawer navigation, grouped under section labels. Owner-only entries are hidden for managers (and
 *  guarded by `ownerGuard`). */
export const ADMIN_NAV_GROUPS: readonly AdminNavGroup[] = [
  {
    label: 'Overview',
    items: [{ label: 'Dashboard', path: '/admin', exact: true, icon: 'dashboard' }],
  },
  {
    label: 'Operations',
    items: [
      { label: 'Orders', path: '/admin/orders', icon: 'orders' },
      { label: 'Tables & QR', path: '/admin/tables', icon: 'tables' },
    ],
  },
  {
    label: 'Menu',
    items: [
      { label: 'Categories', path: '/admin/menu/categories', icon: 'categories' },
      { label: 'Items', path: '/admin/menu/items', icon: 'items' },
    ],
  },
  {
    label: 'Kiosks',
    items: [
      { label: 'Kiosks', path: '/admin/kiosks', exact: true, icon: 'kiosk' },
      { label: 'Kiosk appearance', path: '/admin/kiosks/appearance', icon: 'appearance' },
    ],
  },
  {
    label: 'Business',
    items: [
      { label: 'Reports', path: '/admin/reports', ownerOnly: true, icon: 'reports' },
      { label: 'Settings', path: '/admin/settings', ownerOnly: true, icon: 'settings' },
      { label: 'Staff & devices', path: '/admin/staff', ownerOnly: true, icon: 'staff' },
    ],
  },
  {
    label: 'Platform',
    items: [{ label: 'Restaurants', path: '/admin/platform', platformAdminOnly: true, icon: 'platform' }],
  },
];

/** Flat list of every nav item, in order — used by the command palette and anything that doesn't need grouping. */
export const ADMIN_NAV: readonly AdminNavItem[] = ADMIN_NAV_GROUPS.flatMap((g) => g.items);

function visible(item: AdminNavItem, isOwner: boolean, isPlatformAdmin: boolean): boolean {
  if (item.platformAdminOnly) return isPlatformAdmin;
  return isOwner || !item.ownerOnly;
}

export function visibleNavGroups(isOwner: boolean, isPlatformAdmin: boolean): AdminNavGroup[] {
  return ADMIN_NAV_GROUPS.map((g) => ({
    ...g,
    items: g.items.filter((item) => visible(item, isOwner, isPlatformAdmin)),
  })).filter((g) => g.items.length > 0);
}

export function visibleNav(isOwner: boolean, isPlatformAdmin: boolean): AdminNavItem[] {
  return ADMIN_NAV.filter((item) => visible(item, isOwner, isPlatformAdmin));
}
