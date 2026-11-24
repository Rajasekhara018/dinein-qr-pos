import { InjectionToken } from '@angular/core';

/**
 * The words HqTableFooter shows. The consuming app provides HQ_TABLE_FOOTER_TEXTS with its own
 * translated text (each member is called when needed, so a language switch is picked up);
 * without it the English defaults below are used.
 */
export interface HqTableFooterTexts {
  /** Shown instead of the range when the page holds no items. */
  noResults(): string;
  /** Leads the range: "Showing 1–20". */
  showing(): string;
  /** Joins the range and the total: "Showing 1–20 of 134". */
  of(): string;
  /** Label of the page-size selector. */
  rows(): string;
  /** Accessible label / tooltip of the first-page button. */
  firstPage(): string;
  /** Accessible label / tooltip of the previous-page button. */
  previousPage(): string;
  /** Accessible label / tooltip of the next-page button. */
  nextPage(): string;
}

export const HQ_TABLE_FOOTER_TEXTS = new InjectionToken<HqTableFooterTexts>('HQ_TABLE_FOOTER_TEXTS');

export const HQ_TABLE_FOOTER_DEFAULT_TEXTS: HqTableFooterTexts = {
  noResults: () => 'No results',
  showing: () => 'Showing',
  of: () => 'of',
  rows: () => 'Rows',
  firstPage: () => 'First page',
  previousPage: () => 'Previous page',
  nextPage: () => 'Next page',
};
