/** Screen permission keys (mapped to privileges in hq-shared-lib's privilege map) and audit screen keys of the franchise screens. */
export const FRANCHISES_PERMISSION = 'view_franchises';
export const MY_FRANCHISES_PERMISSION = 'view_my_franchises';
export const FRANCHISE_PLANS_PERMISSION = 'view_franchise_plans';

/** Stable audit keys (area.screen): queryable history, never renamed. */
export const FRANCHISES_SCREEN_KEY = 'access_management.franchises';
export const MY_FRANCHISES_SCREEN_KEY = 'access_management.my_franchises';
export const FRANCHISE_PLANS_SCREEN_KEY = 'access_management.franchise_plans';
/** The screen key of the page that adds or changes a plan. */
export const FRANCHISE_PLAN_FORM_SCREEN_KEY = 'access_management.franchise_plan_form';
/** The route segment of the plans list; its form pages are `new` and `:id/edit` below it. */
export const FRANCHISE_PLANS_ROUTE = 'franchise-plans';
/** The route segment of the packages list; its form pages are `new` and `:id/edit` below it. */
export const FRANCHISE_PACKAGES_ROUTE = 'franchise-packages';
/** The screen key of the page that opens or manages a franchise. */
export const FRANCHISE_FORM_SCREEN_KEY = 'access_management.franchise_form';
/** The route segment of the franchises list; its form pages are `new` and `:id/edit` below it. */
export const FRANCHISES_ROUTE = 'franchises';


/** Most registered applications offered when opening a franchise or giving it another one. */
export const APPLICATION_OPTIONS_LIMIT = 200;

/** Privilege codes (exact names in privilege.json) behind the actions on the franchise screens. */
export const FRANCHISE_PRIVILEGES = {
  create: 'FRANCHISE_CREATE',
  addApplication: 'FRANCHISE_APPLICATION_CREATE',
  updateApplication: 'FRANCHISE_APPLICATION_UPDATE',
  listAgents: 'FRANCHISE_AGENT_LIST',
  addAgent: 'FRANCHISE_AGENT_CREATE',
  requestAgents: 'FRANCHISE_AGENT_REQUEST_CREATE',
  createPlan: 'FRANCHISE_PLAN_CREATE',
  readPlan: 'FRANCHISE_PLAN_READ',
  updatePlan: 'FRANCHISE_PLAN_UPDATE',
  deletePlan: 'FRANCHISE_PLAN_DELETE',
  setStatus: 'FRANCHISE_STATUS_UPDATE',
  removeAgent: 'FRANCHISE_AGENT_DELETE',
  listAdminMembers: 'FRANCHISE_ADMIN_MEMBER_LIST',
  readLegalProfile: 'FRANCHISE_LEGAL_PROFILE_READ',
  readMyLegalProfile: 'FRANCHISE_MINE_LEGAL_PROFILE_READ',
  readOnboarding: 'FRANCHISE_ONBOARDING_READ',
  readHierarchy: 'FRANCHISE_HIERARCHY_READ',
  readMyOnboarding: 'FRANCHISE_MINE_ONBOARDING_READ',
  /** Switch an application of a franchise to another package (or none). */
  changePackage: 'FRANCHISE_APPLICATION_PACKAGE_UPDATE',
  /** The event types of an application, to show the commission in force for each. */
  listEventTypes: 'EVENT_TYPE_LIST',
  /** The franchise form's own place picker (usable places only), for whoever opens or changes franchises. */
  placeOptions: 'FRANCHISE_PLACE_OPTION_LIST',
  placeOptionChildren: 'FRANCHISE_PLACE_OPTION_CHILDREN_LIST',
} as const;

/**
 * Applications sold by place: a franchise for one of them must cover a place, or no sale is ever routed to it. Kept equal to the
 * franchise service's {@code FranchiseConstants.LOCATION_BASED_APPLICATIONS}, which refuses the request anyway.
 */
export const LOCATION_BASED_APPLICATIONS: readonly string[] = ['BESTRIDE_RIDER_APP', 'FOOD_DELIVER_APP'];

