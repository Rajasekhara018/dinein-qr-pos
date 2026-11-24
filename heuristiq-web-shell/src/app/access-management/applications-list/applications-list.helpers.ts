import { SpringPage } from '../../shared/models/uam.models';

/** Translates a key with parameters (TranslateService.instant). */
export type Translate = (key: string, params?: Record<string, unknown>) => string;

/** A page with no rows (UAM answered success without a page). */
export function emptyPage<T>(page: number, size: number): SpringPage<T> {
  return { content: [], totalElements: 0, totalPages: 0, size, number: page, numberOfElements: 0, first: true, last: true, empty: true };
}
