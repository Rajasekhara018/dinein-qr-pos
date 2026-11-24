import {
  COVERAGE_AGE_KEYS,
  COVERAGE_DEFAULT_THRESHOLDS,
  COVERAGE_ERROR_KEYS,
  COVERAGE_MAX_UPLOAD_BYTES,
  COVERAGE_SCHEMA_VERSION,
  COVERAGE_STALE_AFTER_DAYS,
  COVERAGE_TREND_VIEW,
} from './system-coverage.constants';
import {
  CoverageAge,
  CoverageDocument,
  CoverageFilters,
  CoverageLevel,
  CoverageMetric,
  CoverageParseResult,
  CoverageProject,
  CoverageRow,
  CoverageSortMode,
  CoverageThresholds,
  CoverageTrendPoint,
  CoverageTrendValue,
} from './system-coverage.models';

const MS_PER_MINUTE = 60_000;
const MS_PER_HOUR = 60 * MS_PER_MINUTE;
const MS_PER_DAY = 24 * MS_PER_HOUR;

/** The lower of lines and branches, or null when either is absent (a project that was not measured). */
export function projectScore(project: CoverageProject): number | null {
  const lines = project.metrics?.lines?.pct;
  const branches = project.metrics?.branches?.pct;
  if (project.status !== 'ok' || typeof lines !== 'number' || typeof branches !== 'number') return null;
  return Math.min(lines, branches);
}

/**
 * Classifies a project: MISSING when it was not measured, BELOW_FLOOR under the floor, AT_TARGET from the target up
 * (both lines and branches), OK in between. Mirrors the server's belowFloorCount / atTargetCount.
 */
export function classifyProject(project: CoverageProject, thresholds: CoverageThresholds): CoverageLevel {
  const score = projectScore(project);
  if (score === null) return 'MISSING';
  if (score < thresholds.floor) return 'BELOW_FLOOR';
  return score >= thresholds.target ? 'AT_TARGET' : 'OK';
}

/** Thresholds of a document, or the defaults when it carries none. */
export function thresholdsOf(document: CoverageDocument | null | undefined): CoverageThresholds {
  const t = document?.thresholds;
  return t && typeof t.floor === 'number' && typeof t.target === 'number' ? t : COVERAGE_DEFAULT_THRESHOLDS;
}

/** Methods for a backend project, functions for a web project; null when not measured. */
export function methodsOrFunctions(project: CoverageProject): CoverageMetric | null {
  const m = project.metrics;
  if (!m) return null;
  return (project.kind === 'backend' ? m.methods : m.functions) ?? null;
}

/** True when the project was measured more than `days` before `nowMs`. A missing or unreadable date is not stale. */
export function isStale(measuredAt: string | null, nowMs: number, days = COVERAGE_STALE_AFTER_DAYS): boolean {
  if (!measuredAt) return false;
  const t = Date.parse(measuredAt);
  return Number.isFinite(t) && nowMs - t > days * MS_PER_DAY;
}

/** How long ago `iso` was, as a translation key and count; null when the date is unreadable. */
export function ageOf(iso: string | null | undefined, nowMs: number): CoverageAge | null {
  const t = iso ? Date.parse(iso) : NaN;
  if (!Number.isFinite(t)) return null;
  const diff = nowMs - t;
  if (diff < MS_PER_MINUTE) return { key: COVERAGE_AGE_KEYS.now, count: 0 };
  if (diff < MS_PER_HOUR) return { key: COVERAGE_AGE_KEYS.minutes, count: Math.floor(diff / MS_PER_MINUTE) };
  if (diff < MS_PER_DAY) return { key: COVERAGE_AGE_KEYS.hours, count: Math.floor(diff / MS_PER_HOUR) };
  return { key: COVERAGE_AGE_KEYS.days, count: Math.floor(diff / MS_PER_DAY) };
}

/** Builds the table rows of a document. */
export function buildRows(document: CoverageDocument, nowMs: number): CoverageRow[] {
  const thresholds = thresholdsOf(document);
  return (document.projects ?? []).map((p) => ({
    key: p.key,
    kind: p.kind,
    level: classifyProject(p, thresholds),
    stale: isStale(p.measuredAt, nowMs),
    measuredAt: p.measuredAt,
    lines: p.metrics?.lines?.pct ?? null,
    branches: p.metrics?.branches?.pct ?? null,
    methodsOrFunctions: methodsOrFunctions(p)?.pct ?? null,
    score: projectScore(p),
    groups: [...(p.groups ?? [])].sort((a, b) => a.lines.pct - b.lines.pct || a.name.localeCompare(b.name)),
  }));
}

/** Rows that match the kind, level and text filters (text matches the project key, case-insensitively). */
export function filterRows(rows: readonly CoverageRow[], filters: CoverageFilters): CoverageRow[] {
  const q = filters.query.trim().toLowerCase();
  return rows.filter(
    (r) =>
      (!filters.kind || r.kind === filters.kind) &&
      (!filters.level || r.level === filters.level) &&
      (!q || r.key.toLowerCase().includes(q)),
  );
}

/**
 * Sorts rows without touching the input. 'worst' puts the lowest score first and projects that were not measured
 * last; 'best' is the reverse of the measured ones (unmeasured still last); 'name' is alphabetical. Ties by name.
 */
