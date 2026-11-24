import { NamedPlanRow } from './franchise-packages.models';
import { currentPlanVersion, planCodeOf } from './franchise-named-plans.helpers';

/**
 * The rows of one named plan as they apply today: one per event type and type of place (its current version), the row for every
 * event first. Empty when the plan has no rows.
 */
export function namedPlanRuleRows<T extends NamedPlanRow>(rows: readonly T[], code: string, today: string): T[] {
  const byScope = new Map<string, T[]>();
  for (const row of rows) {
    if (planCodeOf(row) !== code) { continue; }
    const key = `${row.scope.eventType ?? ''}|${row.scope.placeType ?? ''}`;
    byScope.set(key, [...(byScope.get(key) ?? []), row]);
  }
  const current = [...byScope.values()].map((versions) => currentPlanVersion(versions, today)).filter((row): row is T => !!row);
  const sortKey = (row: T) => `${row.scope.eventType ? 1 : 0}|${row.scope.eventType ?? ''}|${row.scope.placeType ? 1 : 0}|${row.scope.placeType ?? ''}`;
  return current.sort((a, b) => sortKey(a).localeCompare(sortKey(b)));
}

/** The plan's name from its rows (any version that has one), else its code. */
export function namedPlanName(rows: readonly NamedPlanRow[], code: string): string {
  return rows.filter((row) => planCodeOf(row) === code).map((row) => row.planName?.trim() ?? '').find((name) => !!name) || code;
}
