import { InjectionToken } from '@angular/core';

/**
 * The words HqOnboardingForm shows itself: validation messages and the select placeholder. Field labels and the
 * form author's own pattern messages come from the schema, so they are not here. The consuming app provides
 * HQ_ONBOARDING_FORM_TEXTS with its own translated text (each member is called when needed, so a language switch is
 * picked up); without it the English defaults below are used.
 */
export interface HqOnboardingFormTexts {
  required(label: string): string;
  invalidEmail(): string;
  invalidPattern(label: string): string;
  min(label: string, min: number): string;
  max(label: string, max: number): string;
  minLength(label: string, min: number): string;
  maxLength(label: string, max: number): string;
  dateMin(label: string, min: string): string;
  dateMax(label: string, max: string): string;
  selectPlaceholder(): string;
}

export const HQ_ONBOARDING_FORM_TEXTS = new InjectionToken<HqOnboardingFormTexts>('HQ_ONBOARDING_FORM_TEXTS');

export const HQ_ONBOARDING_FORM_DEFAULT_TEXTS: HqOnboardingFormTexts = {
  required: (label) => `${label} is required.`,
  invalidEmail: () => 'Enter a valid email address.',
  invalidPattern: (label) => `${label} is not in the expected format.`,
  min: (label, min) => `${label} must be at least ${min}.`,
  max: (label, max) => `${label} must be at most ${max}.`,
  minLength: (label, min) => `${label} must be at least ${min} characters.`,
  maxLength: (label, max) => `${label} must be at most ${max} characters.`,
  dateMin: (label, min) => `${label} must be on or after ${min}.`,
  dateMax: (label, max) => `${label} must be on or before ${max}.`,
  selectPlaceholder: () => 'Select…',
};
