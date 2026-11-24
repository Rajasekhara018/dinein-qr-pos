import { HqAmountLike, hqAmountCode, hqAmountValue } from 'hq-shared-lib';
import { APPLIED_RULE_LEVELS, NAMED_PLAN_OPTIONS_LIMIT, NAMED_PLAN_SCOPE_LEVEL } from './franchise-packages.constants';
import {
  NamedCommissionPlanRow, NamedFeePlanRow, NamedPlanKind, NamedPlanOption, NamedPlanPicker, NamedPlanRow, NamedReferralPlanRow,
} from './franchise-packages.models';
import { FALLBACK_CURRENCY } from './franchises.constants';
import { formatMoney } from './franchises.helpers';

/** Length of an ISO date ("2026-11-09"): dates and instants from the API are compared on their day. */
const ISO_DAY_LENGTH = 10;
/** Basis points in one percent. */
const BASIS_POINTS_PER_PERCENT = 100;
/** Between the parts of a one-line plan summary. */
const SUMMARY_SEPARATOR = ' · ';

/** How a summary turns backend codes (GST treatment, commission party) into translated words. */
export interface PlanSummaryLabels {
  gst: (code: string) => string;
  party: (code: string) => string;
}

/** "?scopeLevel=PLAN&vertical=<app>&size=100": the named plans of one application. */
export function namedPlanQueryString(applicationCode: string, size: number = NAMED_PLAN_OPTIONS_LIMIT): string {
  const params = new URLSearchParams({ scopeLevel: NAMED_PLAN_SCOPE_LEVEL, vertical: applicationCode, size: String(size) });
  return `?${params.toString()}`;
}

/** "?vertical=<app>&size=100": every plan row of one application (all levels), to show what applies without a named plan. */
export function appliedRulesQueryString(applicationCode: string, size: number = NAMED_PLAN_OPTIONS_LIMIT): string {
  const params = new URLSearchParams({ vertical: applicationCode, size: String(size) });
  return `?${params.toString()}`;
}

/**
 * The plans in force today that a franchise follows when its package picks no plan: the application's plan for every event, its plans
 * for one event type and for one type of place (one row per scope, its current version), every event first, then by event and place.
 */
export function appliedRuleRows<T extends NamedPlanRow>(rows: readonly T[], today: string): T[] {
  const byScope = new Map<string, T[]>();
  for (const row of rows) {
    if (!APPLIED_RULE_LEVELS.includes(row.scope?.level ?? '') || !effectiveOn(row, today)) { continue; }
    const key = `${row.scope.level}|${row.scope.eventType ?? ''}|${row.scope.placeType ?? ''}`;
    byScope.set(key, [...(byScope.get(key) ?? []), row]);
  }
  const current = [...byScope.values()].map((versions) => currentPlanVersion(versions, today)).filter((row): row is T => !!row);
  const sortKey = (row: T) => `${row.scope.eventType ? 1 : 0}|${row.scope.eventType ?? ''}|${row.scope.placeType ? 1 : 0}|${row.scope.placeType ?? ''}`;
  return current.sort((a, b) => sortKey(a).localeCompare(sortKey(b)));
}

/** An event type code in plain words: RIDE_COMPLETED -> "Ride completed". */
export function eventTypeLabel(code: string | null | undefined): string {
  const words = (code ?? '').trim().toLowerCase().split('_').filter(Boolean).join(' ');
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : '';
}

/** Today as an ISO day, the way rule dates are compared. */
export function isoDay(date: Date): string {
  return date.toISOString().slice(0, ISO_DAY_LENGTH);
}

/** The plan code of a named-plan row ('' when the row is not a named plan). */
export function planCodeOf(row: NamedPlanRow): string {
  return row.scope?.level === NAMED_PLAN_SCOPE_LEVEL ? row.scope.planCode?.trim() ?? '' : '';
}

function effectiveOn(row: NamedPlanRow, today: string): boolean {
  const from = row.effectiveFrom?.slice(0, ISO_DAY_LENGTH) ?? '';
  const to = row.effectiveTo?.slice(0, ISO_DAY_LENGTH) ?? '';
  return (!from || from <= today) && (!to || to >= today);
}

/** The version of a plan that applies today (the latest start among those in force), else the latest-starting one. */
export function currentPlanVersion<T extends NamedPlanRow>(versions: readonly T[], today: string): T | null {
  const inForce = versions.filter((row) => effectiveOn(row, today));
  const pool = inForce.length > 0 ? inForce : versions;
  let best: T | null = null;
  for (const row of pool) {
    if (!best || (row.effectiveFrom ?? '') > (best.effectiveFrom ?? '')) { best = row; }
  }
  return best;
}

