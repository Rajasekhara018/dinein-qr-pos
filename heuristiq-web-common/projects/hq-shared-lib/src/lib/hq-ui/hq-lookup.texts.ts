import { InjectionToken } from '@angular/core';

/**
 * The words HqLookupDialog needs. The consuming app provides HQ_LOOKUP_TEXTS with its own translated text
 * (each member is called when needed, so a language switch is picked up); without it the English defaults are used.
 * The members marked optional may be left out of an app's texts: the English default is used for those.
 */
export interface HqLookupTexts {
  search(): string;
  typeToSearch(minLength: number): string;
  noResults(): string;
  loadFailed(): string;
  retry(): string;
  selected(count: number): string;
  clearSelection(): string;
  cancel(): string;
  select(): string;
  /** The button that closes the dialog without choosing (top right). */
  close?(): string;
  /** The floating label of the search box. */
  searchLabel?(): string;
  /** The button beside the search box. */
  searchButton?(): string;
  /** "Showing results for <text>". */
  showingResultsFor?(): string;
  /** Empties the search and shows everything again. */
  clearResult?(): string;
  /** The first search-scope chip: search across the searchable columns together. */
  allFields?(): string;
  /** The search box placeholder once one column's chip is chosen, e.g. "Search in Email". */
  searchIn?(field: string): string;
  /** Accessible name of the group of search-scope chips. */
  searchFields?(): string;
  /** Accessible name of the header checkbox that ticks every record on the page. */
  selectAllOnPage?(): string;
  /** The link that switches to advanced conditions (only shown when the screen turns `advanced` on). */
  addConditions?(): string;
  /** Adds one more condition row. */
  addCondition?(): string;
  /** Runs the search with the conditions. */
  advancedSearch?(): string;
  /** The line above the condition rows. */
  advancedHint?(): string;
  /** Back from the conditions to the quick search. */
  normalSearch?(): string;
  /** Label of the AND / OR choice of a row. */
  join?(): string;
  /** Label of a row's column choice. */
  field?(): string;
  /** Label of a row's operator choice. */
  condition?(): string;
  /** Label of a row's value. */
  value?(): string;
  /** Accessible name of a row's remove button. */
  removeCondition?(): string;
  /** Operator name: equals. */
  equals?(): string;
  /** Operator name: not equals. */
  notEquals?(): string;
  /** Operator name: contains. */
  contains?(): string;
  /** The "true" value of a yes/no column. */
  yes?(): string;
  /** The "false" value of a yes/no column. */
  no?(): string;
}

export const HQ_LOOKUP_TEXTS = new InjectionToken<HqLookupTexts>('HQ_LOOKUP_TEXTS');

export const HQ_LOOKUP_DEFAULT_TEXTS: Required<HqLookupTexts> = {
  search: () => 'Search...',
  typeToSearch: (minLength) => `Type at least ${minLength} characters to search.`,
  noResults: () => 'No matching records.',
  loadFailed: () => 'Failed to load data.',
  retry: () => 'Try again',
  selected: (count) => `${count} selected`,
  clearSelection: () => 'Clear selection',
  cancel: () => 'Cancel',
  select: () => 'Select',
  close: () => 'Close',
  searchLabel: () => 'Search',
  searchButton: () => 'SEARCH',
  showingResultsFor: () => 'Showing results for',
  clearResult: () => 'Clear result',
  allFields: () => 'All fields',
  searchIn: (field) => `Search in ${field}`,
  searchFields: () => 'Search in',
  selectAllOnPage: () => 'Select all on this page',
  addConditions: () => '+ Add conditions',
  addCondition: () => '+ Add condition',
  advancedSearch: () => 'Advanced search',
  advancedHint: () => 'Select a field, choose a condition and a value to filter the data.',
  normalSearch: () => 'Normal search',
  join: () => 'Join',
  field: () => 'Field',
  condition: () => 'Condition',
  value: () => 'Value',
  removeCondition: () => 'Remove condition',
  equals: () => 'Equals',
  notEquals: () => 'Not equals',
  contains: () => 'Contains',
  yes: () => 'Yes',
  no: () => 'No',
};
