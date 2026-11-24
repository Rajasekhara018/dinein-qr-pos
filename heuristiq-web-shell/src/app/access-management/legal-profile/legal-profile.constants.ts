/** Whose legal profile a page edits: an organization, any franchise (administrators) or one the signed-in person owns. */
export type LegalProfileSubject = 'organization' | 'franchise' | 'myFranchise';

/** The last segment of the three legal-profile routes (`organizations/:id/legal-profile`, ...). */
export const LEGAL_PROFILE_ROUTE = 'legal-profile';

export const ORGANIZATION_LEGAL_PROFILE_PERMISSION = 'view_organization_legal_profile';
export const FRANCHISE_LEGAL_PROFILE_PERMISSION = 'view_franchise_legal_profile';
export const MY_FRANCHISE_LEGAL_PROFILE_PERMISSION = 'view_my_franchise_legal_profile';

export const ORGANIZATION_LEGAL_PROFILE_SCREEN_KEY = 'access_management.organization_legal_profile';
export const FRANCHISE_LEGAL_PROFILE_SCREEN_KEY = 'access_management.franchise_legal_profile';
export const MY_FRANCHISE_LEGAL_PROFILE_SCREEN_KEY = 'access_management.my_franchise_legal_profile';

/** What each kind of page reads and saves with, where it goes back to, and its words. */
export interface LegalProfileSubjectConfig {
  readPrivilege: string;
  updatePrivilege: string;
  backRoute: string;
  listKey: string;
}

export const LEGAL_PROFILE_SUBJECTS: Readonly<Record<LegalProfileSubject, LegalProfileSubjectConfig>> = {
  organization: {
    readPrivilege: 'ORGANIZATION_LEGAL_PROFILE_READ',
    updatePrivilege: 'ORGANIZATION_LEGAL_PROFILE_UPDATE',
    backRoute: 'organizations',
    listKey: 'nav.items.organizations',
  },
  franchise: {
    readPrivilege: 'FRANCHISE_LEGAL_PROFILE_READ',
    updatePrivilege: 'FRANCHISE_LEGAL_PROFILE_UPDATE',
    backRoute: 'franchises',
    listKey: 'nav.items.franchises',
  },
  myFranchise: {
    readPrivilege: 'FRANCHISE_MINE_LEGAL_PROFILE_READ',
    updatePrivilege: 'FRANCHISE_MINE_LEGAL_PROFILE_UPDATE',
    backRoute: 'my-franchises',
    listKey: 'nav.items.myFranchises',
  },
};

/**
 * The words that name a field in the "Fill in: ..." hint of the setup action bar, by the last segment of the field path the
 * validation rules report (`addresses.REGISTERED.city` → `city`, `taxIds.0.value` → `value`); values are translation keys.
 */
export const LEGAL_PROFILE_ISSUE_LABEL_KEYS: Readonly<Record<string, string>> = {
  entityType: 'legalProfile.entityTypeLabel',
  legalName: 'legalProfile.legalNameLabel',
  tradingName: 'legalProfile.tradingNameLabel',
  registrationNumber: 'legalProfile.registrationNumberLabel',
  registrationCountry: 'legalProfile.registrationCountryLabel',
  taxIds: 'legalProfile.taxSection',
  type: 'legalProfile.taxTypeLabel',
  value: 'legalProfile.taxValueLabel',
  kind: 'legalProfile.addressSection',
  line1: 'legalProfile.line1Label',
  line2: 'legalProfile.line2Label',
  city: 'legalProfile.cityLabel',
  country: 'legalProfile.addressCountryLabel',
  region: 'legalProfile.regionLabel',
  postalCode: 'legalProfile.postalCodeLabel',
};

/** HTTP status the services answer when the profile changed since it was read. */
export const LEGAL_PROFILE_STALE_STATUS = 409;

/** Prefix of the message keys the services return for a profile they refuse (translated under legalProfile.errors.*). */
export const LEGAL_PROFILE_ERROR_PREFIX = 'legalProfile.';

/**
 * Countries whose states and districts are picked from the franchise location data (the places the franchise service holds).
 * India only for now; to cover another country add its ISO 3166 alpha-2 code here once its places are loaded in the franchise
 * location table. Any other country keeps plain text boxes for the state and the city / district.
 */
export const LEGAL_PROFILE_LOCATION_COUNTRIES: readonly string[] = ['IN'];

/** Privileges (names in privilege.json) of the place-option reads the pickers use; without them the boxes stay plain text. */
export const LEGAL_PROFILE_LOCATION_PRIVILEGES = {
  states: 'FRANCHISE_PLACE_OPTION_LIST',
  districts: 'FRANCHISE_PLACE_OPTION_CHILDREN_LIST',
} as const;

/** Places read per request: more than any state has districts, and within the search index page cap. */
export const LEGAL_PROFILE_LOCATION_PAGE_SIZE = 500;

/** Location types of the two pickers (the state, and the district below it). */
export const LEGAL_PROFILE_STATE_TYPE = 'STATE';
export const LEGAL_PROFILE_DISTRICT_TYPE = 'DISTRICT';
