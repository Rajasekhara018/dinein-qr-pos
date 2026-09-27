import { computed, Injectable, signal } from '@angular/core';
import {
  CartLineRequest,
  CartProblem,
  MenuItem,
  MenuResponse,
} from '../../../core/api/models';
import { BillEstimate, estimateBill } from '../../guest/data/cart-pricing';
import {
  CartLine,
  CartLineIssue,
  issueFor,
  ItemSelection,
  MAX_CART_LINES,
  MAX_LINE_QUANTITY,
} from '../../guest/data/cart.models';
import { buildLine, validate } from '../../guest/data/cart.store';

/**
 * The cart of one staff-assisted order. In memory only (a waiter's phone serves many tables, so nothing is
 * persisted) and provided per flow component. Lines reuse the guest cart's line model and pricing rules, so the
 * estimate matches the guest app; the server total from the place-order response is authoritative.
 */
@Injectable()
export class StaffCartStore {
  private readonly _lines = signal<readonly CartLine[]>([]);
  private readonly _pricesIncludeGst = signal(false);

  readonly lines = this._lines.asReadonly();
  readonly pricesIncludeGst = this._pricesIncludeGst.asReadonly();

  readonly isEmpty = computed(() => this._lines().length === 0);
  readonly itemCount = computed(() => this._lines().reduce((n, l) => n + l.quantity, 0));
  readonly orderableLines = computed(() => this._lines().filter((l) => !l.issue));
  readonly hasIssues = computed(() => this._lines().some((l) => !!l.issue));
  /** Client-side ESTIMATE (paise) over orderable lines. */
  readonly bill = computed<BillEstimate>(() =>
    estimateBill(this.orderableLines(), this._pricesIncludeGst()),
  );

  /** itemId → quantity across that item's lines (menu badges). */
  readonly quantityByItem = computed(() => {
    const map = new Map<number, number>();
    for (const line of this._lines()) map.set(line.itemId, (map.get(line.itemId) ?? 0) + line.quantity);
    return map;
  });

  setPricesIncludeGst(value: boolean): void {
    this._pricesIncludeGst.set(value);
  }

  /** Adds a selection, merging into an identical line. Unavailable items are refused. Returns the line key. */
  add(item: MenuItem, selection: ItemSelection): string | null {
    if (!item.available) return null;
    const line = buildLine(item, selection);
    const existing = this._lines().find((l) => l.key === line.key);
    if (existing) {
      this.setQuantity(existing.key, existing.quantity + line.quantity);
    } else if (this._lines().length < MAX_CART_LINES) {
      this._lines.update((lines) => [...lines, line]);
    } else {
      return null;
    }
    return line.key;
  }

  /** Replaces a line after editing it in the item sheet (merging into an identical line). */
  replace(key: string, item: MenuItem, selection: ItemSelection): void {
    const updated = buildLine(item, selection);
    this._lines.update((lines) => {
      const index = lines.findIndex((l) => l.key === key);
      if (index < 0) return lines;
      const duplicate = lines.find((l) => l.key === updated.key && l.key !== key);
      if (duplicate) {
        return lines
          .filter((l) => l.key !== key)
          .map((l) =>
            l.key === duplicate.key
              ? { ...l, quantity: Math.min(MAX_LINE_QUANTITY, l.quantity + updated.quantity) }
              : l,
          );
      }
      const copy = [...lines];
      copy[index] = { ...updated, addedAt: lines[index].addedAt };
      return copy;
    });
  }

  /** ≤ 0 removes the line; capped at 50. */
  setQuantity(key: string, quantity: number): void {
    if (quantity <= 0) {
      this.remove(key);
      return;
    }
    const q = Math.min(MAX_LINE_QUANTITY, Math.floor(quantity));
    this._lines.update((lines) => lines.map((l) => (l.key === key ? { ...l, quantity: q } : l)));
  }

  remove(key: string): void {
    this._lines.update((lines) => lines.filter((l) => l.key !== key));
  }

  removeUnavailable(): void {
    this._lines.update((lines) => lines.filter((l) => !l.issue));
  }

  clear(): void {
    this._lines.set([]);
  }

  find(key: string): CartLine | undefined {
    return this._lines().find((l) => l.key === key);
  }

  /** Applies an ITEM_UNAVAILABLE response (`lineIndex` indexes into `keys`, the lines in request order). */
  markProblems(problems: readonly CartProblem[], keys: readonly string[]): void {
    const issues = new Map<string, CartLineIssue>();
    for (const problem of problems) {
      const key = keys[problem.lineIndex];
      if (key && !issues.has(key)) issues.set(key, issueFor(problem.reason));
    }
    if (!issues.size) return;
    this._lines.update((lines) =>
      lines.map((l) => (issues.has(l.key) ? { ...l, issue: issues.get(l.key)! } : l)),
    );
  }

  /** Re-validates and re-prices every line against a fresh menu. */
  reconcile(menu: MenuResponse): void {
    const items = new Map<number, MenuItem>();
    for (const category of menu.categories) for (const item of category.items) items.set(item.id, item);
    this._pricesIncludeGst.set(menu.pricesIncludeGst);
    this._lines.update((lines) =>
      lines.map((line) => {
        const item = items.get(line.itemId);
        const problem = validate(line, item);
        if (problem || !item) return { ...line, issue: issueFor(problem ?? 'ITEM_NOT_FOUND') };
        const fresh = buildLine(item, line);
        return { ...fresh, quantity: line.quantity, addedAt: line.addedAt };
      }),
    );
  }

  /** The request lines (ids and quantities only) plus their keys in request order. */
  toItems(): { items: CartLineRequest[]; keys: string[] } {
    const lines = this.orderableLines();
    return {
      keys: lines.map((l) => l.key),
      items: lines.map((l) => ({
        itemId: l.itemId,
        variantId: l.variantId,
        addonIds: [...l.addonIds],
        quantity: l.quantity,
        notes: l.notes || null,
      })),
    };
  }
}
