import { HQ_DATE_RANGE_DEFAULT_PRESET, hqDateRangeFor, hqTodayInZone } from 'hq-shared-lib';
import { TableColumnSpec } from '../shared/table-columns.helpers';
import { safeHttpUrl } from '../system-qa/system-qa.helpers';
import {
  QA_BAR_VIEW,
  QA_COLUMN_KEYS,
  QA_DEFAULT_WORKSPACE,
  QA_DONUT_VIEW,
  QA_EMPTY_FILTERS,
  QA_LINE_VIEW,
  QA_MAX_PAGE_SIZE,
  QA_NUMERIC_COLUMNS,
  QA_STATUSES,
  QA_STATUS_TONE,
  QA_TAG_EXPRESSION_MAX,
  QA_TAG_EXPRESSION_PATTERN,
  QA_TEST_SET_DESCRIPTION_MAX,
  QA_TEST_SET_NAME_MAX,
  QA_TEST_SET_NAME_MIN,
  QA_WORKSPACES,
  QaColumn,
  QaTone,
} from './qa-dashboard.constants';
import {
  QaBar,
  QaChartPoint,
  QaDonutSlice,
  QaExecutiveSummary,
  QaFacets,
  QaFailures,
  QaFilters,
  QaOverview,
  QaReleasePoint,
  QaStatus,
  QaTestCasePage,
  QaTestCaseRow,
  QaTestCaseTotals,
  QaTotals,
  QaWorkspace,
} from './qa-dashboard.models';

const MS_PER_SECOND = 1000;
const MS_PER_MINUTE = 60 * MS_PER_SECOND;
const MS_PER_HOUR = 60 * MS_PER_MINUTE;

// ---- workspace and filters ----

/** The workspace named by a query parameter; anything else is the default. */
export function parseWorkspace(value: string | null | undefined): QaWorkspace {
  const upper = (value ?? '').toUpperCase();
  return (QA_WORKSPACES as readonly string[]).includes(upper) ? (upper as QaWorkspace) : QA_DEFAULT_WORKSPACE;
}

/** A fresh, empty filter set (never share the constant: the screens change their copy). */
/**
 * The from / to of the period hq-date-range starts on (the current month, today in the user's timezone). A screen seeds its
 * filters with it, so the range control's first emit matches and the screen loads only once.
 */
export function qaDefaultPeriod(zone: string | null | undefined, now: Date = new Date()): Pick<QaFilters, 'from' | 'to'> {
  const range = hqDateRangeFor(HQ_DATE_RANGE_DEFAULT_PRESET, hqTodayInZone(zone, now));
  return { from: range.from, to: range.to };
}

export function emptyFilters(overrides: Partial<QaFilters> = {}): QaFilters {
  return { ...QA_EMPTY_FILTERS, ...overrides };
}

/** True when anything but the fixed suite narrows the list. */
export function hasActiveFilters(filters: QaFilters, ignore: ReadonlyArray<keyof QaFilters> = []): boolean {
  return (Object.keys(filters) as Array<keyof QaFilters>).some((key) => !ignore.includes(key) && String(filters[key] ?? '').trim() !== '');
}

/** The query parameters of a filter set: empty values are left out and the text is trimmed. */
export function filterParams(workspace: QaWorkspace, filters: QaFilters, extra: Record<string, string | number> = {}): Record<string, string | number> {
  const params: Record<string, string | number> = { workspace };
  (Object.keys(filters) as Array<keyof QaFilters>).forEach((key) => {
    const value = String(filters[key] ?? '').trim();
    if (value) params[key] = value;
  });
  return { ...params, ...extra };
}

/** Clamps a page request to what the API accepts. */
export function clampPage(page: number, size: number): { page: number; size: number } {
  return { page: Math.max(Math.trunc(page) || 0, 0), size: Math.min(Math.max(Math.trunc(size) || 1, 1), QA_MAX_PAGE_SIZE) };
}

/** The sub-modules of the chosen module ('' = none until a module is chosen). */
export function subModulesOf(facets: QaFacets | null, module: string): string[] {
  if (!facets || !module) return [];
  return facets.modules.find((m) => m.module === module)?.subModules ?? [];
}

