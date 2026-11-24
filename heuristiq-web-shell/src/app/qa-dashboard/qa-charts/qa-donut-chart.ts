import { Component, Input, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqBarListItem, HqCommonTableColumn } from 'hq-shared-lib';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { QA_STATUSES, QA_STATUS_KEYS } from '../qa-dashboard.constants';
import { formatPct } from '../qa-dashboard.helpers';
import { QaStatus, QaTestCaseTotals } from '../qa-dashboard.models';
import { QA_DONUT_TABLE_COLUMNS, QA_STATUS_TONES } from './qa-charts.constants';
import { QaDonutTableRow } from './qa-charts.models';

/**
 * The overall pass / fail / skip split. Pass, fail and skip are states, so they are drawn as a status bar list (status colours
 * with the status name beside each bar, never colour alone) under the pass share as the headline number, inside a chart card
 * that also offers the counts as a table.
 */
@Component({
  selector: 'app-qa-donut-chart',
  standalone: false,
  templateUrl: './qa-donut-chart.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-charts.scss'],
})
export class QaDonutChart implements OnInit, OnChanges, OnDestroy {
  private readonly translate = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();

  @Input() totals: Pick<QaTestCaseTotals, 'passed' | 'failed' | 'skipped'> = { passed: 0, failed: 0, skipped: 0 };
  /** Already-translated card title and subtitle. */
  @Input() title = '';
  @Input() subtitle = '';

  readonly statuses = QA_STATUSES;

  total = 0;
  successText = '0';
  /** One bar per status, coloured by its status tone. */
  items: HqBarListItem[] = [];
  columns: HqCommonTableColumn<QaDonutTableRow>[] = [];
  /** The counts as rows of the table view, one per status (status name already translated). */
  tableRows: QaDonutTableRow[] = [];

  ngOnInit(): void {
    watchColumnsUntil(this.translate, QA_DONUT_TABLE_COLUMNS, (columns) => (this.columns = columns), this.destroy$);
    this.translate.onLangChange.pipe(takeUntil(this.destroy$)).subscribe(() => this.build());
  }

  ngOnChanges(): void {
    this.build();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  countOf(status: QaStatus): number {
    return status === 'PASSED' ? this.totals.passed : status === 'FAILED' ? this.totals.failed : this.totals.skipped;
  }

  private build(): void {
    this.total = this.totals.passed + this.totals.failed + this.totals.skipped;
    this.successText = formatPct(this.total > 0 ? (this.totals.passed / this.total) * 100 : 0);
    this.items = this.statuses.map((status) => ({
      label: this.translate.instant(QA_STATUS_KEYS[status]),
      value: this.countOf(status),
      tone: QA_STATUS_TONES[status],
    }));
    this.tableRows = this.statuses.map((status) => ({ status: this.translate.instant(QA_STATUS_KEYS[status]), count: this.countOf(status) }));
  }
}
