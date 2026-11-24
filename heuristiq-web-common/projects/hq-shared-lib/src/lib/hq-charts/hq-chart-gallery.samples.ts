import type { HqCommonTableColumn } from '../hq-ui/hq-common-table/hq-common-table';
import {
  HQ_CHART_AXIS_TYPES, HQ_CHART_PREMIUM_TYPES, HqChartNode, HqChartSeries, HqChartSpec, HqChartType,
} from './hq-chart.options';
import { HqDateRange, HqDateRangeBucket, hqDateRangeBucket, hqDateRangeDays, hqRangeDay, hqRangeIsoDay } from './hq-date-range.helpers';
import { HqWaterfallStep, hqChartNodeRows, hqFiveNumberSummary, hqWaterfallData, hqWaterfallRunning } from './hq-chart.transforms';

/** Which filter pill a gallery chart belongs to. */
export type HqGalleryFamily = 'axis' | 'nonAxis';

/** One card of the chart gallery: the chart, its table view and a copyable usage snippet. */
export interface HqGalleryItem {
  type: HqChartType;
  family: HqGalleryFamily;
  /** An ApexCharts premium type (trial watermark without a licence key). */
  premium: boolean;
  title: string;
  description: string;
  spec: HqChartSpec;
  tableColumns: HqCommonTableColumn<Record<string, unknown>>[];
  tableRows: Record<string, unknown>[];
  snippet: string;
  /** Wide charts span two grid columns. */
  wide?: boolean;
}

type Row = Record<string, unknown>;

/** Deterministic pseudo-random numbers (mulberry32): the same period always gives the same samples. */
export function hqSeededRandom(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** A stable number from a text (the period), to seed the samples. */
export function hqSeedOf(text: string): number {
  let hash = 2166136261;
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

/** The x-axis buckets of a period: one label per day, week or month (hqDateRangeBucket), with the bucket's start date. */
export function hqGalleryBuckets(range: HqDateRange): { label: string; start: Date }[] {
  const from = hqRangeDay(range.from);
  const to = hqRangeDay(range.to);
  if (!from || !to || to < from) {
    return [];
  }
  const bucket: HqDateRangeBucket = hqDateRangeBucket(range);
  const out: { label: string; start: Date }[] = [];
  const fmt = (d: Date): string => bucket === 'month'
    ? d.toLocaleDateString('en-IN', { month: 'short', year: '2-digit' })
    : d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
  let cursor = new Date(from);
  while (cursor <= to && out.length < 400) {
    out.push({ label: fmt(cursor), start: new Date(cursor) });
    cursor = bucket === 'month' ? new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1)
      : new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() + (bucket === 'week' ? 7 : 1));
  }
  return out;
}

const col = (key: string, label: string): HqCommonTableColumn<Row> => ({ key, label });
const round = (v: number, digits = 0): number => Math.round(v * 10 ** digits) / 10 ** digits;

/** A usage snippet: the template tag and the spec, with long data arrays cut short. */
function snippetOf(spec: HqChartSpec): string {
  const trim = (value: unknown, depth: number): unknown => {
    if (Array.isArray(value)) {
      const head = value.slice(0, depth > 1 ? 3 : 4).map((v) => trim(v, depth + 1));
      return value.length > head.length ? [...head, `… ${value.length - head.length} more`] : head;
    }
    if (value && typeof value === 'object') {
      return Object.fromEntries(Object.entries(value).filter(([, v]) => typeof v !== 'function').map(([k, v]) => [k, trim(v, depth + 1)]));
    }
    return value;
  };
  return `<hq-chart [spec]="spec" ariaLabel="…"></hq-chart>\n\nspec: HqChartSpec = ${JSON.stringify(trim(spec, 0), null, 2)};`;
}

/** Normal-ish random value (sum of uniforms), deterministic. */
function normal(next: () => number, mean: number, spread: number): number {
  return mean + ((next() + next() + next() + next() - 2) / 2) * spread;
}

