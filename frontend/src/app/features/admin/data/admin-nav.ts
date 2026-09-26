export interface AdminNavItem {
  label: string;
  /** Absolute router link. */
  path: string;
  /** SVG path data for a 24×24 stroke icon. */
  icon: string;
  ownerOnly?: boolean;
  exact?: boolean;
}

/** Sidebar / drawer navigation. Owner-only entries are hidden for managers (and guarded by `ownerGuard`). */
export const ADMIN_NAV: readonly AdminNavItem[] = [
  {
    label: 'Dashboard',
    path: '/admin',
    exact: true,
    icon: 'M4 13h6V4H4v9Zm0 7h6v-4H4v4Zm10 0h6v-9h-6v9Zm0-16v4h6V4h-6Z',
  },
  {
    label: 'Orders',
    path: '/admin/orders',
    icon: 'M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 5h6M9 12h6M9 16h4',
  },
  {
    label: 'Categories',
    path: '/admin/menu/categories',
    icon: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z',
  },
  {
    label: 'Items',
    path: '/admin/menu/items',
    icon: 'M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01',
  },
  {
    label: 'Tables & QR',
    path: '/admin/tables',
    icon: 'M4 4h6v6H4V4Zm10 0h6v6h-6V4ZM4 14h6v6H4v-6Zm10 0h2v2h-2v-2Zm4 0h2v2h-2v-2Zm-4 4h2v2h-2v-2Zm4 0h2v2h-2v-2Z',
  },
  {
    label: 'Reports',
    path: '/admin/reports',
    ownerOnly: true,
    icon: 'M3 20h18M6 16v-5m5 5V7m5 9v-8m4 8V4',
  },
  {
    label: 'Settings',
    path: '/admin/settings',
    ownerOnly: true,
    icon: 'M4 6h9m4 0h3M4 12h3m4 0h9M4 18h11m4 0h1M15 4v4M9 10v4m8 2v4',
  },
  {
    label: 'Staff & devices',
    path: '/admin/staff',
    ownerOnly: true,
    icon: 'M16 20v-1a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v1m6.5-9a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM21 20v-1a4 4 0 0 0-3-3.9M15.5 4.1a3.5 3.5 0 0 1 0 6.8',
  },
];

export function visibleNav(isOwner: boolean): AdminNavItem[] {
  return ADMIN_NAV.filter((item) => isOwner || !item.ownerOnly);
}
