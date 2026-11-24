import { hqAmountOrNull } from 'hq-shared-lib';
import { FranchisePlan, FranchisePlanRequest, LocationType, PlaceRef, PlanScope } from './franchise.models';
import { allowedParentTypes, placePath } from './franchise-locations.helpers';
import { CountryOption, PlanForm, emptyPlanForm, planFormError, planRegion, planToForm, roundFee, toPlanRequest } from './franchises.helpers';
import { MIN_AGENT_LIMIT } from './franchises.constants';

/**
 * The plan form of the place model: the first release's text fields (kept only as a fallback for someone who may not read
 * the places reference) plus the scope and the place chosen in the cascading picker.
 */
export interface ScopedPlanForm extends PlanForm {
  scope: PlanScope;
  /** Place plan: the deepest place chosen. */
  locationId: string;
  /** Type default: the type it covers, the state or district it is under ('' = the whole country) and that place's type. */
  scopeType: LocationType | '';
  scopeLocationId: string;
  scopeParentType: LocationType | '';
  /** The path to show as already chosen when an existing plan is changed. */
  chain: PlaceRef[];
}

/** The scope an older plan answer (without {@code scope}) has: no country is general, only a country is the country's. */
export function inferScope(plan: Pick<FranchisePlan, 'scope' | 'countryCode' | 'stateCode' | 'stateName' | 'city'>): PlanScope {
  if (plan.scope) {
    return plan.scope;
  }
  if (!plan.countryCode) {
    return 'GENERAL';
  }
  return plan.stateCode || plan.stateName || plan.city ? 'PLACE' : 'COUNTRY';
}

export function emptyScopedPlanForm(currency: string): ScopedPlanForm {
  return { ...emptyPlanForm(currency), scope: 'PLACE', locationId: '', scopeType: '', scopeLocationId: '', scopeParentType: '', chain: [] };
}

/** The form of an existing plan; an older bare-number fee is read in {@code fallbackCurrency}. */
export function planToScopedForm(plan: FranchisePlan, fallbackCurrency?: string): ScopedPlanForm {
  const chain = plan.chain ?? [];
  return {
    ...planToForm(plan, fallbackCurrency),
    scope: inferScope(plan),
    locationId: plan.locationId ?? '',
    scopeType: plan.scopeType ?? '',
    scopeLocationId: plan.scopeLocationId ?? '',
    scopeParentType: plan.scopeLocationId ? chain[chain.length - 1]?.type ?? '' : '',
    chain,
  };
}

/** Translation key of the first thing wrong with the plan, or null when it can be sent. */
export function scopedPlanFormError(form: ScopedPlanForm, canPickPlaces: boolean): string | null {
  if (!form.applicationCode) {
    return 'accessManagement.franchisePlans.errors.application';
  }
  if (form.agentLimit === null || form.agentLimit < MIN_AGENT_LIMIT || form.fee === null || form.fee < 0) {
    return 'accessManagement.franchises.errors.terms';
  }
  switch (form.scope) {
    case 'PLACE':
      if (canPickPlaces) {
        return form.locationId ? null : 'accessManagement.franchisePlans.errors.place';
      }
      return form.countryCode ? planFormError(form) : 'accessManagement.franchisePlans.errors.country';
    case 'TYPE_DEFAULT':
      if (!form.scopeType) {
        return 'accessManagement.franchisePlans.errors.scopeType';
      }
      if (!form.scopeLocationId) {
        return form.countryCode ? null : 'accessManagement.franchisePlans.errors.typeDefaultWhere';
      }
      return allowedParentTypes(form.scopeType).includes(form.scopeParentType as LocationType)
        ? null : 'accessManagement.franchisePlans.errors.typeDefaultParent';
    case 'COUNTRY':
      return form.countryCode ? null : 'accessManagement.franchisePlans.errors.country';
    default:
      return null;
  }
}

/**
 * The request for a valid plan: only the fields of its scope are sent (a place plan sends just the place). The fee is
 * `{ code, value }` in the form's currency.
 */
export function toScopedPlanRequest(form: ScopedPlanForm, countries: readonly CountryOption[], canPickPlaces: boolean): FranchisePlanRequest {
  const base = {
    applicationCode: form.applicationCode,
    agentLimit: form.agentLimit as number,
    fee: hqAmountOrNull(roundFee(form.fee), form.currency),
  };
  const countryName = countries.find((option) => option.code === form.countryCode)?.name ?? '';
  switch (form.scope) {
    case 'PLACE':
      return canPickPlaces ? { ...base, locationId: form.locationId } : toPlanRequest(form, countries);
    case 'TYPE_DEFAULT':
      return {
        ...base, scopeType: form.scopeType as LocationType,
        ...(form.scopeLocationId ? { scopeLocationId: form.scopeLocationId } : { countryCode: form.countryCode, countryName }),
      };
    case 'COUNTRY':
      return { ...base, countryCode: form.countryCode, countryName };
    default:
      return base;
  }
}

/** The place of a plan: the chain joined with " > ", or the first release's text when the answer has no chain. */
export function planPlace(plan: FranchisePlan, general: string): string {
  return plan.chain && plan.chain.length > 0 ? placePath(plan.chain) : planRegion(plan, general);
}
