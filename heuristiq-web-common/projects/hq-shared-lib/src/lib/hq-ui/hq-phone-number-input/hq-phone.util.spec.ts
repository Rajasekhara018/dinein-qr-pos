import { FormControl } from '@angular/forms';
import {
  hqIsValidPhone,
  hqNormalizePhone,
  hqParsePhone,
  hqPhoneCallingCode,
  hqPhoneCountries,
  hqPhoneFlag,
  hqPhoneValidator,
  hqToInternational,
} from './hq-phone.util';
import { HQ_INDIAN_MOBILE_PATTERN, HQ_PHONE_FALLBACK_COUNTRY } from './hq-phone-number-input.constants';

describe('hq-phone.util', () => {
  it('builds the flag emoji from an ISO code in any case', () => {
    expect(hqPhoneFlag('in')).toBe('\u{1F1EE}\u{1F1F3}');
    expect(hqPhoneFlag('US')).toBe('\u{1F1FA}\u{1F1F8}');
  });

  it('gives the calling code of a country, or empty for an unknown one', () => {
    expect(hqPhoneCallingCode('in')).toBe('91');
    expect(hqPhoneCallingCode('GB')).toBe('44');
    expect(hqPhoneCallingCode('ZZ')).toBe('');
    expect(hqPhoneCallingCode('')).toBe('');
  });

  describe('hqPhoneCountries', () => {
    it('lists every country sorted by its name in the locale', () => {
      const list = hqPhoneCountries('en');
      expect(list.length).toBeGreaterThan(200);
      const names = list.map((c) => c.name);
      expect([...names].sort((a, b) => a.localeCompare(b, 'en'))).toEqual(names);
      expect(list.find((c) => c.iso === 'IN')).toEqual({ iso: 'IN', name: 'India', callingCode: '91', flag: hqPhoneFlag('IN') });
    });

    it('limits the list to the allowed countries (any case); an empty list means all', () => {
      expect(hqPhoneCountries('en', ['us', 'IN']).map((c) => c.iso)).toEqual(['IN', 'US']);
      expect(hqPhoneCountries('en', []).length).toBe(hqPhoneCountries('en', null).length);
    });

    it('falls back to the ISO code as the name when the locale is unusable', () => {
      const list = hqPhoneCountries('!!', ['IN', 'GB']);
      expect(list.map((c) => c.name)).toEqual(['GB', 'IN']);
    });
  });

  describe('hqParsePhone', () => {
    it('returns the default country and no digits for an empty value', () => {
      expect(hqParsePhone(null)).toEqual({ country: HQ_PHONE_FALLBACK_COUNTRY, national: '' });
      expect(hqParsePhone('   ', 'us')).toEqual({ country: 'US', national: '' });
      expect(hqParsePhone(undefined)).toEqual({ country: 'IN', national: '' });
    });

    it('reads international numbers', () => {
      expect(hqParsePhone('+919876543210')).toEqual({ country: 'IN', national: '9876543210' });
      expect(hqParsePhone('+44 20 7946 0958', 'IN')).toEqual({ country: 'GB', national: '2079460958' });
    });

    it('reads a number without a country code in the default country (legacy bare mobile)', () => {
      expect(hqParsePhone('9876543210', 'in')).toEqual({ country: 'IN', national: '9876543210' });
    });

    it('resolves a number whose country is ambiguous from its calling code, preferring the default', () => {
      expect(hqParsePhone('+1 999 555 0123', 'CA')).toEqual({ country: 'CA', national: '9995550123' });
      const other = hqParsePhone('+1 999 555 0123', 'IN');
      expect(hqPhoneCallingCode(other.country)).toBe('1');
      expect(other.national).toBe('9995550123');
    });

    it('keeps the default country for a non-geographic calling code', () => {
      expect(hqParsePhone('+80012345678', 'IN')).toEqual({ country: 'IN', national: '12345678' });
    });

    it('splits an incomplete international number at its calling code', () => {
      expect(hqParsePhone('+441', 'IN')).toEqual({ country: 'GB', national: '1' });
      expect(hqParsePhone('+441', 'JE')).toEqual({ country: 'JE', national: '1' });
      expect(hqParsePhone('+71', 'RU')).toEqual({ country: 'RU', national: '1' });
    });

    it('falls back to the default country and the digits when nothing can be read', () => {
      expect(hqParsePhone('+999', 'IN')).toEqual({ country: 'IN', national: '999' });
      expect(hqParsePhone('+', 'IN')).toEqual({ country: 'IN', national: '' });
      expect(hqParsePhone('abc', 'IN')).toEqual({ country: 'IN', national: '' });
    });
  });

  it('builds the international form, capped at the E.164 length', () => {
    expect(hqToInternational('IN', '98 765-43210')).toBe('+919876543210');
    expect(hqToInternational('IN', '')).toBe('');
    expect(hqToInternational('IN', null as unknown as string)).toBe('');
    expect(hqToInternational('ZZ', '123')).toBe('');
    expect(hqToInternational('IN', '1234567890123456789')).toBe('+911234567890123');
  });

  it('validates numbers of any country', () => {
    expect(hqIsValidPhone('+919876543210')).toBe(true);
    expect(hqIsValidPhone('9876543210', 'in')).toBe(true);
    expect(hqIsValidPhone('+442079460958')).toBe(true);
    expect(hqIsValidPhone('12345')).toBe(false);
    expect(hqIsValidPhone('')).toBe(false);
    expect(hqIsValidPhone(null)).toBe(false);
  });

  it('normalizes a valid number to E.164 and rejects an invalid one', () => {
    expect(hqNormalizePhone('98765 43210')).toBe('+919876543210');
    expect(hqNormalizePhone('020 7946 0958', 'gb')).toBe('+442079460958');
    expect(hqNormalizePhone('12345')).toBeNull();
    expect(hqNormalizePhone(null)).toBeNull();
  });

  it('validates a plain form control, treating empty as valid', () => {
    const validate = hqPhoneValidator();
    expect(validate(new FormControl(''))).toBeNull();
    expect(validate(new FormControl(null))).toBeNull();
    expect(validate(new FormControl('9876543210'))).toBeNull();
    expect(validate(new FormControl('12345'))).toEqual({ phone: true });
    expect(hqPhoneValidator('GB')(new FormControl('020 7946 0958'))).toBeNull();
  });

  it('keeps the deprecated Indian mobile pattern working', () => {
    expect(HQ_INDIAN_MOBILE_PATTERN.test('9876543210')).toBe(true);
    expect(HQ_INDIAN_MOBILE_PATTERN.test('5876543210')).toBe(false);
  });
});
