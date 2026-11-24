import { LanguageEnum } from '../models/uam.models';
import { ISO_COUNTRY_CODES, LANGUAGE_TAG_BY_ENUM, TIME_ZONE_RENAMES } from './regional-names.constants';

/** A value and the name a person reads for it. */
export interface RegionalOption {
  value: string;
  label: string;
}

/** The browser's name for a code, or the code itself when it has none (an old browser, an unknown code). */
function displayName(locale: string, type: 'language' | 'region' | 'currency', code: string): string {
  try {
    return new Intl.DisplayNames([locale], { type }).of(code) ?? code;
  } catch {
    return code;
  }
}

/**
 * A language as people pick it: its own name and, when different, its English name ("తెలుగు (Telugu)", "English"), so anyone can
 * find their language whatever language the screen is in. Falls back to the code for one the browser does not know.
 */
export function languageLabel(code: LanguageEnum | string | null | undefined): string {
  if (!code) {
    return '';
  }
  const tag = LANGUAGE_TAG_BY_ENUM[code.toUpperCase() as LanguageEnum] ?? code.toLowerCase();
  const own = displayName(tag, 'language', tag);
  const english = displayName('en', 'language', tag);
  if (own === tag && english === tag) {
    return code;
  }
  return own.toLowerCase() === english.toLowerCase() ? english : `${own} (${english})`;
}

/** The languages as choices, in the given order. */
export function languageOptions(codes: readonly LanguageEnum[]): RegionalOption[] {
  return codes.map((code) => ({ value: code, label: languageLabel(code) }));
}

/** Every country (ISO 3166) by name in the screen's language, with its code ("India (IN)"), sorted by name. */
export function countryOptions(locale: string): RegionalOption[] {
  return ISO_COUNTRY_CODES.map((code) => ({ value: code, label: `${displayName(locale, 'region', code)} (${code})` }))
    .sort((a, b) => a.label.localeCompare(b.label, locale));
}

/** Every currency (ISO 4217) by name in the screen's language, with its code ("Indian Rupee (INR)"), sorted by name. */
export function currencyOptions(locale: string): RegionalOption[] {
  return supported('currency').map((code) => ({ value: code, label: `${displayName(locale, 'currency', code)} (${code})` }))
    .sort((a, b) => a.label.localeCompare(b.label, locale));
}

/** Every IANA time zone ("Asia/Kolkata"), under its current name, sorted. */
export function timeZoneOptions(): RegionalOption[] {
  const zones = new Set(supported('timeZone').map((zone) => TIME_ZONE_RENAMES[zone] ?? zone));
  return [...zones].sort().map((zone) => ({ value: zone, label: zone.replace(/_/g, ' ') }));
}

function supported(kind: 'currency' | 'timeZone'): string[] {
  const intl = Intl as unknown as { supportedValuesOf?: (key: string) => string[] };
  try {
    return intl.supportedValuesOf?.(kind) ?? [];
  } catch {
    return [];
  }
}
