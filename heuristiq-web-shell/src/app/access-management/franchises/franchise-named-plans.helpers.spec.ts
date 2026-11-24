import { describe, expect, it } from 'vitest';
import {
  appliedRuleRows, appliedRulesQueryString, eventTypeLabel,
  currentPlanVersion, emptyPlanPicker, hasPlanOption, isoDay, namedPlanOptions, namedPlanQueryString, percentText, planCodeOf, planSummary,
  withPickedOption,
} from './franchise-named-plans.helpers';
import { NamedCommissionPlanRow, NamedFeePlanRow, NamedPlanRow, NamedReferralPlanRow } from './franchise-packages.models';

const TODAY = '2026-11-09';
const plan = (id: string, planCode: string, planName: string | null, effectiveFrom: string, effectiveTo: string | null = null): NamedPlanRow => ({
  id, scope: { level: 'PLAN', planCode, vertical: 'MATRI_APP' }, planName, effectiveFrom, effectiveTo,
});
const labels = { gst: (code: string) => `gst:${code}`, party: (code: string) => `party:${code}` };

describe('named plan helpers', () => {
  it('asks for the named plans of one application', () => {
    expect(namedPlanQueryString('MATRI_APP')).toBe('?scopeLevel=PLAN&vertical=MATRI_APP&size=100');
    expect(namedPlanQueryString('RIDE', 10)).toBe('?scopeLevel=PLAN&vertical=RIDE&size=10');
  });

  it('reads the day of a date and the plan code of a named-plan row only', () => {
    expect(isoDay(new Date('2026-11-09T18:30:00Z'))).toBe('2026-11-09');
    expect(planCodeOf(plan('a', ' GOLD ', 'Gold', '2026-01-01'))).toBe('GOLD');
    expect(planCodeOf({ id: 'b', scope: { level: 'VERTICAL', planCode: 'X' } })).toBe('');
  });

  it('takes the version in force today with the latest start, else the latest start of all', () => {
    const old = plan('old', 'GOLD', 'Gold', '2025-01-01');
    const now = plan('now', 'GOLD', 'Gold 2', '2026-06-01');
    const future = plan('future', 'GOLD', 'Gold 3', '2027-01-01');
    const ended = plan('ended', 'GOLD', 'Gold 0', '2024-01-01', '2024-12-31');
    expect(currentPlanVersion([old, now, future, ended], TODAY)?.id).toBe('now');
    expect(currentPlanVersion([future, ended], TODAY)?.id).toBe('future');
    expect(currentPlanVersion([], TODAY)).toBeNull();
  });

  it('groups versions into one option per plan code, named by the plan (else its code), sorted by name', () => {
    const options = namedPlanOptions([
      plan('s1', 'SILVER', 'Silver', '2026-01-01'),
      plan('g1', 'GOLD', null, '2025-01-01'),
      plan('g2', 'GOLD', 'Gold', '2026-01-01'),
      plan('n1', 'NONAME', null, '2026-01-01'),
      { id: 'x', scope: { level: 'VERTICAL', vertical: 'MATRI_APP' } },
    ], TODAY);
    expect(options.map((option) => [option.code, option.name])).toEqual([['GOLD', 'Gold'], ['NONAME', 'NONAME'], ['SILVER', 'Silver']]);
    expect(options[0].current?.id).toBe('g2');
  });

  it('summarises a named plan by its row for every event, not by a row for one event type', () => {
    const subscription: NamedPlanRow = { ...plan('e1', 'GOLD', 'Gold', '2026-06-01'), scope: { level: 'PLAN', planCode: 'GOLD', vertical: 'RIDE', eventType: 'DRIVER_SUBSCRIPTION' } };
    const options = namedPlanOptions([plan('g1', 'GOLD', 'Gold', '2026-01-01'), subscription], TODAY);
    expect(options).toHaveLength(1);
    expect(options[0].current?.id).toBe('g1');
    expect(namedPlanOptions([subscription], TODAY)[0].current?.id).toBe('e1');
  });

  it('keeps a pick that is not listed as a first option with its known name, and never twice', () => {
    const listed = namedPlanOptions([plan('g', 'GOLD', 'Gold', '2026-01-01')], TODAY);
    expect(withPickedOption(listed, 'OLD', 'Old plan').map((option) => option.code)).toEqual(['OLD', 'GOLD']);
    expect(withPickedOption(listed, 'OLD', '')[0].name).toBe('OLD');
    expect(withPickedOption(listed, 'GOLD', 'Gold').length).toBe(1);
    expect(withPickedOption(listed, '', 'x').length).toBe(1);
    expect(hasPlanOption(listed, 'GOLD')).toBe(true);
    expect(hasPlanOption(listed, 'OLD')).toBe(false);
    expect(emptyPlanPicker()).toEqual({ options: [], loading: false, loadFailed: false });
  });

  it('writes basis points as a percentage', () => {
    expect(percentText(1500)).toBe('15%');
    expect(percentText(250)).toBe('2.5%');
    expect(percentText(null)).toBe('0%');
  });

  describe('a one-line summary of a plan', () => {
    it('shows a fixed fee as money with its GST, and a percentage fee as a rate', () => {
      const fixed: NamedFeePlanRow = {
        ...plan('f', 'F', 'F', '2026-01-01'), currency: 'INR',
        terms: { feeType: 'FIXED', fixedAmount: { code: 'INR', value: 100000 }, gstTreatment: 'INCLUSIVE' },
      };
      const summary = planSummary('fee', fixed, labels, 'en-IN');
      expect(summary).toContain('1,00,000');
      expect(summary).toContain('gst:INCLUSIVE');
      const percentage: NamedFeePlanRow = { ...fixed, terms: { feeType: 'PERCENTAGE', percentageBp: 200 } };
      expect(planSummary('fee', percentage, labels)).toBe('2%');
      expect(planSummary('fee', { ...fixed, terms: null }, labels)).toBe('');
    });

    it('lists the active commission lines by party', () => {
      const commission: NamedCommissionPlanRow = {
        ...plan('c', 'C', 'C', '2026-01-01'),
        lines: [
          { party: 'AGENT', valueType: 'PERCENTAGE', basisPoints: 1500 },
          { party: 'PLATFORM', basisPoints: 1000 },
          { party: 'OWNER', basisPoints: 7500, active: false },
        ],
      };
      expect(planSummary('commission', commission, labels)).toBe('party:AGENT 15% · party:PLATFORM 10%');
      expect(planSummary('commission', { ...commission, lines: null }, labels)).toBe('');
    });

    it('shows a referral rate, fixed or percentage', () => {
      const referral: NamedReferralPlanRow = { ...plan('r', 'R', 'R', '2026-01-01'), rate: { valueType: 'PERCENTAGE', basisPoints: 200 } };
      expect(planSummary('referral', referral, labels)).toBe('2%');
      const fixed: NamedReferralPlanRow = { ...referral, rate: { valueType: 'FIXED', fixedAmount: { code: 'INR', value: 50 } } };
      expect(planSummary('referral', fixed, labels, 'en-IN')).toContain('50');
      expect(planSummary('referral', { ...referral, rate: null }, labels)).toBe('');
    });

    it('is blank for an unknown plan', () => {
      expect(planSummary('fee', null, labels)).toBe('');
    });
  });
});

