import { hqAmountOrNull } from 'hq-shared-lib';
import {
  MAX_BASIS_POINTS, PACKAGE_DESCRIPTION_MAX, PACKAGE_MAX_LINES, PACKAGE_NAME_MAX,
} from './franchise-packages.constants';
import {
  NamedPlanKind, PackageLine, PackageReferral, PackageTemplate, PackageTemplateQuery, PackageTemplateRequest, PackageValueType,
} from './franchise-packages.models';
import { CommissionParty, LocationType } from './franchise.models';

const NS = 'accessManagement.franchisePackages.errors';

/** A commission line while it is typed (the explicit commission block of the open-franchise form). */
export interface PackageLineForm {
  party: CommissionParty | '';
  valueType: PackageValueType;
  basisPoints: number | null;
  fixedAmount: number | null;
  minAmount: number | null;
  maxAmount: number | null;
  active: boolean;
}

/** A referral rate while it is typed (the explicit referral of the open-franchise form). */
export interface PackageReferralForm {
  valueType: PackageValueType;
  basisPoints: number | null;
  fixedAmount: number | null;
  minAmount: number | null;
  maxAmount: number | null;
  maxReferralsPerMember: number | null;
}

/** The whole package form. A plan code of '' means "follow the rules that apply"; a place type of '' means "any place". */
export interface PackageForm {
  name: string;
  applicationCode: string;
  description: string;
  placeType: LocationType | '';
  feePlanCode: string;
  commissionPlanCode: string;
  referralPlanCode: string;
  active: boolean;
  version: number | null;
}

/** The form (and template) field that holds the picked code of each plan kind. */
export type PackagePlanField = 'feePlanCode' | 'commissionPlanCode' | 'referralPlanCode';

/** The form field of a plan kind. */
export function planField(kind: NamedPlanKind): PackagePlanField {
  if (kind === 'fee') { return 'feePlanCode'; }
  return kind === 'commission' ? 'commissionPlanCode' : 'referralPlanCode';
}

/** The name of the plan a template picked for a kind ('' when it picked none); the code when the answer has no name. */
export function pickedPlanName(template: PackageTemplate, kind: NamedPlanKind): string {
  const code = template[planField(kind)]?.trim() ?? '';
  if (!code) { return ''; }
  let name: string | null | undefined;
  if (kind === 'fee') {
    name = template.feePlanName;
  } else {
    name = kind === 'commission' ? template.commissionPlanName : template.referralPlanName;
  }
  return name?.trim() || code;
}

export function emptyLineForm(): PackageLineForm {
  return { party: '', valueType: 'PERCENTAGE', basisPoints: null, fixedAmount: null, minAmount: null, maxAmount: null, active: true };
}

export function emptyReferralForm(): PackageReferralForm {
  return { valueType: 'PERCENTAGE', basisPoints: 0, fixedAmount: null, minAmount: null, maxAmount: null, maxReferralsPerMember: null };
}

export function emptyPackageForm(): PackageForm {
  return {
    name: '', applicationCode: '', description: '', placeType: '', feePlanCode: '', commissionPlanCode: '', referralPlanCode: '',
    active: true, version: null,
  };
}

/** The form for changing a template (every field comes from the API's answer, the version included). */
export function packageToForm(template: PackageTemplate): PackageForm {
  return {
    name: template.name, applicationCode: template.applicationCode, description: template.description ?? '',
    placeType: template.placeType ?? '', feePlanCode: '', commissionPlanCode: template.commissionPlanCode ?? '', referralPlanCode: template.referralPlanCode ?? '',
    active: template.active, version: template.version,
  };
}

function present(value: number | null | undefined): value is number {
  return value !== null && value !== undefined && !Number.isNaN(value);
}

/** Translation key of what is wrong with one commission line, or null. The server checks the lines as a plan (sum, bounds). */
export function lineError(line: PackageLineForm): string | null {
  if (!line.party) { return `${NS}.lineParty`; }
  if (line.valueType === 'FIXED') {
    return present(line.fixedAmount) && line.fixedAmount > 0 ? null : `${NS}.lineFixed`;
  }
  return present(line.basisPoints) && line.basisPoints >= 0 && line.basisPoints <= MAX_BASIS_POINTS ? null : `${NS}.lineBasisPoints`;
}

/** Translation key of what is wrong with the commission lines, or null. */
export function linesError(lines: readonly PackageLineForm[]): string | null {
  if (lines.length > PACKAGE_MAX_LINES) { return `${NS}.tooManyLines`; }
  const seen = new Set<string>();
  for (const line of lines) {
    const error = lineError(line);
    if (error) { return error; }
    if (seen.has(line.party)) { return `${NS}.duplicateParty`; }
    seen.add(line.party);
  }
  return null;
}

