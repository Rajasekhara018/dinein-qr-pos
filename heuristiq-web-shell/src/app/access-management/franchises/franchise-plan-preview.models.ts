import { AppliedRule } from './franchise-packages.models';

/**
 * The commission or referral a franchise will follow for one application, as a preview: the named plan its package picks (by
 * name), or the application's rules that apply when it picks none, one line per event type and type of place.
 */
export interface PlanPreviewSection {
  /** 'plan': the package's named plan; 'rules': the rules of the application that apply. */
  source: 'plan' | 'rules';
  planName: string;
  lines: AppliedRule[];
}

/** A section, null when the caller may not list that kind of plan, or 'failed' when it could not be read. */
export type PlanPreviewResult = PlanPreviewSection | null | 'failed';

export interface PlanPreview {
  commission: PlanPreviewResult;
  referral: PlanPreviewResult;
}