export function sortRows(rows: readonly CoverageRow[], mode: CoverageSortMode): CoverageRow[] {
  const byName = (a: CoverageRow, b: CoverageRow): number => a.key.localeCompare(b.key);
  const byScore = (direction: 1 | -1) => (a: CoverageRow, b: CoverageRow): number => {
    if (a.score === null && b.score === null) return byName(a, b);
    if (a.score === null) return 1;
    if (b.score === null) return -1;
    return direction * (a.score - b.score) || byName(a, b);
  };
  const compare = mode === 'name' ? byName : byScore(mode === 'best' ? -1 : 1);
  return [...rows].sort(compare);
}

/** How many projects fall in each level. */
export function countLevels(rows: readonly CoverageRow[]): Record<CoverageLevel, number> {
  const counts: Record<CoverageLevel, number> = { BELOW_FLOOR: 0, OK: 0, AT_TARGET: 0, MISSING: 0 };
  rows.forEach((r) => (counts[r.level] += 1));
  return counts;
}

/** Average line coverage of the measured projects of a document, rounded to 2 decimals; null when none was measured. */
export function averageLinesPct(document: CoverageDocument): number | null {
  const values = (document.projects ?? [])
    .filter((p) => p.status === 'ok' && typeof p.metrics?.lines?.pct === 'number')
    .map((p) => p.metrics?.lines?.pct as number);
  if (values.length === 0) return null;
  return Math.round((values.reduce((a, b) => a + b, 0) / values.length) * 100) / 100;
}

/** Places trend values (given oldest first) in the SVG viewBox: x spread evenly, y from 0% (bottom) to 100% (top). */
export function buildTrendPoints(values: readonly CoverageTrendValue[]): CoverageTrendPoint[] {
  const { width, height, padding } = COVERAGE_TREND_VIEW;
  const innerW = width - 2 * padding;
  const innerH = height - 2 * padding;
  return values.map((v, i) => {
    const clamped = Math.min(100, Math.max(0, v.value));
    const x = values.length === 1 ? width / 2 : padding + (innerW * i) / (values.length - 1);
    const y = padding + innerH * (1 - clamped / 100);
    return { ...v, x: round2(x), y: round2(y) };
  });
}

/** The `points` attribute of a polyline through the trend points. */
export function trendPolyline(points: readonly CoverageTrendPoint[]): string {
  return points.map((p) => `${p.x},${p.y}`).join(' ');
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

/**
 * Checks a parsed collector document on the client (the server validates again). Returns the translation key of the first
 * problem, or null when the document is acceptable.
 */
export function validateCollectorDocument(value: unknown): string | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return COVERAGE_ERROR_KEYS.notObject;
  const doc = value as Record<string, unknown>;
  if (doc['schemaVersion'] !== COVERAGE_SCHEMA_VERSION) return COVERAGE_ERROR_KEYS.schemaVersion;
  if (typeof doc['generatedAt'] !== 'string' || !Number.isFinite(Date.parse(doc['generatedAt']))) {
    return COVERAGE_ERROR_KEYS.generatedAt;
  }
  const t = doc['thresholds'] as Record<string, unknown> | null | undefined;
  if (!t || typeof t['floor'] !== 'number' || typeof t['target'] !== 'number') return COVERAGE_ERROR_KEYS.thresholds;
  if (!Array.isArray(doc['projects']) || doc['projects'].length === 0) return COVERAGE_ERROR_KEYS.noProjects;
  return null;
}

/** Reads the text of a collector file: size limit, JSON syntax, then {@link validateCollectorDocument}. */
export function parseCollectorText(text: string, maxBytes = COVERAGE_MAX_UPLOAD_BYTES): CoverageParseResult {
  if (new TextEncoder().encode(text).length > maxBytes) return { document: null, errorKey: COVERAGE_ERROR_KEYS.tooLarge };
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { document: null, errorKey: COVERAGE_ERROR_KEYS.notJson };
  }
  const errorKey = validateCollectorDocument(parsed);
  return errorKey ? { document: null, errorKey } : { document: parsed as CoverageDocument, errorKey: null };
}

/** The translation key (and {{detail}}) to show for a failed API call. 403 = no privilege; 400/413/422 = the server refused the document. */
export function uploadErrorOf(error: unknown): { key: string; detail: string } {
  const e = error as { status?: number; error?: { message?: unknown; detail?: unknown } } | null;
  const raw = e?.error?.message ?? e?.error?.detail;
  const detail = typeof raw === 'string' ? raw.slice(0, 300) : '';
  if (e?.status === 403) return { key: COVERAGE_ERROR_KEYS.forbidden, detail };
  if (e?.status === 400 || e?.status === 413 || e?.status === 422) return { key: COVERAGE_ERROR_KEYS.rejected, detail };
  return { key: COVERAGE_ERROR_KEYS.failed, detail };
}

/** Reads a chosen file as text; rejects when the browser cannot read it. */
export function readFileText(file: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '');
    reader.onerror = () => reject(reader.error ?? new Error('unreadable'));
    reader.readAsText(file);
  });
}