/**
 * Sample data for every chart type the library draws, regenerated from the period: time charts take its days (bucketed by day,
 * week or month), the others scale their counts with its length. Deterministic, so the same period always shows the same data.
 */
export function hqGallerySamples(range: HqDateRange, titles: Partial<Record<HqChartType, { title: string; description: string }>> = {}): HqGalleryItem[] {
  const next = hqSeededRandom(hqSeedOf(`${range.from}|${range.to}`));
  const days = Math.max(1, hqDateRangeDays(range));
  const scale = Math.max(1, days / 7);
  const buckets = hqGalleryBuckets(range);
  const times = buckets.map((b) => b.label);
  const per = (base: number, jitter = 0.35): number => Math.round(base * scale / Math.max(1, buckets.length) * (1 + (next() - 0.5) * 2 * jitter));
  const cities = ['Hyderabad', 'Bengaluru', 'Chennai', 'Pune', 'Mumbai', 'Delhi'];
  const vehicles = ['Bike', 'Auto', 'Mini', 'Sedan'];

  const items: HqGalleryItem[] = [];
  const add = (type: HqChartType, spec: HqChartSpec, columns: HqCommonTableColumn<Row>[], rows: Row[], wide = false): void => {
    const text = titles[type] ?? HQ_GALLERY_DEFAULT_TITLES[type];
    items.push({
      type, family: HQ_CHART_AXIS_TYPES.includes(type) ? 'axis' : 'nonAxis', premium: HQ_CHART_PREMIUM_TYPES.includes(type),
      title: text.title, description: text.description, spec, tableColumns: columns, tableRows: rows, snippet: snippetOf(spec), wide,
    });
  };

  // ── Axis-based ────────────────────────────────────────────────────────────────────────────────────────────────────────────
  const booked = times.map(() => per(900));
  const completed = booked.map((b) => Math.round(b * (0.78 + next() * 0.12)));
  add('line', { type: 'line', labels: times, series: [{ name: 'Booked', data: booked, slot: 1 }, { name: 'Completed', data: completed, slot: 3 }], yTitle: 'Rides' },
    [col('period', 'Period'), col('booked', 'Booked'), col('completed', 'Completed')], times.map((t, i) => ({ period: t, booked: booked[i], completed: completed[i] })), true);

  const revenue = times.map(() => per(450000, 0.25));
  add('area', { type: 'area', labels: times, format: 'currency', currency: 'INR', series: [{ name: 'Revenue', data: revenue, slot: 1 }] },
    [col('period', 'Period'), col('revenue', 'Revenue')], times.map((t, i) => ({ period: t, revenue: revenue[i] })));

  const cityRides = cities.map(() => Math.round(400 * scale * (0.5 + next())));
  add('column', { type: 'column', labels: cities, series: [{ name: 'Rides', data: cityRides, slot: 1 }], yTitle: 'Rides' },
    [col('city', 'City'), col('rides', 'Rides')], cities.map((c, i) => ({ city: c, rides: cityRides[i] })));

  const drivers = ['Ravi K', 'Anil P', 'Suresh M', 'Kiran R', 'Mahesh T', 'Vijay S'];
  const trips = drivers.map(() => Math.round(30 * scale * (0.6 + next()))).sort((a, b) => b - a);
  add('bar', { type: 'bar', horizontal: true, dataLabels: true, labels: drivers, series: [{ name: 'Trips', data: trips, slot: 1 }] },
    [col('driver', 'Driver'), col('trips', 'Trips')], drivers.map((d, i) => ({ driver: d, trips: trips[i] })));

  const orders = times.map(() => per(700));
  const avg = orders.map((_, i) => Math.round(orders.slice(Math.max(0, i - 6), i + 1).reduce((s, v) => s + v, 0) / Math.min(i + 1, 7)));
  add('mixed', { type: 'mixed', labels: times, series: [{ name: 'Orders', data: orders, type: 'column', slot: 1 }, { name: '7-period average', data: avg, type: 'line', slot: 2 }] },
    [col('period', 'Period'), col('orders', 'Orders'), col('avg', '7-period average')], times.map((t, i) => ({ period: t, orders: orders[i], avg: avg[i] })), true);

  const points = (n: number, base: number): [number, number][] => Array.from({ length: n }, () => {
    const km = round(1 + next() * 18, 1);
    return [km, Math.round(base + km * (12 + next() * 6))];
  });
  const cars = points(Math.round(12 + scale * 2), 60);
  const bikes = points(Math.round(12 + scale * 2), 20);
  add('scatter', { type: 'scatter', xTitle: 'Distance (km)', yTitle: 'Fare (₹)', series: [{ name: 'Cars', data: cars, slot: 1 }, { name: 'Bikes', data: bikes, slot: 2 }] },
    [col('mode', 'Mode'), col('km', 'Distance (km)'), col('fare', 'Fare (₹)')],
    [...cars.map(([km, fare]) => ({ mode: 'Cars', km, fare })), ...bikes.map(([km, fare]) => ({ mode: 'Bikes', km, fare }))]);

  const bubbles = vehicles.slice(0, 3).map((v, s) => ({
    name: v, slot: s + 1,
    data: Array.from({ length: 5 }, () => [round(2 + next() * 10, 1), Math.round(40 + next() * 60), Math.round(5 + next() * 30 * Math.sqrt(scale))] as [number, number, number]),
  }));
  add('bubble', { type: 'bubble', xTitle: 'Avg wait (min)', yTitle: 'Satisfaction', series: bubbles },
    [col('vehicle', 'Vehicle'), col('wait', 'Avg wait (min)'), col('score', 'Satisfaction'), col('rides', 'Rides')],
    bubbles.flatMap((b) => b.data.map(([w, s, r]) => ({ vehicle: b.name, wait: w, score: s, rides: r }))));

  const weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  const hours = Array.from({ length: 12 }, (_, i) => `${String(i * 2).padStart(2, '0')}:00`);
  const heat = weekdays.map((d, r) => hours.map((_, c) => Math.round((c > 3 && c < 11 ? 40 : 8) * scale * (0.5 + next()) * (r > 4 ? 1.3 : 1))));
  add('heatmap', { type: 'heatmap', labels: hours, series: weekdays.map((d, r) => ({ name: d, data: heat[r] })) },
    [col('day', 'Day'), ...hours.map((h, i) => col(`h${i}`, h))], weekdays.map((d, r) => ({ day: d, ...Object.fromEntries(heat[r].map((v, i) => [`h${i}`, v])) })), true);

  let price = 100;
  const candles = times.map((t) => {
    const open = price;
    const close = round(open * (1 + (next() - 0.48) * 0.08), 1);
    const high = round(Math.max(open, close) * (1 + next() * 0.03), 1);
    const low = round(Math.min(open, close) * (1 - next() * 0.03), 1);
    price = close;
    return { x: t, y: [open, high, low, close] };
  });
  add('candlestick', { type: 'candlestick', series: [{ name: 'Surge index', data: candles }] },
    [col('period', 'Period'), col('o', 'Open'), col('h', 'High'), col('l', 'Low'), col('c', 'Close')],
    candles.map((k) => ({ period: k.x, o: k.y[0], h: k.y[1], l: k.y[2], c: k.y[3] })), true);

  const waits = cities.slice(0, 5).map((c) => ({ city: c, samples: Array.from({ length: 30 }, () => Math.max(1, round(normal(next, 6 + next() * 4, 6), 1))) }));
  const boxes = waits.map((w) => ({ x: w.city, y: hqFiveNumberSummary(w.samples) ?? [0, 0, 0, 0, 0] }));
  add('boxPlot', { type: 'boxPlot', yTitle: 'Wait (min)', series: [{ name: 'Wait time', data: boxes }] },
    [col('city', 'City'), col('min', 'Min'), col('q1', 'Q1'), col('median', 'Median'), col('q3', 'Q3'), col('max', 'Max')],
    boxes.map((b) => ({ city: b.x, min: round(b.y[0], 1), q1: round(b.y[1], 1), median: round(b.y[2], 1), q3: round(b.y[3], 1), max: round(b.y[4], 1) })));

  const lengths = Array.from({ length: Math.round(120 * Math.min(scale, 8)) }, () => Math.max(0.5, round(normal(next, 7, 10), 1)));
  add('histogram', { type: 'histogram', bins: 12, xTitle: 'Trip length (km)', yTitle: 'Trips', series: [{ name: 'Trips', data: lengths, slot: 1 }] },
    [col('range', 'Trip length (km)'), col('trips', 'Trips')], histogramRows(lengths, 12));

  const groups = vehicles.map((v, i) => ({ x: v, y: Array.from({ length: 40 }, () => Math.max(1, round(normal(next, 4 + i * 2.5, 6), 1))) }));
  const summaryRows = groups.map((g) => {
    const s = hqFiveNumberSummary(g.y) ?? [0, 0, 0, 0, 0];
    return { group: g.x, n: g.y.length, median: round(s[2], 1), q1: round(s[1], 1), q3: round(s[3], 1) };
  });
  const summaryCols = [col('group', 'Vehicle'), col('n', 'Trips'), col('q1', 'Q1'), col('median', 'Median'), col('q3', 'Q3')];
  add('violin', { type: 'violin', yTitle: 'Fare per km (₹)', series: [{ name: 'Fare per km', data: groups }] }, summaryCols, summaryRows);

  const start = hqRangeDay(range.from) ?? new Date();
  const hour = 3_600_000;
  const shifts = ['Driver A', 'Driver B', 'Driver C', 'Driver D'].map((d, i) => {
    const s = start.getTime() + (6 + i * 2 + Math.round(next() * 2)) * hour;
    return { x: d, y: [s, s + (6 + Math.round(next() * 4)) * hour] };
  });
  add('rangeBar', { type: 'rangeBar', xType: 'datetime', series: [{ name: 'Shift', data: shifts, slot: 1 }] },
    [col('driver', 'Driver'), col('from', 'From'), col('to', 'To')],
    shifts.map((s) => ({ driver: s.x, from: new Date(s.y[0]).toLocaleString('en-IN'), to: new Date(s.y[1]).toLocaleString('en-IN') })));

  const band = times.map((t) => {
    const mid = 180 + next() * 60;
    return { x: t, lo: Math.round(mid - 40 - next() * 30), hi: Math.round(mid + 40 + next() * 30), mid: Math.round(mid) };
  });
  add('rangeArea', {
    type: 'rangeArea', yTitle: 'Fare (₹)',
    series: [{ name: 'Fare range', data: band.map((b) => ({ x: b.x, y: [b.lo, b.hi] })), slot: 1 }, { name: 'Median fare', type: 'line', data: band.map((b) => ({ x: b.x, y: b.mid })), slot: 2 }],
  }, [col('period', 'Period'), col('lo', 'Low'), col('mid', 'Median'), col('hi', 'High')], band.map((b) => ({ period: b.x, lo: b.lo, mid: b.mid, hi: b.hi })), true);

  const streams = vehicles.map((v, i) => ({ name: v, slot: i + 1, data: times.map((_, t) => Math.round(per(300) * (1 + Math.sin((t + i * 2) / 3) * 0.5))) }));
  add('streamgraph', { type: 'streamgraph', labels: times, series: streams },
    [col('period', 'Period'), ...vehicles.map((v) => col(v, v))], times.map((t, i) => ({ period: t, ...Object.fromEntries(streams.map((s) => [s.name, s.data[i]])) })), true);

  const steps: HqWaterfallStep[] = [
    { label: 'Opening', delta: Math.round(200000 * scale) }, { label: 'Fares', delta: Math.round(380000 * scale * (0.8 + next() * 0.4)) },
    { label: 'Tips', delta: Math.round(30000 * scale) }, { label: 'Refunds', delta: -Math.round(45000 * scale * (0.6 + next())) },
    { label: 'Commission', delta: -Math.round(90000 * scale) }, { label: 'Net', total: 'subtotal' },
    { label: 'Payouts', delta: -Math.round(260000 * scale * (0.8 + next() * 0.3)) }, { label: 'Closing', total: 'total' },
  ];
  add('waterfall', { type: 'waterfall', format: 'currency', currency: 'INR', series: [{ name: 'Cash flow', data: hqWaterfallData(steps) }] },
    [col('label', 'Step'), col('change', 'Change'), col('running', 'Running total')], hqWaterfallRunning(steps), true);

  const dumb = cities.slice(0, 5).map((c) => ({ x: c, y: [round(4 + next() * 6, 1), round(3 + next() * 6, 1)] }));
  add('dumbbell', { type: 'dumbbell', labels: ['Previous period', 'This period'], xTitle: 'Avg wait (min)', series: [{ name: 'Avg wait', data: dumb }] },
    [col('city', 'City'), col('prev', 'Previous period'), col('now', 'This period')], dumb.map((d) => ({ city: d.x, prev: d.y[0], now: d.y[1] })));

  const zones = ['Central', 'North', 'South', 'East', 'West', 'Airport', 'Tech park'];
  const zoneRides = zones.map(() => Math.round(500 * scale * (0.3 + next())));
  add('treemap', { type: 'treemap', series: [{ name: 'Rides', data: zones.map((z, i) => ({ x: z, y: zoneRides[i] })) }] },
    [col('zone', 'Zone'), col('rides', 'Rides')], zones.map((z, i) => ({ zone: z, rides: zoneRides[i] })));

  add('raincloud', { type: 'raincloud', yTitle: 'Fare per km (₹)', series: [{ name: 'Fare per km', data: groups }] }, summaryCols, summaryRows);

  const ratings = vehicles.slice(0, 3).map((v) => ({ x: v, y: Array.from({ length: Math.round(25 + scale * 2) }, () => Math.min(5, Math.max(1, round(normal(next, 4.1, 2), 1)))) }));
  add('beeswarm', { type: 'beeswarm', yTitle: 'Rating', series: [{ name: 'Ratings', data: ratings }] },
    [col('vehicle', 'Vehicle'), col('n', 'Ratings'), col('median', 'Median')],
    ratings.map((r) => ({ vehicle: r.x, n: r.y.length, median: round((hqFiveNumberSummary(r.y) ?? [0, 0, 0])[2], 1) })));

  // ── Non-axis ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
  const methods = ['UPI', 'Card', 'Cash', 'Wallet'];
  const pay = methods.map(() => Math.round(1000 * scale * (0.4 + next())));
  const labelled = (key: string, label: string, names: string[], values: number[]): [HqCommonTableColumn<Row>[], Row[]] =>
    [[col('name', label), col('value', key)], names.map((n, i) => ({ name: n, value: values[i] }))];
  add('pie', { type: 'pie', labels: methods, series: pay }, ...labelled('Payments', 'Method', methods, pay));

  const statuses = ['Completed', 'Cancelled', 'No driver', 'Ongoing'];
  const st = statuses.map((_, i) => Math.round((i === 0 ? 900 : 120) * scale * (0.6 + next() * 0.6)));
  add('donut', { type: 'donut', labels: statuses, series: st, centerLabel: 'Rides' }, ...labelled('Rides', 'Status', statuses, st));

  const kpis = ['Acceptance', 'Completion', 'On time'];
  const kv = kpis.map(() => Math.round(70 + next() * 28));
  add('radialBar', { type: 'radialBar', format: 'percent', labels: kpis, series: kv }, ...labelled('Rate (%)', 'Measure', kpis, kv));

  const util = Math.round(45 + next() * 50);
  add('gauge', { type: 'gauge', format: 'percent', labels: ['Fleet utilisation'], series: [util] }, ...labelled('Utilisation (%)', 'Measure', ['Fleet utilisation'], [util]));

  const axes = ['Safety', 'Comfort', 'Price', 'Speed', 'Support', 'App'];
  const thisP = axes.map(() => Math.round(55 + next() * 40));
  const prevP = axes.map(() => Math.round(50 + next() * 40));
  add('radar', { type: 'radar', labels: axes, series: [{ name: 'This period', data: thisP, slot: 1 }, { name: 'Previous period', data: prevP, slot: 2 }] },
    [col('axis', 'Measure'), col('now', 'This period'), col('prev', 'Previous period')], axes.map((a, i) => ({ axis: a, now: thisP[i], prev: prevP[i] })));

  const services = ['Rides', 'Rentals', 'Outstation', 'Parcels', 'Airport'];
  const sv = services.map(() => Math.round(300 * scale * (0.3 + next())));
  add('polarArea', { type: 'polarArea', labels: services, series: sv }, ...labelled('Bookings', 'Service', services, sv));

  const stages = ['Requested', 'Accepted', 'Arrived', 'Started', 'Completed'];
  let left = Math.round(2000 * scale);
  const fv = stages.map((_, i) => (i === 0 ? left : (left = Math.round(left * (0.7 + next() * 0.25)))));
  add('funnel', { type: 'funnel', labels: stages, series: fv }, ...labelled('Rides', 'Stage', stages, fv));

  const tiers = ['All customers', 'Active', 'Repeat', 'Loyal', 'VIP'];
  let base = Math.round(5000 * scale);
  const tv = tiers.map((_, i) => (i === 0 ? base : (base = Math.round(base * (0.45 + next() * 0.2)))));
  add('pyramid', { type: 'pyramid', labels: tiers, series: tv }, ...labelled('Customers', 'Tier', tiers, tv));

  const tree: HqChartNode[] = [
    { name: 'South', slot: 1, children: ['Hyderabad', 'Bengaluru', 'Chennai'].map((c) => ({ name: c, children: [{ name: 'Rides', value: Math.round(300 * scale * (0.4 + next())) }, { name: 'Rentals', value: Math.round(80 * scale * (0.4 + next())) }] })) },
    { name: 'West', slot: 2, children: ['Pune', 'Mumbai'].map((c) => ({ name: c, children: [{ name: 'Rides', value: Math.round(280 * scale * (0.4 + next())) }, { name: 'Rentals', value: Math.round(70 * scale * (0.4 + next())) }] })) },
    { name: 'North', slot: 3, children: [{ name: 'Delhi', children: [{ name: 'Rides', value: Math.round(320 * scale * (0.4 + next())) }, { name: 'Rentals', value: Math.round(90 * scale * (0.4 + next())) }] }] },
  ];
  const treeCols = [col('path', 'Region › City › Service'), col('level', 'Level'), col('value', 'Bookings')];
  add('sunburst', { type: 'sunburst', series: [{ name: 'Bookings', data: tree }] }, treeCols, hqChartNodeRows(tree));
  add('icicle', { type: 'icicle', series: [{ name: 'Bookings', data: tree }] }, treeCols, hqChartNodeRows(tree), true);

  const seg = ['New', 'Returning', 'Corporate'];
  const sg = seg.map(() => Math.round(400 * scale * (0.3 + next())));
  add('unit', { type: 'unit', labels: seg, series: sg, unitValue: Math.max(1, Math.round(10 * scale)) }, ...labelled('Customers', 'Segment', seg, sg));

  const online = ['Online', 'On trip', 'Offline'];
  const ov = online.map(() => Math.round(60 * scale * (0.3 + next())));
  add('pictogram', { type: 'pictogram', labels: online, series: ov, unitValue: Math.max(1, Math.round(5 * scale)) }, ...labelled('Drivers', 'Status', online, ov));

  const share = methods.map(() => 1 + next());
  const total = share.reduce((s, v) => s + v, 0);
  const pct = share.map((v) => Math.round((v / total) * 100));
  add('waffle', { type: 'waffle', format: 'percent', labels: methods, series: pct }, ...labelled('Share (%)', 'Method', methods, pct));

  return items;
}

