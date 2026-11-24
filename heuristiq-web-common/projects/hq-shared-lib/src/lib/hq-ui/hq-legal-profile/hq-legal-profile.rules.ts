import {
  HQ_ADDRESS_KINDS, HQ_COUNTRY_CODE_PATTERN, HQ_ENTITY_TYPES, HQ_LEGAL_PROFILE_ERRORS as E, HQ_LEGAL_PROFILE_LIMITS as L,
  HQ_POSTAL_CODE_GENERIC_PATTERN, HQ_POSTAL_CODE_RULES, HQ_REGION_REQUIRED_COUNTRIES, HQ_REGISTRATION_NUMBER_PATTERN,
  HQ_TAX_ID_TYPES,
} from './hq-legal-profile.constants';
import { HqLegalAddress, HqLegalProfile, HqLegalProfileError, HqTaxId, HqTaxIdType } from './hq-legal-profile.model';

/** A profile with nothing filled in, as the services return one that was never saved. */
export function hqEmptyLegalProfile(): HqLegalProfile {
  return {
    entityType: null, legalName: null, tradingName: null, registrationNumber: null, registrationCountry: null,
    taxRegistered: false, taxIds: [], addresses: [], version: null,
  };
}

/** A tax id as stored: upper case, without spaces. */
export function hqNormalizeTaxValue(value: string | null | undefined): string {
  return (value ?? '').replace(/\s+/g, '').toUpperCase();
}

/** The checked type for a code, or undefined for one the platform does not know. */
export function hqTaxIdType(code: string | null | undefined): HqTaxIdType | undefined {
  return HQ_TAX_ID_TYPES.find((type) => type.code === (code ?? '').toUpperCase());
}

/** The tax id types to offer for a country first (its own, then the general one, then the others). */
export function hqTaxIdTypesFor(country: string | null | undefined): HqTaxIdType[] {
  const code = (country ?? '').toUpperCase();
  return [...HQ_TAX_ID_TYPES].sort((a, b) => rank(a, code) - rank(b, code));
}

function rank(type: HqTaxIdType, country: string): number {
  if (country && type.country === country) return 0;
  return type.country === null ? 1 : 2;
}

/** True when the country does not use postal codes (the field is hidden and must stay empty). */
export function hqHasNoPostalCodes(country: string | null | undefined): boolean {
  return !!HQ_POSTAL_CODE_RULES[(country ?? '').toUpperCase()]?.none;
}

/** The profile as the services store it: trimmed, empty text as null, codes and tax ids upper case. */
export function hqNormalizeLegalProfile(profile: HqLegalProfile): HqLegalProfile {
  return {
    entityType: code(profile.entityType),
    legalName: text(profile.legalName),
    tradingName: text(profile.tradingName),
    registrationNumber: text(profile.registrationNumber),
    registrationCountry: code(profile.registrationCountry),
    taxRegistered: !!profile.taxRegistered,
    taxIds: (profile.taxIds ?? []).map((t) => ({ type: (code(t.type) ?? ''), value: hqNormalizeTaxValue(t.value) })),
    addresses: (profile.addresses ?? []).map(normalizeAddress),
    version: profile.version ?? null,
  };
}

function normalizeAddress(address: HqLegalAddress): HqLegalAddress {
  const postal = text(address.postalCode);
  return {
    kind: address.kind,
    line1: text(address.line1) ?? '',
    line2: text(address.line2),
    city: text(address.city) ?? '',
    region: text(address.region),
    postalCode: postal ? postal.toUpperCase() : null,
    country: code(address.country) ?? '',
  };
}

function text(value: string | null | undefined): string | null {
  const trimmed = (value ?? '').trim();
  return trimmed ? trimmed : null;
}

function code(value: string | null | undefined): string | null {
  const trimmed = text(value);
  return trimmed ? trimmed.toUpperCase() : null;
}

