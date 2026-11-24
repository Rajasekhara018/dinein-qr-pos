import { NamedPlanKind } from './franchise-packages.models';
import { PlanSummaryLabels, planSummary } from './franchise-named-plans.helpers';
import { PlanPreviewResult } from './franchise-plan-preview.models';
import { RULE_SOURCE_BY_EVENT_KEY, RULE_SOURCE_FALLBACK_KEY, RULE_SOURCE_KEYS, RuleInForceKind } from './franchise-rules-in-force.constants';
import { RuleInForce, RuleInForceLine, RuleResolution } from './franchise-rules-in-force.models';

/** The summary kind of each resolver kind. */
const SUMMARY_KIND: Readonly<Record<RuleInForceKind, NamedPlanKind>> = { COMMISSION_PLAN: 'commission', REFERRAL_PLAN: 'referral' };

/** "?kind=COMMISSION_PLAN&franchiseId=<id>&vertical=<app>": the rule of one kind in force for one application of a franchise. */
export function ruleResolveQueryString(kind: RuleInForceKind, franchiseId: string, applicationCode: string, eventType?: string): string {
  const params = new URLSearchParams({ kind, franchiseId, vertical: applicationCode });
  if (eventType) params.set('eventType', eventType);
  return `?${params.toString()}`;
}

/** "?applicationCode=<app>&activeOnly=true": the active event types of one application. */
export function eventTypesQueryString(applicationCode: string): string {
  return `?${new URLSearchParams({ applicationCode, activeOnly: 'true' }).toString()}`;
}

/**
 * The rule in force as the setup shows it: its own plan (scope FRANCHISE), a named plan a package linked it to (scope PLAN, by its
 * code), or a rule of the organization (city, place type, application or platform); null when nothing was resolved.
 */
export function ruleInForce(resolution: RuleResolution | null, labels: PlanSummaryLabels): RuleInForce | null {
  if (!resolution?.rule) return null;
  const level = resolution.scope?.level ?? '';
  return {
    sourceKey: RULE_SOURCE_KEYS[level] ?? RULE_SOURCE_FALLBACK_KEY,
    sourceParams: { code: resolution.scope?.planCode?.trim() ?? '' },
    summary: planSummary(SUMMARY_KIND[resolution.kind] ?? 'commission', resolution.rule, labels),
  };
}

/**
 * The application's rules by event type, as the rules column shows them when no rule for every event is in force; null when there
 * are none, they could not be read, or the caller may not list them.
 */
export function rulesByEvent(result: PlanPreviewResult): RuleInForce | null {
  if (!result || result === 'failed' || !result.lines.length) return null;
  return { sourceKey: RULE_SOURCE_BY_EVENT_KEY, sourceParams: {}, summary: '', lines: result.lines };
}

/**
 * The commission in force for each event type, as the rules column shows it when no rule for every event is in force; null when no
 * event has one either.
 */
export function commissionByEvent(lines: readonly RuleInForceLine[]): RuleInForce | null {
  if (!lines.length) return null;
  return { sourceKey: RULE_SOURCE_BY_EVENT_KEY, sourceParams: {}, summary: '', eventLines: [...lines] };
}
