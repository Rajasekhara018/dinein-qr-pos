import { ChangeDetectorRef, Component, Inject, OnDestroy, OnInit, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subscription } from 'rxjs';
import {
  HqAdvancedQuery, HqLookupColumn, HqLookupDialogData, HqLookupOperator, HqLookupTable,
} from '../hq-common.models';
import { HqCommonSearchService } from '../hq-common-search.service';
import { HQ_COMMON_MODEL_DEFAULT_TEXTS, HQ_COMMON_MODEL_TEXTS, HqCommonModelTexts } from './hq-common-model.texts';

/**
 * Lookup dialog (tou-common-model): quick search on a chosen column, advanced field/operator/
 * value filtering with AND/OR, paged results, single or multi select. Open it through
 * HqCommonSearchService.openLookup(); it closes with the updated HqLookupDialogData.
 */
@Component({
  selector: 'hq-common-model',
  standalone: false,
  templateUrl: './hq-common-model.html',
  styleUrl: './hq-common-model.scss',
})
export class HqCommonModel implements OnInit, OnDestroy {
  /** The popup's own words: the app's HQ_COMMON_MODEL_TEXTS, else English. */
  readonly t: HqCommonModelTexts = inject(HQ_COMMON_MODEL_TEXTS, { optional: true }) ?? HQ_COMMON_MODEL_DEFAULT_TEXTS;

  table!: HqLookupTable;
  searchColumns: HqLookupColumn[] = [];
  displayedColumns: string[] = [];

  rows: any[] = [];
  total = 0;
  pageIndex = 0;
  pageSize = 10;
  readonly pageSizeOptions = [10, 20, 50];

  search = '';
  lastSearch = '';
  searchField = '';
  loading = false;
  errorMessage = '';

  isAdvanced = false;
  advancedFilters: HqAdvancedQuery[] = [];
  get operators(): Array<{ value: HqLookupOperator; label: string }> {
    return [
      { value: 'EQ', label: this.t.equals() },
      { value: 'NE', label: this.t.notEquals() },
      { value: 'LIKE', label: this.t.contains() },
    ];
  }

  private sub?: Subscription;

  constructor(
    public dialogRef: MatDialogRef<HqCommonModel, HqLookupDialogData>,
    @Inject(MAT_DIALOG_DATA) public data: HqLookupDialogData,
    private searchService: HqCommonSearchService,
    private cdr: ChangeDetectorRef,
  ) {}

  get hasPreFilters(): boolean {
    return !!this.data.filters?.length;
  }

  ngOnInit(): void {
    this.table = this.searchService.getTable(this.data.lookupTable);
    this.searchColumns = this.table.columns.filter((c) => c.searchable !== false);
    this.displayedColumns = [...this.table.columns.map((c) => c.key), 'actions'];
    this.searchField = this.table.defaultSearchField;
    this.data.selected = [...(this.data.selected ?? [])];
    this.load();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  // ── Loading ──────────────────────────────────────────────────────────────
  load(pageIndex = this.pageIndex): void {
    this.sub?.unsubscribe();
    this.loading = true;
    this.errorMessage = '';
    this.pageIndex = pageIndex;
    const advanced = this.isAdvanced ? this.advancedFilters : [];
    this.sub = this.searchService
      .query(this.table.id, { page: pageIndex, size: this.pageSize, search: this.isAdvanced ? '' : this.search, searchField: this.searchField },
        advanced, this.data.filters ?? [])
      .subscribe({
        next: (page) => {
          this.rows = page.rows;
          this.total = page.total;
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.rows = [];
          this.total = 0;
          this.loading = false;
          this.errorMessage = err?.error?.message ?? this.t.loadFailed();
          this.cdr.markForCheck();
        },
      });
  }

  /** A page or page-size change from the table footer (page is 0-based). */
  onPage(event: { pageIndex: number; pageSize: number }): void {
    this.pageSize = event.pageSize;
    this.load(event.pageIndex);
  }

  // ── Quick search ─────────────────────────────────────────────────────────
  selectSearchField(key: string): void {
    this.searchField = key;
  }

  runSearch(): void {
    this.lastSearch = this.search.trim();
    this.load(0);
  }

  clearResult(): void {
    this.search = '';
    this.lastSearch = '';
    if (this.isAdvanced) this.advancedFilters = [this.newFilter()];
    this.load(0);
  }

  // ── Advanced search ──────────────────────────────────────────────────────
  openAdvanced(): void {
    this.isAdvanced = true;
    this.search = '';
    this.lastSearch = '';
    if (!this.advancedFilters.length) this.advancedFilters = [this.newFilter()];
  }

  addCondition(): void {
    this.advancedFilters = [...this.advancedFilters, { ...this.newFilter(), condition: 'OR' }];
  }

  removeCondition(index: number): void {
    this.advancedFilters = this.advancedFilters.filter((_, i) => i !== index);
    if (!this.advancedFilters.length) this.advancedFilters = [this.newFilter()];
  }

  runAdvanced(): void {
    this.load(0);
  }

  normalSearch(): void {
    this.isAdvanced = false;
    this.advancedFilters = [];
    this.load(0);
  }

  // ── Selection ────────────────────────────────────────────────────────────
  cell(row: any, key: string): any {
    return this.searchService.read(row, key);
  }

  isSelected(row: any): boolean {
    const key = String(this.cell(row, this.table.keyField));
    return this.data.multiSelect
      ? !!this.data.selected?.some((s) => s.key === key)
      : this.data.modelKey === key;
  }

  pick(row: any): void {
    const key = String(this.cell(row, this.table.keyField) ?? '');
    const value = String(this.cell(row, this.table.displayField) ?? key);
    if (this.data.multiSelect) {
      if (!this.data.selected!.some((s) => s.key === key)) {
        this.data.selected = [...this.data.selected!, { key, value, element: row }];
        this.data.displayValue = this.t.values(this.data.selected.length);
      }
      return; // multi-select stays open; Done closes it
    }
    this.data.modelKey = key;
    this.data.displayValue = value;
    this.data.element = row;
    this.dialogRef.close(this.data);
  }

  unpick(key: string): void {
    this.data.selected = (this.data.selected ?? []).filter((s) => s.key !== key);
    this.data.displayValue = this.data.selected.length ? this.t.values(this.data.selected.length) : '';
  }

  clearSelection(): void {
    this.data.modelKey = '';
    this.data.displayValue = '';
    this.data.element = undefined;
    this.data.selected = [];
  }

  close(): void {
    this.dialogRef.close(this.data);
  }

  private newFilter(): HqAdvancedQuery {
    return { fieldName: this.table.defaultSearchField, operator: 'LIKE', value: '', condition: 'AND' };
  }
}
