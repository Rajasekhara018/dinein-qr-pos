/**
 * What the Applications hub (the /home screen) shows. The applications themselves are NOT listed here: they are the sidebar's
 * areas (NAV_AREAS), and the screens inside each one are the menu entries (NAV_ITEMS), so a screen added to the menu appears on
 * the hub by itself and the hub can never disagree with the menu or with what the user may open.
 */

/**
 * The screen to open first for each application, in order of preference; the first one the user may open wins. When none of them is
 * allowed the first screen the user can open in that application is used instead.
 */
export const APP_LANDING_PATHS: Readonly<Record<string, ReadonlyArray<string>>> = {
  admin: ['/admin/dashboard', '/admin/rides', '/admin/customers'],
  train: ['/admin/train-admin/dashboard', '/admin/train-admin/bookings'],
  matrimony: ['/matrimony/dashboard', '/matrimony/profile-review'],
  wallet: ['/wallet/home', '/wallet/admin'],
  accounting: ['/accounting/admin/chart', '/wallet/admin/accounting/transactions'],
  reports: ['/reports/home'],
  access: ['/access-management/users', '/access-management/organizations'],
  franchise: ['/access-management/franchises', '/admin/franchise-admin/payout-approvals'],
  owner: ['/access-management/my-franchises', '/matrimony/agent/complaints'],
  agent: ['/matrimony/agent', '/wallet/agent'],
  food: ['/food/dashboard', '/food/orders', '/food/restaurants'],
  ims: ['/inventory/dashboard', '/inventory/items'],
  developer: ['/api-docs', '/service-registry'],
  qa: ['/qa/overview', '/system/qa'],
};

/** How many screen names a card previews before "+N more". */
export const APP_PREVIEW_COUNT = 3;

/**
 * Capabilities every application shares (sign-in and access control, approvals, audit, organizations, documents, notifications ...).
 * `path` must be a screen in the menu: its permission decides whether the tile is a link or only information.
 * The title and description are i18n keys: home.platform.<key>.title and home.platform.<key>.desc.
 */
export interface PlatformCapability {
  key: string;
  icon: string;
  path: string;
}

export const PLATFORM_CAPABILITIES: ReadonlyArray<PlatformCapability> = [
  { key: 'access', icon: 'bi-shield-lock', path: '/access-management/roles' },
  { key: 'approvals', icon: 'bi-patch-check', path: '/admin/change-requests' },
  { key: 'audit', icon: 'bi-journal-text', path: '/admin/audit' },
  { key: 'organizations', icon: 'bi-building', path: '/access-management/organizations' },
  { key: 'workflow', icon: 'bi-diagram-3', path: '/workflow' },
  { key: 'documents', icon: 'bi-file-earmark-check', path: '/document-management/document-types' },
  { key: 'communications', icon: 'bi-chat-square-text', path: '/nms-templates' },
  { key: 'integration', icon: 'bi-hdd-network', path: '/service-registry' },
  { key: 'quality', icon: 'bi-clipboard-data', path: '/qa/overview' },
];

/** The filter chips above the application grid. */
export type AppFilter = 'all' | 'available' | 'locked';
export const APP_FILTERS: ReadonlyArray<AppFilter> = ['all', 'available', 'locked'];

/**
 * Which catalogue application (UAM's ApplicationCatalogue code) each hub application belongs to. An organization sees a hub
 * application only when it is subscribed to that code. Train is deliberately NOT listed: the catalogue has no Train
 * application and it is not required for now, so the Train area is hidden everywhere (hub, sidebar, header, quick navigate).
 */
export const APP_CODE_BY_SUITE_KEY: Readonly<Record<string, string>> = {
  admin: 'BESTRIDE_RIDER_APP',
  matrimony: 'PASUPU_KUMKUMA_MATRIMONY_APP',
  wallet: 'CENTRALISED_WALLET_APP',
  food: 'FOOD_DELIVER_APP',
  franchise: 'FRANCHISE_MANAGEMENT_APP',
  // Inventory (IMS_* privileges) is part of the Best Purchase catalogue application.
  ims: 'BEST_PURCHASE_APP',
};

/**
 * Hub areas that are platform tooling, not catalogue applications, so no subscription applies: they show whenever the user may
 * open at least one of their screens. Any area in neither list is hidden. The My franchise and Agent workspaces are here too:
 * they have no catalogue application of their own, and each of their sections follows the subscription of the application it
 * comes from (WORKSPACE_SECTION_APPS in nav-areas.constants.ts).
 */
export const CORE_SUITE_APP_KEYS: ReadonlyArray<string> = ['access', 'reports', 'developer', 'qa', 'wallet', 'accounting', 'owner', 'agent'];

/** Whether an application is a sellable product or a capability shared by all products. */
export type AppKind = 'product' | 'shared';

/**
 * Products are the catalogue applications an organization subscribes to, sold to end users: in privilege.json these are the
 * `service` groups admin and rider-* (ride, dispatch, customer, pricing, onboarding, location, driver-earnings), matrimony,
 * food, rider-wallet and rider-train (the privilegeCode prefixes RIDE, MATRIMONY, FOOD, WALLET, TRAIN). Everything the catalogue
 * marks SHARED serves all of them: uam (access), rider-reports and search (reporting), nms, dms, rider-audit, rider-workflow
 * and rider-config.
 */
export const PRODUCT_APP_KEYS: ReadonlyArray<string> = ['admin', 'train', 'matrimony', 'wallet', 'food', 'franchise', 'ims'];

/**
 * Applications that show on the Applications hub only (not in the sidebar, header or quick navigate), whatever the
 * subscriptions: the user needs to be able to open at least one of their screens. Train has no catalogue application yet.
 * Train, Franchise and IMS also have a sidebar area, which (like every product area) shows only when the organization is subscribed.
 */
export const HUB_ONLY_APP_KEYS: ReadonlyArray<string> = ['train', 'franchise', 'ims', 'communications', 'documents', 'support', 'payments'];

/**
 * Applications the privilege catalogue has but the sidebar has no area for. They show on the hub when the user can open one of
 * their screens (the menu items whose path starts with one of `pathPrefixes`, taken out of the area that lists them) or holds
 * any privilege of their features. Name: home.apps.name.<key>; description: home.apps.tagline.<key>.
 */
export interface HubExtraApp {
  key: string;
  icon: string;
  pathPrefixes: ReadonlyArray<string>;
}

export const HUB_EXTRA_APPS: ReadonlyArray<HubExtraApp> = [
  { key: 'communications', icon: 'bi-chat-square-text', pathPrefixes: ['/nms-templates'] },
  { key: 'documents', icon: 'bi-file-earmark-check', pathPrefixes: ['/document-management'] },
  { key: 'support', icon: 'bi-headset', pathPrefixes: ['/admin/support'] },
  { key: 'payments', icon: 'bi-credit-card', pathPrefixes: [] },
];

/** The order and i18n namespace (home.apps.kind.<kind>) of the two groups on the hub. */
export const APP_KINDS: ReadonlyArray<AppKind> = ['product', 'shared'];

/**
 * The features each application offers, generated from privilege.json by `npm run gen:app-features` (rules in
 * app-feature-rules.json). Every privilege is in exactly one feature; a user sees a feature when they hold any of its privileges.
 * The text of a feature is home.apps.features.<app>.<key>.title and .desc.
 */
export { APP_FEATURES } from './app-features.generated';

/** How many features a card shows before "Show all". */
export const FEATURE_PREVIEW_COUNT = 4;

/** localStorage key of the access token (its `apps` claim lists the applications the user is granted). */
export const ACCESS_TOKEN_KEY = 'access_token';
