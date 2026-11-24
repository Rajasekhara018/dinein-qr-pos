import { Component, Input, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqChartSpec, HqCommonTableColumn } from 'hq-shared-lib';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { QA_STATUSES, QA_STATUS_KEYS } from '../qa-dashboard.constants';
import { buildBars } from '../qa-dashboard.helpers';
import { QaBar, QaReleasePoint, QaStatus } from '../qa-dashboard.models';
import { QA_CHART_HEIGHT, QA_RELEASE_TABLE_COLUMNS, QA_STATUS_SLOTS } from './qa-charts.constants';
import { QaReleaseTableRow } from './qa-charts.models';

/**
 * Pass / fail / skip per release as stacked bars (the newest twelve), drawn by the shared chart in the page's theme: a legend
 * names the three statuses, hovering a bar gives the numbers, and the card's table view offers the same data.
 */
@Component({
  selector: 'app-qa-release-chart',
  standalone: false,
  templateUrl: './qa-release-chart.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-charts.scss'],
})
export class QaReleaseChart implements OnInit, OnChanges, OnDestroy {
  private readonly translate = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  @Input() releases: QaReleasePoint[] = [];
  /** Already-translated card title and subtitle. */
  @Input() title = '';
  @Input() subtitle = '';

  bars: QaBar[] = [];
  spec: HqChartSpec | null = null;
  columns: HqCommonTableColumn<QaReleaseTableRow>[] = [];
  /** The bars as rows of the table view: one per release with its count per status. */
  tableRows: QaReleaseTableRow[] = [];

  ngOnInit(): void {
    watchColumnsUntil(this.translate, QA_RELEASE_TABLE_COLUMNS, (columns) => (this.columns = columns), this.destroy$);
    this.translate.onLangChange.pipe(takeUntil(this.destroy$)).subscribe(() => this.buildSpec());
  }

  ngOnChanges(): void {
    this.bars = buildBars(this.releases);
    this.tableRows = this.bars.map((bar) => ({
      release: bar.release,
      PASSED: this.countOf(bar, 'PASSED'),
      FAILED: this.countOf(bar, 'FAILED'),
      SKIPPED: this.countOf(bar, 'SKIPPED'),
    }));
    this.buildSpec();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  countOf(bar: QaBar, status: QaStatus): number {
    return bar.segments.find((s) => s.status === status)?.value ?? 0;
  }

  private buildSpec(): void {
    this.spec = {
      type: 'bar',
      stacked: true,
      height: QA_CHART_HEIGHT,
      labels: this.bars.map((bar) => bar.release),
      series: QA_STATUSES.map((status) => ({
        name: this.translate.instant(QA_STATUS_KEYS[status]),
        data: this.bars.map((bar) => this.countOf(bar, status)),
        slot: QA_STATUS_SLOTS[status],
      })),
      showLegend: true,
    };
  }
}
