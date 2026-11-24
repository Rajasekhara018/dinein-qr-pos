import { HqChartSpec } from 'hq-shared-lib';
import { MetricView, SectionView } from './today.models';
import { TODAY_DASHBOARD_BARS, TODAY_DASHBOARD_I18N, TODAY_DASHBOARD_KPIS } from './today-dashboard.constants';

/** A headline figure of the dashboard row: the lead figure of one card, ready to draw. */
export interface DashboardKpi {
  key: string;
  label: string;
  /** The figure as text, already formatted. */
  value: string;
  icon: string;
  tone: 'plain' | 'pending' | 'bad';
  link: string;
  /** The card it comes from. */
  caption: string;
}

/** The charts and headline figures drawn above the cards; a chart with nothing to show is null and is left out. */
export interface TodayDashboard {
  kpis: DashboardKpi[];
  /** Open work (queues and failures) split by application area. */
  byArea: HqChartSpec | null;
  /** The biggest single queues, as a ranking. */
  biggestQueues: HqChartSpec | null;
  /** The share of areas with nothing waiting, as a gauge. */
  clearShare: HqChartSpec | null;
  clearCount: number;
  areaCount: number;
}

export const EMPTY_TODAY_DASHBOARD: TodayDashboard = { kpis: [], byArea: null, biggestQueues: null, clearShare: null, clearCount: 0, areaCount: 0 };

/** A figure that is a queue or a failure and is not zero: what the user has to act on. */
function isOpenWork(metric: MetricView): boolean {
  return metric.highlighted && metric.value !== null && metric.value > 0;
}

/**
 * Builds the dashboard from the cards that have answered (a loading or failed card contributes nothing, so a chart never shows
 * a half-loaded picture). {@code label} turns a translation key into the text of the active language.
 */
export function buildTodayDashboard(sections: readonly SectionView[], label: (key: string) => string): TodayDashboard {
  const ready = sections.filter((section) => section.state === 'ready');
  if (ready.length === 0) {
    return EMPTY_TODAY_DASHBOARD;
  }

  const kpis: DashboardKpi[] = [];
  for (const section of ready) {
    const lead = section.metrics.find((metric) => metric.value !== null && metric.text !== null);
    if (lead && kpis.length < TODAY_DASHBOARD_KPIS) {
      kpis.push({
        key: `${section.id}.${lead.key}`, label: label(lead.labelKey), value: lead.text as string, icon: lead.icon,
        tone: lead.highlighted ? lead.tone : 'plain', link: lead.link ?? section.link, caption: label(section.titleKey),
      });
    }
  }

  const perArea = ready
    .map((section) => ({ title: label(section.titleKey), open: section.metrics.filter(isOpenWork).reduce((sum, m) => sum + (m.value as number), 0) }))
    .filter((area) => area.open > 0);
  const totalOpen = perArea.reduce((sum, area) => sum + area.open, 0);
  const byArea: HqChartSpec | null = perArea.length === 0 ? null : {
    type: 'donut', series: perArea.map((area) => area.open), labels: perArea.map((area) => area.title),
    centerLabel: label(`${TODAY_DASHBOARD_I18N}.openItems`), centerValue: String(totalOpen), height: 260,
  };

  const queues = ready.flatMap((section) => section.metrics.filter(isOpenWork).map((metric) => ({ name: label(metric.labelKey), value: metric.value as number })))
    .sort((a, b) => b.value - a.value)
    .slice(0, TODAY_DASHBOARD_BARS);
  const biggestQueues: HqChartSpec | null = queues.length === 0 ? null : {
    type: 'bar', horizontal: true, series: [{ name: label(`${TODAY_DASHBOARD_I18N}.items`), data: queues.map((q) => q.value) }],
    labels: queues.map((q) => q.name), height: Math.max(180, queues.length * 44 + 40),
  };

  const clearCount = ready.filter((section) => section.attention === 0).length;
  const clearShare: HqChartSpec = {
    type: 'radialBar', series: [Math.round((clearCount / ready.length) * 100)], labels: [label(`${TODAY_DASHBOARD_I18N}.areasClear`)],
    format: 'percent', height: 260,
  };

  return { kpis, byArea, biggestQueues, clearShare, clearCount, areaCount: ready.length };
}