// ---- display ----

/** The report address of a run, only when it is a plain http(s) link (never a script or file address). */
export function reportLink(url: string | null | undefined): string | null {
  return safeHttpUrl(url);
}

export function statusTone(status: string): QaTone {
  return QA_STATUS_TONE[status] ?? 'neutral';
}

/** Translates a code through its key map; a code with no key, or a missing translation, is shown as the raw code. */
export function labelFor(translate: (key: string) => string, keys: Readonly<Record<string, string>>, code: string | null | undefined): string {
  if (!code) return '';
  const key = keys[code];
  if (!key) return code;
  const label = translate(key);
  return label === key ? code : label;
}

export interface QaDurationPart {
  unit: 'h' | 'm' | 's' | 'ms';
  value: number;
}

/** A duration split into units: hours and minutes, minutes and seconds, seconds, or milliseconds. */
export function durationParts(ms: number): QaDurationPart[] {
  const safe = Math.max(Math.round(Number.isFinite(ms) ? ms : 0), 0);
  if (safe < MS_PER_SECOND) return [{ unit: 'ms', value: safe }];
  if (safe < MS_PER_MINUTE) return [{ unit: 's', value: Math.round(safe / MS_PER_SECOND) }];
  if (safe < MS_PER_HOUR) {
    const minutes = Math.floor(safe / MS_PER_MINUTE);
    const seconds = Math.round((safe % MS_PER_MINUTE) / MS_PER_SECOND);
    return seconds === 0 ? [{ unit: 'm', value: minutes }] : [{ unit: 'm', value: minutes }, { unit: 's', value: seconds }];
  }
  const hours = Math.floor(safe / MS_PER_HOUR);
  const minutes = Math.round((safe % MS_PER_HOUR) / MS_PER_MINUTE);
  return minutes === 0 ? [{ unit: 'h', value: hours }] : [{ unit: 'h', value: hours }, { unit: 'm', value: minutes }];
}

/** One decimal, no trailing zero ("49.5", "50"). */
export function formatPct(value: number | null | undefined): string {
  const safe = Number.isFinite(value as number) ? (value as number) : 0;
  return String(Math.round(safe * 10) / 10);
}

/** The first line of a (masked) failure message, cut to a readable length. */
export function shortMessage(message: string | null | undefined, max = 160): string {
  const line = (message ?? '').split('\n', 1)[0].trim();
  return line.length > max ? `${line.slice(0, max)}…` : line;
}

// ---- charts ----

/** Stacked pass/fail/skip bars of the newest releases (oldest first), placed in the SVG viewBox. */
export function buildBars(releases: readonly QaReleasePoint[]): QaBar[] {
  const view = QA_BAR_VIEW;
  const shown = releases.slice(-view.maxBars);
  if (shown.length === 0) return [];
  const max = Math.max(...shown.map((r) => r.passed + r.failed + r.skipped), 1);
  const innerW = view.width - 2 * view.paddingX;
  const innerH = view.height - view.paddingTop - view.paddingBottom;
  const slot = innerW / shown.length;
  const width = Math.max(slot - view.gap * 2, 4);
  return shown.map((r, i) => {
    const total = r.passed + r.failed + r.skipped;
    let bottom = view.height - view.paddingBottom;
    const segments = (QA_STATUSES as readonly QaStatus[])
      .map((status) => ({ status, value: status === 'PASSED' ? r.passed : status === 'FAILED' ? r.failed : r.skipped }))
      .filter((s) => s.value > 0)
      .map((s) => {
        const height = Math.max((s.value / max) * innerH, 1);
        bottom -= height;
        return { status: s.status, value: s.value, y: round2(bottom), height: round2(height) };
      });
    return { release: r.release, x: round2(view.paddingX + slot * i + (slot - width) / 2), width: round2(width), segments, total };
  });
}

