import { describe, expect, it } from 'vitest';
import { buildChartOptions, HqChartNode, HqChartPalette } from './hq-chart.options';
import { hqChartNodesToApex } from './hq-chart.transforms';

const tree: HqChartNode[] = [{ name: 'South', slot: 1, children: [
  { name: 'Hyderabad', children: [{ name: 'Rides', value: 30 }, { name: 'Rentals', value: 10 }] },
  { name: 'Chennai', value: 20 },
] }];
const palette: HqChartPalette = { colors: ['#126b50'], text: '#111', muted: '#666', grid: '#ddd', dark: false, fontFamily: 'Arial' };

describe('partition chart data', () => {
  it('supplies the x/y data read by ApexCharts before it renders a hierarchy', () => {
    const nodes = hqChartNodesToApex(tree, () => '#126b50');
    expect(nodes).toEqual([{ x: 'South', y: 60, color: '#126b50', children: [
      { x: 'Hyderabad', y: 40, children: [{ x: 'Rides', y: 30 }, { x: 'Rentals', y: 10 }] },
      { x: 'Chennai', y: 20 },
    ] }]);
    expect(tree[0].value).toBeUndefined();
  });
  it.each(['sunburst', 'icicle'] as const)('keeps %s roots and descendants usable by the non-axis parser', type => {
    const options = buildChartOptions({ type, series: [{ name: 'Bookings', data: tree }] }, palette, true);
    const series = options.series as { data: { x: string; y: number }[] }[];
    expect(options.chart?.type).toBe(type);
    expect(series[0].data.map(node => [node.x, node.y])).toEqual([['South', 60]]);
  });
});
