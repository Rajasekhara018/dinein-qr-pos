import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  model,
  output,
} from '@angular/core';
import { BreakpointService } from '../../core/ui/breakpoint.service';

export type SortDirection = 'asc' | 'desc';

export interface DataTableSort {
  key: string;
  direction: SortDirection;
}

export interface DataTableColumn<T> {
  key: string;
  header: string;
  /** Reads the cell's display value from a row. Defaults to `row[key]`. */
  value?: (row: T) => string | number | null | undefined;
  sortable?: boolean;
  align?: 'start' | 'end' | 'center';
  /** Hide this column under the 768px stacked-card layout (e.g. a column that duplicates the card title). */
  hideOnCard?: boolean;
}

/**
 * Generic data table: sortable headers, sticky header, row hover, optional row selection, pagination and a
 * density toggle. Collapses to stacked cards under 768px (each row becomes a card of "label: value" pairs) so it
 * never forces horizontal scrolling on a phone.
 *
 * Sorting and pagination are presentational only — this component does not slice `rows` itself when
 * `serverSide` is set (the caller already gave it one page); otherwise it sorts and paginates `rows` in place.
 *
 * ```html
 * <app-data-table
 *   [columns]="columns"
 *   [rows]="items()"
 *   [(sort)]="sort"
 *   [(page)]="page"
 *   [pageSize]="20"
 *   rowIdKey="id"
 *   [(selected)]="selectedIds"
 * />
 * ```
 */
@Component({
  selector: 'app-data-table',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './data-table.html',
})
export class DataTable<T extends Record<string, unknown>> {
  readonly columns = input.required<DataTableColumn<T>[]>();
  readonly rows = input.required<T[]>();
  readonly caption = input('');
  readonly rowIdKey = input<string>('id');
  readonly selectable = input(false, { transform: booleanAttribute });
  readonly selected = model<Set<unknown>>(new Set());
  readonly sort = model<DataTableSort | null>(null);
  readonly page = model(1);
  readonly pageSize = input(20);
  /** When true, `rows` is assumed to already be the current page/sort from the server — no client slicing. */
  readonly serverSide = input(false, { transform: booleanAttribute });
  readonly totalRows = input<number | null>(null);
  readonly density = model<'comfortable' | 'compact'>('comfortable');
  readonly emptyMessage = input('No rows to show.');
  readonly rowClick = output<T>();

  protected readonly breakpoint = inject(BreakpointService);
  protected readonly isCard = this.breakpoint.isHandset;

  private readonly sortedRows = computed<T[]>(() => {
    const rows = this.rows();
    const sort = this.sort();
    if (this.serverSide() || !sort) return rows;
    const col = this.columns().find((c) => c.key === sort.key);
    const read = col?.value ?? ((row: T) => row[sort.key] as string | number | null | undefined);
    const dir = sort.direction === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
      const av = read(a);
      const bv = read(b);
      if (av == null && bv == null) return 0;
      if (av == null) return -1 * dir;
      if (bv == null) return 1 * dir;
      if (av < bv) return -1 * dir;
      if (av > bv) return 1 * dir;
      return 0;
    });
  });

  protected readonly pageCount = computed(() => {
    const total = this.totalRows() ?? this.sortedRows().length;
    return Math.max(1, Math.ceil(total / this.pageSize()));
  });

  protected readonly pagedRows = computed<T[]>(() => {
    const sorted = this.sortedRows();
    if (this.serverSide()) return sorted;
    const start = (this.page() - 1) * this.pageSize();
    return sorted.slice(start, start + this.pageSize());
  });

  protected readonly allOnPageSelected = computed(() => {
    const rows = this.pagedRows();
    if (!rows.length) return false;
    const sel = this.selected();
    return rows.every((row) => sel.has(this.idOf(row)));
  });

  protected idOf(row: T): unknown {
    return row[this.rowIdKey()];
  }

  protected cellValue(row: T, col: DataTableColumn<T>): string | number {
    const v = col.value ? col.value(row) : (row[col.key] as string | number | null | undefined);
    return v ?? '—';
  }

  protected toggleSort(col: DataTableColumn<T>): void {
    if (!col.sortable) return;
    const current = this.sort();
    if (!current || current.key !== col.key) {
      this.sort.set({ key: col.key, direction: 'asc' });
    } else if (current.direction === 'asc') {
      this.sort.set({ key: col.key, direction: 'desc' });
    } else {
      this.sort.set(null);
    }
  }

  protected toggleRow(row: T): void {
    const id = this.idOf(row);
    const next = new Set(this.selected());
    if (next.has(id)) next.delete(id);
    else next.add(id);
    this.selected.set(next);
  }

  protected toggleAllOnPage(): void {
    const next = new Set(this.selected());
    const rows = this.pagedRows();
    if (this.allOnPageSelected()) {
      rows.forEach((row) => next.delete(this.idOf(row)));
    } else {
      rows.forEach((row) => next.add(this.idOf(row)));
    }
    this.selected.set(next);
  }

  protected goToPage(page: number): void {
    this.page.set(Math.min(Math.max(1, page), this.pageCount()));
  }

  protected toggleDensity(): void {
    this.density.set(this.density() === 'comfortable' ? 'compact' : 'comfortable');
  }
}