/** Equal-width bins of raw values, for a histogram's table view. */
function histogramRows(values: readonly number[], bins: number): Row[] {
  if (!values.length) {
    return [];
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  const width = (max - min) / bins || 1;
  const counts = Array.from({ length: bins }, () => 0);
  values.forEach((v) => counts[Math.min(bins - 1, Math.floor((v - min) / width))]++);
  return counts.map((c, i) => ({ range: `${round(min + i * width, 1)}–${round(min + (i + 1) * width, 1)}`, trips: c }));
}

/** English title and one-line description of every gallery chart (the app may pass translated ones). */
export const HQ_GALLERY_DEFAULT_TITLES: Record<HqChartType, { title: string; description: string }> = {
  line: { title: 'Line', description: 'Change over time for one or a few series.' },
  area: { title: 'Area', description: 'A single trend over time, with its volume shaded.' },
  bar: { title: 'Bar', description: 'A ranking: categories sorted along a value axis.' },
  column: { title: 'Column', description: 'Compare a few categories side by side.' },
  mixed: { title: 'Mixed / combo', description: 'Columns and a line in the same unit, on one axis.' },
  scatter: { title: 'Scatter', description: 'How two measures move together, point by point.' },
  bubble: { title: 'Bubble', description: 'A scatter with a third measure as the bubble size.' },
  heatmap: { title: 'Heatmap', description: 'A grid of two categories, coloured by magnitude.' },
  candlestick: { title: 'Candlestick', description: 'Open, high, low and close for each period.' },
  boxPlot: { title: 'Box plot', description: 'Median, quartiles and range of each group.' },
  histogram: { title: 'Histogram', description: 'How raw values are spread across bins.' },
  violin: { title: 'Violin', description: 'The full distribution shape of each group.' },
  rangeBar: { title: 'Range bar / timeline', description: 'Start and end of tasks or shifts on a time axis.' },
  rangeArea: { title: 'Range area', description: 'A band between low and high, with the median as a line.' },
  streamgraph: { title: 'Streamgraph', description: 'How the mix of categories flows over time.' },
  waterfall: { title: 'Waterfall', description: 'Steps that build up and draw down a running total.' },
  dumbbell: { title: 'Dumbbell', description: 'The change between two periods for each category.' },
  treemap: { title: 'Treemap', description: 'Parts of a whole as nested rectangles.' },
  raincloud: { title: 'Raincloud', description: 'Distribution, box and every observation together.' },
  beeswarm: { title: 'Beeswarm', description: 'Every observation per group, spread so none hides another.' },
  pie: { title: 'Pie', description: 'Share of a whole, for a few parts.' },
  donut: { title: 'Donut', description: 'Share of a whole with the total in the centre.' },
  radialBar: { title: 'Radial bar', description: 'A few rates, each as a ring.' },
  gauge: { title: 'Gauge', description: 'One value against its scale.' },
  radar: { title: 'Radar', description: 'Several measures of two profiles at a glance.' },
  polarArea: { title: 'Polar area', description: 'Categories compared by the size of their wedge.' },
  funnel: { title: 'Funnel', description: 'How many make it through each stage.' },
  pyramid: { title: 'Pyramid', description: 'Ordered tiers, from the broad base to the top.' },
  sunburst: { title: 'Sunburst', description: 'A hierarchy as rings, from the centre outward.' },
  icicle: { title: 'Icicle', description: 'A hierarchy as bands, level by level.' },
  unit: { title: 'Unit', description: 'One mark per unit, grouped by category.' },
  pictogram: { title: 'Pictogram', description: 'Unit marks drawn as icons.' },
  waffle: { title: 'Waffle', description: 'Shares of a whole on a 10 × 10 grid.' },
};

/** Day of a period as ISO text (re-exported for screens building their own samples). */
export const hqGalleryIsoDay = hqRangeIsoDay;

/** HqChartSeries re-typed for screens copying a sample. */
export type HqGallerySeries = HqChartSeries;
