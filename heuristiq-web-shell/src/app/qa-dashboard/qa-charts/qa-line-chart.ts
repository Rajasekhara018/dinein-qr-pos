import { Component, Input, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqChartSpec, HqCommonTableColumn } from 'hq-shared-lib';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { TableColumnSpec, translateColumns } from '../../shared/table-columns.helpers';
import { QA_CHART_HEIGHT, QA_LINE_TABLE_LABEL_COLUMN } from './qa-charts.constants';
import { QaLineSeries, QaLineTableRow } from './qa-charts.models';

/**
 * One or more lines over time (oldest first) on one shared scale, drawn by the shared chart in the page's theme (crosshair
 * tooltip; a legend when there are two or more lines). The card's table view offers the same data.
 */
@Component({
  selector: 'app-qa-line-chart',
  standalone: false,
  templateUrl: './qa-line-chart.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-charts.scss'],
})
export class QaLineChart implements OnInit, OnChanges, OnDestroy {
  private readonly translate = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  @Input() series: QaLineSeries[] = [];
  /** Translation key of the chart's accessible name. */
  @Input() labelKey = '';
  /** Already-translated card title and subtitle. */
  @Input() title = '';
  @Input() subtitle = '';

  /** The x-axis labels, from the longest series. */
  labels: string[] = [];
  spec: HqChartSpec | null = null;
  /** Table view: the label column, then one column per series (rebuilt with the series and on a language switch). */
  columns: HqCommonTableColumn<QaLineTableRow>[] = [];
  tableRows: QaLineTableRow[] = [];

  ngOnInit(): void {
    this.translate.onLangChange.pipe(takeUntil(this.destroy$)).subscribe(() => this.build());
  }

  ngOnChanges(): void {
    const longest = this.series.reduce((a, b) => (b.values.length > a.length ? b.values : a), [] as QaLineSeries['values']);
    this.labels = longest.map((v) => v.label);
    this.tableRows = this.labels.map((label, i) => ({
      label,
      values: Object.fromEntries(this.series.map((s) => [s.key, s.values[i]?.value ?? null])),
    }));
    this.build();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isEmpty(): boolean {
    return this.series.every((s) => s.values.length === 0);
  }

  private build(): void {
    this.spec = {
      type: 'line',
      height: QA_CHART_HEIGHT,
      labels: this.labels,
      series: this.series.map((s) => ({
        name: this.translate.instant(s.labelKey),
        data: this.labels.map((_, i) => s.values[i]?.value ?? 0),
        slot: s.slot,
      })),
      showLegend: this.series.length > 1,
    };
    const specs: TableColumnSpec<QaLineTableRow>[] = [
      QA_LINE_TABLE_LABEL_COLUMN,
      ...this.series.map((s) => ({
        key: s.key,
        labelKey: s.labelKey,
        className: 'qa-num',
        // A series shorter than the longest one leaves its cell blank.
        render: (row: QaLineTableRow): string => {
          const value = row.values[s.key];
          return value === null || value === undefined ? '' : String(value);
        },
      })),
    ];
    this.columns = translateColumns(specs, this.translate);
  }
}
