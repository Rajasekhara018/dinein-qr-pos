import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../../core/api/api-error';
import { AdminMenuApi } from '../../../../core/api/admin.api';
import { ItemResponse, ItemSearchParams } from '../../../../core/api/models';
import { ToastService } from '../../../../core/ui/toast.service';
import { formatInr } from '../../../../core/util/money';
import { errorMessage } from '../../shared/form-errors';
import {
  applyPriceChange,
  diffPriceChange,
  PriceChange,
  toPriceRequest,
  undoChangeFor,
} from '../data/inline-price';

export interface ItemFilters {
  categoryId: number | null;
  q: string;
  available: boolean | null;
}

const PAGE_SIZE = 50;

/**
 * State of the items page: search/filter/paging, availability toggles and inline rate edits with optimistic
 * updates + Undo. Provided by `ItemsPage` (one instance per page).
 */
@Injectable()
export class ItemsListStore {
  private readonly api = inject(AdminMenuApi);
  private readonly toasts = inject(ToastService);

  private readonly _items = signal<readonly ItemResponse[]>([]);
  private readonly _total = signal(0);
  private readonly _page = signal(0);
  private readonly _totalPages = signal(0);
  private readonly _loading = signal(false);
  private readonly _error = signal<unknown>(null);
  private readonly _loaded = signal(false);
  private readonly _busy = signal<ReadonlySet<number>>(new Set());

  readonly items = this._items.asReadonly();
  readonly total = this._total.asReadonly();
  readonly page = this._page.asReadonly();
  readonly totalPages = this._totalPages.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();
  readonly loaded = this._loaded.asReadonly();
  readonly busyIds = this._busy.asReadonly();

  readonly filters = signal<ItemFilters>({ categoryId: null, q: '', available: null });
  readonly hasFilters = computed(() => {
    const f = this.filters();
    return f.categoryId !== null || !!f.q.trim() || f.available !== null;
  });

  private requestSeq = 0;

  async load(page = 0): Promise<void> {
    const seq = ++this.requestSeq;
    const f = this.filters();
    const params: ItemSearchParams = {
      categoryId: f.categoryId ?? undefined,
      q: f.q.trim() || undefined,
      available: f.available ?? undefined,
      page,
      size: PAGE_SIZE,
    };
    this._loading.set(true);
    try {
      const result = await firstValueFrom(this.api.items(params));
      if (seq !== this.requestSeq) return; // a newer search won
      this._items.set(result.content);
      this._total.set(result.totalElements);
      this._page.set(result.page);
      this._totalPages.set(result.totalPages);
      this._error.set(null);
      this._loaded.set(true);
    } catch (error) {
      if (seq === this.requestSeq) this._error.set(error);
    } finally {
      if (seq === this.requestSeq) this._loading.set(false);
    }
  }

  setFilters(patch: Partial<ItemFilters>): void {
    this.filters.update((f) => ({ ...f, ...patch }));
    void this.load(0);
  }

  isBusy(id: number): boolean {
    return this._busy().has(id);
  }

  find(id: number): ItemResponse | undefined {
    return this._items().find((i) => i.id === id);
  }

  /** Replaces a row in place (no-op if it is not listed any more). */
  replace(item: ItemResponse): void {
    this._items.update((list) => list.map((i) => (i.id === item.id ? item : i)));
  }

  remove(id: number): void {
    this._items.update((list) => list.filter((i) => i.id !== id));
    this._total.update((n) => Math.max(0, n - 1));
  }

  async setAvailability(item: ItemResponse, available: boolean): Promise<void> {
    const previous = this.find(item.id) ?? item;
    this.replace({ ...previous, available });
    this.markBusy(item.id, true);
    try {
      this.replace(await firstValueFrom(this.api.setItemAvailability(item.id, available)));
      this.toasts.success(`${item.name} is now ${available ? 'available' : 'out of stock'}.`, {
        key: `availability-${item.id}`,
      });
    } catch (error) {
      this.replace(previous);
      this.toasts.error(errorMessage(error, 'Could not change availability.'));
    } finally {
      this.markBusy(item.id, false);
    }
  }

  /**
   * Inline rate edit: optimistic update, PATCH with the row's version, then an "Undo" toast. Undo PATCHes the
   * previous prices back using the NEW version. A 409 (edited elsewhere) reloads the row.
   * Resolves true when saved.
   */
  async updatePrice(itemId: number, change: PriceChange): Promise<boolean> {
    const current = this.find(itemId);
    if (!current) return false;
    const diff = diffPriceChange(current, change);
    if (!diff) return true;
    this.replace(applyPriceChange(current, diff));
    this.markBusy(itemId, true);
    try {
      const saved = await firstValueFrom(
        this.api.updateItemPrice(itemId, toPriceRequest(diff, current.version)),
      );
      this.replace(saved);
      const undo = undoChangeFor(current, diff);
      this.toasts.success(`Price of ${current.name} updated to ${describePrice(saved)}.`, {
        key: `price-${itemId}`,
        durationMs: 8000,
        action: { label: 'Undo', run: () => void this.undoPrice(itemId, undo) },
      });
      return true;
    } catch (error) {
      await this.handlePriceError(itemId, current, error);
      return false;
    } finally {
      this.markBusy(itemId, false);
    }
  }

  async undoPrice(itemId: number, undo: PriceChange): Promise<void> {
    const current = this.find(itemId);
    if (!current) return;
    const diff = diffPriceChange(current, undo);
    if (!diff) return;
    this.replace(applyPriceChange(current, diff));
    this.markBusy(itemId, true);
    try {
      const saved = await firstValueFrom(
        this.api.updateItemPrice(itemId, toPriceRequest(diff, current.version)),
      );
      this.replace(saved);
      this.toasts.info(`Price of ${current.name} restored to ${describePrice(saved)}.`, {
        key: `price-${itemId}`,
      });
    } catch (error) {
      await this.handlePriceError(itemId, current, error);
    } finally {
      this.markBusy(itemId, false);
    }
  }

  /** Re-fetches one row (e.g. after a 409). */
  async reloadRow(itemId: number): Promise<void> {
    try {
      this.replace(await firstValueFrom(this.api.item(itemId)));
    } catch {
      // The item may have been deleted meanwhile: refresh the page.
      void this.load(this._page());
    }
  }

  private async handlePriceError(itemId: number, previous: ItemResponse, error: unknown) {
    if (error instanceof ApiError && error.code === 'CONCURRENT_MODIFICATION') {
      this.replace(previous);
      await this.reloadRow(itemId);
      this.toasts.warning(
        `${previous.name} was changed by someone else. The latest price is shown — please try again.`,
        { key: `price-${itemId}` },
      );
      return;
    }
    this.replace(previous);
    this.toasts.error(errorMessage(error, 'Could not update the price.'), { key: `price-${itemId}` });
  }

  private markBusy(id: number, busy: boolean): void {
    this._busy.update((set) => {
      const next = new Set(set);
      if (busy) next.add(id);
      else next.delete(id);
      return next;
    });
  }
}

export function describePrice(item: ItemResponse): string {
  if (item.hasVariants && item.variants.length) {
    return item.variants.map((v) => `${v.name} ${formatInr(v.price)}`).join(', ');
  }
  return formatInr(item.basePrice ?? item.displayPrice ?? 0);
}
