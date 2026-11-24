import { InjectionToken } from '@angular/core';

/**
 * The words of the lookup popup (hq-common-model). The consuming app provides HQ_COMMON_MODEL_TEXTS with its own translated
 * text (each member is called when needed, so a language switch is picked up); without it the English defaults below are used.
 * The popup's title and the column names come from the lookup table itself and are not here.
 */
export interface HqCommonModelTexts {
  close(): string;
  done(): string;
  remove(value: string): string;
  removeAll(): string;
  clearSelection(): string;
  /** The line above the column chips. */
  columnLead(): string;
  /** The smaller line under it. */
  columnSub(): string;
  searchLabel(): string;
  search(): string;
  clearSearch(): string;
  showingResultsFor(): string;
  noRecordFound(): string;
  forValue(): string;
  clear(): string;
  clearResult(): string;
  select(): string;
  addConditions(): string;
  normalSearch(): string;
  addCondition(): string;
  advancedSearch(): string;
  advancedHint(): string;
  join(): string;
  field(): string;
  condition(): string;
  value(): string;
  removeCondition(): string;
  equals(): string;
  notEquals(): string;
  contains(): string;
  loadFailed(): string;
  values(count: number): string;
  searchField(): string;
}

export const HQ_COMMON_MODEL_TOKEN_NAME = 'HQ_COMMON_MODEL_TEXTS';

export const HQ_COMMON_MODEL_TEXTS = new InjectionToken<HqCommonModelTexts>(HQ_COMMON_MODEL_TOKEN_NAME);

export const HQ_COMMON_MODEL_DEFAULT_TEXTS: HqCommonModelTexts = {
  close: () => 'Close',
  done: () => 'Done',
  remove: (value) => `Remove ${value}`,
  removeAll: () => 'Remove all',
  clearSelection: () => 'Clear selection',
  columnLead: () => 'Select the chip to search based on that parameter',
  columnSub: () => 'Filter data based on column names',
  searchLabel: () => 'Search',
  search: () => 'SEARCH',
  clearSearch: () => 'Clear search',
  showingResultsFor: () => 'Showing results for',
  noRecordFound: () => 'No record found',
  forValue: () => 'for',
  clear: () => 'Clear',
  clearResult: () => 'Clear result',
  select: () => 'Select',
  addConditions: () => '+ Add conditions',
  normalSearch: () => 'Normal search',
  addCondition: () => '+ Add condition',
  advancedSearch: () => 'Advanced search',
  advancedHint: () => 'Select a field, choose a condition and a value to filter the data.',
  join: () => 'Join',
  field: () => 'Field',
  condition: () => 'Condition',
  value: () => 'Value',
  removeCondition: () => 'Remove condition',
  equals: () => 'Equals',
  notEquals: () => 'Not equals',
  contains: () => 'Contains',
  loadFailed: () => 'Unable to load records.',
  values: (count) => `${count} values`,
  searchField: () => 'Search field',
};
