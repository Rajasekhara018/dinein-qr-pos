import { Component } from '@angular/core';
import { HqCommonTableColumn } from 'hq-shared-lib';

import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { QaTestSetExecution } from '../qa-dashboard.models';
import { QaScreenBase } from '../qa-screen-base';
import { QA_TEST_SET_RUN_COLUMNS } from './qa-test-set-runs.constants';

/** QA automation > Execution summary, test set based: for each test set, its latest run and the results of it. */
@Component({
  selector: 'app-qa-test-set-runs',
  standalone: false,
  templateUrl: './qa-test-set-runs.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-test-set-runs.scss'],
})
export class QaTestSetRunsPage extends QaScreenBase {
  /** Every test set's latest run, as the API sends them; the table shows the ones of the open workspace. */
  all: QaTestSetExecution[] = [];
  loaded = false;
  columns: HqCommonTableColumn<QaTestSetExecution>[] = [];
  /** Class of every test set row (the row hook the specs and the UI suite look for). */
  readonly rowClassOf = (): string => 'qa-set-row';

  override ngOnInit(): void {
    watchColumnsUntil(this.translate, QA_TEST_SET_RUN_COLUMNS, (columns) => (this.columns = columns), this.destroy$);
    super.ngOnInit();
  }

  get executions(): QaTestSetExecution[] {
    return this.all.filter((e) => e.workspace === this.workspace);
  }

  protected onWorkspaceChange(): void {
    this.reload();
  }

  reload(): void {
    if (!this.access.can(this.privileges.executionList)) return;
    this.load(
      this.svc.testSetExecutions(),
      (executions) => {
        this.all = executions;
        this.loaded = true;
      },
      () => {
        this.all = [];
        this.loaded = false;
      },
    );
  }

  trackBySet(_: number, execution: QaTestSetExecution): string {
    return execution.testSetId;
  }
}
