import { InjectionToken } from '@angular/core';

/**
 * The fallback words HqCommonTable shows when the per-usage inputs (`emptyMessage`, `errorMessage`,
 * `actionsLabel`) are left empty, plus the boolean cell labels. The consuming app provides
 * HQ_COMMON_TABLE_TEXTS with its own translated text (each member is called when needed, so a
 * language switch is picked up); without it the English defaults below are used.
 */
export interface HqCommonTableTexts {
  noRecords(): string;
  loadFailed(): string;
  actions(): string;
  yes(): string;
  no(): string;
}

export const HQ_COMMON_TABLE_TEXTS = new InjectionToken<HqCommonTableTexts>('HQ_COMMON_TABLE_TEXTS');

export const HQ_COMMON_TABLE_DEFAULT_TEXTS: HqCommonTableTexts = {
  noRecords: () => 'No records found.',
  loadFailed: () => 'Failed to load data.',
  actions: () => 'Actions',
  yes: () => 'Yes',
  no: () => 'No',
};