/** Translation key of what is wrong with a referral, or null. */
export function referralError(referral: PackageReferralForm): string | null {
  if (referral.valueType === 'FIXED') {
    if (!present(referral.fixedAmount) || referral.fixedAmount <= 0) { return `${NS}.referralFixed`; }
  } else if (!present(referral.basisPoints) || referral.basisPoints < 0 || referral.basisPoints > MAX_BASIS_POINTS) {
    return `${NS}.referralBasisPoints`;
  }
  if (present(referral.maxReferralsPerMember) && referral.maxReferralsPerMember < 1) { return `${NS}.referralMax`; }
  return null;
}

/**
 * Translation key of the first thing wrong with a package form, or null when it can be sent. The picked plans are checked by
 * the service (an unknown plan or one of another application is refused with its own message).
 */
export function packageFormError(form: PackageForm): string | null {
  if (!form.name.trim() || form.name.trim().length > PACKAGE_NAME_MAX) { return `${NS}.name`; }
  if (!form.applicationCode) { return `${NS}.application`; }
  if (form.description.length > PACKAGE_DESCRIPTION_MAX) { return `${NS}.description`; }
  return null;
}

/**
 * One commission line as the API takes it; fields that do not apply to its value type are left out. The minimum and
 * maximum bound a percentage only: the service rejects a fixed line that carries a range. Amounts go as `{ code, value }`
 * in {@code currency}.
 */
export function lineToRequest(line: PackageLineForm, currency: string): PackageLine {
  const fixed = line.valueType === 'FIXED';
  return {
    party: line.party as CommissionParty,
    valueType: line.valueType,
    ...(fixed ? { fixedAmount: hqAmountOrNull(line.fixedAmount, currency) } : { basisPoints: line.basisPoints }),
    ...(!fixed && present(line.minAmount) ? { minAmount: hqAmountOrNull(line.minAmount, currency) } : {}),
    ...(!fixed && present(line.maxAmount) ? { maxAmount: hqAmountOrNull(line.maxAmount, currency) } : {}),
    active: line.active,
  };
}

/** A referral rate as the API takes it; amounts go as `{ code, value }` in {@code currency}. */
export function referralToRequest(referral: PackageReferralForm, currency: string): PackageReferral {
  const fixed = referral.valueType === 'FIXED';
  return {
    valueType: referral.valueType,
    ...(fixed ? { fixedAmount: hqAmountOrNull(referral.fixedAmount, currency) } : { basisPoints: referral.basisPoints }),
    ...(!fixed && present(referral.minAmount) ? { minAmount: hqAmountOrNull(referral.minAmount, currency) } : {}),
    ...(!fixed && present(referral.maxAmount) ? { maxAmount: hqAmountOrNull(referral.maxAmount, currency) } : {}),
    ...(present(referral.maxReferralsPerMember) ? { maxReferralsPerMember: referral.maxReferralsPerMember } : {}),
  };
}

/** A picked plan code as the API takes it: trimmed, and null for "follow the rules that apply". */
function planCodeOrNull(code: string | null | undefined): string | null {
  const trimmed = code?.trim() ?? '';
  return trimmed ? trimmed : null;
}

/** The whole template as POST / PUT take it (a PUT replaces the template, so an empty pick is sent as null, which clears it). */
export function toPackageRequest(form: PackageForm): PackageTemplateRequest {
  const description = form.description.trim();
  return {
    name: form.name.trim(),
    applicationCode: form.applicationCode,
    ...(description ? { description } : {}),
    placeType: form.placeType || null,
    // A package never picks a fee plan: the joining fee comes from the franchise plan of the place.
    feePlanCode: null,
    commissionPlanCode: planCodeOrNull(form.commissionPlanCode),
    referralPlanCode: planCodeOrNull(form.referralPlanCode),
    active: form.active,
    ...(form.version !== null ? { version: form.version } : {}),
  };
}

/** The same template with only its switch changed (how a template is switched on or off). */
export function withActive(template: PackageTemplate, active: boolean): PackageTemplateRequest {
  return toPackageRequest({ ...packageToForm(template), active });
}

/** "?applicationCode=..&active=..&q=..&page=..&size=.." for the list call. */
export function packageQueryString(query: PackageTemplateQuery): string {
  const params = new URLSearchParams();
  if (query.applicationCode) { params.set('applicationCode', query.applicationCode); }
  if (query.active !== undefined) { params.set('active', String(query.active)); }
  if (query.q?.trim()) { params.set('q', query.q.trim()); }
  if (query.page !== undefined) { params.set('page', String(query.page)); }
  if (query.size !== undefined) { params.set('size', String(query.size)); }
  const text = params.toString();
  return text ? `?${text}` : '';
}
