/** Screen permission key (mapped to privileges in hq-shared-lib's privilege map) of the owner's "My drivers" screen. */
export const FRANCHISE_DRIVERS_PERMISSION = 'view_franchise_drivers';

/** Stable audit key of the "My drivers" screen: queryable history, never renamed. */
export const FRANCHISE_DRIVERS_SCREEN_KEY = 'access_management.franchise_drivers';

/** Route segment under `franchises/:franchiseId` of the "My drivers" screen. */
export const FRANCHISE_DRIVERS_ROUTE = 'drivers';

/**
 * Route of the "My drivers" menu entry (My franchise workspace): not a screen of its own, it opens the drivers of the owner's
 * only franchise, or My franchises to pick one when there are several (or none, or the list cannot be read).
 */
export const MY_DRIVERS_ROUTE = 'my-drivers';

/** Route segment of the owner's franchise list, where an owner of several franchises picks the one whose drivers to open. */
export const MY_FRANCHISES_ROUTE = 'my-franchises';

/** Privilege codes (exact names in privilege.json) behind the screen and its actions. */
export const FRANCHISE_DRIVERS_PRIVILEGES = {
  listDrivers: 'FRANCHISE_DRIVER_LIST',
  onboardDriver: 'FRANCHISE_DRIVER_ONBOARDING_CREATE',
  listOwnFranchises: 'FRANCHISE_MINE_LIST',
} as const;

/** Rows per page of the driver list. */
export const FRANCHISE_DRIVERS_PAGE_SIZE = 20;

/**
 * The driver onboarding form for owners, served by the federated Rider_AdminWeb remote mounted under `/admin`
 * (screen permission `onboard_franchise_drivers`); organization and franchise are preset from the query parameters.
 */
export const DRIVER_ONBOARDING_PATH: readonly string[] = ['/admin', 'franchise-drivers', 'onboard'];

/** API error status to the translation key of the message shown on this screen. */
export const DRIVER_ERROR_KEYS: Readonly<Record<number, string>> = {
  403: 'accessManagement.franchiseDrivers.errors.forbidden',
  404: 'accessManagement.franchiseDrivers.errors.notFound',
  503: 'accessManagement.franchiseDrivers.errors.unavailable',
};

export const DRIVER_FALLBACK_ERROR_KEY = 'accessManagement.franchiseDrivers.errors.generic';
