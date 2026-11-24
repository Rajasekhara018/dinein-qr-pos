import { InjectionToken } from '@angular/core';

/**
 * The words HqAppDialog needs. The consuming app provides HQ_APP_DIALOG_TEXTS with its own translated text
 * (each member is called when the dialog needs it, so a language switch is picked up); without it the
 * English defaults below are used.
 */
export interface HqAppDialogTexts {
  required(label: string): string;
  invalidNumber(label: string): string;
  min(label: string, min: number): string;
  max(label: string, max: number): string;
  cancel(): string;
  confirm(): string;
  working(): string;
}

export const HQ_APP_DIALOG_TEXTS = new InjectionToken<HqAppDialogTexts>('HQ_APP_DIALOG_TEXTS');

export const HQ_APP_DIALOG_DEFAULT_TEXTS: HqAppDialogTexts = {
  required: (label) => `${label} is required`,
  invalidNumber: (label) => `${label} must be a valid number`,
  min: (label, min) => `${label} must be at least ${min}`,
  max: (label, max) => `${label} must be ${max} or less`,
  cancel: () => 'Cancel',
  confirm: () => 'OK',
  working: () => 'Working...',
};
