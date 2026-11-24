import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChartComponent } from 'ng-apexcharts';
import { beforeEach, describe, expect, it } from 'vitest';
import { HqChart } from './hq-chart';
import { HqChartCard } from './hq-chart-card';
import { HqKpiCard } from './hq-kpi-card';

/** Stands in for the real ApexCharts renderer, which needs a real browser to draw. */
@Component({ selector: 'apx-chart', standalone: true, template: '<span class="stub-chart"></span>' })
class StubApexChart {
  @Input() series: unknown;
  @Input() chart: unknown;
  @Input() colors: unknown;
  @Input() labels: unknown;
  @Input() xaxis: unknown;
  @Input() yaxis: unknown;
  @Input() stroke: unknown;
  @Input() fill: unknown;
  @Input() markers: unknown;
  @Input() dataLabels: unknown;
  @Input() grid: unknown;
  @Input() legend: unknown;
  @Input() tooltip: unknown;
  @Input() plotOptions: unknown;
}

function withStub(): void {
  TestBed.overrideComponent(HqChart, { remove: { imports: [ChartComponent] }, add: { imports: [StubApexChart] } });
}

describe('HqChart', () => {
  let fixture: ComponentFixture<HqChart>;

  beforeEach(() => {
    document.documentElement.removeAttribute('data-theme-mode');
    withStub();
    fixture = TestBed.createComponent(HqChart);
  });

  it('draws a chart for a spec with data', () => {
    fixture.componentRef.setInput('spec', { type: 'bar', series: [{ name: 'a', data: [1, 2] }], labels: ['x', 'y'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('apx-chart')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.hq-chart__empty')).toBeNull();
    expect(fixture.componentInstance.options?.chart?.type).toBe('bar');
  });

  it('shows the given text instead of a chart when there is nothing to draw', () => {
    fixture.componentRef.setInput('spec', { type: 'donut', series: [0, 0] });
    fixture.componentRef.setInput('emptyText', 'No data yet');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('apx-chart')).toBeNull();
    expect(fixture.nativeElement.querySelector('.hq-chart__empty')?.textContent).toContain('No data yet');
  });

  it('draws again with the new colours when the theme mode of the page changes', async () => {
    fixture.componentRef.setInput('spec', { type: 'bar', series: [{ name: 'a', data: [1] }] });
    fixture.detectChanges();
    expect(fixture.componentInstance.options?.tooltip?.theme).toBe('light');

    document.documentElement.setAttribute('data-theme-mode', 'dark');
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();

    expect(fixture.componentInstance.options?.tooltip?.theme).toBe('dark');
    document.documentElement.removeAttribute('data-theme-mode');
  });

  it('gives a sparkline its small height and the chart its accessible name', () => {
    fixture.componentRef.setInput('spec', { type: 'area', series: [{ name: 'a', data: [1, 2] }], sparkline: true });
    fixture.componentRef.setInput('ariaLabel', 'Trend');
    fixture.detectChanges();

    expect(fixture.componentInstance.height).toBe(48);
    expect(fixture.nativeElement.querySelector('[role="img"]').getAttribute('aria-label')).toBe('Trend');
  });
});

describe('HqKpiCard', () => {
  function card(inputs: Record<string, unknown>): ComponentFixture<HqKpiCard> {
    TestBed.resetTestingModule();
    TestBed.overrideComponent(HqChart, { remove: { imports: [ChartComponent] }, add: { imports: [StubApexChart] } });
    const f = TestBed.createComponent(HqKpiCard);
    Object.entries({ label: 'Assets', value: '1,248', ...inputs }).forEach(([k, v]) => f.componentRef.setInput(k, v));
    f.detectChanges();
    return f;
  }

  it('shows the label, the figure and the small print', () => {
    const el: HTMLElement = card({ caption: 'vs last week' }).nativeElement;

    expect(el.querySelector('.kpi__label')?.textContent).toBe('Assets');
    expect(el.querySelector('.kpi__value')?.textContent).toBe('1,248');
    expect(el.querySelector('.kpi__caption')?.textContent).toBe('vs last week');
    expect(el.querySelector('.kpi__trend')).toBeNull();
    expect(el.querySelector('hq-chart')).toBeNull();
  });

  it('colours the trend by whether a rise is good news for this figure', () => {
    expect(card({ trend: 4 }).componentInstance.trendTone).toBe('good');
    expect(card({ trend: -4 }).componentInstance.trendTone).toBe('bad');
    expect(card({ trend: 4, trendMeaning: 'up-is-bad' }).componentInstance.trendTone).toBe('bad');
    expect(card({ trend: -4, trendMeaning: 'up-is-bad' }).componentInstance.trendTone).toBe('good');
    expect(card({ trend: 0 }).componentInstance.trendTone).toBe('neutral');
  });

  it('writes the size of the change without its sign, with an arrow for the direction', () => {
    const el: HTMLElement = card({ trend: -12.34 }).nativeElement;

    expect(el.querySelector('.kpi__trend')?.textContent?.trim()).toBe('12.3%');
    expect(el.querySelector('.kpi__trend .bi-arrow-down-short')).not.toBeNull();
  });

  it('draws a trend line only from two points on, and a badge only when given an icon', () => {
    expect(card({ series: [3] }).nativeElement.querySelector('hq-chart')).toBeNull();
    expect(card({ series: [3, 5, 4] }).nativeElement.querySelector('hq-chart')).not.toBeNull();
    expect(card({}).nativeElement.querySelector('.kpi__badge')).toBeNull();
    expect(card({ icon: 'bi-people' }).nativeElement.querySelector('.kpi__badge .bi-people')).not.toBeNull();
  });

  it('marks a queue or a failure in its own colour', () => {
    expect(card({ tone: 'pending' }).nativeElement.querySelector('.kpi--pending')).not.toBeNull();
    expect(card({ tone: 'bad' }).nativeElement.querySelector('.kpi--bad')).not.toBeNull();
  });
});

describe('HqChartCard', () => {
  it('frames its content with a title, a subtitle and an actions slot', () => {
    TestBed.resetTestingModule();
    @Component({
      standalone: true,
      imports: [HqChartCard],
      template: `<hq-chart-card title="Fleet status" subtitle="Today"><a actions href="#">View fleet</a><p class="inside">chart</p></hq-chart-card>`,
    })
    class Host {}
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.card__title')?.textContent).toBe('Fleet status');
    expect(el.querySelector('.card__subtitle')?.textContent).toBe('Today');
    expect(el.querySelector('.card__actions a')?.textContent).toBe('View fleet');
    expect(el.querySelector('.card__body .inside')).not.toBeNull();
  });
});
