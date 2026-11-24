/**
 * Plain-language areas the 800-odd technical permission codes are grouped under. The first segment of a permission's API path
 * (/api/v1/<segment>/...) decides the area; a segment not listed here goes under 'other'. Area keys double as the last part of
 * the translation key accessManagement.roles.access.areas.<key>.
 */
export const ROLE_ACCESS_AREA_BY_SEGMENT: Readonly<Record<string, string>> = {
  uam: 'access',
  auth: 'access',
  admin: 'admin',
  train: 'train',
  irctc: 'train',
  matrimony: 'matrimony',
  food: 'food',
  booking: 'rides',
  ride: 'rides',
  dispatch: 'rides',
  driver: 'rides',
  'driver-earnings': 'rides',
  location: 'rides',
  station: 'rides',
  call: 'rides',
  'split-fare': 'rides',
  customer: 'rides',
  corporate: 'rides',
  pricing: 'rides',
  users: 'rides',
  user: 'rides',
  support: 'rides',
  'profile-images': 'rides',
  payment: 'payments',
  payments: 'payments',
  wallet: 'wallet',
  nms: 'notifications',
  workflow: 'workflow',
  config: 'configuration',
  onboarding: 'onboarding',
  dms: 'documents',
  documents: 'documents',
  audit: 'audit',
  search: 'search',
  report: 'reports',
  reports: 'reports',
  qa: 'quality',
  'test-management': 'quality',
  franchise: 'franchise',
  commerce: 'shop',
};

export const ROLE_ACCESS_OTHER_AREA = 'other';

/** Bootstrap icon shown beside each area, so areas can be told apart at a glance. */
export const ROLE_ACCESS_AREA_ICONS: Readonly<Record<string, string>> = {
  access: 'bi-key',
  admin: 'bi-gear-wide-connected',
  matrimony: 'bi-heart',
  food: 'bi-basket',
  train: 'bi-train-front',
  rides: 'bi-car-front',
  payments: 'bi-credit-card',
  wallet: 'bi-wallet2',
  notifications: 'bi-bell',
  workflow: 'bi-diagram-3',
  configuration: 'bi-sliders',
  onboarding: 'bi-person-plus',
  documents: 'bi-folder2-open',
  audit: 'bi-journal-text',
  search: 'bi-search',
  franchise: 'bi-shop',
  reports: 'bi-file-earmark-bar-graph',
  quality: 'bi-clipboard-check',
  shop: 'bi-cart',
  other: 'bi-three-dots',
};


/** Areas in the order they are shown. */
export const ROLE_ACCESS_AREA_ORDER: readonly string[] = [
  'access', 'admin', 'matrimony', 'train', 'rides', 'payments', 'wallet', 'notifications', 'workflow',
  'configuration', 'onboarding', 'documents', 'audit', 'search', 'franchise', 'reports', 'food', 'shop', 'quality', ROLE_ACCESS_OTHER_AREA,
];

/**
 * The applications the areas belong to, so a role can be built per application: the Rider app, Matrimony, Train, Wallet and payments,
 * Franchise, and the platform itself (access, configuration and the services every application shares). An area not listed here
 * belongs to the platform. Application keys double as the last part of accessManagement.roles.access.applications.<key>.
 */
export const ROLE_ACCESS_APPLICATION_BY_AREA: Readonly<Record<string, string>> = {
  reports: 'reports',
  food: 'food',
  shop: 'shop',
  quality: 'platform',
  admin: 'rider',
  rides: 'rider',
  matrimony: 'matrimony',
  train: 'train',
  wallet: 'money',
  payments: 'money',
  franchise: 'franchise',
  access: 'platform',
  configuration: 'platform',
  onboarding: 'platform',
  documents: 'platform',
  notifications: 'platform',
  workflow: 'platform',
  audit: 'platform',
  search: 'platform',
  [ROLE_ACCESS_OTHER_AREA]: 'platform',
};

export const ROLE_ACCESS_PLATFORM_APPLICATION = 'platform';

/** Applications in the order they are shown: the platform (access management) first, as the areas always were. */
export const ROLE_ACCESS_APPLICATION_ORDER: readonly string[] = [ROLE_ACCESS_PLATFORM_APPLICATION, 'rider', 'matrimony', 'train', 'money', 'franchise', 'reports', 'food', 'shop'];

/** Bootstrap icon of each application. */
export const ROLE_ACCESS_APPLICATION_ICONS: Readonly<Record<string, string>> = {
  rider: 'bi-car-front',
  matrimony: 'bi-heart',
  train: 'bi-train-front',
  money: 'bi-wallet2',
  franchise: 'bi-shop',
  platform: 'bi-gear-wide-connected',
  reports: 'bi-file-earmark-bar-graph',
  food: 'bi-basket',
  shop: 'bi-cart',
};

/** What a permission lets someone do, from its action: looking at things, or changing them. */
export const ROLE_ACCESS_VIEW_ACTIONS: ReadonlySet<string> = new Set(['READ', 'LIST', 'VIEW', 'SEARCH', 'FILTER']);

/** Trailing words of a permission code that name the action rather than the thing acted on. */
export const ROLE_ACCESS_ACTION_WORDS: ReadonlySet<string> = new Set([
  'LIST', 'READ', 'VIEW', 'SEARCH', 'FILTER', 'CREATE', 'UPDATE', 'DELETE', 'MANAGE', 'APPROVE', 'REJECT', 'VERIFY',
  'REVIEW', 'ENABLE', 'DISABLE', 'ACTIVATE', 'DEACTIVATE', 'LOCK', 'UNLOCK', 'IMPORT', 'EXPORT', 'DOWNLOAD', 'UPLOAD',
  'ASSIGN', 'UNASSIGN', 'ACCESS',
]);

export const ROLE_ACCESS_SEARCH_DEBOUNCE_MS = 200;

/** Words in a permission code that mean the action is hard to undo or moves money; a feature with one is tagged "Sensitive". */
export const ROLE_ACCESS_SENSITIVE_WORDS: ReadonlySet<string> = new Set([
  'DELETE', 'REFUND', 'REVERSE', 'PAYOUT', 'TOPUP', 'ADJUSTMENT', 'ROLLBACK', 'GRANT', 'CREDIT',
]);

/** Cross-product applications and shared service groups. Unlisted groups are product applications. */
export const ROLE_ACCESS_SHARED_APPLICATIONS: readonly string[] = ['platform', 'money', 'franchise', 'reports'];
