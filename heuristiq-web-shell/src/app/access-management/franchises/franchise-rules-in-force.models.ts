import { AppliedRule, NamedCommissionPlanRow, NamedPlanScope, NamedReferralPlanRow } from './franchise-packages.models';
import { RuleInForceKind } from './franchise-rules-in-force.constants';

/** What the rule resolver answers: the rule in force for a franchise and application today, and the scope it was found in. */
export interface RuleResolution {
  kind: RuleInForceKind;
  date: string;
  scope: NamedPlanScope & { franchiseId?: string | null };
  rule: NamedCommissionPlanRow | NamedReferralPlanRow | null;
}

/** One rule as the franchise setup shows it: where it comes from and its terms in one line. */
export interface RuleInForce {
  /** Translation key and params of its source ("this franchise's own plan", "plan GOLD (from a package)", "organization rule"). */
  sourceKey: string;
  sourceParams: Record<string, string>;
  summary: string;
  /** For the application's rules by event (no rule for every event is in force): one line per event type and type of place. */
  lines?: AppliedRule[];
  /** The commission in force for each event type of the application, each with where it comes from. */
  eventLines?: RuleInForceLine[];
}

/** The commission in force for one event type: the event's name, the rule's terms in one line and its source. */
export interface RuleInForceLine {
  eventType: string;
  summary: string;
  sourceKey: string;
  sourceParams: Record<string, string>;
}

/** One event type of the catalogue, as the franchise service lists it. */
export interface EventTypeOption {
  code: string;
  applicationCode?: string | null;
  description?: string | null;
  active?: boolean;
}

/**
 * The commission and referral in force for one application: a rule, null when none is in force, or 'failed' when it could not be
 * read.
 */
export type RulesInForce = Record<RuleInForceKind, RuleInForce | null | 'failed'>;
