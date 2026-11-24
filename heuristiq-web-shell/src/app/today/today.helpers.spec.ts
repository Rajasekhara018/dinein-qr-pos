import { describe, expect, it } from 'vitest';
import { TODAY_SECTIONS } from './today.constants';
import {
  agentDeskFigures, attentionItemsOf, earningsFigures, formatMetric, isHighlighted, readNumber, readString, toMetricView,
} from './today.helpers';
import { EarningsAccount } from '../access-management/franchises/franchise.models';
import { MetricSpec, MetricView, SectionView } from './today.models';

describe('today.helpers', () => {
  it('reads a number at a dotted path, including numbers sent as text', () => {
    expect(readNumber({ metrics: { bookingsToday: 12 } }, 'metrics.bookingsToday')).toBe(12);
    expect(readNumber({ totalLiability: '1500.50' }, 'totalLiability')).toBe(1500.5);
  });

  it('reads a money figure sent as { code, value } as its value', () => {
    expect(readNumber({ today: { revenue: { code: 'INR', value: 2500.75 } } }, 'today.revenue')).toBe(2500.75);
    expect(readNumber({ totalLiability: { code: 'INR', value: null } }, 'totalLiability')).toBeNull();
    expect(readNumber({ metrics: { other: 1 } }, 'metrics')).toBeNull();
  });

  it('returns null (not zero) for a missing, blank or non-numeric value', () => {
    expect(readNumber({}, 'a.b')).toBeNull();
    expect(readNumber({ a: '' }, 'a')).toBeNull();
    expect(readNumber({ a: 'abc' }, 'a')).toBeNull();
    expect(readNumber(null, 'a')).toBeNull();
    expect(readNumber({ a: null }, 'a')).toBeNull();
  });

  it('formats counts, rupees and percentages', () => {
    expect(formatMetric(1234567, 'count')).toBe('12,34,567');
    expect(formatMetric(4500, 'currency')).toContain('4,500');
    expect(formatMetric(98.46, 'percent')).toBe('98.5%');
  });

  it('highlights only a non-zero queue or failure, never a plain figure', () => {
    expect(isHighlighted(3, 'pending')).toBe(true);
    expect(isHighlighted(0, 'bad')).toBe(false);
    expect(isHighlighted(null, 'pending')).toBe(false);
    expect(isHighlighted(9, 'plain')).toBe(false);
  });

  it('builds a figure with a dash-ready null text when the source lacks the value', () => {
    const spec = TODAY_SECTIONS.find((s) => s.id === 'matrimony')!.metrics[0];
    expect(toMetricView(spec, { pendingReviewProfiles: 4 }).text).toBe('4');
    expect(toMetricView(spec, {}).text).toBeNull();
  });

  it('gives every figure of every card a unique key and a label key under today.metrics', () => {
    for (const section of TODAY_SECTIONS) {
      const keys = section.metrics.map((m) => m.key);
      expect(new Set(keys).size, section.id).toBe(keys.length);
      for (const metric of section.metrics) expect(metric.labelKey.startsWith('today.metrics.'), metric.key).toBe(true);
    }
  });

  describe('attentionItemsOf', () => {
    const metric = (key: string, highlighted: boolean, link?: string): MetricView =>
      ({ key, icon: 'bi-x', labelKey: key, text: '3', value: 3, tone: 'pending', highlighted, link });
    const section = (id: SectionView['id'], state: SectionView['state'], metrics: MetricView[]): SectionView =>
      ({ id, titleKey: id, icon: 'bi-x', link: `/${id}`, state, attention: 0, metrics });

    it('lifts the highlighted figures of ready cards, in card order, each leading to its own screen or its card', () => {
      const items = attentionItemsOf([
        section('waiting', 'ready', [metric('a', true, '/own'), metric('b', false)]),
        section('wallet', 'ready', [metric('c', true)]),
      ]);

      expect(items.map((i) => [i.metric.key, i.link, i.sectionId])).toEqual([['a', '/own', 'waiting'], ['c', '/wallet', 'wallet']]);
    });

    it('ignores cards that are loading or failed', () => {
      expect(attentionItemsOf([section('waiting', 'loading', [metric('a', true)]), section('train', 'error', [metric('b', true)])])).toEqual([]);
    });
  });

  describe('links and strings', () => {
    it('readString reads a non-empty string at a dotted path only', () => {
      expect(readString({ links: { a: '/x' } }, 'links.a')).toBe('/x');
      expect(readString({ links: { a: ' ' } }, 'links.a')).toBeNull();
      expect(readString({ links: { a: 3 } }, 'links.a')).toBeNull();
      expect(readString({ links: null }, 'links.a')).toBeNull();
      expect(readString(null, 'a')).toBeNull();
    });

    it('a figure with a linkPath leads where the response says, else to its own link', () => {
      const queue: MetricSpec = { key: 'q', icon: 'bi-x', labelKey: 'today.metrics.q', path: 'queue', format: 'count', tone: 'pending', linkPath: 'links.queue' };
      expect(toMetricView(queue, { queue: 2, links: { queue: '/somewhere' } }).link).toBe('/somewhere');
      expect(toMetricView(queue, { queue: 2 }).link).toBeUndefined();
      expect(toMetricView(queue, { queue: 2 }).highlighted).toBe(true);
    });

    it('no card of the screen is the removed Application setup source', () => {
      expect(TODAY_SECTIONS.map((section) => section.id)).not.toContain('readiness');
    });
  });
});

describe('today.helpers: the agent desk and earnings figures', () => {
  it('reads the agent desk counts and counts the meetings of the week', () => {
    expect(agentDeskFigures({ customers: 12, openLeads: 3, overdueRequests: 1, successes: 2, meetingsThisWeek: [{}, {}, {}] })).toEqual({
      customers: 12, openLeads: 3, overdueRequests: 1, successes: 2, meetingsThisWeek: 3,
    });
  });

  it('leaves a figure the answer does not carry empty, so the tile shows a dash and not a wrong zero', () => {
    expect(agentDeskFigures(null)).toEqual({ customers: null, openLeads: null, overdueRequests: null, successes: null, meetingsThisWeek: null });
    expect(agentDeskFigures({ customers: 0 })).toMatchObject({ customers: 0, meetingsThisWeek: null });
  });

  it('adds the earnings up over every earnings account (an owner may have one per franchise)', () => {
    const account = (available: number | null, pending: number | null, settled: number | null) => ({
      ownerType: 'AGENT', ownerId: 'u1', franchiseCode: 'RBM-1',
      available: available === null ? null : { code: 'INR', value: available },
      pending: pending === null ? null : { code: 'INR', value: pending },
      settled: settled === null ? null : { code: 'INR', value: settled },
      reserved: null,
    }) as unknown as EarningsAccount;

    expect(earningsFigures({ accounts: [account(100, 20, 500), account(50, null, 250)] })).toEqual({ available: 150, pending: 20, settled: 750 });
    expect(earningsFigures({ accounts: [] })).toEqual({ available: null, pending: null, settled: null });
    expect(earningsFigures(null)).toEqual({ available: null, pending: null, settled: null });
  });
});