describe('the plans a package follows when it picks none', () => {
  const rule = (id: string, level: string, from: string, extra: Partial<NamedPlanRow['scope']> = {}, to: string | null = null): NamedPlanRow =>
    ({ id, scope: { level, vertical: 'RIDE', ...extra }, effectiveFrom: from, effectiveTo: to } as NamedPlanRow);

  it('keeps the application, event-type and place-type plans in force today, one per scope (its current version), every event first', () => {
    const rows = [
      rule('e1', 'EVENT_TYPE_VERTICAL', '2026-01-01', { eventType: 'RIDE_COMPLETED' }),
      rule('e2', 'EVENT_TYPE_VERTICAL', '2026-06-01', { eventType: 'RIDE_COMPLETED' }),
      rule('p1', 'PLACE_TYPE_VERTICAL', '2026-01-01', { eventType: 'RIDE_COMPLETED', placeType: 'DISTRICT' }),
      rule('v1', 'VERTICAL', '2026-01-01'),
      rule('named', 'PLAN', '2026-01-01', { planCode: 'GOLD' }),
      rule('city', 'CITY_VERTICAL', '2026-01-01'),
      rule('ended', 'EVENT_TYPE_VERTICAL', '2025-01-01', { eventType: 'DRIVER_SUBSCRIPTION' }, '2025-12-31'),
    ];

    expect(appliedRuleRows(rows, '2026-11-15').map((row) => row.id)).toEqual(['v1', 'e2', 'p1']);
  });

  it('writes an event type code in plain words', () => {
    expect(eventTypeLabel('RIDE_COMPLETED')).toBe('Ride completed');
    expect(eventTypeLabel(null)).toBe('');
  });

  it('asks for every plan row of the application, whatever its level', () => {
    expect(appliedRulesQueryString('RIDE')).toBe('?vertical=RIDE&size=100');
  });
});
