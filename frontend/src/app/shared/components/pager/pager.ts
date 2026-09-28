import { booleanAttribute, ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/** A run of page numbers, or a `'…'` gap marker, for the numbered pagination control. */
export type PagerToken = number | '…';

/**
 * Windows down a long page range to a fixed set of tokens: always the first two and last two pages, the
 * current page and its immediate neighbours, and `'…'` for the gaps in between. Short ranges (<= 7 pages)
 * show every page.
 */
export function pagerWindow(current: number, pageCount: number): PagerToken[] {
  if (pageCount <= 7) return Array.from({ length: pageCount }, (_, i) => i + 1);
  const keep = new Set<number>([1, 2, pageCount - 1, pageCount, current - 1, current, current + 1]);
  const sorted = [...keep].filter((p) => p >= 1 && p <= pageCount).sort((a, b) => a - b);
  const tokens: PagerToken[] = [];
  let previous = 0;
  for (const p of sorted) {
    if (previous && p - previous > 1) tokens.push('…');
    tokens.push(p);
    previous = p;
  }
  return tokens;
}

/**
 * Pagination bar: "Showing X–Y of Z entries" plus first/previous/numbered-pages/next/last controls and an
 * optional rows-per-page selector. `page` is 1-based; the parent owns the actual paging (fetching the next
 * server page, or re-slicing an already-loaded array) and reacts to `(pageChange)`/`(pageSizeChange)`.
 *
 * ```html
 * <app-pager [page]="page()" [pageCount]="pageCount()" [total]="total()" [pageSize]="pageSize()"
 *            (pageChange)="goToPage($event)" />
 * ```
 *
 * By default it draws its own card (rounded, bordered, elevated) for standalone placement below a list. Set
 * `flush` to embed it as the last child of a list's own card instead — it then draws only a top divider, so the
 * rows and the pagination footer read as one combined, elevated block rather than two stacked cards.
 */
@Component({
  selector: 'app-pager',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'hostClasses()' },
  templateUrl: './pager.html',
})
export class Pager {
  readonly page = input.required<number>();
  readonly pageCount = input.required<number>();
  readonly total = input.required<number>();
  readonly pageSize = input.required<number>();
  /** Choices offered by the page-size selector. Empty (the default) hides it. */
  readonly pageSizeOptions = input<readonly number[]>([]);
  readonly label = input('Pagination');
  readonly entryNoun = input('entries');
  readonly flush = input(false, { transform: booleanAttribute });

  protected readonly hostClasses = computed(() =>
    this.flush()
      ? 'block border-t border-line p-3'
      : 'block rounded-card border border-line bg-surface p-3 shadow-md',
  );

  readonly pageChange = output<number>();
  readonly pageSizeChange = output<number>();

  protected readonly tokens = computed(() => pagerWindow(this.page(), this.pageCount()));

  protected readonly range = computed(() => {
    const total = this.total();
    if (!total) return { from: 0, to: 0 };
    const from = (this.page() - 1) * this.pageSize() + 1;
    const to = Math.min(total, this.page() * this.pageSize());
    return { from, to };
  });

  protected go(page: number): void {
    const clamped = Math.min(Math.max(1, page), this.pageCount());
    if (clamped !== this.page()) this.pageChange.emit(clamped);
  }

  protected onPageSizeChange(value: string): void {
    const size = Number(value);
    if (Number.isFinite(size) && size > 0) this.pageSizeChange.emit(size);
  }
}
