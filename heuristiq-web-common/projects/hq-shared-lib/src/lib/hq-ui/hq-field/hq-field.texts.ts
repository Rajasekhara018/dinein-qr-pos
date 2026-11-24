import { InjectionToken } from '@angular/core';

/**
 * The words HqField shows by itself: the show / hide password button and the length counter. The consuming app provides
 * HQ_FIELD_TEXTS with its own translated text (each member is called when needed, so a language switch is picked up);
 * without it the English defaults below are used. Labels, hints and errors are passed per field and are never defaulted here.
 */
export interface HqFieldTexts {
  showPassword(): string;
  hidePassword(): string;
  /** The "length / limit" counter under a field with a character limit. */
  counter(length: number, max: number): string;
  /** Under a date field with `noPastDates` when a date before today is chosen. */
  pastDate(): string;
}

export const HQ_FIELD_TOKEN_NAME = 'HQ_FIELD_TEXTS';

export const HQ_FIELD_TEXTS = new InjectionToken<HqFieldTexts>(HQ_FIELD_TOKEN_NAME);

export const HQ_FIELD_DEFAULT_TEXTS: HqFieldTexts = {
  showPassword: () => 'Show password',
  hidePassword: () => 'Hide password',
  counter: (length, max) => `${length} / ${max}`,
  pastDate: () => 'Choose today or a later date',
};
