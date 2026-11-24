import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqDateRange } from 'hq-shared-lib';
import { Subject } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';

import { QA_ENVIRONMENT_KEYS, QA_SEARCH_DEBOUNCE_MS, QA_STATUSES, QA_STATUS_KEYS, QA_SUITES, QA_SUITE_KEYS } from '../qa-dashboard.constants';
import { emptyFilters, hasActiveFilters, labelFor, subModulesOf } from '../qa-dashboard.helpers';
import { QaFacets, QaFilters } from '../qa-dashboard.models';

/**
 * The filter row above a QA list or chart: search, module, sub-module, status, suite, environment, release and a date
 * range. A screen switches on the controls it needs; the bar tells the screen the new filter set (the search text after a short pause).
 */
@Component({
  selector: 'app-qa-filter-bar',
  standalone: false,
  templateUrl: './qa-filter-bar.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-filter-bar.scss'],
})
export class QaFilterBar implements OnInit, OnDestroy {
  private readonly translate = inject(TranslateService);
  private readonly destroy$ = new Subject<void>();
  private readonly search$ = new Subject<string>();

  @Input() facets: QaFacets | null = null;
  @Input() filters: QaFilters = emptyFilters();
  @Input() showSearch = false;
  @Input() showModule = false;
  @Input() showStatus = false;
  @Input() showSuite = false;
  @Input() showEnvironment = true;
  @Input() showRelease = true;
  @Input() showRange = false;
  @Output() filtersChange = new EventEmitter<QaFilters>();

  readonly statuses = QA_STATUSES;
  /** The chosen period (a preset such as "current month", or custom dates); it fills filters.from / filters.to. */
  range: HqDateRange | null = null;
  readonly suites = QA_SUITES;
  searchText = '';

  ngOnInit(): void {
    this.searchText = this.filters.q;
    this.search$.pipe(debounceTime(QA_SEARCH_DEBOUNCE_MS), takeUntil(this.destroy$)).subscribe((q) => this.update({ q }));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get subModules(): string[] {
    return subModulesOf(this.facets, this.filters.module);
  }

  /** Whether anything besides the period narrows the data (the period always has a value, so it does not count). */
  get isFiltered(): boolean {
    return hasActiveFilters(this.filters, this.showRange ? ['from', 'to'] : []);
  }

  /** A new period: the charts and lists reload for its dates (inclusive), sent to the API as from / to. */
  onRange(range: HqDateRange): void {
    this.range = range;
    if (range.from !== this.filters.from || range.to !== this.filters.to) this.update({ from: range.from, to: range.to });
  }

  onSearchInput(text: string): void {
    this.searchText = text;
    this.search$.next(text);
  }

  /** Changing the module forgets the sub-module (it belongs to the old module). */
  onModuleChange(module: string): void {
    this.update({ module, subModule: '' });
  }

  update(patch: Partial<QaFilters>): void {
    this.filtersChange.emit({ ...this.filters, ...patch });
  }

  /** Clears every filter but keeps the chosen period. */
  clear(): void {
    this.searchText = '';
    this.filtersChange.emit(emptyFilters(this.showRange ? { from: this.filters.from, to: this.filters.to } : {}));
  }

  statusLabel(status: string): string {
    return labelFor((k) => this.translate.instant(k), QA_STATUS_KEYS, status);
  }

  suiteLabel(suite: string): string {
    return labelFor((k) => this.translate.instant(k), QA_SUITE_KEYS, suite);
  }

  environmentLabel(environment: string): string {
    return labelFor((k) => this.translate.instant(k), QA_ENVIRONMENT_KEYS, environment);
  }
}
