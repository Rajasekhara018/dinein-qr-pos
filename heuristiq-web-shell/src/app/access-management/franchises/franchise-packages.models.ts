import type { HqAmount } from 'hq-shared-lib';
import { CommissionParty, LocationType, PageResult } from './franchise.models';

export type PackageValueType = 'PERCENTAGE' | 'FIXED';

/** One commission line of an explicit commission block (open-franchise / add-application request). */
export interface PackageLine {
  party: CommissionParty;
  valueType?: PackageValueType;
  basisPoints?: number | null;
  fixedAmount?: HqAmount | null;
  minAmount?: HqAmount | null;
  maxAmount?: HqAmount | null;
  active?: boolean;
}

/** An explicit referral rate on an open-franchise / add-application request. */
export interface PackageReferral {
  valueType: PackageValueType;
  basisPoints?: number | null;
  fixedAmount?: HqAmount | null;
  minAmount?: HqAmount | null;
  maxAmount?: HqAmount | null;
  maxReferralsPerMember?: number | null;
}

/**
 * The answer for one package template. A package picks a named fee, commission and referral plan by code (null = follow the
 * rules that apply); a franchise opened from it is linked to those plans. The names come with the codes for display.
 * {@code placeType} (null = any place) ties the package to one type of place: a franchise opened from it must cover a place of that
 * type. The agent places come from the package's fee plan, not from the package.
 */
export interface PackageTemplate {
  id: string;
  tenantId?: string | null;
  name: string;
  applicationCode: string;
  description?: string | null;
  active: boolean;
  placeType?: LocationType | null;
  feePlanCode?: string | null;
  feePlanName?: string | null;
  commissionPlanCode?: string | null;
  commissionPlanName?: string | null;
  referralPlanCode?: string | null;
  referralPlanName?: string | null;
  version: number;
}

/** POST and PUT body: the whole template; {@code version} only on PUT. A plan code of null means "follow the rules that apply". */
export interface PackageTemplateRequest {
  name: string;
  applicationCode: string;
  description?: string;
  placeType: LocationType | null;
  feePlanCode: string | null;
  commissionPlanCode: string | null;
  referralPlanCode: string | null;
  active: boolean;
  version?: number;
}

export interface PackageTemplateQuery {
  applicationCode?: string;
  active?: boolean;
  q?: string;
  page?: number;
  size?: number;
}

export type PackageTemplatePage = PageResult<PackageTemplate>;

// ── named plans a package can pick ────────────────────────────────────────

/** The three kinds of named plan a package picks. */
export type NamedPlanKind = 'fee' | 'commission' | 'referral';

/** The scope of a rule row; a named plan has level PLAN and its code in {@code planCode}. */
export interface NamedPlanScope {
  level: string;
  planCode?: string | null;
  vertical?: string | null;
  /** A named commission plan may also have a row for one event type next to its row for every event. */
  eventType?: string | null;
  /** The type of place of a commission plan for one type of place (PLACE_TYPE_VERTICAL). */
  placeType?: string | null;
}

/** One plan that applies to an application when a package picks none: what it covers and its terms in one line. */
export interface AppliedRule {
  /** The event type it is for ('' for every event). */
  eventType: string;
  /** The type of place it is for ('' for every place). */
  placeType: string;
  summary: string;
}

/** What every named-plan row has in common; several rows (versions) can share one plan code. */
export interface NamedPlanRowBase {
  id: string;
  scope: NamedPlanScope;
  planName?: string | null;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
}

/** The fee terms of a fee schedule row (amounts are `{ code, value }`). */
export interface NamedFeeTerms {
  feeType: 'FIXED' | 'PERCENTAGE';
  fixedAmount?: HqAmount | null;
  percentageBp?: number | null;
  gstTreatment?: string | null;
}

export interface NamedFeePlanRow extends NamedPlanRowBase {
  currency?: string | null;
  terms?: NamedFeeTerms | null;
}

export interface NamedCommissionPlanRow extends NamedPlanRowBase {
  lines?: PackageLine[] | null;
}

export interface NamedReferralPlanRow extends NamedPlanRowBase {
  rate?: PackageReferral | null;
}

/** Any named-plan row, whatever its kind. */
export type NamedPlanRow = NamedFeePlanRow | NamedCommissionPlanRow | NamedReferralPlanRow;

/** One named plan offered by a picker: its code (what the package stores), its name and its current version (null when unknown). */
export interface NamedPlanOption {
  code: string;
  name: string;
  current: NamedPlanRow | null;
}

/** What one plan picker of the package form shows: its options, and whether they are loading or failed to load. */
export interface NamedPlanPicker {
  options: NamedPlanOption[];
  loading: boolean;
  loadFailed: boolean;
}

/** The plan rows of one kind as a load returned them; {@code failed} when the call failed, {@code listed} when it was made. */
export interface NamedPlanLoad {
  rows: NamedPlanRow[];
  listed: boolean;
  failed: boolean;
}
