import { KitchenConfig, KitchenOrderView, OrderStatus } from '../../../core/api/models';

/**
 * Pure, framework-free board logic (ordering, optimistic moves, auto-hide, age escalation) so it can be unit-tested
 * without TestBed. The store (`KitchenBoardStore`) only wires these to signals, REST and STOMP.
 */

export type KitchenColumn = 'CONFIRMED' | 'PREPARING' | 'READY';
export const KITCHEN_COLUMNS: readonly KitchenColumn[] = ['CONFIRMED', 'PREPARING', 'READY'];

export const COLUMN_LABELS: Record<KitchenColumn, string> = {
  CONFIRMED: 'New',
  PREPARING: 'Preparing',
  READY: 'Ready',
};

/** `ok` < `warn` (amber) < `alert` (red). */
export type AgeLevel = 'ok' | 'warn' | 'alert';

/** Used until `/api/v1/kitchen/config` answers (mirrors the backend defaults). */
export const DEFAULT_KITCHEN_CONFIG: KitchenConfig = {
  restaurantName: 'Kitchen',
  warnMinutes: 10,
  alertMinutes: 20,
  readyAutoHideMinutes: 10,
};

export interface KitchenAction {
  target: OrderStatus;
  label: 'Start' | 'Ready' | 'Served';
}

const MINUTE = 60_000;

export function isKitchenStatus(status: OrderStatus | null | undefined): status is KitchenColumn {
  return status === 'CONFIRMED' || status === 'PREPARING' || status === 'READY';
}

/** The kitchen button for a status: Start (→PREPARING), Ready (→READY), Served (→COMPLETED). */
export function nextAction(status: OrderStatus): KitchenAction | null {
  switch (status) {
    case 'CONFIRMED':
      return { target: 'PREPARING', label: 'Start' };
    case 'PREPARING':
      return { target: 'READY', label: 'Ready' };
    case 'READY':
      return { target: 'COMPLETED', label: 'Served' };
    default:
      return null;
  }
}

function time(iso: string | null | undefined): number {
  if (!iso) return Number.NaN;
  return new Date(iso).getTime();
}

/** Oldest paid first; orders without `paidAt` go last; ties broken by id (stable, deterministic). */
export function compareOldestFirst(a: KitchenOrderView, b: KitchenOrderView): number {
  const ta = time(a.paidAt);
  const tb = time(b.paidAt);
  const na = Number.isNaN(ta);
  const nb = Number.isNaN(tb);
  if (na !== nb) return na ? 1 : -1;
  if (!na && ta !== tb) return ta - tb;
  return a.id - b.id;
}

export function sortOldestFirst(orders: readonly KitchenOrderView[]): KitchenOrderView[] {
  return [...orders].sort(compareOldestFirst);
}

export function removeOrder(orders: readonly KitchenOrderView[], id: number): KitchenOrderView[] {
  return orders.filter((o) => o.id !== id);
}

/** Inserts or replaces an order; orders that left the kitchen (COMPLETED, CANCELLED…) are removed. */
export function upsertOrder(
  orders: readonly KitchenOrderView[],
  order: KitchenOrderView,
): KitchenOrderView[] {
  const rest = removeOrder(orders, order.id);
  return isKitchenStatus(order.status) ? sortOldestFirst([...rest, order]) : rest;
}

/** The order as it would look after moving to `status` at `now` (for optimistic updates). */
export function withStatus(
  order: KitchenOrderView,
  status: OrderStatus,
  now: number,
): KitchenOrderView {
  const at = new Date(now).toISOString();
  return {
    ...order,
    status,
    preparingAt: status === 'PREPARING' ? (order.preparingAt ?? at) : order.preparingAt,
    readyAt: status === 'READY' ? (order.readyAt ?? at) : order.readyAt,
  };
}

/** Moves an order to `status` locally (removing it when it leaves the kitchen). Unknown ids are ignored. */
export function applyStatus(
  orders: readonly KitchenOrderView[],
  id: number,
  status: OrderStatus,
  now: number,
): KitchenOrderView[] {
  const order = orders.find((o) => o.id === id);
  if (!order) return [...orders];
  return upsertOrder(orders, withStatus(order, status, now));
}

/**
 * Reconciles a REST snapshot with local state: the server wins, except for orders with an in-flight status change,
 * whose optimistic version (or optimistic removal) is kept until the PATCH settles.
 */
export function mergeFetched(
  fetched: readonly KitchenOrderView[],
  current: readonly KitchenOrderView[],
  pendingIds: ReadonlySet<number>,
): KitchenOrderView[] {
  if (pendingIds.size === 0)
    return sortOldestFirst(fetched.filter((o) => isKitchenStatus(o.status)));
  const result = fetched.filter((o) => !pendingIds.has(o.id) && isKitchenStatus(o.status));
  for (const id of pendingIds) {
    const local = current.find((o) => o.id === id);
    if (local && isKitchenStatus(local.status)) result.push(local);
  }
  return sortOldestFirst(result);
}

/** READY orders disappear `readyAutoHideMinutes` after `readyAt` (the server applies the same rule on fetch). */
export function isAutoHidden(
  order: KitchenOrderView,
  now: number,
  readyAutoHideMinutes: number,
): boolean {
  if (order.status !== 'READY') return false;
  const readyAt = time(order.readyAt);
  if (Number.isNaN(readyAt)) return false;
  return now - readyAt >= readyAutoHideMinutes * MINUTE;
}

export function visibleOrders(
  orders: readonly KitchenOrderView[],
  now: number,
  config: Pick<KitchenConfig, 'readyAutoHideMinutes'>,
): KitchenOrderView[] {
  return orders.filter(
    (o) => isKitchenStatus(o.status) && !isAutoHidden(o, now, config.readyAutoHideMinutes),
  );
}

export function groupByColumn(
  orders: readonly KitchenOrderView[],
): Record<KitchenColumn, KitchenOrderView[]> {
  const groups: Record<KitchenColumn, KitchenOrderView[]> = {
    CONFIRMED: [],
    PREPARING: [],
    READY: [],
  };
  for (const order of sortOldestFirst(orders)) {
    if (isKitchenStatus(order.status)) groups[order.status].push(order);
  }
  return groups;
}

/** Milliseconds since the order was paid (0 when unknown or in the future because of clock skew). */
export function elapsedMs(order: KitchenOrderView, now: number): number {
  const paid = time(order.paidAt);
  return Number.isNaN(paid) ? 0 : Math.max(0, now - paid);
}

/**
 * Age escalation for orders still being worked on: amber once older than `warnMinutes`, red once older than
 * `alertMinutes`. READY orders are done cooking, so they never escalate.
 */
export function ageLevel(
  order: KitchenOrderView,
  now: number,
  config: Pick<KitchenConfig, 'warnMinutes' | 'alertMinutes'>,
): AgeLevel {
  if (order.status === 'READY') return 'ok';
  const age = elapsedMs(order, now);
  if (age > config.alertMinutes * MINUTE) return 'alert';
  if (age > config.warnMinutes * MINUTE) return 'warn';
  return 'ok';
}

/** `4:07`, `12:30`, `1:02:09`. */
export function formatElapsed(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`;
}

/** Spoken form for screen readers: `12 minutes 30 seconds`. */
export function describeElapsed(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const parts: string[] = [];
  if (h) parts.push(`${h} hour${h === 1 ? '' : 's'}`);
  parts.push(`${m} minute${m === 1 ? '' : 's'}`);
  return parts.join(' ');
}
