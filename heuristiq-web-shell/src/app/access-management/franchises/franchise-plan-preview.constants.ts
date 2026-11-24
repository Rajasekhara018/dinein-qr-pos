import { NamedPlanKind, PackageTemplate } from './franchise-packages.models';

/** The kinds a plan preview shows, with the package template field that picks a named plan of that kind. */
export const PREVIEW_KINDS: ReadonlyArray<{
  kind: Extract<NamedPlanKind, 'commission' | 'referral'>;
  code: (template: PackageTemplate) => string | null | undefined;
}> = [
  { kind: 'commission', code: (template) => template.commissionPlanCode },
  { kind: 'referral', code: (template) => template.referralPlanCode },
];

/** Translation namespace of the plan preview. */
export const PLAN_PREVIEW_I18N = 'accessManagement.franchises.planPreview';
