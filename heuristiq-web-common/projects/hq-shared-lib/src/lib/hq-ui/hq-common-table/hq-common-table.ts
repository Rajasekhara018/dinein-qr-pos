import { Component, ContentChild, ContentChildren, Directive, EventEmitter, Input, Output, QueryList, TemplateRef, inject } from '@angular/core';
import { HQ_COMMON_TABLE_DEFAULT_TEXTS, HQ_COMMON_TABLE_TEXTS, HqCommonTableTexts } from './hq-common-table.texts';

export interface HqCommonTableColumn<T extends object = Record<string, unknown>> {
  /** Dot-path into the row, e.g. "code" or "address.city". */
  key: string;
  /** Already-translated column heading. */
  label: string;
  render?: (row: T) => string;
  /** Header click sorts the rows shown (this page) by the cell text; also emits `sortChange` for server-side sorting. */
  sortable?: boolean;
  /** Shows the full cell text as a tooltip (for long, truncated values). */
  tooltip?: boolean;
  /** Extra CSS class(es) for this column's header and cells, e.g. to give it a fixed width. */
  className?: string;
}

export type HqSortDirection = 'asc' | 'desc';

export interface HqCommonTableSort {
  key: string;
  direction: HqSortDirection;
}

/** Context handed to the projected `#rowActions` template: `let-row` is the row itself. */
export interface HqCommonTableRowContext<T extends object> {
  $implicit: T;
}

/** Context of a projected cell template: `let-row` is the row. */
export interface HqCommonTableCellContext<T extends object> {
  $implicit: T;
}

/**
 * Marks an `<ng-template>` as the content of one column's cells, for cells that need markup (a badge, a link, two lines)
 * instead of text: `<ng-template hqCell="status" let-row><span class="badge">{{ row.status }}</span></ng-template>`.
 * The key is the column's `key`; a column without such a template renders its text as before.
 */
@Directive({ selector: 'ng-template[hqCell]', standalone: false })
export class HqCommonTableCell<T extends object = Record<string, unknown>> {
  @Input('hqCell') key = '';
  constructor(public readonly template: TemplateRef<HqCommonTableCellContext<T>>) {}
}

/**
 * Marks an `<ng-template>` as the content of one column's HEADER cell, for a header that needs markup (a select-all
 * checkbox): `<ng-template hqHead="select" let-col><button ...></button></ng-template>`. The key is the column's `key`.
 */
@Directive({ selector: 'ng-template[hqHead]', standalone: false })
export class HqCommonTableHead<T extends object = Record<string, unknown>> {
  @Input('hqHead') key = '';
  constructor(public readonly template: TemplateRef<{ $implicit: HqCommonTableColumn<T> }>) {}
}

/**
 * Reusable table shell used across every list screen -- renders columns/rows generically and
 * projects an optional per-row actions template via #rowActions.
 * Usage:
 *   <hq-common-table [columns]="columns" [rows]="rows" [isLoading]="isLoading">
 *     <ng-template #rowActions let-row>
 *       <button (click)="edit(row)">Edit</button>
 *     </ng-template>
 *   </hq-common-table>
 * The library has no translation dependency: pass `emptyMessage` / `errorMessage` / `actionsLabel`
 * per usage, or provide HQ_COMMON_TABLE_TEXTS once for the app-wide fallbacks.
 */
@Component({
  selector: 'hq-common-table',
  standalone: false,
  templateUrl: './hq-common-table.html',
  styleUrl: './hq-common-table.scss',
})
export class HqCommonTable<T extends object = Record<string, unknown>> {
  private readonly texts: HqCommonTableTexts =
    inject(HQ_COMMON_TABLE_TEXTS, { optional: true }) ?? HQ_COMMON_TABLE_DEFAULT_TEXTS;

  @Input() columns: HqCommonTableColumn<T>[] = [];
  @Input() rows: T[] = [];
  @Input() isLoading = false;
  @Input() hasError = false;
  /** Caller-supplied text; empty falls back to HQ_COMMON_TABLE_TEXTS. */
  @Input() emptyMessage = '';
  @Input() errorMessage = '';
  @Input() showActionsColumn = true;
  @Input() actionsLabel = '';
  /** When set, rows render as clickable and emit `rowClick` -- used to navigate to a detail page. */
  @Input() clickableRows = false;
  @Output() rowClick = new EventEmitter<T>();
  /** CSS class(es) added to a row, e.g. to tint the chosen ones; null for none. */
  @Input() rowClass: ((row: T) => string | string[] | null) | null = null;
  /** With `clickableRows`, makes each row focusable and lets Enter act like a click (a picker's rows). */
  @Input() keyboardRows = false;
  /** Index (in `rows`) of a row to highlight, e.g. the one just edited; null for none. */
  @Input() highlightIndex: number | null = null;
  /** Emitted when a sortable header is clicked: key + direction (third click clears: null). */
  @Output() sortChange = new EventEmitter<HqCommonTableSort | null>();

