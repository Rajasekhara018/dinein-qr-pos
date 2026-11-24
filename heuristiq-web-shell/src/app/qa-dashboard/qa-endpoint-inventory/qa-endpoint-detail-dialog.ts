import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { QA_DASHBOARD_PRIVILEGES, QA_ROUTES, QA_STATUS_KEYS, QaTone } from '../qa-dashboard.constants';
import { labelFor, statusTone } from '../qa-dashboard.helpers';
import { QA_ENDPOINT_TEST_CASE_SEARCH_PARAM, QA_ENDPOINT_TEST_COLUMNS } from './qa-endpoint-inventory.constants';
import { methodClass } from './qa-endpoint-inventory.helpers';
import { QaEndpointDetail, QaEndpointItem, QaEndpointTest } from './qa-endpoint-inventory.models';
import { QaEndpointService } from './qa-endpoint-inventory.service';

/**
 * One endpoint of the inventory: what it is (description, privilege, operation) and the test cases that called it, each
 * with its last status and a link to its row in the test case inventory. The row passed in is shown at once; the rest is loaded.
 */
@Component({
  selector: 'app-qa-endpoint-detail-dialog',
  standalone: false,
  templateUrl: './qa-endpoint-detail-dialog.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-endpoint-inventory.scss'],
})
export class QaEndpointDetailDialog implements OnInit, OnDestroy {
  private readonly svc = inject(QaEndpointService);
  private readonly ref = inject(MatDialogRef<QaEndpointDetailDialog>);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  readonly endpoint: QaEndpointItem = inject(MAT_DIALOG_DATA);
  readonly privileges = QA_DASHBOARD_PRIVILEGES;
  readonly testCasesRoute = QA_ROUTES.testCases;

  detail: QaEndpointDetail | null = null;
  isLoading = false;
  hasError = false;
  columns: HqCommonTableColumn<QaEndpointTest>[] = [];
  /** Class of every test case row (the row hook the specs and the UI suite look for). */
  readonly rowClassOf = (): string => 'qa-test-row';

  ngOnInit(): void {
    watchColumnsUntil(this.translate, QA_ENDPOINT_TEST_COLUMNS, (columns) => (this.columns = columns), this.destroy$);
    this.isLoading = true;
    this.svc
      .detail(this.endpoint.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (detail) => {
          this.detail = detail;
          this.isLoading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.hasError = true;
          this.isLoading = false;
          this.cdr.markForCheck();
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  close(): void {
    this.ref.close();
  }

  methodClass(method: string): string {
    return methodClass(method);
  }

  /** The query parameters that open the test case inventory on one test case in the API workspace. */
  testCaseParams(test: QaEndpointTest): Record<string, string> {
    return { workspace: 'API', [QA_ENDPOINT_TEST_CASE_SEARCH_PARAM]: test.testKey };
  }

  statusLabel(status: string): string {
    return labelFor((k) => this.translate.instant(k), QA_STATUS_KEYS, status);
  }

  statusTone(status: string): QaTone {
    return statusTone(status);
  }

  trackByTest(_: number, test: QaEndpointTest): string {
    return test.id || test.testKey;
  }
}
