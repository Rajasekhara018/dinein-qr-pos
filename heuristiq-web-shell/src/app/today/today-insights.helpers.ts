import { HqAmountLike, HqChartSpec, hqAmountCode, hqAmountValue } from 'hq-shared-lib';
import { TODAY_INSIGHTS_CURRENCY, TODAY_INSIGHTS_HEIGHTS, TODAY_INSIGHTS_I18N, TODAY_INSIGHTS_LIMITS } from './today-insights.constants';
import { TodayInsights } from './today-insights.models';

/** The charts the analytics make; a chart whose data did not arrive (or is empty) is null and the screen leaves it out. */
export interface TodayInsightCharts {
  activity: HqChartSpec | null;
  audience: HqChartSpec | null;
  revenue: HqChartSpec | null;
  funnel: HqChartSpec | null;
  /** Train bookings per day, and what they were worth. */
  trainBookings: HqChartSpec | null;
  trainRevenue: HqChartSpec | null;
  /** Wallet ledger activity: the value of the journals posted each day, per type. */
  walletActivity: HqChartSpec | null;
  /** Rides requested, completed and cancelled per day, and the fare of the completed ones. */
  ridesTrend: HqChartSpec | null;
  ridesRevenue: HqChartSpec | null;
}

export const EMPTY_INSIGHT_CHARTS: TodayInsightCharts = { activity: null, audience: null, revenue: null, funnel: null, trainBookings: null, trainRevenue: null, walletActivity: null, ridesTrend: null, ridesRevenue: null };

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * The window of the matrimony charts: whole UTC days, from `days` days ago (counting today) to the start of tomorrow. Both ends sit
 * on UTC midnight, which is what lets the server answer from its stored daily numbers instead of recomputing the range.
 */
export function insightsRange(now: Date, days: number): { from: string; to: string } {
  const todayStart = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  return { from: new Date(todayStart - (days - 1) * DAY_MS).toISOString(), to: new Date(todayStart + DAY_MS).toISOString() };
}

/** "2026-10-07T00:00:00Z" as "7 Oct" for an axis. */
export function shortDate(iso: string, locale: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short' }).format(date);
}

