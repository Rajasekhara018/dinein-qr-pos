import { describe, expect, it } from 'vitest';
import { buildInsightCharts, chartCurrency, chartValue, humanise, insightsRange, shortDate } from './today-insights.helpers';
import { HqAmount } from 'hq-shared-lib';
import { TodayInsights } from './today-insights.models';

const inr = (value: number): HqAmount => ({ code: 'INR', value });

const label = (key: string): string => key; // no translations: the key comes back

const full: TodayInsights = {
  timeSeries: {
    buckets: ['2026-10-01T00:00:00Z', '2026-10-02T00:00:00Z'],
    series: [
      { key: 'REGISTRATIONS', unit: 'COUNT', values: [4, 7] },
      { key: 'REVENUE', unit: 'MONEY', values: [100, 200] },
      { key: 'ACTIVATIONS', unit: 'COUNT', values: [0, 0] },
      { key: 'PROFILES_APPROVED', unit: 'COUNT', values: [1, 2] },
    ],
  },
  funnel: { stages: [{ key: 'reg', label: 'Registered', count: 120 }, { key: 'paid', label: 'Paid', count: 30 }] },
  revenue: { netCollected: inr(5000), byProductType: [{ key: 'SUBSCRIPTION', net: inr(4000) }, { key: 'BOOST', net: inr(1000) }, { key: 'EMPTY', net: inr(0) }] },
  demographics: { byGender: [{ key: 'MALE', total: 60 }, { key: 'FEMALE', total: 40 }, { key: 'OTHER', total: 0 }], byAgeBand: [] },
  trainTrend: { bookingTrend: [{ day: 'Mon', bookings: 5, revenue: inr(500) }, { day: 'Tue', bookings: 0, revenue: inr(0) }] },
  walletTrend: { days: ['2026-10-01', '2026-10-02'], series: [{ journalType: 'PURCHASE', amounts: [inr(100), inr(250)], counts: [2, 3] }, { journalType: 'HOLD', amounts: [inr(0), inr(0)], counts: [0, 0] }] },
  ridesTrend: [
    { day: '2026-10-01', requested: 10, completed: 7, cancelled: 2, revenue: inr(1250.5) },
    { day: '2026-10-02', requested: 0, completed: 0, cancelled: 0, revenue: inr(0) },
  ],
};

describe('humanise and shortDate', () => {
  it('writes a series key for people', () => {
    expect(humanise('PROFILES_CREATED')).toBe('Profiles created');
    expect(humanise('mother-tongue')).toBe('Mother tongue');
  });

  it('writes a date for an axis, and gives an unreadable one back unchanged', () => {
    expect(shortDate('2026-10-07T12:00:00Z', 'en-IN')).toMatch(/7 Oct|Oct 7/);
    expect(shortDate('not a date', 'en-IN')).toBe('not a date');
  });
});