/** How long the notices after opening a franchise ("sign in again", "organization is company-run") stay up, in milliseconds. */
export const SIGN_IN_AGAIN_NOTICE_MS = 8000;

/** The screen key of the administrator's read-only members page of one franchise (route `franchises/:id/members`). */
export const FRANCHISE_ADMIN_MEMBERS_SCREEN_KEY = 'access_management.franchise_admin_members';

/** Translation keys of the franchise statuses; a code with no key is shown as it is. */
export const FRANCHISE_STATUS_LABEL_KEYS: Readonly<Record<string, string>> = {
  ACTIVE: 'accessManagement.franchises.statuses.ACTIVE',
  SUSPENDED: 'accessManagement.franchises.statuses.SUSPENDED',
  CLOSED: 'accessManagement.franchises.statuses.CLOSED',
};

/** What each status change is called in the confirmation dialogs: the target status to its translation key prefix. */
export const FRANCHISE_STATUS_ACTION_KEYS: Readonly<Record<string, string>> = {
  SUSPENDED: 'accessManagement.franchises.statusActions.suspend',
  ACTIVE: 'accessManagement.franchises.statusActions.reactivate',
  CLOSED: 'accessManagement.franchises.statusActions.close',
};

/** Shown in a cell that has nothing to show (a franchise with no place, an owner with no name). */
export const EMPTY_CELL = '—';

/** Translation keys of the note under the place picker that says where the suggested terms came from. */
export const PLACE_NOTE_KEYS = {
  own: 'accessManagement.franchisePlans.resolve.own',
  inheritedFrom: 'accessManagement.franchisePlans.resolve.inheritedFrom',
  inheritedCountry: 'accessManagement.franchisePlans.resolve.inheritedCountry',
  none: 'accessManagement.franchises.placePlanNone',
} as const;

/** Agents asked for by default when an owner asks for more places. */
export const DEFAULT_REQUESTED_AGENTS = 5;

/** Smallest agent limit a franchise application can have. */
export const MIN_AGENT_LIMIT = 1;

/** Used when the preconfigured offers could not be loaded: the currency fees are shown in until UAM says otherwise. */
export const FALLBACK_CURRENCY = 'INR';

/** Fraction digits every fee is shown and kept with. */
export const FEE_FRACTION_DIGITS = 2;

/**
 * Time zones whose country is known without asking the browser. The browser's own table (Intl.Locale#getTimeZones) is used
 * first where it exists; this covers browsers without it, for the countries the platform serves today.
 */
export const TIME_ZONE_COUNTRIES: Readonly<Record<string, string>> = {
  'Asia/Kolkata': 'IN',
  'Asia/Calcutta': 'IN',
  'Asia/Dubai': 'AE',
  'Asia/Riyadh': 'SA',
  'Asia/Singapore': 'SG',
  'Asia/Colombo': 'LK',
  'Asia/Kathmandu': 'NP',
  'Asia/Dhaka': 'BD',
  'Europe/London': 'GB',
  'America/New_York': 'US',
  'America/Chicago': 'US',
  'America/Denver': 'US',
  'America/Los_Angeles': 'US',
  'Australia/Sydney': 'AU',
};

/** Currencies a franchise may be priced in. Only the rupee for now; add codes here when other currencies are opened up. */
export const FRANCHISE_CURRENCIES: readonly string[] = ['INR'];

/** Language the country names stored with a plan are written in (they are shown to every administrator the same way). */
export const COUNTRY_NAME_LANGUAGE = 'en';

/** The first-level subdivisions (states) of every country, by ISO 3166-2 code: see assets/geo/README.md. */
export const GEO_SUBDIVISIONS_URL = 'assets/geo/subdivisions.json';

/** The districts of one country live in <folder>/<ISO country code>.json, loaded when that country is chosen. */
export const GEO_DISTRICTS_URL = 'assets/geo/districts';

/** Translation namespace of the package of an application of a managed franchise. */
export const FRANCHISE_PACKAGE_I18N = 'accessManagement.franchises.package';
