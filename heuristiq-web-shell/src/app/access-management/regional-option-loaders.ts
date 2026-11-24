import { Observable, of } from 'rxjs';
import { countryOptions, currencyOptions, timeZoneOptions } from '../shared/utils/regional-names.helpers';
import { AdminFormOption } from './admin-form.types';

/** The screen's language (the language service keeps it on the page), for naming countries and currencies in it. */
function screenLocale(): string {
  return (typeof document !== 'undefined' && document.documentElement.lang) || 'en';
}

/** An organization's currency: every ISO 4217 currency by name, in the screen's language. */
export const loadCurrencyOptions = (): Observable<AdminFormOption[]> => of(currencyOptions(screenLocale()));

/** An organization's home country: every ISO 3166 country by name, in the screen's language. */
export const loadCountryOptions = (): Observable<AdminFormOption[]> => of(countryOptions(screenLocale()));

/** An organization's time zone: every IANA time zone. */
export const loadTimeZoneOptions = (): Observable<AdminFormOption[]> => of(timeZoneOptions());
