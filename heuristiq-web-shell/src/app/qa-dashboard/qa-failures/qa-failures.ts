import { Component } from '@angular/core';
import { HqBarListItem, HqCommonTableColumn } from 'hq-shared-lib';
import { takeUntil } from 'rxjs/operators';

import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { emptyFilters, qaDefaultPeriod, normalizeFailures, shortMessage } from '../qa-dashboard.helpers';
import { QaFailingCase, QaFailures, QaFilters } from '../qa-dashboard.models';
import { QaScreenBase } from '../qa-screen-base';
import { QA_TOP_FAILING_COLUMNS } from './qa-failures.constants';

/**
 * QA automation > Failure analysis: what failed, grouped by cause and by module, the test cases that fail most and the
 * ones that both pass and fail (flaky), so the first thing to fix is clear.
 */
@Component({
  selector: 'app-qa-failures',
  standalone: false,
  templateUrl: './qa-failures.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-failures.scss'],
})
export class QaFailuresPage extends QaScreenBase {
  filters: QaFilters = emptyFilters(qaDefaultPeriod(this.timezones.effectiveTimezone()));
  failures: QaFailures = normalizeFailures(null);
  loaded = false;
  topFailingColumns: HqCommonTableColumn<QaFailingCase>[] = [];
  /** Failures per cause and per module as bar-list items (also the rows of each card's table view). */
  categoryItems: HqBarListItem[] = [];
  moduleItems: HqBarListItem[] = [];
  categoryColumns: HqCommonTableColumn<HqBarListItem>[] = [];
  moduleColumns: HqCommonTableColumn<HqBarListItem>[] = [];

  override ngOnInit(): void {
    watchColumnsUntil(this.translate, QA_TOP_FAILING_COLUMNS, (columns) => (this.topFailingColumns = columns), this.destroy$);
    this.translate.onLangChange.pipe(takeUntil(this.destroy$)).subscribe(() => this.buildBars());
    super.ngOnInit();
  }

  protected onWorkspaceChange(): void {
    this.filters = emptyFilters(qaDefaultPeriod(this.timezones.effectiveTimezone()));
    this.facets = null;
    this.loadFacets();
    this.reload();
  }

  reload(): void {
    if (!this.access.can(this.privileges.failureRead)) return;
    this.load(
      this.svc.failures(this.workspace, this.filters),
      (failures) => {
        this.failures = failures;
        this.loaded = true;
        this.buildBars();
      },
      () => {
        this.failures = normalizeFailures(null);
        this.loaded = false;
        this.buildBars();
      },
    );
  }

  onFiltersChange(filters: QaFilters): void {
    this.filters = filters;
    this.reload();
  }

  /** The two failure breakdowns as ranked bars (one scale per chart), plus the columns of their table views. */
  private buildBars(): void {
    const t = (key: string, params?: object): string => this.translate.instant(key, params);
    this.categoryItems = this.failures.byCategory.map((c) => ({ label: this.categoryLabel(c.category), value: c.count }));
    this.moduleItems = this.failures.byModule.map((m) => ({
      label: m.module,
      value: m.failed,
      hint: t('qaDashboard.failures.failedOfTotal', { failed: m.failed, total: m.total }),
    }));
    this.categoryColumns = [
      { key: 'label', label: t('qaDashboard.failures.byCategory') },
      { key: 'value', label: t('qaDashboard.columns.count'), className: 'qa-num' },
    ];
    this.moduleColumns = [
      { key: 'label', label: t('qaDashboard.failures.byModule') },
      { key: 'hint', label: t('qaDashboard.columns.count'), className: 'qa-num' },
    ];
  }

  get isEmpty(): boolean {
    return this.loaded && this.failures.totalFailed === 0 && this.failures.flaky.length === 0;
  }

  message(row: QaFailingCase): string {
    return shortMessage(row.failureMessage);
  }
}
