import { describe, expect, it } from 'vitest';
import { NamedCommissionPlanRow } from './franchise-packages.models';
import { namedPlanName, namedPlanRuleRows } from './franchise-plan-preview.helpers';

const row = (id: string, code: string, eventType: string | null, from: string, name: string | null = null): NamedCommissionPlanRow => ({
  id, scope: { level: 'PLAN', planCode: code, eventType }, planName: name, effectiveFrom: from, effectiveTo: null,
});

describe('plan preview helpers', () => {
  it('gives one current row per event type of the named plan, the row for every event first', () => {
    const rows = [
      row('a1', 'GOLD', 'RIDE_COMPLETED', '2026-01-01'), row('a2', 'GOLD', 'RIDE_COMPLETED', '2026-06-01'),
      row('b', 'GOLD', null, '2026-01-01'), row('c', 'SILVER', null, '2026-01-01'), row('d', 'GOLD', 'DRIVER_SUBSCRIPTION', '2027-01-01'),
    ];
    expect(namedPlanRuleRows(rows, 'GOLD', '2026-11-16').map((r) => r.id)).toEqual(['b', 'd', 'a2']);
    expect(namedPlanRuleRows(rows, 'NONE', '2026-11-16')).toEqual([]);
  });

  it("names the plan by any version's name, else by its code", () => {
    expect(namedPlanName([row('a', 'GOLD', null, '2026-01-01'), row('b', 'GOLD', null, '2026-02-01', ' Gold ')], 'GOLD')).toBe('Gold');
    expect(namedPlanName([row('a', 'GOLD', null, '2026-01-01')], 'GOLD')).toBe('GOLD');
  });
});
