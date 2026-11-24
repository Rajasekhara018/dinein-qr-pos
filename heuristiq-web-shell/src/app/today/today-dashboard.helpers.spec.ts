import { describe, expect, it } from 'vitest';
import { MetricView, SectionView, TodaySource } from './today.models';
import { EMPTY_TODAY_DASHBOARD, buildTodayDashboard } from './today-dashboard.helpers';

const label = (key: string): string => `t:${key}`;

const metric = (key: string, value: number | null, tone: MetricView['tone'] = 'pending', link?: string): MetricView => ({
  key, icon: `bi-${key}`, labelKey: `m.${key}`, text: value === null ? null : String(value), value, tone,
  highlighted: tone !== 'plain' && value !== null && value > 0, link,
});

const section = (id: TodaySource, state: SectionView['state'], metrics: MetricView[]): SectionView => ({
  id, titleKey: `s.${id}`, icon: 'bi-x', link: `/${id}`, state, attention: metrics.filter((m) => m.highlighted).length, metrics,
});

describe('buildTodayDashboard', () => {
  it('draws nothing until a card has answered, and never from a loading or failed card', () => {
    expect(buildTodayDashboard([], label)).toBe(EMPTY_TODAY_DASHBOARD);
    expect(buildTodayDashboard([section('waiting', 'loading', [metric('a', 3)]), section('train', 'error', [metric('b', 4)])], label)).toBe(EMPTY_TODAY_DASHBOARD);
  });

  it('takes the lead figure of each answered card as a headline figure, linked to its screen, with its card as the caption', () => {
    const dashboard = buildTodayDashboard([
      section('waiting', 'ready', [metric('mine', 4, 'pending', '/workflow')]),
      section('wallet', 'ready', [metric('float', 120, 'plain')]),
    ], label);

    expect(dashboard.kpis).toEqual([
      { key: 'waiting.mine', label: 't:m.mine', value: '4', icon: 'bi-mine', tone: 'pending', link: '/workflow', caption: 't:s.waiting' },
      { key: 'wallet.float', label: 't:m.float', value: '120', icon: 'bi-float', tone: 'plain', link: '/wallet', caption: 't:s.wallet' },
    ]);
  });

  it('skips a figure the source did not supply, uses the next one, and shows at most five headline figures', () => {
    const many = (['a', 'b', 'c', 'd', 'e', 'f'] as unknown as TodaySource[]).map((id) => section(id, 'ready', [metric('x', 1, 'plain')]));
    expect(buildTodayDashboard(many, label).kpis).toHaveLength(5);

    const dashboard = buildTodayDashboard([section('waiting', 'ready', [metric('missing', null), metric('second', 7, 'plain')])], label);
    expect(dashboard.kpis[0].key).toBe('waiting.second');
  });

  it('splits the open work by area in a donut with the total in its centre, leaving out areas with nothing waiting', () => {
    const dashboard = buildTodayDashboard([
      section('waiting', 'ready', [metric('mine', 4), metric('overdue', 2, 'bad')]),
      section('train', 'ready', [metric('queue', 0)]),
      section('wallet', 'ready', [metric('pending', 5), metric('float', 900, 'plain')]),
    ], label);

    expect(dashboard.byArea).toMatchObject({ type: 'donut', series: [6, 5], labels: ['t:s.waiting', 't:s.wallet'], centerValue: '11' });
  });

  it('ranks the biggest queues, largest first, at most six', () => {
    const metrics = [1, 9, 3, 7, 5, 2, 8].map((v, i) => metric(`q${i}`, v));
    const dashboard = buildTodayDashboard([section('waiting', 'ready', metrics)], label);

    expect(dashboard.biggestQueues).toMatchObject({ type: 'bar', horizontal: true });
    expect((dashboard.biggestQueues?.series as { data: number[] }[])[0].data).toEqual([9, 8, 7, 5, 3, 2]);
    expect(dashboard.biggestQueues?.labels?.[0]).toBe('t:m.q1');
  });

  it('shows the share of areas with nothing waiting as a gauge', () => {
    const dashboard = buildTodayDashboard([
      section('waiting', 'ready', [metric('mine', 4)]),
      section('train', 'ready', [metric('queue', 0)]),
      section('wallet', 'ready', [metric('pending', 0)]),
      section('operations', 'ready', [metric('open', 0)]),
    ], label);

    expect(dashboard.clearCount).toBe(3);
    expect(dashboard.areaCount).toBe(4);
    expect(dashboard.clearShare).toMatchObject({ type: 'radialBar', series: [75], format: 'percent' });
  });

  it('has no donut or ranking when everything is clear, but still the gauge at one hundred percent', () => {
    const dashboard = buildTodayDashboard([section('waiting', 'ready', [metric('mine', 0)])], label);

    expect(dashboard.byArea).toBeNull();
    expect(dashboard.biggestQueues).toBeNull();
    expect(dashboard.clearShare?.series).toEqual([100]);
  });
});
