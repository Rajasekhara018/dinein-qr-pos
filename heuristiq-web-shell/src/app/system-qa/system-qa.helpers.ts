import { Marked, Tokens } from 'marked';

import { QA_LINK_REL, QA_TREND_VIEW } from './system-qa.constants';
import {
  QaDocumentSummary,
  QaDurationPart,
  QaRunSummary,
  QaScenario,
  QaScenarioFilters,
  QaTrendPoint,
  QaTrendValue,
} from './system-qa.models';

const MS_PER_SECOND = 1000;
const MS_PER_MINUTE = 60 * MS_PER_SECOND;
const MS_PER_HOUR = 60 * MS_PER_MINUTE;

/**
 * Share of executed scenarios that passed (skipped ones are not counted), rounded to one decimal; null when nothing
 * was executed.
 */
export function passRate(run: Pick<QaRunSummary, 'passed' | 'failed'>): number | null {
  const passed = run.passed ?? 0;
  const executed = passed + (run.failed ?? 0);
  if (executed <= 0) return null;
  return Math.round((passed / executed) * 1000) / 10;
}

/** Pass rates of the given runs (newest first in, oldest first out); runs with nothing executed are left out. */
export function trendValues(runs: readonly QaRunSummary[]): QaTrendValue[] {
  const values: QaTrendValue[] = [];
  runs.forEach((r) => {
    const value = passRate(r);
    if (value !== null) values.push({ id: r.id, startedAt: r.startedAt, value });
  });
  return values.reverse();
}

/** Places trend values (oldest first) in the SVG viewBox: x spread evenly, y from 0% (bottom) to 100% (top). */
export function buildTrendPoints(values: readonly QaTrendValue[]): QaTrendPoint[] {
  const { width, height, padding } = QA_TREND_VIEW;
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
export function trendPolyline(points: readonly QaTrendPoint[]): string {
  return points.map((p) => `${p.x},${p.y}`).join(' ');
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

/** Splits a duration into the two largest non-zero units; zero or an invalid value is 0 ms. */
export function durationParts(ms: number | null | undefined): QaDurationPart[] {
  const total = typeof ms === 'number' && Number.isFinite(ms) && ms > 0 ? Math.round(ms) : 0;
  if (total < MS_PER_SECOND) return [{ unit: 'ms', value: total }];
  const parts: QaDurationPart[] = [];
  const hours = Math.floor(total / MS_PER_HOUR);
  const minutes = Math.floor((total % MS_PER_HOUR) / MS_PER_MINUTE);
  const seconds = Math.floor((total % MS_PER_MINUTE) / MS_PER_SECOND);
  if (hours > 0) parts.push({ unit: 'h', value: hours });
  if (minutes > 0) parts.push({ unit: 'm', value: minutes });
  if (seconds > 0 && hours === 0) parts.push({ unit: 's', value: seconds });
  return parts;
}

const STATUS_RANK: Readonly<Record<string, number>> = { FAILED: 0, SKIPPED: 1 };

/** Failed scenarios first, then skipped, then the rest; the original order is kept inside each group. */
export function sortFailuresFirst(scenarios: readonly QaScenario[]): QaScenario[] {
  const rank = (s: QaScenario): number => STATUS_RANK[s.status] ?? 2;
  return scenarios
    .map((s, i) => ({ s, i }))
    .sort((a, b) => rank(a.s) - rank(b.s) || a.i - b.i)
    .map((x) => x.s);
}

/** Scenarios that match the status, tag, pilot id and text filters (text matches feature, name and pilot id, ignoring case). */
export function filterScenarios(scenarios: readonly QaScenario[], filters: QaScenarioFilters): QaScenario[] {
  const q = filters.query.trim().toLowerCase();
  return scenarios.filter(
    (s) =>
      (!filters.status || s.status === filters.status) &&
      (!filters.tag || (s.tags ?? []).includes(filters.tag)) &&
      (!filters.pilotId || s.pilotId === filters.pilotId) &&
      (!q || [s.feature, s.name, s.pilotId ?? ''].some((t) => t.toLowerCase().includes(q))),
  );
}

/** Distinct tags of the scenarios, sorted. */
export function distinctTags(scenarios: readonly QaScenario[]): string[] {
  return [...new Set(scenarios.flatMap((s) => s.tags ?? []))].sort((a, b) => a.localeCompare(b));
}

/** Distinct pilot ids of the scenarios, sorted naturally (A2 before A10). */
export function distinctPilotIds(scenarios: readonly QaScenario[]): string[] {
  const ids = scenarios.map((s) => s.pilotId).filter((id): id is string => !!id);
  return [...new Set(ids)].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
}

/** Documents newest first (a missing or unreadable date goes last). */
export function sortDocuments(documents: readonly QaDocumentSummary[]): QaDocumentSummary[] {
  const time = (d: QaDocumentSummary): number => {
    const t = Date.parse(d.updatedAt);
    return Number.isFinite(t) ? t : Number.NEGATIVE_INFINITY;
  };
  return [...documents].sort((a, b) => {
    const ta = time(a);
    const tb = time(b);
    return ta === tb ? a.name.localeCompare(b.name) : tb > ta ? 1 : -1;
  });
}

/** A file size as a number and a unit ('B' | 'KB' | 'MB'); the unit is a symbol, not a sentence. */
export function sizeOf(bytes: number | null | undefined): { value: number; unit: 'B' | 'KB' | 'MB' } {
  const n = typeof bytes === 'number' && Number.isFinite(bytes) && bytes > 0 ? bytes : 0;
  if (n < 1024) return { value: n, unit: 'B' };
  if (n < 1024 * 1024) return { value: Math.round((n / 1024) * 10) / 10, unit: 'KB' };
  return { value: Math.round((n / (1024 * 1024)) * 10) / 10, unit: 'MB' };
}

/** Only http(s) addresses are linked; anything else (javascript:, data:, relative) is not a link. */
export function safeHttpUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.toString() : null;
  } catch {
    return null;
  }
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/**
 * One markdown parser for the documents tab. Raw HTML inside the markdown is shown as text, and every link opens in a
 * new tab with rel=noopener. The result still goes only through Angular's [innerHTML] sanitizer.
 */
const markdownParser = new Marked({
  gfm: true,
  renderer: {
    html({ text }: Tokens.HTML | Tokens.Tag): string {
      return escapeHtml(text);
    },
    link(this: { parser: { parseInline(tokens: Tokens.Link['tokens']): string } }, { href, title, tokens }: Tokens.Link): string {
      const label = this.parser.parseInline(tokens);
      const target = safeHttpUrl(href);
      if (!target) return label;
      const titleAttr = title ? ` title="${escapeHtml(title)}"` : '';
      return `<a href="${escapeHtml(target)}"${titleAttr} target="_blank" rel="${QA_LINK_REL}">${label}</a>`;
    },
  },
});

/** Renders markdown to an HTML string (to be bound with [innerHTML] so Angular sanitizes it). */
export function renderMarkdown(markdown: string | null | undefined): string {
  if (!markdown) return '';
  return markdownParser.parse(markdown, { async: false });
}
