import { computed, inject, Injectable, signal } from '@angular/core';
import {
  CartProblem,
  CartProblemReason,
  MenuItem,
  MenuResponse,
  PlaceOrderRequest,
} from '../../../core/api/models';
import { toPaise } from '../../../core/util/money';
import { SafeStorage, STORAGE_KEYS } from '../../../core/util/storage';
import { BillEstimate, estimateBill } from './cart-pricing';
import {
  CartLine,
  CartLineIssue,
  issueFor,
  ItemSelection,
  lineKey,
  MAX_CART_LINES,
  MAX_CUSTOMER_NAME,
  MAX_LINE_QUANTITY,
  MAX_ORDER_NOTES,
  normaliseNotes,
} from './cart.models';

interface PersistedCart {
  v: 1;
  savedAt: number;
  lines: CartLine[];
  notes: string;
  customerName: string;
  customerPhone: string;
  pricesIncludeGst: boolean;
  pendingOrderId: number | null;
}

/** Carts older than this are discarded (guest sessions last 12 h). */
const MAX_AGE_MS = 24 * 60 * 60 * 1000;

export interface ReconcileResult {
  repriced: number;
  unavailable: number;
}

/**
 * Guest cart (signals). Persisted per table in localStorage (every access wrapped in try/catch via SafeStorage),
 * cleared after a successful payment. Totals are ESTIMATES that mirror the server's pricing rules; the server is
 * authoritative (see cart-pricing.ts).
 */
@Injectable({ providedIn: 'root' })
export class CartStore {
  private readonly storage = inject(SafeStorage);

  private readonly _tableId = signal<number | null>(null);
  private readonly _lines = signal<readonly CartLine[]>([]);
  private readonly _notes = signal('');
  private readonly _customerName = signal('');
  private readonly _customerPhone = signal('');
  private readonly _pricesIncludeGst = signal(false);
  private readonly _pendingOrderId = signal<number | null>(null);

  readonly tableId = this._tableId.asReadonly();
  readonly lines = this._lines.asReadonly();
  readonly notes = this._notes.asReadonly();
  readonly customerName = this._customerName.asReadonly();
  readonly customerPhone = this._customerPhone.asReadonly();
  readonly pricesIncludeGst = this._pricesIncludeGst.asReadonly();
  /** Order created by the last checkout attempt (cleared together with the cart once it is paid). */
  readonly pendingOrderId = this._pendingOrderId.asReadonly();

  readonly isEmpty = computed(() => this._lines().length === 0);
  readonly itemCount = computed(() => this._lines().reduce((n, l) => n + l.quantity, 0));
  readonly orderableLines = computed(() => this._lines().filter((l) => !l.issue));
  readonly issueLines = computed(() => this._lines().filter((l) => !!l.issue));
  readonly hasIssues = computed(() => this.issueLines().length > 0);

  /** Estimated bill (paise) over orderable lines. */
  readonly bill = computed<BillEstimate>(() =>
    estimateBill(this.orderableLines(), this._pricesIncludeGst()),
  );
  readonly subtotal = computed(() => this.bill().subtotal);
  readonly total = computed(() => this.bill().grandTotal);

  /** itemId → total quantity across that item's lines (for menu card steppers). */
  readonly quantityByItem = computed(() => {
    const map = new Map<number, number>();
    for (const line of this._lines())
      map.set(line.itemId, (map.get(line.itemId) ?? 0) + line.quantity);
    return map;
  });

  // ─── Table binding & persistence ──────────────────────────────────────────────────────────────

  /** Switches to the cart of `tableId`, restoring it from storage. */
  bindTable(tableId: number): void {
    if (this._tableId() === tableId) return;
    const stored = this.storage.getJson<PersistedCart>(STORAGE_KEYS.cart(tableId));
    const valid =
      stored &&
      stored.v === 1 &&
      Array.isArray(stored.lines) &&
      Date.now() - stored.savedAt < MAX_AGE_MS;
    this._lines.set(valid ? stored.lines.filter(isValidLine) : []);
    this._notes.set(valid ? (stored.notes ?? '') : '');
    this._customerName.set(valid ? (stored.customerName ?? '') : '');
    this._customerPhone.set(valid ? (stored.customerPhone ?? '') : '');
    this._pricesIncludeGst.set(valid ? !!stored.pricesIncludeGst : false);
    this._pendingOrderId.set(valid ? (stored.pendingOrderId ?? null) : null);
    this._tableId.set(tableId);
    if (!valid && stored) this.storage.removeItem(STORAGE_KEYS.cart(tableId));
  }

