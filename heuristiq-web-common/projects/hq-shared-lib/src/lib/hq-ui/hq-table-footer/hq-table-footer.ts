import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { HQ_TABLE_FOOTER_DEFAULT_SIZE, HQ_TABLE_FOOTER_FIRST_PAGE } from './hq-table-footer.constants';
import { HQ_TABLE_FOOTER_DEFAULT_TEXTS, HQ_TABLE_FOOTER_TEXTS, HqTableFooterTexts } from './hq-table-footer.texts';

/**
 * Pagination footer that sits directly under `hq-common-table` on every "page + size" list screen.
 * Backed by the backend's real page metadata: it shows an honest "Showing X–Y (of N)" range from
 * the items actually returned, and disables Next once the API says there is no more.
 * Usage:
 *   <hq-common-table ...></hq-common-table>
 *   <hq-table-footer [page]="page" [size]="size" [itemCount]="rows.length" [hasMore]="!last"
 *                     [totalItems]="total" [loading]="isLoading" [sizeOptions]="[10, 20, 50]"
 *                     (pageChange)="goTo($event)" (sizeChange)="resize($event)"></hq-table-footer>
 * The library has no translation dependency: provide HQ_TABLE_FOOTER_TEXTS once for the app's words.
 */
@Component({
  selector: 'hq-table-footer',
  standalone: false,
  templateUrl: './hq-table-footer.html',
  styleUrl: './hq-table-footer.scss',
})
export class HqTableFooter {
  readonly texts: HqTableFooterTexts =
    inject(HQ_TABLE_FOOTER_TEXTS, { optional: true }) ?? HQ_TABLE_FOOTER_DEFAULT_TEXTS;

  /** 0-indexed current page. */
  @Input() page = HQ_TABLE_FOOTER_FIRST_PAGE;
  @Input() size = HQ_TABLE_FOOTER_DEFAULT_SIZE;
  /** Items returned on the current page -- drives the "Showing X–Y" range. */
  @Input() itemCount = 0;
  /** Whether the API indicated more results exist beyond this page. */
  @Input() hasMore = false;
  /** Total matching items across all pages (e.g. `Page<T>.totalElements`). Omit to hide the "of N" suffix. */
  @Input() totalItems: number | null = null;
  @Input() loading = false;
  /** Omit (or pass null) to hide the page-size selector entirely. */
  @Input() sizeOptions: readonly number[] | null = null;

  @Output() readonly pageChange = new EventEmitter<number>();
  @Output() readonly sizeChange = new EventEmitter<number>();

  get rangeStart(): number {
    return this.itemCount === 0 ? 0 : this.page * this.size + 1;
  }

  get rangeEnd(): number {
    return this.page * this.size + this.itemCount;
  }

  get disablePrev(): boolean {
    return this.loading || this.page === HQ_TABLE_FOOTER_FIRST_PAGE;
  }

  get disableNext(): boolean {
    return this.loading || !this.hasMore;
  }

  first(): void {
    if (this.disablePrev) return;
    this.pageChange.emit(HQ_TABLE_FOOTER_FIRST_PAGE);
  }

  prev(): void {
    if (this.disablePrev) return;
    this.pageChange.emit(this.page - 1);
  }

  next(): void {
    if (this.disableNext) return;
    this.pageChange.emit(this.page + 1);
  }

  /** A page size was picked (the number from the list; '' when the picker is cleared, which is ignored). */
  onSizeChange(value: number | string): void {
    const parsed = Number(value);
    if (!Number.isNaN(parsed) && parsed > 0 && parsed !== this.size) {
      this.sizeChange.emit(parsed);
    }
  }
}
