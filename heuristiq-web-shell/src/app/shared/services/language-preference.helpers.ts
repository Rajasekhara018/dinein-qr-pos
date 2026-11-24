import { LanguageEnum, UserPreference } from '../models/uam.models';
import { LANGUAGE_ENUM_BY_CODE } from './language-preference.constants';

/** The app's language code for a language saved on the account, or null when the app has no text for it (or none was saved). */
export function languageCodeFromEnum(value: LanguageEnum | string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const wanted = value.toUpperCase();
  return Object.keys(LANGUAGE_ENUM_BY_CODE).find((code) => LANGUAGE_ENUM_BY_CODE[code] === wanted) ?? null;
}

/** The value to save on the account for an app language code, or null for a code the account cannot hold. */
export function languageEnumFromCode(code: string | null | undefined): LanguageEnum | null {
  return (code && LANGUAGE_ENUM_BY_CODE[code.toLowerCase()]) || null;
}

/** The language a saved preference asks for (Profile > Preferences > Regional), as an app language code. */
export function preferredLanguageCode(preference: UserPreference | null | undefined): string | null {
  return languageCodeFromEnum(preference?.regional?.language);
}