  // ─── Mutations ────────────────────────────────────────────────────────────────────────────────

  /** Adds a selection, merging into an existing identical line. Returns the line key. */
  add(item: MenuItem, selection: ItemSelection): string {
    const line = buildLine(item, selection);
    const existing = this._lines().find((l) => l.key === line.key);
    if (existing) {
      this.setQuantity(existing.key, existing.quantity + line.quantity);
    } else if (this._lines().length < MAX_CART_LINES) {
      this.update((lines) => [...lines, line]);
    }
    return line.key;
  }

  /** Replaces a line with a new selection (edit from the sheet); merges if it now equals another line. */
  replace(key: string, item: MenuItem, selection: ItemSelection): string {
    const updated = buildLine(item, selection);
    this.update((lines) => {
      const index = lines.findIndex((l) => l.key === key);
      if (index < 0) return lines;
      const duplicate = lines.find((l) => l.key === updated.key && l.key !== key);
      if (duplicate) {
        return lines
          .filter((l) => l.key !== key)
          .map((l) =>
            l.key === duplicate.key
              ? { ...l, quantity: clampQty(l.quantity + updated.quantity) }
              : l,
          );
      }
      const copy = [...lines];
      copy[index] = { ...updated, addedAt: lines[index].addedAt };
      return copy;
    });
    return updated.key;
  }

  increment(key: string): void {
    const line = this.find(key);
    if (line) this.setQuantity(key, line.quantity + 1);
  }

  decrement(key: string): void {
    const line = this.find(key);
    if (line) this.setQuantity(key, line.quantity - 1);
  }

  /** Sets a line's quantity (≤ 0 removes it; capped at 50). */
  setQuantity(key: string, quantity: number): void {
    if (quantity <= 0) {
      this.remove(key);
      return;
    }
    this.update((lines) =>
      lines.map((l) => (l.key === key ? { ...l, quantity: clampQty(quantity) } : l)),
    );
  }

  /** Decrements the most recently added line of an item (menu card "−" for customisable items). */
  decrementLatest(itemId: number): void {
    const latest = [...this._lines()]
      .filter((l) => l.itemId === itemId)
      .sort((a, b) => b.addedAt - a.addedAt)[0];
    if (latest) this.decrement(latest.key);
  }

  remove(key: string): void {
    this.update((lines) => lines.filter((l) => l.key !== key));
  }

  removeUnavailable(): void {
    this.update((lines) => lines.filter((l) => !l.issue));
  }

  setNotes(notes: string): void {
    this._notes.set((notes ?? '').slice(0, MAX_ORDER_NOTES));
    this.persist();
  }

  setCustomer(name: string, phone: string): void {
    this._customerName.set((name ?? '').slice(0, MAX_CUSTOMER_NAME));
    this._customerPhone.set((phone ?? '').replace(/\D/g, '').slice(0, 10));
    this.persist();
  }

  setPricesIncludeGst(value: boolean): void {
    if (this._pricesIncludeGst() !== value) {
      this._pricesIncludeGst.set(value);
      this.persist();
    }
  }

  markCheckout(orderId: number): void {
    this._pendingOrderId.set(orderId);
    this.persist();
  }

  /** Empties the cart (after a successful payment) but keeps the guest's name/phone for next time. */
  clear(): void {
    this._lines.set([]);
    this._notes.set('');
    this._pendingOrderId.set(null);
    this.persist();
  }

  // ─── Server feedback ──────────────────────────────────────────────────────────────────────────

  /**
   * Applies an ITEM_UNAVAILABLE response. `keys` are the line keys in the order they were sent
   * (`CartProblem.lineIndex` indexes into it).
   */
  markProblems(problems: readonly CartProblem[], keys: readonly string[]): void {
    const issues = new Map<string, CartLineIssue>();
    for (const problem of problems) {
      const key = keys[problem.lineIndex];
      if (key && !issues.has(key)) issues.set(key, issueFor(problem.reason));
    }
    if (!issues.size) return;
    this.update((lines) =>
      lines.map((l) => (issues.has(l.key) ? { ...l, issue: issues.get(l.key)! } : l)),
    );
  }

