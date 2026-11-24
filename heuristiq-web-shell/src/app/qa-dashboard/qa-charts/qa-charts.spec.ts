import { NO_ERRORS_SCHEMA } from '@angular/core';
import { HqChartSeries, HqChartSpec } from 'hq-shared-lib';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { QA_STATUS_KEYS } from '../qa-dashboard.constants';
import { release } from '../qa-dashboard.fixtures';
import { QA_CHART_HEIGHT, QA_STATUS_SLOTS, QA_STATUS_TONES } from './qa-charts.constants';
import { QaLineSeries } from './qa-charts.models';
import { QaDonutChart } from './qa-donut-chart';
import { QaLineChart } from './qa-line-chart';
import { QaReleaseChart } from './qa-release-chart';

/** The named series of a chart spec (the QA charts never use bare-number series). */
function seriesOf(spec: HqChartSpec | null): HqChartSeries[] {
  return (spec?.series ?? []) as HqChartSeries[];
}

/** Creates the chart with its inputs; the shared hq chart components are not under test here. */
function setup<T extends object>(component: new () => T, inputs: Partial<T>): { fixture: ComponentFixture<T>; instance: T } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ declarations: [component], imports: [TranslateModule.forRoot()], schemas: [NO_ERRORS_SCHEMA] });
  const fixture = TestBed.createComponent(component);
  const instance = fixture.componentInstance;
  Object.assign(instance, inputs);
  (instance as unknown as { ngOnChanges(): void }).ngOnChanges();
  fixture.detectChanges();
  return { fixture, instance };
}

describe('QaReleaseChart', () => {
  const releases = [release('QA_REG_OCT25_R1', { passed: 5, failed: 4, skipped: 1 }), release('QA_REG_OCT26_R2')];

  it('stacks one bar per release, one series per status in its fixed palette slot', () => {
    const { instance } = setup(QaReleaseChart, { releases });
    expect(instance.spec?.type).toBe('bar');
    expect(instance.spec?.stacked).toBe(true);
    expect(instance.spec?.height).toBe(QA_CHART_HEIGHT);
    expect(instance.spec?.labels).toEqual(['QA_REG_OCT25_R1', 'QA_REG_OCT26_R2']);
    expect(seriesOf(instance.spec).map((s) => s.name)).toEqual([QA_STATUS_KEYS['PASSED'], QA_STATUS_KEYS['FAILED'], QA_STATUS_KEYS['SKIPPED']]);
    expect(seriesOf(instance.spec).map((s) => s.slot)).toEqual([QA_STATUS_SLOTS.PASSED, QA_STATUS_SLOTS.FAILED, QA_STATUS_SLOTS.SKIPPED]);
    expect(seriesOf(instance.spec)[0].data).toEqual([5, 7]);
    expect(seriesOf(instance.spec)[1].data).toEqual([4, 2]);
  });

  it('offers the same numbers as table rows, one per release', () => {
    const { instance } = setup(QaReleaseChart, { releases });
    expect(instance.tableRows).toEqual([
      { release: 'QA_REG_OCT25_R1', PASSED: 5, FAILED: 4, SKIPPED: 1 },
      { release: 'QA_REG_OCT26_R2', PASSED: 7, FAILED: 2, SKIPPED: 1 },
    ]);
  });

  it('has no bars without releases, and a status missing from a bar counts as zero', () => {
    const { instance } = setup(QaReleaseChart, { releases: [] });
    expect(instance.bars).toEqual([]);
    expect(instance.spec?.labels).toEqual([]);
    expect(instance.countOf({ release: 'x', segments: [] } as never, 'FAILED')).toBe(0);
  });

  it('renames the series when the language changes', () => {
    const { instance } = setup(QaReleaseChart, { releases });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('te', { qaDashboard: { status: { PASSED: 'పాస్' } } }, true);
    translate.use('te');
    expect(seriesOf(instance.spec)[0].name).toBe('పాస్');
  });
});

describe('QaDonutChart', () => {
  it('splits the totals by status with its tone and gives the success rate', () => {
    const { instance } = setup(QaDonutChart, { totals: { passed: 6, failed: 3, skipped: 1 } });
    expect(instance.total).toBe(10);
    expect(instance.successText).toBe('60');
    expect(instance.items.map((i) => i.value)).toEqual([6, 3, 1]);
    expect(instance.items.map((i) => i.tone)).toEqual([QA_STATUS_TONES.PASSED, QA_STATUS_TONES.FAILED, QA_STATUS_TONES.SKIPPED]);
    expect(instance.tableRows).toEqual([
      { status: QA_STATUS_KEYS['PASSED'], count: 6 },
      { status: QA_STATUS_KEYS['FAILED'], count: 3 },
      { status: QA_STATUS_KEYS['SKIPPED'], count: 1 },
    ]);
  });

  it('shows a zero success rate when nothing ran', () => {
    const { instance } = setup(QaDonutChart, { totals: { passed: 0, failed: 0, skipped: 0 } });
    expect(instance.total).toBe(0);
    expect(instance.successText).toBe('0');
  });

  it('stops listening to language changes once destroyed', () => {
    const { fixture, instance } = setup(QaDonutChart, { totals: { passed: 1, failed: 0, skipped: 0 } });
    fixture.destroy();
    const before = instance.items;
    TestBed.inject(TranslateService).use('hi');
    expect(instance.items).toBe(before);
  });
});

describe('QaLineChart', () => {
  const series: QaLineSeries[] = [
    { key: 'ui', labelKey: 'qaDashboard.workspaces.UI', slot: 1, values: [{ label: 'Oct 1', value: 10 }, { label: 'Oct 2', value: 12 }] },
    { key: 'api', labelKey: 'qaDashboard.workspaces.API', slot: 2, values: [{ label: 'Oct 1', value: 4 }] },
  ];

  it('draws one line per series over the longest series labels, with a legend for two or more', () => {
    const { instance } = setup(QaLineChart, { series });
    expect(instance.labels).toEqual(['Oct 1', 'Oct 2']);
    expect(instance.spec?.type).toBe('line');
    expect(seriesOf(instance.spec).map((s) => s.slot)).toEqual([1, 2]);
    expect(seriesOf(instance.spec)[1].data).toEqual([4, 0]);
    expect(instance.spec?.showLegend).toBe(true);
    expect(instance.isEmpty).toBe(false);
  });

  it('leaves the cell of a shorter series blank in the table view', () => {
    const { instance } = setup(QaLineChart, { series });
    expect(instance.tableRows[1]).toEqual({ label: 'Oct 2', values: { ui: 12, api: null } });
    expect(instance.columns.map((c) => c.key)).toEqual(['label', 'ui', 'api']);
    const apiColumn = instance.columns[2] as unknown as { render(row: unknown): string };
    expect(apiColumn.render(instance.tableRows[1])).toBe('');
    expect(apiColumn.render(instance.tableRows[0])).toBe('4');
  });

  it('has no legend for a single line and is empty without values', () => {
    const { instance } = setup(QaLineChart, { series: [{ ...series[0], values: [] }] });
    expect(instance.spec?.showLegend).toBe(false);
    expect(instance.isEmpty).toBe(true);
    expect(instance.labels).toEqual([]);
  });
});
