import { describe, expect, it } from 'vitest';
import {
  HqChartPalette, HqChartSpec, buildChartOptions, chartIsEmpty, compactNumber, formatChartValue, parseColor, readChartPalette, tint,
} from './hq-chart.options';

const palette: HqChartPalette = { colors: ['#A31621', '#E8A33D'], text: '#111', muted: '#666', grid: 'rgba(0,0,0,0.07)', dark: false, fontFamily: 'Outfit' };

describe('colours', () => {
  it('parses hex and rgb colours and gives null for anything it cannot resolve', () => {
    expect(parseColor('#A31621')).toEqual([163, 22, 33]);
    expect(parseColor('#abc')).toEqual([170, 187, 204]);
    expect(parseColor('rgb(10, 20, 30)')).toEqual([10, 20, 30]);
    expect(parseColor('rgba(10 20 30 / 0.5)')).toEqual([10, 20, 30]);
    expect(parseColor('color-mix(in srgb, red 50%, white)')).toBeNull();
  });

  it('lightens a colour towards white, and leaves an unparseable one alone', () => {
    expect(tint('#000000', 0.5)).toBe('#808080');
    expect(tint('#ffffff', 0.5)).toBe('#ffffff');
    expect(tint('var(--x)', 0.5)).toBe('var(--x)');
  });

  it('reads the palette from the theme variables of the page, and falls back when a variable is not a plain colour', () => {
    const root = document.createElement('div');
    root.style.setProperty('--hq-primary', '#A31621');
    root.style.setProperty('--hq-secondary', 'color-mix(in srgb, red, blue)');
    root.setAttribute('data-theme-mode', 'dark');
    document.body.appendChild(root);

    const read = readChartPalette(root);
    document.body.removeChild(root);

    expect(read.colors[0]).toBe('#A31621');
    expect(read.colors[1]).toBe('#D4A017');
    expect(read.dark).toBe(true);
    expect(read.colors).toHaveLength(8);
  });
});

describe('number formatting', () => {
  it('writes axis figures in the short Indian form', () => {
    expect(compactNumber(950)).toBe('950');
    expect(compactNumber(12500)).toBe('12.5k');
    expect(compactNumber(250000)).toBe('2.5L');
    expect(compactNumber(20000000)).toBe('2.0Cr');
    expect(compactNumber(Number.NaN)).toBe('0');
  });

  it('formats a figure as a number, a percentage or an amount, and an empty string for no figure', () => {
    expect(formatChartValue(1234567)).toBe('12,34,567');
    expect(formatChartValue(45.678, 'percent')).toBe('45.7%');
    expect(formatChartValue(1500, 'currency', 'INR')).toContain('1,500');
    expect(formatChartValue(150000, 'currency', 'INR', true)).toContain('1.5L');
    expect(formatChartValue(Number.NaN)).toBe('');
  });
});

describe('chartIsEmpty', () => {
  it('is empty for no series, no points, and a donut whose slices are all zero', () => {
    expect(chartIsEmpty({ type: 'bar', series: [] })).toBe(true);
    expect(chartIsEmpty({ type: 'bar', series: [{ name: 'a', data: [] }] })).toBe(true);
    expect(chartIsEmpty({ type: 'donut', series: [0, 0] })).toBe(true);
    expect(chartIsEmpty({ type: 'donut', series: [0, 3] })).toBe(false);
    expect(chartIsEmpty({ type: 'area', series: [{ name: 'a', data: [1, 2] }] })).toBe(false);
  });
});

