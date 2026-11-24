import { DOCUMENT } from '@angular/common';
import { Component, inject } from '@angular/core';
import { HqCommonTableColumn } from 'hq-shared-lib';

import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { QA_HEADLINE_KEYS, QA_LIST_LIMIT } from '../qa-dashboard.constants';
import { emptyFilters, labelFor, normalizeExecutiveSummary } from '../qa-dashboard.helpers';
import { QaExecutiveSummary, QaFilters, QaModuleTotals } from '../qa-dashboard.models';
import { QaScreenBase } from '../qa-screen-base';
import { QA_EXECUTIVE_MODULE_COLUMNS } from './qa-executive-summary.constants';

/**
 * QA automation > Executive summary: one page for management, made to be printed. The headline is a code from the server
 * that this screen translates; nothing is invented when there are no runs.
 */
@Component({
  selector: 'app-qa-executive-summary',
  standalone: false,
  templateUrl: './qa-executive-summary.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-executive-summary.scss'],
})
export class QaExecutiveSummaryPage extends QaScreenBase {
  private readonly document = inject(DOCUMENT);

  filters: QaFilters = emptyFilters();
  summary: QaExecutiveSummary = normalizeExecutiveSummary(null);
  loaded = false;
  moduleColumns: HqCommonTableColumn<QaModuleTotals>[] = [];

  override ngOnInit(): void {
    watchColumnsUntil(this.translate, QA_EXECUTIVE_MODULE_COLUMNS, (columns) => (this.moduleColumns = columns), this.destroy$);
    super.ngOnInit();
  }

  protected onWorkspaceChange(): void {
    this.filters = emptyFilters();
    this.facets = null;
    this.loadFacets();
    this.reload();
  }

  reload(): void {
    if (!this.access.can(this.privileges.executiveSummaryRead)) return;
    this.load(
      this.svc.executiveSummary(this.workspace, this.filters),
      (summary) => {
        this.summary = summary;
        this.loaded = true;
      },
      () => {
        this.summary = normalizeExecutiveSummary(null);
        this.loaded = false;
      },
    );
  }

  onFiltersChange(filters: QaFilters): void {
    this.filters = filters;
    this.reload();
  }

  print(): void {
    this.document.defaultView?.print();
  }

  headlineText(code: string): string {
    return labelFor((k) => this.translate.instant(k), QA_HEADLINE_KEYS, code);
  }

  /** Change in failures against the previous release: positive = more failures. */
  get failureDelta(): number | null {
    const previous = this.summary.previousRelease;
    return previous ? this.summary.totals.failed - previous.totals.failed : null;
  }

  get risks() {
    return this.summary.topRisks.slice(0, QA_LIST_LIMIT);
  }

  get slowest() {
    return this.summary.slowest.slice(0, 5);
  }

  get hasRuns(): boolean {
    return this.loaded && this.summary.totals.total > 0;
  }
}