  /**
   * Re-validates and re-prices every line against a fresh menu: lines whose item/variant/addon disappeared or became
   * unavailable get an `issue`; the rest get fresh names/prices and their issue cleared.
   */
  reconcile(menu: MenuResponse): ReconcileResult {
    const items = new Map<number, MenuItem>();
    for (const category of menu.categories)
      for (const item of category.items) items.set(item.id, item);
    let repriced = 0;
    let unavailable = 0;
    this._pricesIncludeGst.set(menu.pricesIncludeGst);
    this.update((lines) =>
      lines.map((line) => {
        const item = items.get(line.itemId);
        const problem = validate(line, item);
        if (problem || !item) {
          if (!line.issue) unavailable++;
          return { ...line, issue: issueFor(problem ?? 'ITEM_NOT_FOUND') };
        }
        const fresh = buildLine(item, line);
        if (fresh.unitPrice !== line.unitPrice || fresh.gstPercent !== line.gstPercent) repriced++;
        return { ...fresh, quantity: line.quantity, addedAt: line.addedAt };
      }),
    );
    return { repriced, unavailable };
  }

  // ─── Checkout payload ─────────────────────────────────────────────────────────────────────────

  /** The order request plus the line keys in request order (to map `CartProblem.lineIndex` back). */
  toOrderRequest(): { request: PlaceOrderRequest; keys: string[] } {
    const lines = this.orderableLines();
    return {
      keys: lines.map((l) => l.key),
      request: {
        items: lines.map((l) => ({
          itemId: l.itemId,
          variantId: l.variantId,
          addonIds: l.addonIds,
          quantity: l.quantity,
          notes: l.notes || null,
        })),
        notes: this._notes().trim() || null,
        customerName: this._customerName().trim() || null,
        customerPhone: this._customerPhone().trim() || null,
      },
    };
  }

  find(key: string): CartLine | undefined {
    return this._lines().find((l) => l.key === key);
  }

  private update(fn: (lines: readonly CartLine[]) => readonly CartLine[]): void {
    this._lines.update(fn);
    this.persist();
  }

  private persist(): void {
    const tableId = this._tableId();
    if (tableId == null) return;
    const snapshot: PersistedCart = {
      v: 1,
      savedAt: Date.now(),
      lines: [...this._lines()],
      notes: this._notes(),
      customerName: this._customerName(),
      customerPhone: this._customerPhone(),
      pricesIncludeGst: this._pricesIncludeGst(),
      pendingOrderId: this._pendingOrderId(),
    };
    this.storage.setJson(STORAGE_KEYS.cart(tableId), snapshot);
  }
}

function clampQty(quantity: number): number {
  return Math.max(1, Math.min(MAX_LINE_QUANTITY, Math.floor(quantity)));
}

function buildLine(item: MenuItem, selection: ItemSelection | CartLine): CartLine {
  const variant =
    selection.variantId != null
      ? item.variants.find((v) => v.id === selection.variantId)
      : undefined;
  const addonIds = [...new Set(selection.addonIds)].sort((a, b) => a - b);
  const addons = addonIds
    .map((id) => item.addons.find((a) => a.id === id))
    .filter((a): a is NonNullable<typeof a> => !!a);
  const notes = normaliseNotes(selection.notes);
  const basePaise = toPaise(variant?.price ?? item.basePrice ?? item.displayPrice ?? 0);
  const addonPaise = addons.reduce((sum, a) => sum + toPaise(a.price), 0);
  return {
    key: lineKey(item.id, variant?.id ?? null, addonIds, notes),
    itemId: item.id,
    variantId: variant?.id ?? null,
    addonIds,
    notes,
    quantity: clampQty(selection.quantity),
    name: item.name,
    variantName: variant?.name ?? null,
    addonNames: addons.map((a) => a.name),
    foodType: item.foodType,
    unitPrice: (basePaise + addonPaise) / 100,
    gstPercent: item.gstPercent,
    thumbUrl: item.thumbUrl ?? null,
    addedAt: Date.now(),
    issue: null,
  };
}

function validate(line: CartLine, item: MenuItem | undefined): CartProblemReason | null {
  if (!item) return 'ITEM_NOT_FOUND';
  if (!item.available) return 'ITEM_UNAVAILABLE';
  if (line.variantId != null && !item.variants.some((v) => v.id === line.variantId))
    return 'VARIANT_UNAVAILABLE';
  if (line.variantId == null && item.variants.length > 0) return 'VARIANT_REQUIRED';
  if (line.addonIds.some((id) => !item.addons.some((a) => a.id === id))) return 'ADDON_UNAVAILABLE';
  return null;
}

function isValidLine(line: unknown): line is CartLine {
  const l = line as CartLine;
  return (
    !!l &&
    typeof l.key === 'string' &&
    typeof l.itemId === 'number' &&
    typeof l.quantity === 'number' &&
    l.quantity > 0 &&
    Array.isArray(l.addonIds) &&
    typeof l.unitPrice === 'number'
  );
}