describe('buildChartOptions', () => {
  const bars: HqChartSpec = { type: 'bar', series: [{ name: 'Paid', data: [3, 5] }], labels: ['Mon', 'Tue'], format: 'number' };

  it('takes the colours and font of the palette and draws without a toolbar', () => {
    const options = buildChartOptions(bars, palette, false);

    expect(options.colors).toEqual(palette.colors);
    expect(options.chart?.fontFamily).toBe('Outfit');
    expect(options.chart?.toolbar?.show).toBe(false);
    expect(options.xaxis?.categories).toEqual(['Mon', 'Tue']);
  });

  it('turns animations off for a user who asked for reduced motion', () => {
    expect(buildChartOptions(bars, palette, true).chart?.animations?.enabled).toBe(false);
    expect(buildChartOptions(bars, palette, false).chart?.animations?.enabled).toBe(true);
  });

  it('draws bars rounded and lying down for a ranking, with a dark tooltip on a dark theme', () => {
    const options = buildChartOptions({ ...bars, horizontal: true }, { ...palette, dark: true }, false);

    expect(options.plotOptions?.bar?.horizontal).toBe(true);
    expect(options.plotOptions?.bar?.borderRadius).toBe(6);
    expect(options.tooltip?.theme).toBe('dark');
  });

  it('fills an area chart with a gradient that fades out', () => {
    const options = buildChartOptions({ type: 'area', series: [{ name: 'a', data: [1, 2] }] }, palette, false);

    expect(options.fill?.type).toBe('gradient');
    expect(options.stroke?.width).toBe(3);
  });

  it('makes a donut with the total, or the given figure, in its centre', () => {
    const donut = buildChartOptions({ type: 'donut', series: [3, 7], labels: ['a', 'b'], centerLabel: 'Total' }, palette, false);
    const total = (donut.plotOptions?.pie?.donut?.labels?.total as { formatter: () => string }).formatter();
    expect(total).toBe('10');
    expect(donut.labels).toEqual(['a', 'b']);

    const custom = buildChartOptions({ type: 'donut', series: [3, 7], centerValue: '10 open' }, palette, false);
    expect((custom.plotOptions?.pie?.donut?.labels?.total as { formatter: () => string }).formatter()).toBe('10 open');
  });

  it('makes a radial bar and a bare sparkline with no legend or axes', () => {
    expect(buildChartOptions({ type: 'radialBar', series: [72], labels: ['Done'], format: 'percent' }, palette, false).plotOptions?.radialBar).toBeDefined();

    const spark = buildChartOptions({ type: 'area', series: [{ name: 'a', data: [1, 3, 2] }], sparkline: true }, palette, false);
    expect(spark.chart?.sparkline?.enabled).toBe(true);
    expect(spark.chart?.height).toBe(48);
    expect(spark.legend?.show).toBe(false);
    expect(spark.xaxis).toEqual({});
  });

  it('formats the tooltip and the axis in the chosen format', () => {
    const options = buildChartOptions({ ...bars, format: 'percent' }, palette, false);
    const tooltip = options.tooltip?.y as { formatter: (v: number) => string };
    expect(tooltip.formatter(41.25)).toBe('41.3%');
  });

  it('shows the category names of a horizontal bar chart as they are, never as NaN, and puts the figures on the bottom axis', () => {
    const options = buildChartOptions({ type: 'bar', horizontal: true, series: [{ name: 'People', data: [120, 30] }], labels: ['Registered', 'Paid'] }, palette, false);

    const category = (options.yaxis as { labels: { formatter: (v: number | string) => string } }).labels.formatter;
    expect(category('Registered')).toBe('Registered');
    expect(category('')).toBe('');
    const figure = (options.xaxis?.labels as { formatter: (v: number | string) => string }).formatter;
    expect(figure(1500)).toBe('1.5k');
    expect(figure(30)).toBe('30');
  });

  it('keeps the figures of a vertical chart on the left axis and its names on the bottom', () => {
    const options = buildChartOptions({ type: 'bar', series: [{ name: 'a', data: [5000] }], labels: ['Mon'] }, palette, false);

    expect((options.yaxis as { labels: { formatter: (v: number) => string } }).labels.formatter(5000)).toBe('5.0k');
    expect((options.xaxis?.labels as { formatter?: unknown }).formatter).toBeUndefined();
  });

  it('asks for whole-number ticks on a count chart but not on an amount chart', () => {
    const counts = buildChartOptions({ type: 'bar', series: [{ name: 'a', data: [1, 2] }], labels: ['x', 'y'] }, palette, false);
    const money = buildChartOptions({ type: 'bar', series: [{ name: 'a', data: [1, 2] }], labels: ['x', 'y'], format: 'currency' }, palette, false);

    expect((counts.yaxis as { decimalsInFloat?: number }).decimalsInFloat).toBe(0);
    expect((money.yaxis as { decimalsInFloat?: number }).decimalsInFloat).toBeUndefined();
  });
});
