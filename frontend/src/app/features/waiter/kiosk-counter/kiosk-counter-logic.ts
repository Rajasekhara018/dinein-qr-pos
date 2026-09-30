import {
  KioskCounterOrder,
  KioskCounterOrderItem,
  KioskCounterPaymentMethod,
} from '../../../core/api/models';

export const PAYMENT_METHODS: {
  value: KioskCounterPaymentMethod;
  label: string;
  /** Used in "Take ₹540.00 in cash?" style confirmations. */
  phrase: string;
  testId: string;
}[] = [
  { value: 'CASH', label: 'Cash', phrase: 'in cash', testId: 'pay-cash' },
  { value: 'UPI_AT_COUNTER', label: 'UPI', phrase: 'by UPI', testId: 'pay-upi' },
  { value: 'CARD_AT_COUNTER', label: 'Card', phrase: 'by card', testId: 'pay-card' },
];

export function paymentMethod(value: KioskCounterPaymentMethod) {
  return PAYMENT_METHODS.find((m) => m.value === value) ?? PAYMENT_METHODS[0];
}

/** Token numbers only: `#12`, ` 12 ` and `12` all search for "12". */
export function normalizeTokenQuery(query: string): string {
  return query.replace(/[^0-9A-Za-z]/g, '').toLowerCase();
}

/** Orders whose token contains the search text (empty search = all). */
export function filterByToken(
  orders: readonly KioskCounterOrder[],
  query: string,
): KioskCounterOrder[] {
  const q = normalizeTokenQuery(query);
  if (!q) return [...orders];
  return orders.filter((o) => String(o.displayToken).toLowerCase().includes(q));
}

/** An order whose payment window lapsed can still be paid at the counter; the card shows a small hint. */
export function isExpired(order: Pick<KioskCounterOrder, 'status'>): boolean {
  return order.status === 'EXPIRED';
}

export function removeKioskOrder(
  orders: readonly KioskCounterOrder[],
  id: number,
): KioskCounterOrder[] {
  return orders.filter((o) => o.id !== id);
}

/** Oldest first, matching the server; ties by id so the list order is stable between polls. */
export function sortOldestFirst(orders: readonly KioskCounterOrder[]): KioskCounterOrder[] {
  return [...orders].sort((a, b) => Date.parse(a.placedAt) - Date.parse(b.placedAt) || a.id - b.id);
}

/** `2 × Classic Burger (Large)` */
export function itemTitle(
  item: Pick<KioskCounterOrderItem, 'name' | 'variantName' | 'quantity'>,
): string {
  return `${item.quantity} × ${item.name}${item.variantName ? ` (${item.variantName})` : ''}`;
}

/** `+ Extra cheese, Fries` or '' */
export function addonsLine(item: Pick<KioskCounterOrderItem, 'addons'>): string {
  return item.addons?.length ? `+ ${item.addons.join(', ')}` : '';
}

/** Whole minutes since the order was placed (never negative). */
export function waitedMinutes(placedAt: string, now: number = Date.now()): number {
  const t = Date.parse(placedAt);
  if (Number.isNaN(t)) return 0;
  return Math.max(0, Math.floor((now - t) / 60_000));
}
