import { InjectionToken } from '@angular/core';

/**
 * The words HqAmountInput shows when the `label` / `placeholder` inputs are empty, plus the validation messages. The consuming
 * app provides HQ_AMOUNT_INPUT_TEXTS with its own translated text (each member is called when needed, so a language switch is
 * picked up); without it the English defaults below are used.
 */
export interface HqAmountInputTexts {
  label(): string;
  placeholder(): string;
  required(label: string): string;
  invalid(): string;
  min(min: string): string;
  max(max: string): string;
  /** Accessible name of the currency selector. */
  currencyLabel(): string;
}

export const HQ_AMOUNT_INPUT_TEXTS = new InjectionToken<HqAmountInputTexts>('HQ_AMOUNT_INPUT_TEXTS');

export const HQ_AMOUNT_INPUT_DEFAULT_TEXTS: HqAmountInputTexts = {
  label: () => 'Amount',
  placeholder: () => '0.00',
  required: (label) => `${label} is required.`,
  invalid: () => 'Enter a valid amount.',
  min: (min) => `Enter at least ${min}.`,
  max: (max) => `Enter at most ${max}.`,
  currencyLabel: () => 'Currency',
};