/** Everything wrong with a profile, by the rules both services apply; empty when it can be saved. */
export function hqLegalProfileErrors(raw: HqLegalProfile): HqLegalProfileError[] {
  const profile = hqNormalizeLegalProfile(raw);
  const errors: HqLegalProfileError[] = [];
  const add = (field: string, key: string) => errors.push({ field, key });

  if (profile.entityType && !HQ_ENTITY_TYPES.includes(profile.entityType)) add('entityType', E.invalidEntityType);
  if ((profile.legalName?.length ?? 0) > L.name) add('legalName', E.tooLong);
  if ((profile.tradingName?.length ?? 0) > L.name) add('tradingName', E.tooLong);
  if (profile.registrationNumber) {
    if (profile.registrationNumber.length > L.registrationNumber) add('registrationNumber', E.tooLong);
    else if (!HQ_REGISTRATION_NUMBER_PATTERN.test(profile.registrationNumber)) add('registrationNumber', E.invalidRegistrationNumber);
  }
  if (profile.registrationCountry && !HQ_COUNTRY_CODE_PATTERN.test(profile.registrationCountry)) add('registrationCountry', E.invalidCountry);

  taxIdErrors(profile.taxIds, add);
  profile.addresses.forEach((address, index) => addressErrors(address, index, profile.addresses, add));
  return errors;
}

function taxIdErrors(taxIds: HqTaxId[], add: (field: string, key: string) => void): void {
  if (taxIds.length > L.taxIds) add('taxIds', E.tooManyTaxIds);
  const seen = new Set<string>();
  taxIds.forEach((taxId, index) => {
    const type = hqTaxIdType(taxId.type);
    if (!type) {
      add(`taxIds.${index}.type`, E.invalidTaxType);
      return;
    }
    if (seen.has(type.code)) add(`taxIds.${index}.type`, E.duplicateTaxType);
    seen.add(type.code);
    if (!type.pattern.test(taxId.value)) add(`taxIds.${index}.value`, E.invalidTaxId);
  });
}

function addressErrors(address: HqLegalAddress, index: number, all: HqLegalAddress[], add: (field: string, key: string) => void): void {
  const at = `addresses.${address.kind}`;
  if (!HQ_ADDRESS_KINDS.includes(address.kind)) {
    add(`addresses.${index}.kind`, E.invalidAddressKind);
    return;
  }
  if (all.findIndex((other) => other.kind === address.kind) !== index) add(`${at}.kind`, E.duplicateAddressKind);
  if (!address.line1) add(`${at}.line1`, E.addressRequired);
  else if (address.line1.length > L.addressLine) add(`${at}.line1`, E.tooLong);
  if ((address.line2?.length ?? 0) > L.addressLine) add(`${at}.line2`, E.tooLong);
  if (!address.city) add(`${at}.city`, E.addressRequired);
  else if (address.city.length > L.city) add(`${at}.city`, E.tooLong);
  if (!address.country) add(`${at}.country`, E.addressRequired);
  else if (!HQ_COUNTRY_CODE_PATTERN.test(address.country)) add(`${at}.country`, E.invalidCountry);
  if (!address.region && HQ_REGION_REQUIRED_COUNTRIES.includes(address.country)) add(`${at}.region`, E.addressRequired);
  else if ((address.region?.length ?? 0) > L.region) add(`${at}.region`, E.tooLong);
  if (!postalCodeValid(address.country, address.postalCode)) add(`${at}.postalCode`, E.invalidPostalCode);
}

function postalCodeValid(country: string, postalCode: string | null | undefined): boolean {
  const value = postalCode ?? '';
  if (value.length > L.postalCode) return false;
  const rule = HQ_POSTAL_CODE_RULES[country];
  if (rule?.none) return !value;
  if (!value) return !rule?.required;
  return (rule?.pattern ?? HQ_POSTAL_CODE_GENERIC_PATTERN).test(value);
}