/** A series key ("PROFILES_CREATED") written for people when no translation is given: "Profiles created". */
export function humanise(key: string): string {
  const text = key.replace(/[_-]+/g, ' ').trim().toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function hasData(values: readonly number[]): boolean {
  return values.some((v) => v > 0);
}

/** The number of an amount for a chart: `{ code, value }`, or an older bare number / numeric text; 0 when there is none. */
export function chartValue(amount: HqAmountLike): number {
  return hqAmountValue(amount) ?? 0;
}

/** The currency of a chart: the first amount that names one, else `fallback`, else the default. */
export function chartCurrency(amounts: readonly HqAmountLike[], fallback?: string | null): string {
  for (const amount of amounts) {
    const code = hqAmountCode(amount);
    if (code) { return code; }
  }
  return hqAmountCode(null, fallback) || TODAY_INSIGHTS_CURRENCY;
}

/**
 * Builds the charts from what the analytics returned. {@code label} turns a translation key into text and returns the key itself
 * when there is no translation, which {@link humanise} then makes readable.
 */
export function buildInsightCharts(insights: TodayInsights, label: (key: string) => string, locale: string): TodayInsightCharts {
  const named = (group: string, key: string): string => {
    const text = label(`${TODAY_INSIGHTS_I18N}.${group}.${key}`);
    return text.startsWith(TODAY_INSIGHTS_I18N) ? humanise(key) : text;
  };

  const ts = insights.timeSeries;
  const counts = (ts?.series ?? []).filter((s) => s.unit === 'COUNT' && hasData(s.values)).slice(0, TODAY_INSIGHTS_LIMITS.series);
  const activity: HqChartSpec | null = ts && counts.length > 0 ? {
    type: 'area', height: TODAY_INSIGHTS_HEIGHTS.wide, labels: ts.buckets.map((b) => shortDate(b, locale)),
    series: counts.map((s) => ({ name: named('series', s.key), data: s.values })),
  } : null;

  const gender = (insights.demographics?.byGender ?? []).filter((b) => b.total > 0).slice(0, TODAY_INSIGHTS_LIMITS.slices);
  const audience: HqChartSpec | null = gender.length > 0 ? {
    type: 'donut', height: TODAY_INSIGHTS_HEIGHTS.small, series: gender.map((b) => b.total), labels: gender.map((b) => named('gender', b.key)),
    centerLabel: label(`${TODAY_INSIGHTS_I18N}.members`),
  } : null;

  const products = (insights.revenue?.byProductType ?? []).filter((p) => chartValue(p.net) > 0).slice(0, TODAY_INSIGHTS_LIMITS.products);
  const revenue: HqChartSpec | null = products.length > 0 ? {
    type: 'bar', height: TODAY_INSIGHTS_HEIGHTS.small, format: 'currency', currency: chartCurrency(products.map((p) => p.net), insights.revenue?.currency),
    labels: products.map((p) => named('product', p.key)), series: [{ name: label(`${TODAY_INSIGHTS_I18N}.netRevenue`), data: products.map((p) => chartValue(p.net)) }],
  } : null;

  const stages = (insights.funnel?.stages ?? []).slice(0, TODAY_INSIGHTS_LIMITS.stages);
  const funnel: HqChartSpec | null = stages.some((s) => s.count > 0) ? {
    type: 'bar', horizontal: true, height: TODAY_INSIGHTS_HEIGHTS.small, labels: stages.map((s) => s.label),
    series: [{ name: label(`${TODAY_INSIGHTS_I18N}.people`), data: stages.map((s) => s.count) }],
  } : null;

  const trend = insights.trainTrend?.bookingTrend ?? [];
  const trainBookings: HqChartSpec | null = trend.some((p) => p.bookings > 0) ? {
    type: 'bar', height: TODAY_INSIGHTS_HEIGHTS.small, labels: trend.map((p) => p.day),
    series: [{ name: label(`${TODAY_INSIGHTS_I18N}.bookings`), data: trend.map((p) => p.bookings) }],
  } : null;
  const trainRevenue: HqChartSpec | null = trend.some((p) => chartValue(p.revenue) > 0) ? {
    type: 'area', height: TODAY_INSIGHTS_HEIGHTS.small, format: 'currency', currency: chartCurrency(trend.map((p) => p.revenue)), labels: trend.map((p) => p.day),
    series: [{ name: label(`${TODAY_INSIGHTS_I18N}.netRevenue`), data: trend.map((p) => chartValue(p.revenue)) }],
  } : null;

  const wallet = insights.walletTrend;
  const walletSeries = (wallet?.series ?? []).filter((s) => s.amounts.some((v) => chartValue(v) > 0));
  const walletActivity: HqChartSpec | null = wallet && walletSeries.length > 0 ? {
    type: 'area', height: TODAY_INSIGHTS_HEIGHTS.wide, format: 'currency', currency: chartCurrency(walletSeries.flatMap((s) => s.amounts)), labels: wallet.days.map((d) => shortDate(d, locale)),
    series: walletSeries.map((s) => ({ name: named('journalType', s.journalType), data: s.amounts.map(chartValue) })),
  } : null;

  const rides = insights.ridesTrend ?? [];
  const ridesLabels = rides.map((p) => shortDate(p.day, locale));
  const ridesTrend: HqChartSpec | null = rides.some((p) => p.requested > 0) ? {
    type: 'area', height: TODAY_INSIGHTS_HEIGHTS.small, labels: ridesLabels,
    series: [
      { name: label(`${TODAY_INSIGHTS_I18N}.ridesRequested`), data: rides.map((p) => p.requested) },
      { name: label(`${TODAY_INSIGHTS_I18N}.ridesCompleted`), data: rides.map((p) => p.completed) },
      { name: label(`${TODAY_INSIGHTS_I18N}.ridesCancelled`), data: rides.map((p) => p.cancelled) },
    ],
  } : null;
  const ridesRevenue: HqChartSpec | null = rides.some((p) => chartValue(p.revenue) > 0) ? {
    type: 'area', height: TODAY_INSIGHTS_HEIGHTS.small, format: 'currency', currency: chartCurrency(rides.map((p) => p.revenue)), labels: ridesLabels,
    series: [{ name: label(`${TODAY_INSIGHTS_I18N}.ridesRevenueSeries`), data: rides.map((p) => chartValue(p.revenue)) }],
  } : null;

  return { activity, audience, revenue, funnel, trainBookings, trainRevenue, walletActivity, ridesTrend, ridesRevenue };
}
