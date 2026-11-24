import { DemandQuery, DemandTrailStep } from './franchise-demand.models';

/** The query string of the demand summary call (leading '?'); the parent is left out for the top places. */
export function demandQueryString(query: DemandQuery): string {
  const params = new URLSearchParams({ applicationCode: query.applicationCode });
  if (query.parentId) {
    params.set('parentId', query.parentId);
  }
  params.set('page', String(query.page));
  params.set('size', String(query.size));
  return `?${params.toString()}`;
}

/** The drill-down path cut back to one of its steps (index -1 goes back to the top places). */
export function trailUpTo(trail: readonly DemandTrailStep[], index: number): DemandTrailStep[] {
  return index < 0 ? [] : trail.slice(0, index + 1);
}
