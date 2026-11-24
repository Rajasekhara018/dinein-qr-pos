import { HqLegalAddressKind, HqPostalCodeRule, HqTaxIdType } from './hq-legal-profile.model';

/** The kinds of legal entity, as the services accept them. */
export const HQ_ENTITY_TYPES: readonly string[] = [
  'INDIVIDUAL', 'SOLE_PROPRIETOR', 'PARTNERSHIP', 'LLP', 'PRIVATE_COMPANY', 'PUBLIC_COMPANY', 'NON_PROFIT', 'GOVERNMENT', 'OTHER',
];

/** The general tax id, for a country without its own type here. */
export const HQ_GENERAL_TAX_ID = 'TAX_ID';

/** The tax id types checked by both services (same patterns as the backend), tested after hqNormalizeTaxValue. */
export const HQ_TAX_ID_TYPES: readonly HqTaxIdType[] = [
  { code: 'PAN', country: 'IN', pattern: /^[A-Z]{5}[0-9]{4}[A-Z]$/, example: 'ABCDE1234F' },
  { code: 'GSTIN', country: 'IN', pattern: /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/, example: '29ABCDE1234F1Z5' },
  { code: 'TAN', country: 'IN', pattern: /^[A-Z]{4}[0-9]{5}[A-Z]$/, example: 'BLRA12345B' },
  { code: 'TRN', country: 'AE', pattern: /^[0-9]{15}$/, example: '100123456700003' },
  { code: 'VAT_SA', country: 'SA', pattern: /^3[0-9]{13}3$/, example: '300000000000003' },
  { code: 'VAT_GB', country: 'GB', pattern: /^(GB)?([0-9]{9}|[0-9]{12}|GD[0-9]{3}|HA[0-9]{3})$/, example: 'GB123456789' },
  { code: 'CRN_GB', country: 'GB', pattern: /^([0-9]{8}|[A-Z]{2}[0-9]{6})$/, example: '01234567' },
  { code: 'EIN', country: 'US', pattern: /^[0-9]{2}-?[0-9]{7}$/, example: '12-3456789' },
  { code: HQ_GENERAL_TAX_ID, country: null, pattern: /^[A-Z0-9][A-Z0-9./-]{2,39}$/, example: '' },
];

/** Postal codes by country; a country not listed has optional codes checked by HQ_POSTAL_CODE_GENERIC_PATTERN. */
export const HQ_POSTAL_CODE_RULES: Readonly<Record<string, HqPostalCodeRule>> = {
  IN: { required: true, pattern: /^[1-9][0-9]{5}$/ },
  US: { required: true, pattern: /^[0-9]{5}(-[0-9]{4})?$/ },
  SA: { required: true, pattern: /^[0-9]{5}(-[0-9]{4})?$/ },
  GB: { required: true, pattern: /^[A-Z]{1,2}[0-9][A-Z0-9]? ?[0-9][A-Z]{2}$/ },
  AE: { required: false, none: true },
  QA: { required: false, none: true },
  HK: { required: false, none: true },
};

export const HQ_POSTAL_CODE_GENERIC_PATTERN = /^[A-Z0-9][A-Z0-9 -]{1,11}$/;

/** Countries whose addresses need a state or region. */
export const HQ_REGION_REQUIRED_COUNTRIES: readonly string[] = ['IN', 'US'];

export const HQ_REGISTRATION_NUMBER_PATTERN = /^[A-Za-z0-9][A-Za-z0-9 ./-]*$/;
export const HQ_COUNTRY_CODE_PATTERN = /^[A-Z]{2}$/;

export const HQ_ADDRESS_KINDS: readonly HqLegalAddressKind[] = ['REGISTERED', 'OPERATING'];

/** Field lengths the services accept. */
export const HQ_LEGAL_PROFILE_LIMITS = {
  name: 200,
  registrationNumber: 50,
  taxIds: 10,
  addressLine: 200,
  city: 100,
  region: 100,
  postalCode: 12,
} as const;

/** The message keys both services return, and the form shows through its texts provider. */
export const HQ_LEGAL_PROFILE_ERRORS = {
  invalidEntityType: 'legalProfile.invalidEntityType',
  invalidCountry: 'legalProfile.invalidCountry',
  tooLong: 'legalProfile.tooLong',
  invalidRegistrationNumber: 'legalProfile.invalidRegistrationNumber',
  invalidTaxType: 'legalProfile.invalidTaxType',
  duplicateTaxType: 'legalProfile.duplicateTaxType',
  invalidTaxId: 'legalProfile.invalidTaxId',
  tooManyTaxIds: 'legalProfile.tooManyTaxIds',
  invalidAddressKind: 'legalProfile.invalidAddressKind',
  duplicateAddressKind: 'legalProfile.duplicateAddressKind',
  addressRequired: 'legalProfile.addressRequired',
  invalidPostalCode: 'legalProfile.invalidPostalCode',
  staleVersion: 'legalProfile.staleVersion',
} as const;