/**
 * The picker options from the plan rows of one application: one per plan code (rows sharing a code are versions of one plan),
 * named by the plan's name (its code when none has a name), sorted by name. The plan summarised is its row for every event; a row for one
 * event type only (a named commission plan's event row) is summarised only when the plan has no row for every event.
 */
export function namedPlanOptions(rows: readonly NamedPlanRow[], today: string): NamedPlanOption[] {
  const byCode = new Map<string, NamedPlanRow[]>();
  for (const row of rows) {
    const code = planCodeOf(row);
    if (!code) { continue; }
    byCode.set(code, [...(byCode.get(code) ?? []), row]);
  }
  const options: NamedPlanOption[] = [];
  byCode.forEach((versions, code) => {
    const everyEvent = versions.filter((row) => !row.scope?.eventType?.trim());
    const current = currentPlanVersion(everyEvent.length > 0 ? everyEvent : versions, today);
    const name = current?.planName?.trim() || versions.map((row) => row.planName?.trim() ?? '').find((text) => !!text) || code;
    options.push({ code, name, current });
  });
  return options.sort((a, b) => a.name.localeCompare(b.name));
}

/** Whether a code is among the options. */
export function hasPlanOption(options: readonly NamedPlanOption[], code: string): boolean {
  return options.some((option) => option.code === code);
}

/**
 * The options with the current pick added first when it is not listed (the caller may not list plans, or the plan's rows were
 * not in the page), so the picker shows its name rather than its bare code.
 */
export function withPickedOption(options: readonly NamedPlanOption[], code: string, name: string): NamedPlanOption[] {
  if (!code || hasPlanOption(options, code)) { return [...options]; }
  return [{ code, name: name || code, current: null }, ...options];
}

/** Basis points as a percentage text ("1500" → "15%"). */
export function percentText(basisPoints: number | null | undefined): string {
  return `${(basisPoints ?? 0) / BASIS_POINTS_PER_PERCENT}%`;
}

function moneyOf(amount: HqAmountLike, fallbackCurrency: string, locale?: string): string {
  return formatMoney(hqAmountValue(amount), hqAmountCode(amount, fallbackCurrency), locale);
}

function feeSummary(row: NamedFeePlanRow, labels: PlanSummaryLabels, locale?: string): string {
  const terms = row.terms;
  if (!terms) { return ''; }
  const value = terms.feeType === 'FIXED'
    ? moneyOf(terms.fixedAmount ?? null, row.currency || FALLBACK_CURRENCY, locale) : percentText(terms.percentageBp);
  return [value, terms.gstTreatment ? labels.gst(terms.gstTreatment) : ''].filter((part) => !!part).join(SUMMARY_SEPARATOR);
}

function commissionSummary(row: NamedCommissionPlanRow, labels: PlanSummaryLabels, locale?: string): string {
  return (row.lines ?? [])
    .filter((line) => line.active !== false)
    .map((line) => {
      const value = line.valueType === 'FIXED' ? moneyOf(line.fixedAmount ?? null, FALLBACK_CURRENCY, locale) : percentText(line.basisPoints);
      return `${labels.party(line.party)} ${value}`;
    })
    .join(SUMMARY_SEPARATOR);
}

function referralSummary(row: NamedReferralPlanRow, locale?: string): string {
  const rate = row.rate;
  if (!rate) { return ''; }
  return rate.valueType === 'FIXED' ? moneyOf(rate.fixedAmount ?? null, FALLBACK_CURRENCY, locale) : percentText(rate.basisPoints);
}

/** A one-line summary of a plan's current terms ("₹1,00,000.00 · GST included", "Agent 15% · Owner 85%", "2%"); '' when unknown. */
export function planSummary(kind: NamedPlanKind, row: NamedPlanRow | null, labels: PlanSummaryLabels, locale?: string): string {
  if (!row) { return ''; }
  if (kind === 'fee') { return feeSummary(row as NamedFeePlanRow, labels, locale); }
  if (kind === 'commission') { return commissionSummary(row as NamedCommissionPlanRow, labels, locale); }
  return referralSummary(row as NamedReferralPlanRow, locale);
}

/** A plan picker with no options yet. */
export function emptyPlanPicker(): NamedPlanPicker {
  return { options: [], loading: false, loadFailed: false };
}
