import { InjectionToken } from '@angular/core';

/**
 * The words HqLanguageSwitcher shows. The consuming app provides HQ_LANGUAGE_SWITCHER_TEXTS with
 * its own translated text (each member is called when needed, so a language switch is picked up);
 * without it the English defaults below are used.
 */
export interface HqLanguageSwitcherTexts {
  /** Accessible label / tooltip of the trigger button. */
  change(): string;
  /** Heading of the dropdown list. */
  label(): string;
}

export const HQ_LANGUAGE_SWITCHER_TEXTS = new InjectionToken<HqLanguageSwitcherTexts>('HQ_LANGUAGE_SWITCHER_TEXTS');

export const HQ_LANGUAGE_SWITCHER_DEFAULT_TEXTS: HqLanguageSwitcherTexts = {
  change: () => 'Change language',
  label: () => 'Language',
};
