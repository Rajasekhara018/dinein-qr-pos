import { HqAmount, hqAmountValue } from 'hq-shared-lib';
import { TODAY_CURRENCY, TODAY_LOCALE } from './today.constants';
import { EarningsAccount, MyEarnings } from '../access-management/franchises/franchise.models';
import { AttentionItem, MetricFormat, MetricSpec, MetricTone, MetricView, SectionView, SourceData, TodayAgentDesk } from './today.models';

/**
 * Reads the number at a dotted path (`metrics.bookingsToday`). A `{ code, value }` amount counts as its value, and numeric
 * strings count (older wallet answers send money as text); anything else, or a missing step, is null so the tile shows a dash instead of a wrong zero.
 */
export function readNumber(data: SourceData | null | undefined, path: string): number | null {
  let current: unknown = data;
  for (const step of path.split('.')) {
    if (current === null || typeof current !== 'object') return null;
    current = (current as Record<string, unknown>)[step];
  }
  if (typeof current === 'number' || typeof current === 'string') return hqAmountValue(current);
  // A money figure is `{ code, value }` (money stage 3); its value is the figure.
  if (current !== null && typeof current === 'object' && 'value' in current) return hqAmountValue(current as HqAmount);
  return null;
}

/** Reads a non-empty string at a dotted path; null otherwise. */
export function readString(data: SourceData | null | undefined, path: string): string | null {
  let current: unknown = data;
  for (const step of path.split('.')) {
    if (current === null || typeof current !== 'object') return null;
    current = (current as Record<string, unknown>)[step];
  }
  return typeof current === 'string' && current.trim() !== '' ? current : null;
}

/** A number as text for its kind: grouped count, rupees without decimals (compact above a lakh), or a percentage. */
export function formatMetric(value: number, format: MetricFormat): string {
  switch (format) {
    case 'currency':
      return new Intl.NumberFormat(TODAY_LOCALE, {
        style: 'currency',
        currency: TODAY_CURRENCY,
        maximumFractionDigits: 0,
        notation: Math.abs(value) >= 100_000 ? 'compact' : 'standard',
      }).format(value);
    case 'percent':
      return `${new Intl.NumberFormat(TODAY_LOCALE, { maximumFractionDigits: 1 }).format(value)}%`;
    default:
      return new Intl.NumberFormat(TODAY_LOCALE, { maximumFractionDigits: 0 }).format(value);
  }
}

/** A figure's tone shows only when there is something to look at (a queue of zero is calm). */
export function isHighlighted(value: number | null, tone: MetricTone): boolean {
  return tone !== 'plain' && value !== null && value > 0;
}

/** Builds the renderable figure for one spec from a source's response. */
export function toMetricView(spec: MetricSpec, data: SourceData | null): MetricView {
  const value = readNumber(data, spec.path);
  return {
    key: spec.key,
    icon: spec.icon,
    labelKey: spec.labelKey,
    text: value === null ? null : formatMetric(value, spec.format),
    value,
    tone: spec.tone,
    highlighted: isHighlighted(value, spec.tone),
    link: (spec.linkPath ? readString(data, spec.linkPath) : null) ?? spec.link,
  };
}

/** The highlighted figures of the cards that have answered, in card order: the "needs attention" strip. A failed or loading card contributes none. */
export function attentionItemsOf(sections: readonly SectionView[]): AttentionItem[] {
  return sections
    .filter((section) => section.state === 'ready')
    .flatMap((section) =>
      section.metrics
        .filter((metric) => metric.highlighted)
        .map((metric) => ({ sectionId: section.id, sectionTitleKey: section.titleKey, metric, link: metric.link ?? section.link })),
    );
}

/** The agent desk card's figures: the dashboard's counts, with this week's meetings counted (the answer lists them). */
export function agentDeskFigures(work: TodayAgentDesk | null | undefined): SourceData {
  return {
    customers: work?.customers ?? null,
    openLeads: work?.openLeads ?? null,
    overdueRequests: work?.overdueRequests ?? null,
    successes: work?.successes ?? null,
    meetingsThisWeek: Array.isArray(work?.meetingsThisWeek) ? work!.meetingsThisWeek!.length : null,
  };
}

/**
 * The earnings card's figures: available, pending and settled, each added up over the person's earnings accounts (an owner may
 * have one per franchise). A figure no account reports stays null, so the tile shows a dash instead of a wrong zero.
 */
export function earningsFigures(earnings: MyEarnings | null | undefined): SourceData {
  const sum = (pick: (account: EarningsAccount) => HqAmount | null): number | null => {
    const values = (earnings?.accounts ?? []).map((account) => hqAmountValue(pick(account))).filter((v): v is number => v !== null);
    return values.length ? values.reduce((total, value) => total + value, 0) : null;
  };
  return { available: sum((a) => a.available), pending: sum((a) => a.pending), settled: sum((a) => a.settled) };
}
