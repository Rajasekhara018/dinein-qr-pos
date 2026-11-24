import { Observable } from 'rxjs';

/** Which address of a business: where it is registered, or where it works from when that is elsewhere. */
export type HqLegalAddressKind = 'REGISTERED' | 'OPERATING';

/** One tax registration: its type (PAN, GSTIN, TRN, EIN, ... or a general TAX_ID) and its number. */
export interface HqTaxId {
  type: string;
  value: string;
}

/** A business address in any country; the postal code is checked by `country` (ISO 3166 alpha-2). */
export interface HqLegalAddress {
  kind: HqLegalAddressKind;
  line1: string;
  line2?: string | null;
  city: string;
  region?: string | null;
  postalCode?: string | null;
  country: string;
}

/**
 * The legal entity behind an organization or a franchise: what kind of entity it is, its legal and trading names, where and
 * under which number it is registered, its tax ids and its addresses. The same shape is served by UAM (organizations) and
 * the franchise service (franchises). `version` is the one read, sent back on save (null before the first save).
 */
export interface HqLegalProfile {
  entityType: string | null;
  legalName: string | null;
  tradingName: string | null;
  registrationNumber: string | null;
  registrationCountry: string | null;
  taxRegistered: boolean;
  taxIds: HqTaxId[];
  addresses: HqLegalAddress[];
  version: number | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

/** A tax id type the platform checks, the country that issues it (null for the general one) and an example number. */
export interface HqTaxIdType {
  code: string;
  country: string | null;
  pattern: RegExp;
  example: string;
}

/** How a country's postal codes are checked: required with a pattern, or not used at all. */
export interface HqPostalCodeRule {
  required: boolean;
  pattern?: RegExp;
  none?: boolean;
}

/** A problem with a profile: the field it is on (`taxIds.0.value`, `addresses.REGISTERED.postalCode`) and its message key. */
export interface HqLegalProfileError {
  field: string;
  key: string;
}

/** A choice in one of the form's lists (a country by name). */
export interface HqLegalProfileOption {
  value: string;
  label: string;
}

/**
 * Where an address's state and district come from, supplied by the app (the library has no HTTP or search dependency).
 * `supports(country)` says whether states and districts are listed for that country (ISO 3166 alpha-2); for any other
 * country the form keeps plain text boxes. `states` lists the states of a country; `districts` the districts of one state,
 * where `state` is the state's value as `states` returned it. Both are read lazily and may fail (the form then shows the
 * select's own retry notice).
 */
export interface HqLegalAddressLookup {
  supports(country: string): boolean;
  states(country: string): Observable<HqLegalProfileOption[]>;
  districts(country: string, state: string): Observable<HqLegalProfileOption[]>;
}
