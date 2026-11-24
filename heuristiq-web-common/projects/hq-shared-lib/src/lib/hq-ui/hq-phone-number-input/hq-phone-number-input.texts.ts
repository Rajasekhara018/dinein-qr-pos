import { InjectionToken } from '@angular/core';

/**
 * The words HqPhoneNumberInput shows when the `label` / `placeholder` inputs are empty, plus the
 * validation messages. The consuming app provides HQ_PHONE_NUMBER_INPUT_TEXTS with its own
 * translated text (each member is called when needed, so a language switch is picked up);
 * without it the English defaults below are used.
 */
export interface HqPhoneNumberInputTexts {
  label(): string;
  placeholder(): string;
  required(label: string): string;
  invalid(): string;
  /** Accessible name of the country-code list; the English default is used when an app does not provide it. */
  countryLabel?(): string;
  /** Placeholder of the search box in the country list. */
  searchPlaceholder?(): string;
  /** Shown when the search matches no country. */
  noCountryFound?(): string;
}

export const HQ_PHONE_NUMBER_INPUT_TOKEN_NAME = 'HQ_PHONE_NUMBER_INPUT_TEXTS';

export const HQ_PHONE_NUMBER_INPUT_TEXTS = new InjectionToken<HqPhoneNumberInputTexts>(HQ_PHONE_NUMBER_INPUT_TOKEN_NAME);

export const HQ_PHONE_NUMBER_INPUT_DEFAULT_TEXTS: Required<HqPhoneNumberInputTexts> = {
  label: () => 'Phone number',
  placeholder: () => 'Phone number',
  required: (label) => `${label} is required.`,
  invalid: () => 'Enter a valid phone number.',
  countryLabel: () => 'Country code',
  searchPlaceholder: () => 'Search country or code',
  noCountryFound: () => 'No country found',
};