  /** Whether a row also shows its `#rowDetail` template underneath (an expandable row); false for every row by default. */
  @Input() isRowExpanded: (row: T) => boolean = () => false;
  /** Rows shown as a full-width strip below the row while `isRowExpanded(row)` is true. */
  @ContentChild('rowDetail', { read: TemplateRef }) rowDetailTpl?: TemplateRef<HqCommonTableCellContext<T>>;
  @ContentChildren(HqCommonTableCell) private cellDefs?: QueryList<HqCommonTableCell<T>>;
  @ContentChildren(HqCommonTableHead) private headDefs?: QueryList<HqCommonTableHead<T>>;

  sort: HqCommonTableSort | null = null;

  @ContentChild('rowActions', { read: TemplateRef }) rowActionsTpl?: TemplateRef<HqCommonTableRowContext<T>>;

  get actionsHeading(): string {
    return this.actionsLabel || this.texts.actions();
  }

  get emptyText(): string {
    return this.emptyMessage || this.texts.noRecords();
  }

  get errorText(): string {
    return this.errorMessage || this.texts.loadFailed();
  }

  /** Rows in display order: `rows` as given, or sorted by the active column. */
  get displayRows(): T[] {
    const sort = this.sort;
    if (!sort) return this.rows;
    const col = this.columns.find(c => c.key === sort.key);
    if (!col) return this.rows;
    const factor = sort.direction === 'asc' ? 1 : -1;
    return [...this.rows].sort(
      (a, b) => factor * this.cellValue(a, col).localeCompare(this.cellValue(b, col), undefined, { numeric: true }),
    );
  }

  /** Cycles a sortable column: ascending, descending, none. */
  toggleSort(col: HqCommonTableColumn<T>): void {
    if (!col.sortable) return;
    if (this.sort?.key !== col.key) this.sort = { key: col.key, direction: 'asc' };
    else if (this.sort.direction === 'asc') this.sort = { key: col.key, direction: 'desc' };
    else this.sort = null;
    this.sortChange.emit(this.sort);
  }

  /** `aria-sort` value of a header. */
  ariaSort(col: HqCommonTableColumn<T>): 'ascending' | 'descending' | 'none' | null {
    if (!col.sortable) return null;
    if (this.sort?.key !== col.key) return 'none';
    return this.sort.direction === 'asc' ? 'ascending' : 'descending';
  }

  /** The projected template for a column's cells, if the screen supplied one (`<ng-template hqCell="key">`). */
  cellTemplate(col: HqCommonTableColumn<T>): TemplateRef<HqCommonTableCellContext<T>> | null {
    return this.cellDefs?.find(def => def.key === col.key)?.template ?? null;
  }

  /** The projected template for a column's header, if the screen supplied one (`<ng-template hqHead="key">`). */
  headTemplate(col: HqCommonTableColumn<T>): TemplateRef<{ $implicit: HqCommonTableColumn<T> }> | null {
    return this.headDefs?.find(def => def.key === col.key)?.template ?? null;
  }

  /** The classes of a row, from `rowClass`. */
  classesOf(row: T): string | string[] | null {
    return this.rowClass ? this.rowClass(row) : null;
  }

  /** Enter on a focusable row acts like a click. */
  onRowKey(row: T): void {
    if (this.clickableRows && this.keyboardRows) this.rowClick.emit(row);
  }

  /** Whether a row is the highlighted one (index within the given order). */
  isHighlighted(index: number): boolean {
    return this.highlightIndex !== null && this.highlightIndex === index;
  }

  cellValue(row: T, col: HqCommonTableColumn<T>): string {
    if (col.render) return col.render(row);
    const value = this.getByPath(row, col.key);
    if (Array.isArray(value)) return value.length ? value.join(', ') : '—';
    if (value === null || value === undefined || value === '') return '—';
    if (typeof value === 'boolean') return value ? this.texts.yes() : this.texts.no();
    return String(value);
  }

  private getByPath(obj: unknown, path: string): unknown {
    return path.split('.').reduce<unknown>((acc, key) => {
      if (acc === null || acc === undefined || typeof acc !== 'object') return undefined;
      return (acc as Record<string, unknown>)[key];
    }, obj);
  }
}
