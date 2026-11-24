import { InjectionToken } from '@angular/core';

export interface HqLanguage {
  /** ISO 639-1 code, e.g. "en", "hi", "ar". */
  code: string;
  /** Name in the language itself, as shown in the switcher. */
  nativeName: string;
  dir: 'ltr' | 'rtl';
}

/**
 * What HqLanguageSwitcher needs from the app: the available languages, which one is active and
 * how to switch. The library has no translation dependency, so the app provides its own language
 * service under HQ_LANGUAGE_PROVIDER (e.g. `{ provide: HQ_LANGUAGE_PROVIDER, useExisting: LanguageService }`).
 */
export interface HqLanguageProvider {
  readonly languages: readonly HqLanguage[];
  /** Code of the active language. */
  readonly currentLanguage: string;
  /** The active language entry. */
  readonly current: HqLanguage;
  setLanguage(code: string): void;
}

export const HQ_LANGUAGE_PROVIDER = new InjectionToken<HqLanguageProvider>('HQ_LANGUAGE_PROVIDER');
