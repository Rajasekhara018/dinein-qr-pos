import { phoneToControlValue } from './phone.helpers';

describe('phoneToControlValue', () => {
  it('returns empty for missing values', () => {
    expect(phoneToControlValue(null)).toBe('');
    expect(phoneToControlValue(undefined)).toBe('');
    expect(phoneToControlValue({ countryCode: null, number: null })).toBe('');
  });

  it('passes a plain string or a bare number through', () => {
    expect(phoneToControlValue(' 9876543210 ')).toBe('9876543210');
    expect(phoneToControlValue({ countryCode: null, number: '9876543210' })).toBe('9876543210');
    expect(phoneToControlValue({ countryCode: 'IN', number: '9876543210' })).toBe('9876543210');
  });

  it('builds an international number from a dial code', () => {
    expect(phoneToControlValue({ countryCode: '+91', number: '9876543210' })).toBe('+919876543210');
    expect(phoneToControlValue({ countryCode: '44', number: '7911 123456' })).toBe('+447911123456');
  });

  it('keeps a number that is already international', () => {
    expect(phoneToControlValue({ countryCode: '+91', number: '+919876543210' })).toBe('+919876543210');
  });
});
