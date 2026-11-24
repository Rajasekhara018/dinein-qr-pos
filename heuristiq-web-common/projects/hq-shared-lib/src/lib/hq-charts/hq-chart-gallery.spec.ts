import { describe, expect, it } from 'vitest';
import { HqChartGallery } from './hq-chart-gallery';
import { hqDateRangeFor } from './hq-date-range.helpers';
import { HQ_GALLERY_DEFAULT_TITLES } from './hq-chart-gallery.samples';

describe('chart gallery demo', () => {
  it('provides a chart, table and example for every supported type', () => {
    const gallery = new HqChartGallery();
    expect(gallery.items.map(item => item.type).sort()).toEqual(Object.keys(HQ_GALLERY_DEFAULT_TITLES).sort());
    for (const item of gallery.items) {
      expect(item.tableRows.length).toBeGreaterThan(0);
      expect(item.tableColumns.length).toBeGreaterThan(0);
      expect(item.snippet).toContain(item.type);
    }
  });
  it('combines family and case-insensitive search and supports no matches', () => {
    const gallery = new HqChartGallery();
    gallery.search = '  DONUT  ';
    expect(gallery.visibleItems.map(item => item.type)).toEqual(['donut']);
    gallery.family = 'axis';
    expect(gallery.visibleItems).toEqual([]);
    gallery.search = '';
    expect(gallery.visibleItems.every(item => item.family === 'axis')).toBe(true);
  });
  it('regenerates the samples for a new period and restores the same data for the same period', () => {
    const gallery = new HqChartGallery();
    const first = hqDateRangeFor('last7Days', new Date(2026, 10, 15));
    gallery.changeRange(first);
    const samples = JSON.stringify(gallery.items);
    gallery.changeRange(hqDateRangeFor('last30Days', new Date(2026, 10, 15)));
    expect(JSON.stringify(gallery.items)).not.toBe(samples);
    gallery.changeRange(first);
    expect(JSON.stringify(gallery.items)).toBe(samples);
  });
});
