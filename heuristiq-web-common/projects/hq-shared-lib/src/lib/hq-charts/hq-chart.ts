import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, NgZone, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import type { ApexOptions } from 'apexcharts';
// Icicle is an opt-in renderer, registered on the same ApexCharts core used by ng-apexcharts.
import 'apexcharts/icicle';
import { ChartComponent } from 'ng-apexcharts';
import { HqChartPalette, HqChartSpec, buildChartOptions, chartIsEmpty, readChartPalette } from './hq-chart.options';
import { HQ_CHART_LICENSE, HQ_CHART_TEXTS, hqChartText } from './hq-chart.texts';
import { hqChartReducedMotion, hqWatchChartTheme } from './hq-chart.theme';

/**
 * One ApexCharts chart in the page's theme, for every chart type the dashboards use (see {@link HqChartSpec} for the series shape
 * of each type). Colours come from the validated chart palette for the current light/dark mode and are read again when the theme
 * changes, so the chart always matches the page around it. Standalone, so an app imports it only in the lazy screen that draws
 * charts and pays for ApexCharts only there.
 */
@Component({
  selector: 'hq-chart',
  standalone: true,
  imports: [ChartComponent],
  templateUrl: './hq-chart.html',
  styleUrl: './hq-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqChart implements OnInit, OnChanges, OnDestroy {
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly zone = inject(NgZone);
  private readonly texts = inject(HQ_CHART_TEXTS, { optional: true });
  private readonly license = inject(HQ_CHART_LICENSE, { optional: true });
  private stopWatching: () => void = () => undefined;

  @Input({ required: true }) spec!: HqChartSpec;
  /** Shown instead of a chart when there is nothing to draw (defaults to the "no data" text). */
  @Input() emptyText = '';
  /** Accessible name of the chart (read by a screen reader instead of the drawing). */
  @Input() ariaLabel = '';

  options: ApexOptions | null = null;
  empty = false;
  private palette: HqChartPalette | null = null;

  get height(): number {
    return this.spec?.height ?? (this.spec?.sparkline ? 48 : 280);
  }

  get emptyMessage(): string {
    return this.emptyText || hqChartText(this.texts, 'noData');
  }

  ngOnInit(): void {
    this.palette = this.readPalette();
    this.build();
    this.stopWatching = hqWatchChartTheme(this.zone, () => {
      this.palette = this.readPalette();
      this.build();
    });
  }

  ngOnChanges(): void {
    if (this.palette) {
      this.build();
    }
  }

  ngOnDestroy(): void {
    this.stopWatching();
  }

  private readPalette(): HqChartPalette {
    return {
      ...readChartPalette(), otherLabel: hqChartText(this.texts, 'other'), noDataLabel: hqChartText(this.texts, 'noData'), license: this.license,
    };
  }

  private build(): void {
    if (!this.spec) {
      return;
    }
    this.empty = chartIsEmpty(this.spec);
    this.options = this.empty ? null : buildChartOptions(this.spec, this.palette ?? this.readPalette(), hqChartReducedMotion());
    this.cdr.markForCheck();
  }
}
