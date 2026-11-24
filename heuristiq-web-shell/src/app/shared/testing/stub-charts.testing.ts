import { Component, Input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HqChart } from 'hq-shared-lib';
import { ChartComponent } from 'ng-apexcharts';

/** Stands in for the real ApexCharts renderer, which needs a real browser to draw; the chart's options are still built and can be read. */
@Component({ selector: 'apx-chart', standalone: true, template: '<span class="stub-chart"></span>' })
export class StubApexChart {
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
  @Input() annotations: unknown;
  @Input() states: unknown;
  @Input() theme: unknown;
}

/** Call before creating a component that draws `hq-chart`s, so the specs do not need a canvas. */
export function stubHqCharts(): void {
  TestBed.overrideComponent(HqChart, { remove: { imports: [ChartComponent] }, add: { imports: [StubApexChart] } });
}
