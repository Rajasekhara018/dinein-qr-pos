import { describe, expect, it } from 'vitest';
import { FranchisePlan } from './franchise.models';
import {
  ScopedPlanForm, emptyScopedPlanForm, inferScope, planPlace, planToScopedForm, scopedPlanFormError, toScopedPlanRequest,
} from './franchise-plan-scope.helpers';

const KEY = 'accessManagement.franchisePlans.errors.';
const india = [{ code: 'IN', name: 'India' }];

const form = (over: Partial<ScopedPlanForm> = {}): ScopedPlanForm => ({
  ...emptyScopedPlanForm('INR'), applicationCode: 'MATRIMONY', agentLimit: 20, fee: 20000.456, ...over,
});

const plan = (over: Partial<FranchisePlan> = {}): FranchisePlan => ({
  id: 'p1', applicationCode: 'MATRIMONY', countryCode: 'IN', countryName: 'India', stateCode: 'AP', stateName: 'Andhra Pradesh', city: 'Nuzvid',
  districtCode: 'KR', agentLimit: 20, fee: { code: 'INR', value: 20000 }, ...over,
});

describe('inferScope', () => {
  it('uses the scope the service sent, else follows the first release rules', () => {
    expect(inferScope(plan({ scope: 'TYPE_DEFAULT' }))).toBe('TYPE_DEFAULT');
    expect(inferScope(plan({ countryCode: '', stateCode: '', stateName: '', city: '' }))).toBe('GENERAL');
    expect(inferScope(plan({ stateCode: '', stateName: '', city: '' }))).toBe('COUNTRY');
    expect(inferScope(plan())).toBe('PLACE');
  });
});

describe('the plan form of an existing plan', () => {
  it('starts a place plan with nothing chosen', () => {
    expect(emptyScopedPlanForm('AED')).toMatchObject({ scope: 'PLACE', locationId: '', scopeType: '', scopeLocationId: '', chain: [], currency: 'AED' });
  });

  it('carries the place, the chain and the type default parent of the plan', () => {
    const chain = [
      { id: 's', type: 'STATE' as const, level: 'REGION' as const, code: 'AP', name: 'Andhra Pradesh' },
      { id: 'd', type: 'DISTRICT' as const, level: 'REGION' as const, code: 'AP-KR', name: 'Krishna' },
    ];
    const placePlan = planToScopedForm(plan({ scope: 'PLACE', locationId: 'l1', chain }));
    expect(placePlan).toMatchObject({ scope: 'PLACE', locationId: 'l1', scopeLocationId: '', scopeParentType: '', chain });
    const typeDefault = planToScopedForm(plan({ scope: 'TYPE_DEFAULT', scopeType: 'TOWN', scopeLocationId: 'd', chain }));
    expect(typeDefault).toMatchObject({ scope: 'TYPE_DEFAULT', scopeType: 'TOWN', scopeLocationId: 'd', scopeParentType: 'DISTRICT' });
  });
});

describe('scopedPlanFormError', () => {
  it('asks for the application and valid terms first', () => {
    expect(scopedPlanFormError(form({ applicationCode: '' }), true)).toBe(`${KEY}application`);
    expect(scopedPlanFormError(form({ agentLimit: 0 }), true)).toBe('accessManagement.franchises.errors.terms');
    expect(scopedPlanFormError(form({ fee: -1 }), true)).toBe('accessManagement.franchises.errors.terms');
    expect(scopedPlanFormError(form({ fee: null }), true)).toBe('accessManagement.franchises.errors.terms');
  });

  it('needs a chosen place for a place plan, or a country when the places cannot be read', () => {
    expect(scopedPlanFormError(form(), true)).toBe(`${KEY}place`);
    expect(scopedPlanFormError(form({ locationId: 'l1' }), true)).toBeNull();
    expect(scopedPlanFormError(form(), false)).toBe(`${KEY}country`);
    expect(scopedPlanFormError(form({ countryCode: 'IN', stateName: 'Telangana' }), false)).toBeNull();
  });

  it('needs a type and a parent or country for a type default, and an allowed parent type', () => {
    expect(scopedPlanFormError(form({ scope: 'TYPE_DEFAULT' }), true)).toBe(`${KEY}scopeType`);
    expect(scopedPlanFormError(form({ scope: 'TYPE_DEFAULT', scopeType: 'TOWN' }), true)).toBe(`${KEY}typeDefaultWhere`);
    expect(scopedPlanFormError(form({ scope: 'TYPE_DEFAULT', scopeType: 'TOWN', countryCode: 'IN' }), true)).toBeNull();
    expect(scopedPlanFormError(form({ scope: 'TYPE_DEFAULT', scopeType: 'TOWN', scopeLocationId: 'd', scopeParentType: 'DISTRICT' }), true)).toBeNull();
    expect(scopedPlanFormError(form({ scope: 'TYPE_DEFAULT', scopeType: 'TOWN', scopeLocationId: 'v', scopeParentType: 'VILLAGE' }), true)).toBe(`${KEY}typeDefaultParent`);
    expect(scopedPlanFormError(form({ scope: 'TYPE_DEFAULT', scopeType: 'STATE', scopeLocationId: 's', scopeParentType: 'STATE' }), true)).toBe(`${KEY}typeDefaultParent`);
  });

  it('needs a country for a country plan and nothing more for the general plan', () => {
    expect(scopedPlanFormError(form({ scope: 'COUNTRY' }), true)).toBe(`${KEY}country`);
    expect(scopedPlanFormError(form({ scope: 'COUNTRY', countryCode: 'IN' }), true)).toBeNull();
    expect(scopedPlanFormError(form({ scope: 'GENERAL' }), true)).toBeNull();
  });
});

