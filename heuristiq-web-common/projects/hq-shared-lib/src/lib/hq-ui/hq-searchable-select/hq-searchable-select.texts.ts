import { InjectionToken } from '@angular/core';

/**
 * The words HqSearchableSelect shows itself. The consuming app provides HQ_SEARCHABLE_SELECT_TEXTS with its own translated
 * text (each member is called when needed, so a language switch is picked up); without it the English defaults below are used.
 */
export interface HqSearchableSelectTexts {
  /** Placeholder of the closed control. */
  placeholder(): string;
  /** The last entry of the list: pick it to type a value that is not listed. */
  other(): string;
  /** Placeholder of the free-text box shown after "Other". */
  otherPlaceholder(): string;
  /** Link that returns from the free-text box to the list. */
  backToList(): string;
  /** Accessible name of the clear button. */
  clear(): string;
  /** Shown when the typed text matches nothing: Enter uses what was typed. */
  noMatch(typed: string): string;
  /** Placeholder of the control while its list is being loaded. */
  loading(): string;
  /** Small notice under a control whose list could not be loaded (the control is free text meanwhile). */
  optionsUnavailable(): string;
  /** Small notice under a control whose list loaded but has nothing in it yet (the control is free text meanwhile). Optional: English default when an app does not provide it. */
  optionsEmpty?(): string;
  /** Action next to that notice that asks for the list again. */
  retry(): string;
  /** Hint under a control that waits for another one (the state waits for the country). */
  chooseFirst(label: string): string;
}

export const HQ_SEARCHABLE_SELECT_TEXTS = new InjectionToken<HqSearchableSelectTexts>('HQ_SEARCHABLE_SELECT_TEXTS');

export const HQ_SEARCHABLE_SELECT_DEFAULT_TEXTS: HqSearchableSelectTexts = {
  placeholder: () => 'Select…',
  other: () => 'Other / not listed',
  otherPlaceholder: () => 'Type your value',
  backToList: () => 'Choose from the list',
  clear: () => 'Clear',
  noMatch: (typed) => `No match — press Enter to use “${typed}”`,
  loading: () => 'Loading…',
  optionsUnavailable: () => 'Could not load the list — type a value.',
  optionsEmpty: () => 'Nothing is set up in this list yet — type a value.',
  retry: () => 'Retry',
  chooseFirst: (label) => `Choose ${label} first.`,
};
