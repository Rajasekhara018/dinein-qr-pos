import { describe, expect, it } from 'vitest';
import { FranchisePlan, LocationType } from './franchise.models';
import { TypeFeeRow, buildTypeFeeRows, typeFeeChanges, typeFeeRowState, walletAllocation, walletPerAgent } from './franchise-type-fees.helpers';

const TYPES: LocationType[] = ['STATE', 'DISTRICT', 'TOWN'];

const typeDefault = (over: Partial<FranchisePlan>): FranchisePlan => ({
  id: 'p1', applicationCode: 'MATRI', countryCode: 'IN', countryName: 'India', stateCode: '', stateName: '', city: '', districtCode: '',
  agentLimit: 10, fee: { code: 'INR', value: 5000 }, scope: 'TYPE_DEFAULT', scopeType: 'DISTRICT', scopeLocationId: null, ...over,
});

const row = (over: Partial<TypeFeeRow>): TypeFeeRow => ({
  type: 'TOWN', planId: null, agentLimit: null, fee: null, currency: 'INR', savedAgentLimit: null, savedFee: null, savedCurrency: 'INR', ...over,
});

describe('buildTypeFeeRows', () => {
  it('makes a line per type, filled from the type default saved for exactly that area', () => {
    const rows = buildTypeFeeRows(TYPES, [typeDefault({ id: 'd1', scopeType: 'DISTRICT', agentLimit: 25, fee: { code: 'INR', value: 100000 } })], 'IN', '', 'INR');

    expect(rows.map((r) => r.type)).toEqual(['STATE', 'DISTRICT', 'TOWN']);
    expect(rows[1]).toMatchObject({ planId: 'd1', agentLimit: 25, fee: 100000, savedAgentLimit: 25, savedFee: 100000, currency: 'INR' });
    expect(rows[0]).toMatchObject({ planId: null, agentLimit: null, fee: null });
  });

  it('ignores plans of another scope, another country or another parent', () => {
    const plans = [
      typeDefault({ id: 'place', scope: 'PLACE', scopeType: null }),
      typeDefault({ id: 'ae', countryCode: 'AE' }),
      typeDefault({ id: 'under-ap', scopeLocationId: 'ap' }),
    ];

    expect(buildTypeFeeRows(TYPES, plans, 'IN', '', 'INR').every((r) => r.planId === null)).toBe(true);
    expect(buildTypeFeeRows(TYPES, plans, 'IN', 'ap', 'INR')[1].planId).toBe('under-ap');
  });

  it('uses the default currency for a type with no saved plan and the plan\'s own currency otherwise', () => {
    const rows = buildTypeFeeRows(TYPES, [typeDefault({ fee: { code: 'USD', value: 5000 } })], 'IN', '', 'INR');
    expect(rows[1].currency).toBe('USD');
    expect(rows[0].currency).toBe('INR');
  });

  it('still reads an older bare-number fee, in the default currency', () => {
    const rows = buildTypeFeeRows(TYPES, [typeDefault({ fee: 750 as unknown as FranchisePlan['fee'] })], 'IN', '', 'AED');
    expect(rows[1]).toMatchObject({ fee: 750, savedFee: 750, currency: 'AED' });
  });
});

describe('typeFeeRowState', () => {
  it('is empty when neither value is given, and incomplete when only one is', () => {
    expect(typeFeeRowState(row({}))).toBe('empty');
    expect(typeFeeRowState(row({ agentLimit: 5 }))).toBe('incomplete');
    expect(typeFeeRowState(row({ fee: 100 }))).toBe('incomplete');
  });

  it('refuses fewer than one agent or a negative fee', () => {
    expect(typeFeeRowState(row({ agentLimit: 0, fee: 100 }))).toBe('incomplete');
    expect(typeFeeRowState(row({ agentLimit: 3, fee: -1 }))).toBe('incomplete');
  });

  it('is new without a saved plan, unchanged when the values match the saved ones, changed otherwise', () => {
    expect(typeFeeRowState(row({ agentLimit: 5, fee: 100 }))).toBe('new');
    const saved = { planId: 'p', savedAgentLimit: 5, savedFee: 100, agentLimit: 5, fee: 100 };
    expect(typeFeeRowState(row(saved))).toBe('unchanged');
    expect(typeFeeRowState(row({ ...saved, agentLimit: 6 }))).toBe('changed');
    expect(typeFeeRowState(row({ ...saved, fee: 100.004 }))).toBe('unchanged');
    expect(typeFeeRowState(row({ ...saved, fee: 120 }))).toBe('changed');
    expect(typeFeeRowState(row({ ...saved, currency: 'USD' }))).toBe('changed');
  });

  it('treats a cleared saved line as empty, never as a deletion', () => {
    expect(typeFeeRowState(row({ planId: 'p', savedAgentLimit: 5, savedFee: 100, agentLimit: null, fee: null }))).toBe('empty');
  });
});

describe('typeFeeChanges', () => {
  it('splits the lines into the ones to create, to change and the ones that cannot be saved', () => {
    const rows = [
      row({ type: 'STATE', agentLimit: 2, fee: 10 }),
      row({ type: 'DISTRICT', planId: 'd', savedAgentLimit: 5, savedFee: 50, agentLimit: 6, fee: 50 }),
      row({ type: 'TOWN', agentLimit: 3 }),
      row({ type: 'CITY' as LocationType }),
    ];

    const changes = typeFeeChanges(rows);

    expect(changes.create.map((r) => r.type)).toEqual(['STATE']);
    expect(changes.change.map((r) => r.type)).toEqual(['DISTRICT']);
    expect(changes.incomplete.map((r) => r.type)).toEqual(['TOWN']);
  });
});

describe('agent wallet amounts of a line', () => {
  it('gives the fee times the multiplier, and nothing without a fee', () => {
    expect(walletAllocation(100000, 2)).toBe(200000);
    expect(walletAllocation(555.55, 2)).toBe(1111.1);
    expect(walletAllocation(101, 1.5)).toBe(151.5);
    expect(walletAllocation(null, 2)).toBeNull();
    expect(walletAllocation(0, 2)).toBeNull();
    expect(walletAllocation(-1, 2)).toBeNull();
    expect(walletAllocation(10, -1)).toBeNull();
  });

  it('shares it equally between the agent places, rounded down to the paisa like the backend', () => {
    expect(walletPerAgent(100000, 25, 2)).toBe(8000);
    expect(walletPerAgent(100000, 3, 2)).toBe(66666.66);
    expect(walletPerAgent(555, 558, 2)).toBe(1.98);
    expect(walletPerAgent(100000, null, 2)).toBeNull();
    expect(walletPerAgent(100000, 0, 2)).toBeNull();
    expect(walletPerAgent(null, 5, 2)).toBeNull();
  });
});
