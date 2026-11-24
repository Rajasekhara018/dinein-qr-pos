import { InjectionToken } from '@angular/core';

/**
 * The fallback words of the shared state components (hq-empty-state, hq-error-state, hq-loading-state,
 * hq-spinner, hq-inline-confirm) when a usage leaves its text inputs empty. The consuming app provides
 * HQ_UI_STATE_TEXTS with its own translated text (each member is called when needed, so a language switch is
 * picked up); without it the English defaults below are used.
 */
export interface HqUiStateTexts {
  empty(): string;
  error(): string;
  errorBody(): string;
  retry(): string;
  loading(): string;
  confirm(): string;
  cancel(): string;
  /** "<field> is required." for the inline confirm's reason box. */
  reasonRequired(field: string): string;
  /** Label of a close / dismiss button (hq-alert, hq-modal, hq-drawer). Optional so existing providers keep compiling. */
  close?(): string;
  /** Placeholder shown for an empty value in hq-key-value-list (default an em dash). */
  none?(): string;
  /** Label of the copy-to-clipboard button in hq-key-value-list. */
  copy?(): string;
  /** Toast after a value was copied: "<label> copied." */
  copied?(label: string): string;
  /** Toast when copying failed: "Could not copy <label>." */
  copyFailed?(label: string): string;
  /** Label of hq-filter-bar's "Filters" toggle on phones. */
  filters?(): string;
  /** Label of the breadcrumb navigation in hq-page-header. */
  breadcrumbs?(): string;
  /**
   * Translates a key (hq-key-value-list items given as `labelKey`). Apps with a translation library provide it
   * (e.g. `(key, params) => translate.instant(key, params)`); without it the key itself is shown.
   */
  translate?(key: string, params?: Record<string, unknown>): string;
}

export const HQ_UI_STATE_TEXTS = new InjectionToken<HqUiStateTexts>('HQ_UI_STATE_TEXTS');

export const HQ_UI_STATE_DEFAULT_TEXTS: HqUiStateTexts = {
  empty: () => 'Nothing to show yet.',
  error: () => 'Something went wrong.',
  errorBody: () => 'The data could not be loaded. Please try again.',
  retry: () => 'Retry',
  loading: () => 'Loading…',
  confirm: () => 'Confirm',
  cancel: () => 'Cancel',
  reasonRequired: (field: string) => (field ? `${field} is required.` : 'This field is required.'),
  close: () => 'Close',
  none: () => '—',
  copy: () => 'Copy',
  copied: (label: string) => (label ? `${label} copied.` : 'Copied.'),
  copyFailed: (label: string) => (label ? `Could not copy ${label}.` : 'Could not copy.'),
  filters: () => 'Filters',
  breadcrumbs: () => 'Breadcrumb',
  translate: (key: string) => key,
};

/**
 * The app's text for an optional member, falling back to the English default. Use for every optional member of
 * {@link HqUiStateTexts}: `hqUiText(texts, 'filters')`.
 */
export function hqUiText(texts: HqUiStateTexts, member: 'close' | 'none' | 'copy' | 'filters' | 'breadcrumbs'): string {
  return texts[member]?.() ?? HQ_UI_STATE_DEFAULT_TEXTS[member]!();
}

/** The close / dismiss label from the app's texts, falling back to the English default. */
export function hqCloseText(texts: HqUiStateTexts): string {
  return texts.close?.() ?? HQ_UI_STATE_DEFAULT_TEXTS.close!();
}
