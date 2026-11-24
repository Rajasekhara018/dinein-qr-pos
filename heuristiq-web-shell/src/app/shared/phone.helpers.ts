import { RPhoneNumber } from './models/uam.models';

/** A dial code as the API may send it: digits with an optional leading "+" (not an ISO country name). */
const DIAL_CODE_PATTERN = /^\+?\d{1,4}$/;

/**
 * Turns the phone the API returns ({countryCode, number}, a plain string, or nothing) into the value
 * a `hq-phone-number-input` control holds. With a dial code and a national number it becomes international
 * (`+919876543210`); a number that already starts with "+", or has no usable dial code, is passed on as-is so
 * the input reads a bare number in the app's default country.
 */
export function phoneToControlValue(phone: RPhoneNumber | string | null | undefined): string {
  if (phone === null || phone === undefined) return '';
  if (typeof phone === 'string') return phone.trim();
  const number = (phone.number ?? '').trim();
  if (!number || number.startsWith('+')) return number;
  const code = (phone.countryCode ?? '').trim();
  if (DIAL_CODE_PATTERN.test(code)) {
    return `+${code.replace('+', '')}${number.replace(/\D/g, '')}`;
  }
  return number;
}