describe('toScopedPlanRequest', () => {
  it('sends only the place for a place plan, with the fee rounded to two decimals', () => {
    expect(toScopedPlanRequest(form({ locationId: 'l1', countryCode: 'IN', stateName: 'ignored' }), india, true)).toEqual({
      applicationCode: 'MATRIMONY', agentLimit: 20, fee: { code: 'INR', value: 20000.46 }, locationId: 'l1',
    });
  });

  it('falls back to the deprecated text fields when the places cannot be read', () => {
    const request = toScopedPlanRequest(form({ countryCode: 'IN', stateCode: 'ap', stateName: 'Andhra Pradesh', city: 'Nuzvid' }), india, false);
    expect(request).toMatchObject({ countryCode: 'IN', countryName: 'India', stateCode: 'AP', stateName: 'Andhra Pradesh', city: 'Nuzvid' });
    expect(request.locationId).toBeUndefined();
  });

  it('sends the type and its parent for a type default, or the type and the country when it has no parent', () => {
    expect(toScopedPlanRequest(form({ scope: 'TYPE_DEFAULT', scopeType: 'TOWN', scopeLocationId: 'd', countryCode: 'IN' }), india, true))
      .toEqual({ applicationCode: 'MATRIMONY', agentLimit: 20, fee: { code: 'INR', value: 20000.46 }, scopeType: 'TOWN', scopeLocationId: 'd' });
    expect(toScopedPlanRequest(form({ scope: 'TYPE_DEFAULT', scopeType: 'CITY', countryCode: 'IN' }), india, true))
      .toEqual({ applicationCode: 'MATRIMONY', agentLimit: 20, fee: { code: 'INR', value: 20000.46 }, scopeType: 'CITY', countryCode: 'IN', countryName: 'India' });
  });

  it('sends only the country for a country plan and neither country nor place for the general plan', () => {
    expect(toScopedPlanRequest(form({ scope: 'COUNTRY', countryCode: 'IN', locationId: 'x' }), india, true))
      .toEqual({ applicationCode: 'MATRIMONY', agentLimit: 20, fee: { code: 'INR', value: 20000.46 }, countryCode: 'IN', countryName: 'India' });
    expect(toScopedPlanRequest(form({ scope: 'GENERAL', countryCode: 'IN', locationId: 'x' }), india, true))
      .toEqual({ applicationCode: 'MATRIMONY', agentLimit: 20, fee: { code: 'INR', value: 20000.46 } });
  });
});

describe('planPlace', () => {
  it('shows the chain as a path, else the first release text, else "general"', () => {
    const chain = [{ id: 's', type: 'STATE' as const, level: 'REGION' as const, code: 'AP', name: 'Andhra Pradesh' }, { id: 'd', type: 'DISTRICT' as const, level: 'REGION' as const, code: 'K', name: 'Krishna' }];
    expect(planPlace(plan({ chain }), 'General')).toBe('Andhra Pradesh > Krishna');
    expect(planPlace(plan(), 'General')).toBe('India - Andhra Pradesh - Nuzvid');
    expect(planPlace(plan({ countryCode: '', countryName: '', stateCode: '', stateName: '', city: '' }), 'General')).toBe('General');
  });
});
