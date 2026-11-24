import { InjectionToken } from '@angular/core';

/** What a screen's help panel shows: what the screen is for, the steps for its usual task and one thing worth knowing. */
export interface HqScreenHelpContent {
  what: string;
  steps: string[];
  tip: string;
}

/**
 * The words HqScreenHelp shows. The consuming app provides HQ_SCREEN_HELP_TEXTS, reading each screen's text from its own
 * translation files (called when needed, so a language switch is picked up). `read` returns null for a screen with no help.
 */
export interface HqScreenHelpTexts {
  /** Heading of the collapsible panel, e.g. "How this screen works". */
  title(): string;
  /** The help of one screen (its key is the `screen` input), or null when it has none. */
  read(screen: string): HqScreenHelpContent | null;
}

export const HQ_SCREEN_HELP_TEXTS = new InjectionToken<HqScreenHelpTexts>('HQ_SCREEN_HELP_TEXTS');

/** Used when an app provides no texts: the panel stays hidden. */
export const HQ_SCREEN_HELP_DEFAULT_TEXTS: HqScreenHelpTexts = {
  title: () => 'How this screen works',
  read: () => null,
};

/** localStorage key prefix remembering that a person closed a screen's help (the help used to start open; no longer read). */
export const HQ_SCREEN_HELP_COLLAPSED_PREFIX = 'hq_help_closed_';

/** localStorage key prefix remembering that a person opened a screen's help: the help starts closed everywhere otherwise. */
export const HQ_SCREEN_HELP_OPENED_PREFIX = 'hq_help_opened_';
