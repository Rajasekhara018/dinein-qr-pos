import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import {
  CountryCode,
  getCountries,
  getCountryCallingCode,
  isValidPhoneNumber,
  parsePhoneNumberFromString,
} from 'libphonenumber-js/max';
import { HQ_PHONE_FALLBACK_COUNTRY, HQ_PHONE_MAX_E164_DIGITS } from './hq-phone-number-input.constants';

/** One entry of the country-code list. */
export interface HqPhoneCountry {
  /** ISO 3166 alpha-2 code, for example 'IN'. */
  iso: string;
  /** Country name in the language asked for. */
  name: string;
  /** Calling code digits, for example '91'. */
  callingCode: string;
  /** Flag emoji (a platform that cannot draw it shows the two letters). */
  flag: string;
}

/** A number split for the two inputs of the field. */
export interface HqPhoneParts {
  country: string;
  /** Digits after the calling code (no leading zero, no spaces). */
  national: string;
}

/** The flag emoji of an ISO country code. */
export function hqPhoneFlag(iso: string): string {
  return String.fromCodePoint(...[...iso.toUpperCase()].map((char) => 127397 + char.charCodeAt(0)));
}

/** The calling code digits of a country ('IN' gives '91'), or '' when the country is unknown. */
export function hqPhoneCallingCode(iso: string): string {
  try {
    return getCountryCallingCode(iso.toUpperCase() as CountryCode);
  } catch {
    return '';
  }
}

/** All countries with their calling codes, sorted by name in the given locale; `allowed` limits the list. */
export function hqPhoneCountries(locale: string, allowed?: readonly string[] | null): HqPhoneCountry[] {
  let names: Intl.DisplayNames | null = null;
  try {
    names = new Intl.DisplayNames([locale], { type: 'region' });
  } catch {
    names = null;
  }
  const wanted = allowed && allowed.length ? new Set(allowed.map((iso) => iso.toUpperCase())) : null;
  return getCountries()
    .filter((iso) => !wanted || wanted.has(iso))
    .map((iso) => ({
      iso,
      name: names?.of(iso) ?? iso,
      callingCode: getCountryCallingCode(iso),
      flag: hqPhoneFlag(iso),
    }))
    // An unusable locale (DisplayNames rejected it) would make localeCompare throw too, so sort with the default one.
    .sort((a, b) => a.name.localeCompare(b.name, names ? locale : undefined));
}

/**
 * Splits a stored or typed value into the country and the national digits. A value with a plus is read as international;
 * one without is read in `defaultCountry` (this is how numbers saved before the country code was captured, such as a bare
 * 10-digit Indian mobile, come back into the field). Never throws; a value that cannot be read gives the default country
 * and the digits as typed.
 */
export function hqParsePhone(value: string | null | undefined, defaultCountry: string = HQ_PHONE_FALLBACK_COUNTRY): HqPhoneParts {
  const fallback = defaultCountry.toUpperCase();
  const text = (value ?? '').trim();
  if (!text) {
    return { country: fallback, national: '' };
  }
  const parsed = parsePhoneNumberFromString(text, fallback as CountryCode);
  if (parsed) {
    return { country: parsed.country ?? countryOfCallingCode(parsed.countryCallingCode, fallback), national: parsed.nationalNumber };
  }
  const digits = text.replace(/\D/g, '');
  if (text.startsWith('+')) {
    const split = splitByCallingCode(digits, fallback);
    if (split) return split;
  }
  return { country: fallback, national: digits.replace(/^0+/, '') };
}

/** The international form (+ and digits) of a country and national digits; '' when there are no digits. */
export function hqToInternational(country: string, national: string): string {
  const digits = (national ?? '').replace(/\D/g, '');
  const code = hqPhoneCallingCode(country);
  return digits && code ? `+${code}${digits}`.slice(0, HQ_PHONE_MAX_E164_DIGITS + 1) : '';
}

/** True when the value is a valid number of any country (a number without a plus is read in `defaultCountry`). */
export function hqIsValidPhone(value: string | null | undefined, defaultCountry: string = HQ_PHONE_FALLBACK_COUNTRY): boolean {
  const text = (value ?? '').trim();
  return !!text && isValidPhoneNumber(text, defaultCountry.toUpperCase() as CountryCode);
}

/** The canonical E.164 form (+919876543210) of a valid number, or null when it is not valid. */
export function hqNormalizePhone(value: string | null | undefined, defaultCountry: string = HQ_PHONE_FALLBACK_COUNTRY): string | null {
  const parsed = parsePhoneNumberFromString((value ?? '').trim(), defaultCountry.toUpperCase() as CountryCode);
  return parsed && parsed.isValid() ? parsed.number : null;
}

/** Form validator for a plain text control holding a phone number; empty is valid (add Validators.required to require it). */
export function hqPhoneValidator(defaultCountry: string = HQ_PHONE_FALLBACK_COUNTRY): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string | null | undefined;
    return !value || hqIsValidPhone(value, defaultCountry) ? null : { phone: true };
  };
}

function countryOfCallingCode(callingCode: string, preferred: string): string {
  if (hqPhoneCallingCode(preferred) === callingCode) return preferred;
  return getCountries().find((iso) => getCountryCallingCode(iso) === callingCode) ?? preferred;
}

/** An incomplete international number: finds the calling code at the front (1 to 3 digits). */
function splitByCallingCode(digits: string, preferred: string): HqPhoneParts | null {
  for (let length = 1; length <= 3 && length < digits.length; length++) {
    const code = digits.slice(0, length);
    const country = countryOfCallingCode(code, '');
    if (country && hqPhoneCallingCode(country) === code) {
      return { country: hqPhoneCallingCode(preferred) === code ? preferred : country, national: digits.slice(length) };
    }
  }
  return null;
}