/** The arcs of a pass/fail/skip ring; a status with nothing in it has no arc. */
export function buildDonut(counts: Pick<QaTestCaseTotals, 'passed' | 'failed' | 'skipped'>): QaDonutSlice[] {
  const { size, radius } = QA_DONUT_VIEW;
  const centre = size / 2;
  const values: Array<[QaStatus, number]> = [['PASSED', counts.passed], ['FAILED', counts.failed], ['SKIPPED', counts.skipped]];
  const total = values.reduce((sum, [, v]) => sum + Math.max(v, 0), 0);
  if (total <= 0) return [];
  const nonEmpty = values.filter(([, v]) => v > 0);
  if (nonEmpty.length === 1) {
    // A full ring cannot be one arc (start and end coincide): draw two halves.
    const [status, value] = nonEmpty[0];
    const path = `${arc(centre, radius, 0, Math.PI)} ${arc(centre, radius, Math.PI, 2 * Math.PI, false)}`;
    return [{ status, value, path }];
  }
  let angle = 0;
  return nonEmpty.map(([status, value]) => {
    const sweep = (value / total) * 2 * Math.PI;
    const path = arc(centre, radius, angle, angle + sweep);
    angle += sweep;
    return { status, value, path };
  });
}

function arc(centre: number, radius: number, from: number, to: number, move = true): string {
  const point = (a: number): string => `${round2(centre + radius * Math.sin(a))} ${round2(centre - radius * Math.cos(a))}`;
  const large = to - from > Math.PI ? 1 : 0;
  return `${move ? `M ${point(from)} ` : ''}A ${radius} ${radius} 0 ${large} 1 ${point(to)}`;
}

/** Places values (oldest first) on a line chart: x spread evenly, y from 0 (bottom) to the largest value (top). */
export function buildLinePoints(values: ReadonlyArray<{ label: string; value: number }>, sharedMax?: number): QaChartPoint[] {
  const { width, height, padding } = QA_LINE_VIEW;
  if (values.length === 0) return [];
  const max = Math.max(sharedMax ?? 0, ...values.map((v) => v.value), 1);
  const innerW = width - 2 * padding;
  const innerH = height - 2 * padding;
  return values.map((v, i) => ({
    label: v.label,
    value: v.value,
    x: round2(values.length === 1 ? width / 2 : padding + (innerW * i) / (values.length - 1)),
    y: round2(padding + innerH * (1 - Math.max(v.value, 0) / max)),
  }));
}

