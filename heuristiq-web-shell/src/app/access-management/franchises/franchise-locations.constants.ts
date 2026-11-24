import { LocationLevel, LocationType, PlanRule, PlanScope } from './franchise.models';

/** Screen permission key (mapped to privileges in hq-shared-lib's privilege map) and audit screen key of the Locations screen. */
export const FRANCHISE_LOCATIONS_PERMISSION = 'view_franchise_locations';

/** Stable audit key: queryable history, never renamed. */
export const FRANCHISE_LOCATIONS_SCREEN_KEY = 'franchise.locations';
/** The screen key of the page that adds or changes a place (reported to the audit service when it opens). */
export const FRANCHISE_LOCATION_FORM_SCREEN_KEY = 'franchise.location_form';
/** The route segment of the places list under access management; its form pages are `new` and `:id/edit` below it. */
export const FRANCHISE_LOCATIONS_ROUTE = 'franchise-locations';

/** Privilege codes (exact names in privilege.json) behind the Locations screen and the place pickers. */
export const LOCATION_PRIVILEGES = {
  list: 'LOCATION_LIST',
  read: 'LOCATION_READ',
  children: 'LOCATION_CHILDREN_LIST',
  create: 'LOCATION_CREATE',
  update: 'LOCATION_UPDATE',
  status: 'LOCATION_STATUS_UPDATE',
  import: 'LOCATION_IMPORT_CREATE',
  resolvePlan: 'FRANCHISE_PLAN_RESOLVE_READ',
} as const;

/** Rows per page of the locations list and of the plans list. */
export const LOCATIONS_PAGE_SIZE = 20;

/** Largest page a picker asks for (the API caps a page at 100). */
export const PICKER_PAGE_SIZE = 100;

/** The nine place types, shallowest first. */
export const LOCATION_TYPES: readonly LocationType[] = ['STATE', 'DISTRICT', 'CITY', 'MUNICIPALITY', 'TOWN', 'MANDAL', 'WARD', 'VILLAGE', 'PINCODE'];

export const LOCATION_LEVELS: readonly LocationLevel[] = ['REGION', 'URBAN', 'LOCAL'];

/** The level of each type (fixed by the service). */
export const LOCATION_TYPE_LEVEL: Readonly<Record<LocationType, LocationLevel>> = {
  STATE: 'REGION', DISTRICT: 'REGION', CITY: 'URBAN', MUNICIPALITY: 'URBAN', TOWN: 'URBAN', MANDAL: 'LOCAL', WARD: 'LOCAL', VILLAGE: 'LOCAL', PINCODE: 'LOCAL',
};

/** The types a place of each type may sit directly under (a STATE sits under its country, so under nothing). */
export const ALLOWED_PARENT_TYPES: Readonly<Record<LocationType, readonly LocationType[]>> = {
  STATE: [],
  DISTRICT: ['STATE'],
  CITY: ['DISTRICT', 'STATE'],
  MUNICIPALITY: ['DISTRICT', 'STATE'],
  TOWN: ['DISTRICT', 'STATE'],
  MANDAL: ['DISTRICT'],
  WARD: ['CITY', 'MUNICIPALITY', 'TOWN', 'DISTRICT'],
  VILLAGE: ['MANDAL', 'CITY', 'MUNICIPALITY', 'TOWN', 'DISTRICT'],
  PINCODE: ['WARD', 'VILLAGE', 'MANDAL', 'CITY', 'MUNICIPALITY', 'TOWN', 'DISTRICT'],
};

/** Types a type default may sit under (a state or a district). */
export const TYPE_DEFAULT_PARENT_TYPES: readonly LocationType[] = ['STATE', 'DISTRICT'];

/** Translation keys of the place types, levels, plan scopes and resolution rules; a code with no key is shown as it is. */
export const LOCATION_TYPE_LABEL_KEYS: Readonly<Record<string, string>> = Object.fromEntries(
  LOCATION_TYPES.map((type) => [type, `accessManagement.franchiseLocations.types.${type}`]));

export const LOCATION_LEVEL_LABEL_KEYS: Readonly<Record<string, string>> = {
  REGION: 'accessManagement.franchiseLocations.levels.REGION',
  URBAN: 'accessManagement.franchiseLocations.levels.URBAN',
  LOCAL: 'accessManagement.franchiseLocations.levels.LOCAL',
};

export const PLAN_SCOPES: readonly PlanScope[] = ['PLACE', 'TYPE_DEFAULT', 'COUNTRY', 'GENERAL'];

export const PLAN_SCOPE_LABEL_KEYS: Readonly<Record<string, string>> = {
  PLACE: 'accessManagement.franchisePlans.scopes.PLACE',
  TYPE_DEFAULT: 'accessManagement.franchisePlans.scopes.TYPE_DEFAULT',
  COUNTRY: 'accessManagement.franchisePlans.scopes.COUNTRY',
  GENERAL: 'accessManagement.franchisePlans.scopes.GENERAL',
};

export const PLAN_RULES: readonly PlanRule[] = ['OWN', 'TYPE_DEFAULT', 'PARENT', 'GENERAL'];

export const PLAN_RULE_LABEL_KEYS: Readonly<Record<string, string>> = {
  OWN: 'accessManagement.franchisePlans.rules.OWN',
  TYPE_DEFAULT: 'accessManagement.franchisePlans.rules.TYPE_DEFAULT',
  PARENT: 'accessManagement.franchisePlans.rules.PARENT',
  GENERAL: 'accessManagement.franchisePlans.rules.GENERAL',
};

/** Limits of one import (the service's own caps, checked here first so nothing big is sent in vain). */
export const IMPORT_MAX_ROWS = 5000;
export const IMPORT_MAX_CHARS = 2_000_000;

/** The CSV header of an import and one example line; the template download is these two lines. */
export const IMPORT_CSV_HEADER = 'country_code,type,code,name,parent_type,parent_code,active';
export const IMPORT_CSV_EXAMPLE = 'IN,TOWN,SAMPLE-TOWN-1,Sample Town,DISTRICT,AP-553,true';
export const IMPORT_TEMPLATE_FILE_NAME = 'locations-template.csv';
export const IMPORT_FILE_ACCEPT = '.csv,text/csv';

/** Longest code and name of a place, and the range of the display order (the service's limits). */
export const LOCATION_CODE_MAX = 40;
export const LOCATION_NAME_MAX = 200;
export const LOCATION_ORDER_MAX = 1_000_000;

/** Status the service answers with when a rule of the data stops the change (plans use the place, someone else changed it). */
export const HTTP_CONFLICT = 409;

/** The country a new place starts in. */
export const DEFAULT_LOCATION_COUNTRY = 'IN';
