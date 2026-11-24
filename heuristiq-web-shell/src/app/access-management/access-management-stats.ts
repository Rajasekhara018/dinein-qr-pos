import { TranslateService } from '@ngx-translate/core';
import { HqStatCardItem } from 'hq-shared-lib';
import { ACTIVE_ICON, INACTIVE_ICON } from './access-management-stats.constants';

/**
 * The Total / Active / Inactive cards every access-management list shows, counted from the one page the list
 * already fetched (no extra request). {@code total} is the endpoint's own total across all pages and filters;
 * Active/Inactive are counted over the rows of that page, so Inactive is derived from those rows rather than from {@code total}.
 */
export function activityStats<T>(
  translate: TranslateService,
  total: number,
  rows: readonly T[],
  isActive: (row: T) => boolean,
  totalIcon: string,
): HqStatCardItem[] {
  const active = rows.filter(isActive).length;
  return [
    { label: translate.instant('accessManagement.stats.total'), value: Math.max(total, rows.length), icon: totalIcon, tone: 'blue' },
    { label: translate.instant('accessManagement.stats.active'), value: active, icon: ACTIVE_ICON, tone: 'green' },
    { label: translate.instant('accessManagement.stats.inactive'), value: rows.length - active, icon: INACTIVE_ICON, tone: 'orange' },
  ];
}

/**
 * The same cards from counts over every match (not one page): {@code total} matches, {@code active} of them active, the rest
 * inactive. Used when the list can ask the server for the active count.
 */
export function countedStats(translate: TranslateService, total: number, active: number, totalIcon: string): HqStatCardItem[] {
  const shownActive = Math.min(Math.max(active, 0), total);
  return [
    { label: translate.instant('accessManagement.stats.total'), value: total, icon: totalIcon, tone: 'blue' },
    { label: translate.instant('accessManagement.stats.active'), value: shownActive, icon: ACTIVE_ICON, tone: 'green' },
    { label: translate.instant('accessManagement.stats.inactive'), value: total - shownActive, icon: INACTIVE_ICON, tone: 'orange' },
  ];
}
