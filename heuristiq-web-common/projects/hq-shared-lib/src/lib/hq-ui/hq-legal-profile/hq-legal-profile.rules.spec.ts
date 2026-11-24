import { describe, expect, it } from 'vitest';
import { HQ_LEGAL_PROFILE_ERRORS as E } from './hq-legal-profile.constants';
import { HqLegalAddress, HqLegalProfile } from './hq-legal-profile.model';
import {
  hqEmptyLegalProfile, hqHasNoPostalCodes, hqLegalProfileErrors, hqNormalizeLegalProfile, hqNormalizeTaxValue, hqTaxIdTypesFor,
} from './hq-legal-profile.rules';

function profile(overrides: Partial<HqLegalProfile> = {}): HqLegalProfile {
  return { ...hqEmptyLegalProfile(), ...overrides };
}

function address(overrides: Partial<HqLegalAddress> = {}): HqLegalAddress {
  return { kind: 'REGISTERED', line1: '12 MG Road', city: 'Bengaluru', region: 'Karnataka', postalCode: '560001', country: 'IN', ...overrides };
}

function keysOf(p: HqLegalProfile): string[] {
  return hqLegalProfileErrors(p).map((e) => `${e.field}:${e.key}`);
}

describe('legal profile rules', () => {
  it('accepts an empty profile and a complete Indian one', () => {
    expect(keysOf(profile())).toEqual([]);
    expect(keysOf(profile({
      entityType: 'PRIVATE_COMPANY', legalName: 'Acme Pvt Ltd', registrationNumber: 'U12345KA2020PTC123456', registrationCountry: 'IN',
      taxRegistered: true, taxIds: [{ type: 'PAN', value: 'abcde 1234 f' }, { type: 'GSTIN', value: '29ABCDE1234F1Z5' }],
      addresses: [address()],
    }))).toEqual([]);
  });

  it('normalises text, codes and tax ids the way the services store them', () => {
    const normalized = hqNormalizeLegalProfile(profile({
      entityType: ' llp ', legalName: '  ', registrationCountry: 'in', taxIds: [{ type: 'pan', value: ' abcde1234f ' }],
      addresses: [address({ postalCode: ' sw1a 1aa ', country: 'gb', line2: '' })],
    }));
    expect(normalized.entityType).toBe('LLP');
    expect(normalized.legalName).toBeNull();
    expect(normalized.registrationCountry).toBe('IN');
    expect(normalized.taxIds).toEqual([{ type: 'PAN', value: 'ABCDE1234F' }]);
    expect(normalized.addresses[0]).toMatchObject({ postalCode: 'SW1A 1AA', country: 'GB', line2: null });
    expect(hqNormalizeTaxValue(null)).toBe('');
  });

  it.each([
    ['PAN', 'ABCDE1234F', 'ABCD1234F'],
    ['GSTIN', '29ABCDE1234F1Z5', '29ABCDE1234F1X5'],
    ['TAN', 'BLRA12345B', 'BLR12345B'],
    ['TRN', '100123456700003', '10012345670000'],
    ['VAT_SA', '300000000000003', '200000000000003'],
    ['VAT_GB', 'GB123456789', 'GB1234'],
    ['CRN_GB', 'SC123456', 'S1234567'],
    ['EIN', '12-3456789', '123-456789'],
    ['TAX_ID', 'DE123456789', 'X'],
  ])('checks %s numbers', (type, valid, invalid) => {
    expect(keysOf(profile({ taxIds: [{ type, value: valid }] }))).toEqual([]);
    expect(keysOf(profile({ taxIds: [{ type, value: invalid }] }))).toEqual([`taxIds.0.value:${E.invalidTaxId}`]);
  });

  it('refuses unknown and repeated tax types and more than ten tax ids', () => {
    expect(keysOf(profile({ taxIds: [{ type: 'SSN', value: '123' }] }))).toEqual([`taxIds.0.type:${E.invalidTaxType}`]);
    expect(keysOf(profile({ taxIds: [{ type: 'PAN', value: 'ABCDE1234F' }, { type: 'PAN', value: 'ABCDE1234G' }] })))
      .toEqual([`taxIds.1.type:${E.duplicateTaxType}`]);
    const many = Array.from({ length: 11 }, () => ({ type: 'TAX_ID', value: 'ABC123' }));
    expect(keysOf(profile({ taxIds: many }))).toContain(`taxIds:${E.tooManyTaxIds}`);
  });

  it('checks postal codes by country', () => {
    expect(keysOf(profile({ addresses: [address({ postalCode: '060001' })] }))).toEqual([`addresses.REGISTERED.postalCode:${E.invalidPostalCode}`]);
    expect(keysOf(profile({ addresses: [address({ postalCode: '' })] }))).toEqual([`addresses.REGISTERED.postalCode:${E.invalidPostalCode}`]);
    expect(keysOf(profile({ addresses: [address({ country: 'US', region: 'NY', postalCode: '10001-1234' })] }))).toEqual([]);
    expect(keysOf(profile({ addresses: [address({ country: 'GB', region: null, postalCode: 'SW1A 1AA' })] }))).toEqual([]);
    expect(keysOf(profile({ addresses: [address({ country: 'AE', region: null, postalCode: null })] }))).toEqual([]);
    expect(keysOf(profile({ addresses: [address({ country: 'AE', region: null, postalCode: '12345' })] })))
      .toEqual([`addresses.REGISTERED.postalCode:${E.invalidPostalCode}`]);
    expect(keysOf(profile({ addresses: [address({ country: 'DE', region: null, postalCode: null })] }))).toEqual([]);
    expect(keysOf(profile({ addresses: [address({ country: 'DE', region: null, postalCode: '10115' })] }))).toEqual([]);
    expect(hqHasNoPostalCodes('ae')).toBe(true);
    expect(hqHasNoPostalCodes('IN')).toBe(false);
  });

  it('needs line 1, city and country, and a region in India and the US', () => {
    expect(keysOf(profile({ addresses: [address({ line1: '', city: ' ', region: null })] }))).toEqual([
      `addresses.REGISTERED.line1:${E.addressRequired}`,
      `addresses.REGISTERED.city:${E.addressRequired}`,
      `addresses.REGISTERED.region:${E.addressRequired}`,
    ]);
    expect(keysOf(profile({ addresses: [address({ country: '' , postalCode: null})] }))).toEqual([`addresses.REGISTERED.country:${E.addressRequired}`]);
    expect(keysOf(profile({ addresses: [address(), address()] }))).toEqual([`addresses.REGISTERED.kind:${E.duplicateAddressKind}`]);
  });

  it('checks entity type, lengths, registration number and country', () => {
    expect(keysOf(profile({ entityType: 'TRUST' }))).toEqual([`entityType:${E.invalidEntityType}`]);
    expect(keysOf(profile({ legalName: 'x'.repeat(201) }))).toEqual([`legalName:${E.tooLong}`]);
    expect(keysOf(profile({ registrationNumber: '#123' }))).toEqual([`registrationNumber:${E.invalidRegistrationNumber}`]);
    expect(keysOf(profile({ registrationNumber: '1'.repeat(51) }))).toEqual([`registrationNumber:${E.tooLong}`]);
    expect(keysOf(profile({ registrationCountry: 'IND' }))).toEqual([`registrationCountry:${E.invalidCountry}`]);
  });

  it("offers the registration country's tax types first, then the general one", () => {
    expect(hqTaxIdTypesFor('IN').slice(0, 4).map((t) => t.code)).toEqual(['PAN', 'GSTIN', 'TAN', 'TAX_ID']);
    expect(hqTaxIdTypesFor('AE')[0].code).toBe('TRN');
    expect(hqTaxIdTypesFor(null)[0].code).toBe('TAX_ID');
  });
});