describe('buildInsightCharts', () => {
  it('draws the activity as an area chart of the count series that have data, on the shared date axis', () => {
    const { activity } = buildInsightCharts(full, label, 'en-IN');

    expect(activity).toMatchObject({ type: 'area', height: 300 });
    expect((activity?.series as { name: string; data: number[] }[]).map((s) => s.name)).toEqual(['Registrations', 'Profiles approved']);
    expect(activity?.labels).toHaveLength(2);
  });

  it('shows who is on the platform as a donut without the empty slices', () => {
    const { audience } = buildInsightCharts(full, label, 'en-IN');

    expect(audience).toMatchObject({ type: 'donut', series: [60, 40], labels: ['Male', 'Female'] });
  });

  it('shows net revenue by product as bars in the revenue currency, leaving out products with none', () => {
    const { revenue } = buildInsightCharts(full, label, 'en-IN');

    expect(revenue).toMatchObject({ type: 'bar', format: 'currency', currency: 'INR', labels: ['Subscription', 'Boost'] });
    expect((revenue?.series as { data: number[] }[])[0].data).toEqual([4000, 1000]);
  });

  it('shows the funnel as a ranking of its stages', () => {
    const { funnel } = buildInsightCharts(full, label, 'en-IN');

    expect(funnel).toMatchObject({ type: 'bar', horizontal: true, labels: ['Registered', 'Paid'] });
  });

  it('draws the train trend as bookings per day and their value as an area in rupees', () => {
    const { trainBookings, trainRevenue } = buildInsightCharts(full, label, 'en-IN');

    expect(trainBookings).toMatchObject({ type: 'bar', labels: ['Mon', 'Tue'] });
    expect((trainBookings?.series as { data: number[] }[])[0].data).toEqual([5, 0]);
    expect(trainRevenue).toMatchObject({ type: 'area', format: 'currency', currency: 'INR' });
    expect((trainRevenue?.series as { data: number[] }[])[0].data).toEqual([500, 0]);
  });

  it('leaves the train charts out when there are no bookings in the period', () => {
    const quiet = buildInsightCharts({ ...full, trainTrend: { bookingTrend: [{ day: 'Mon', bookings: 0, revenue: inr(0) }] } }, label, 'en-IN');

    expect(quiet.trainBookings).toBeNull();
    expect(quiet.trainRevenue).toBeNull();
  });

  it('draws the wallet activity as an area chart of the journal types that moved money, in rupees', () => {
    const { walletActivity } = buildInsightCharts(full, label, 'en-IN');

    expect(walletActivity).toMatchObject({ type: 'area', format: 'currency', currency: 'INR' });
    expect((walletActivity?.series as { name: string; data: number[] }[]).map((s) => s.name)).toEqual(['Purchase']);
    expect((walletActivity?.series as { data: number[] }[])[0].data).toEqual([100, 250]);
    expect(walletActivity?.labels).toHaveLength(2);
  });

  it('leaves the wallet chart out when nothing moved', () => {
    expect(buildInsightCharts({ ...full, walletTrend: { days: ['2026-10-01'], series: [{ journalType: 'HOLD', amounts: [inr(0)], counts: [0] }] } }, label, 'en-IN').walletActivity).toBeNull();
  });

  it('draws requested, completed and cancelled rides per day on one date axis', () => {
    const { ridesTrend } = buildInsightCharts(full, label, 'en-IN');

    expect(ridesTrend?.type).toBe('area');
    expect(ridesTrend?.labels).toHaveLength(2);
    expect((ridesTrend?.series as { name: string; data: number[] }[]).map((s) => s.name)).toEqual([
      'today.insights.ridesRequested', 'today.insights.ridesCompleted', 'today.insights.ridesCancelled',
    ]);
    expect((ridesTrend?.series as { data: number[] }[]).map((s) => s.data)).toEqual([[10, 0], [7, 0], [2, 0]]);
  });

  it('draws the ride revenue in rupees, reading a revenue that arrives as text as a number', () => {
    const asText = { ...full, ridesTrend: [{ day: '2026-10-01', requested: 1, completed: 1, cancelled: 0, revenue: '99.50' as unknown as HqAmount }] };
    const { ridesRevenue } = buildInsightCharts(asText, label, 'en-IN');

    expect(ridesRevenue?.format).toBe('currency');
    expect(ridesRevenue?.currency).toBe('INR');
    expect((ridesRevenue?.series as { name: string; data: number[] }[])[0]).toEqual({ name: 'today.insights.ridesRevenueSeries', data: [99.5] });
  });

  it('leaves the ride charts out when no ride was requested and none earned anything', () => {
    const quiet = buildInsightCharts({ ...full, ridesTrend: [{ day: '2026-10-01', requested: 0, completed: 0, cancelled: 0, revenue: inr(0) }] }, label, 'en-IN');

    expect(quiet.ridesTrend).toBeNull();
    expect(quiet.ridesRevenue).toBeNull();
  });

  it('keeps the revenue chart when rides earned money but none is counted as requested', () => {
    const odd = buildInsightCharts({ ...full, ridesTrend: [{ day: '2026-10-01', requested: 0, completed: 0, cancelled: 0, revenue: inr(10) }] }, label, 'en-IN');

    expect(odd.ridesTrend).toBeNull();
    expect(odd.ridesRevenue).not.toBeNull();
  });

  it('uses a translation for a series, gender or product when there is one', () => {
    const translated = (key: string): string => (key === 'today.insights.gender.MALE' ? 'Men' : key);
    expect(buildInsightCharts(full, translated, 'en-IN').audience?.labels?.[0]).toBe('Men');
  });

  it('leaves a chart out when its data did not arrive or is all zero', () => {
    const none = buildInsightCharts({ timeSeries: null, funnel: null, revenue: null, demographics: null, trainTrend: null, walletTrend: null, ridesTrend: null }, label, 'en-IN');
    expect(none).toEqual({ activity: null, audience: null, revenue: null, funnel: null, trainBookings: null, trainRevenue: null, walletActivity: null, ridesTrend: null, ridesRevenue: null });

    const zeros = buildInsightCharts({ ...full, funnel: { stages: [{ key: 'a', label: 'A', count: 0 }] }, timeSeries: { buckets: ['2026-10-01T00:00:00Z'], series: [{ key: 'X', unit: 'COUNT', values: [0] }] } }, label, 'en-IN');
    expect(zeros.funnel).toBeNull();
    expect(zeros.activity).toBeNull();
  });
});

describe('insightsRange', () => {
  it('spans whole UTC days from the first day to the start of tomorrow', () => {
    const range = insightsRange(new Date('2026-11-05T15:42:10.123Z'), 30);

    expect(range).toEqual({ from: '2026-10-07T00:00:00.000Z', to: '2026-11-06T00:00:00.000Z' });
  });

  it('is the same for any time of the same UTC day, and moves at UTC midnight', () => {
    expect(insightsRange(new Date('2026-11-05T00:00:00.000Z'), 3)).toEqual(insightsRange(new Date('2026-11-05T23:59:59.999Z'), 3));
    expect(insightsRange(new Date('2026-11-06T00:00:00.000Z'), 3).to).toBe('2026-11-07T00:00:00.000Z');
  });

  it('counts today as one of the days', () => {
    expect(insightsRange(new Date('2026-11-05T10:00:00Z'), 1)).toEqual({ from: '2026-11-05T00:00:00.000Z', to: '2026-11-06T00:00:00.000Z' });
  });
});

describe('chartValue and chartCurrency', () => {
  it('reads an amount object, an older bare number and numeric text, and 0 when there is none', () => {
    expect(chartValue({ code: 'USD', value: 12.5 })).toBe(12.5);
    expect(chartValue(40)).toBe(40);
    expect(chartValue('7.25')).toBe(7.25);
    expect(chartValue(null)).toBe(0);
  });

  it('takes the currency of the first amount that names one, then the legacy currency, then the default', () => {
    expect(chartCurrency([0, { code: 'aed', value: 1 }])).toBe('AED');
    expect(chartCurrency([5, 6], 'usd')).toBe('USD');
    expect(chartCurrency([5])).toBe('INR');
  });

  it('draws an older bare-number revenue answer in its legacy currency', () => {
    const legacy = { ...full, revenue: { currency: 'USD', netCollected: 10 as unknown as HqAmount, byProductType: [{ key: 'BOOST', net: 10 as unknown as HqAmount }] } };
    const { revenue } = buildInsightCharts(legacy, label, 'en-IN');

    expect(revenue?.currency).toBe('USD');
    expect((revenue?.series as { data: number[] }[])[0].data).toEqual([10]);
  });
});