/** The `points` attribute of a polyline through the points. */
export function polyline(points: readonly QaChartPoint[]): string {
  return points.map((p) => `${p.x},${p.y}`).join(' ');
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

// ---- normalising what the API returns (a missing field never breaks a screen) ----

export function emptyTotals(): QaTotals {
  return { total: 0, passed: 0, failed: 0, skipped: 0, successPct: 0, failurePct: 0, skippedPct: 0, avgDurationMs: 0 };
}

export function normalizeTotals(raw: Partial<QaTotals> | null | undefined): QaTotals {
  return { ...emptyTotals(), ...(raw ?? {}) };
}

export function normalizeOverview(raw: Partial<QaOverview> | null | undefined): QaOverview {
  return {
    mode: raw?.mode ?? 'unique',
    totals: normalizeTotals(raw?.totals),
    releases: raw?.releases ?? [],
    trend: raw?.trend ?? [],
    volume: raw?.volume ?? [],
  };
}

export function normalizeFacets(raw: Partial<QaFacets> | null | undefined): QaFacets {
  return {
    modules: raw?.modules ?? [],
    environments: raw?.environments ?? [],
    releases: raw?.releases ?? [],
    suites: raw?.suites ?? [],
  };
}

export function normalizeRow(raw: Partial<QaTestCaseRow>): QaTestCaseRow {
  return {
    id: raw.id ?? '',
    tcId: raw.tcId ?? null,
    testKey: raw.testKey ?? '',
    name: raw.name ?? '',
    app: raw.app ?? '',
    module: raw.module ?? '',
    subModule: raw.subModule ?? '',
    environment: raw.environment ?? '',
    suites: raw.suites ?? [],
    status: raw.status ?? '',
    iteration: raw.iteration ?? 0,
    passCount: raw.passCount ?? 0,
    failCount: raw.failCount ?? 0,
    skipCount: raw.skipCount ?? 0,
    durationMs: raw.durationMs ?? 0,
    lastRunAt: raw.lastRunAt ?? null,
    lastRunId: raw.lastRunId ?? null,
    release: raw.release ?? null,
    failureCategory: raw.failureCategory ?? null,
    failureMessage: raw.failureMessage ?? null,
    reportUrl: raw.reportUrl ?? null,
  };
}

export function normalizeTestCasePage(raw: Partial<QaTestCasePage> | null | undefined, page: number, size: number): QaTestCasePage {
  const items = (raw?.items ?? []).map(normalizeRow);
  return {
    totals: { total: 0, passed: 0, failed: 0, skipped: 0, ...(raw?.totals ?? {}) },
    items,
    page: raw?.page ?? page,
    size: raw?.size ?? size,
    totalElements: raw?.totalElements ?? items.length,
    totalPages: raw?.totalPages ?? (items.length ? 1 : 0),
  };
}

export function normalizeFailures(raw: Partial<QaFailures> | null | undefined): QaFailures {
  return {
    totalFailed: raw?.totalFailed ?? 0,
    byCategory: raw?.byCategory ?? [],
    byModule: raw?.byModule ?? [],
    topFailing: raw?.topFailing ?? [],
    flaky: raw?.flaky ?? [],
  };
}

export function normalizeExecutiveSummary(raw: Partial<QaExecutiveSummary> | null | undefined): QaExecutiveSummary {
  return {
    generatedAt: raw?.generatedAt ?? '',
    workspace: raw?.workspace ?? QA_DEFAULT_WORKSPACE,
    release: raw?.release ?? null,
    environment: raw?.environment ?? null,
    totals: normalizeTotals(raw?.totals),
    previousRelease: raw?.previousRelease ? { ...raw.previousRelease, totals: normalizeTotals(raw.previousRelease.totals) } : null,
    headline: raw?.headline ?? [],
    topRisks: (raw?.topRisks ?? []).map(normalizeRow),
    slowest: (raw?.slowest ?? []).map(normalizeRow),
    modules: raw?.modules ?? [],
  };
}

// ---- test set form ----

export interface QaTestSetFormValues {
  name: string;
  suite: string;
  tagExpression: string;
  description: string;
}

/** True when the text is a plausible Cucumber tag expression: allowed characters only, balanced brackets, at least one tag. */
export function isValidTagExpression(expression: string): boolean {
  const text = expression.trim();
  if (!text || text.length > QA_TAG_EXPRESSION_MAX || !QA_TAG_EXPRESSION_PATTERN.test(text)) return false;
  let depth = 0;
  for (const ch of text) {
    if (ch === '(') depth++;
    if (ch === ')') depth--;
    if (depth < 0) return false;
  }
  return depth === 0 && /@[a-z0-9_.:\-]+/.test(text);
}

/** The first problem of a test set form as a field name, or null when it is fine. */
export function testSetFormProblem(values: QaTestSetFormValues): 'name' | 'suite' | 'tagExpression' | 'description' | null {
  const name = values.name.trim();
  if (name.length < QA_TEST_SET_NAME_MIN || name.length > QA_TEST_SET_NAME_MAX) return 'name';
  if (!values.suite) return 'suite';
  if (!isValidTagExpression(values.tagExpression)) return 'tagExpression';
  if (values.description.length > QA_TEST_SET_DESCRIPTION_MAX) return 'description';
  return null;
}

// ---- tables ----

/** The column specs of a test case table: the columns its screen shows, the counts right-aligned. */
export function testCaseColumnSpecs(columns: readonly QaColumn[]): TableColumnSpec<QaTestCaseRow>[] {
  return columns.map((column) => ({
    key: column,
    labelKey: QA_COLUMN_KEYS[column],
    ...(QA_NUMERIC_COLUMNS.includes(column) ? { className: 'qa-num' } : {}),
  }));
}
