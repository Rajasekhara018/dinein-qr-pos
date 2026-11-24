import { InjectionToken } from '@angular/core';

/**
 * Words of {@link HqIdleTimeoutDialog}. The app provides HQ_IDLE_TIMEOUT_TEXTS with its translations (each member is
 * called when the dialog renders, so a language switch is picked up); without it the English defaults are used.
 */
export interface HqIdleTimeoutTexts {
  warningTitle(): string;
  /** "You will be signed out in <seconds> seconds…" */
  warningMessage(seconds: number | null): string;
  stayActive(): string;
  logout(): string;
  expiredTitle(): string;
  expiredMessage(): string;
  loginAgain(): string;
}

export const HQ_IDLE_TIMEOUT_TEXTS = new InjectionToken<HqIdleTimeoutTexts>('HQ_IDLE_TIMEOUT_TEXTS');

export const HQ_IDLE_TIMEOUT_DEFAULT_TEXTS: HqIdleTimeoutTexts = {
  warningTitle: () => 'Are you still there?',
  warningMessage: (seconds: number | null) =>
    seconds === null ? 'You will be signed out soon because of inactivity.' : `You will be signed out in ${seconds} seconds because of inactivity.`,
  stayActive: () => 'Stay signed in',
  logout: () => 'Sign out',
  expiredTitle: () => 'Session timed out',
  expiredMessage: () => 'You were signed out because of inactivity. Please sign in again.',
  loginAgain: () => 'Sign in again',
};
