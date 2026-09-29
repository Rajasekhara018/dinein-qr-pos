import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom, Subject } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import {
  KITCHEN_STATUSES,
  KitchenOrderView,
  KitchenRealtimeEvent,
  TOPICS,
  WaiterConfig,
} from '../../../core/api/models';
import { WaiterApi } from '../../../core/api/waiter.api';
import { AuthStore } from '../../../core/auth/auth.store';
import { silentErrors } from '../../../core/http/http-context';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ToastService } from '../../../core/ui/toast.service';
import {
  applyStatus,
  isKitchenStatus,
  mergeFetched,
  removeOrder,
  upsertOrder,
} from '../../kitchen/data/board-state';

/** How long a newly READY order keeps its highlight. */
export const READY_HIGHLIGHT_MS = 15_000;
/** Ticker for "ready 3 min ago" labels and highlight expiry. */
const TICK_MS = 15_000;

function time(iso: string | null | undefined): number {
  const t = iso ? Date.parse(iso) : Number.NaN;
  return Number.isNaN(t) ? Number.POSITIVE_INFINITY : t;
}

/** READY orders, the one waiting longest first (by `readyAt`, then `paidAt`, then id). */
export function readyOldestFirst(orders: readonly KitchenOrderView[]): KitchenOrderView[] {
  return orders
    .filter((o) => o.status === 'READY')
    .sort(
      (a, b) =>
        time(a.readyAt ?? a.paidAt) - time(b.readyAt ?? b.paidAt) ||
        time(a.paidAt) - time(b.paidAt) ||
        a.id - b.id,
    );
}

/** Filter value of the Active tab: a table label, `TAKEAWAY`, or null for all. */
export type ActiveFilter = string | null;
export const TAKEAWAY_FILTER = 'TAKEAWAY';

export function filterActive(
  orders: readonly KitchenOrderView[],
  filter: ActiveFilter,
): KitchenOrderView[] {
  if (!filter) return [...orders];
  if (filter === TAKEAWAY_FILTER) return orders.filter((o) => o.orderType === 'TAKEAWAY');
  return orders.filter((o) => o.tableLabel === filter);
}

/**
 * Live order state of the waiter screen: CONFIRMED / PREPARING / READY orders (READY stay until served). REST is
 * the source of truth and STOMP (`/topic/kitchen/orders`) a hint:
 * - `ORDER_STATUS_CHANGED` → READY carries the full order: it is inserted at once and announced on
 *   {@link readyArrived$} (vibration + chime), then reconciled with a refetch;
 * - every other event applies locally and refetches; every (re)connect refetches.
 * "Served" is optimistic with rollback. Provided by the waiter shell, so subscriptions die with it.
 */
@Injectable()
export class WaiterBoardStore {
  private readonly api = inject(WaiterApi);
  private readonly realtime = inject(RealtimeService);
  private readonly auth = inject(AuthStore);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _config = signal<WaiterConfig | null>(null);
  private readonly _orders = signal<readonly KitchenOrderView[]>([]);
  private readonly _loading = signal(true);
  private readonly _error = signal<ApiError | null>(null);
  private readonly _pending = signal<ReadonlySet<number>>(new Set());
  private readonly _arrivals = signal<ReadonlyMap<number, number>>(new Map());
  private readonly _now = signal(Date.now());

  readonly config = this._config.asReadonly();
  /** Active orders, oldest paid first. */
  readonly orders = this._orders.asReadonly();
  readonly loading = this._loading.asReadonly();
  /** Set only when the orders could not load at all. */
  readonly error = this._error.asReadonly();
  readonly pending = this._pending.asReadonly();
  readonly now = this._now.asReadonly();

  readonly ready = computed(() => readyOldestFirst(this._orders()));
  readonly readyCount = computed(() => this.ready().length);
  /** Table labels (sorted naturally) and whether takeaway orders exist, for the Active tab filter. */
  readonly filterOptions = computed(() => {
    const labels = new Set<string>();
    let takeaway = false;
    for (const o of this._orders()) {
      if (o.orderType === 'TAKEAWAY') takeaway = true;
      if (o.tableLabel) labels.add(o.tableLabel);
    }
    const tables = [...labels].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
    return { tables, takeaway };
  });
  /** Ids inside their "just became ready" highlight window. */
  readonly highlighted = computed(() => {
    const now = this._now();
    const ids = new Set<number>();
    for (const [id, at] of this._arrivals()) if (now - at < READY_HIGHLIGHT_MS) ids.add(id);
    return ids;
  });

  private readonly readySubject = new Subject<readonly number[]>();
  /** Ids of orders that just became READY (not on the first load). Alert here. */
  readonly readyArrived$ = this.readySubject.asObservable();

  private knownReady = new Set<number>();
  private initialLoadDone = false;
  private inflight: Promise<void> | null = null;
  private refetchQueued = false;
  private started = false;
  private destroyed = false;
  private ticker: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      if (this.ticker) clearInterval(this.ticker);
      this.readySubject.complete();
    });
  }

  /** Loads config + orders and subscribes to realtime. Idempotent. The caller sets the STOMP auth header. */
  start(): void {
    if (this.started) return;
    this.started = true;
    const restaurantId = this.auth.user()?.restaurantId;
    if (restaurantId) {
      this.realtime
        .watch<KitchenRealtimeEvent>(TOPICS.kitchenOrders(restaurantId))
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((event) => this.onEvent(event));
    }
    this.realtime.onConnected(() => void this.refresh(), this.destroyRef);
    this.ticker = setInterval(() => this._now.set(Date.now()), TICK_MS);
    void this.loadConfig();
    void this.refresh();
  }

  async loadConfig(): Promise<void> {
    try {
      const config = await firstValueFrom(this.api.config());
      if (!this.destroyed) this._config.set(config);
    } catch {
      // The orders call reports connectivity/auth problems.
    }
  }

  /** Single-flight refetch; a request made while one is in flight queues exactly one more. */
  refresh(): Promise<void> {
    if (this.inflight) {
      this.refetchQueued = true;
      return this.inflight;
    }
    this.inflight = this.fetchOrders().finally(() => {
      this.inflight = null;
      if (this.refetchQueued && !this.destroyed) {
        this.refetchQueued = false;
        void this.refresh();
      }
    });
    return this.inflight;
  }

  /** READY → served (COMPLETED): removed at once, restored with a toast if the server refuses. */
  async serve(order: KitchenOrderView): Promise<void> {
    if (order.status !== 'READY' || this._pending().has(order.id)) return;
    const original = this._orders().find((o) => o.id === order.id) ?? order;
    this.setPending(order.id, true);
    this._orders.update((list) => removeOrder(list, order.id));
    try {
      const updated = await firstValueFrom(this.api.serve(order.id));
      if (this.destroyed) return;
      this._orders.update((list) =>
        isKitchenStatus(updated.status) ? upsertOrder(list, updated) : removeOrder(list, updated.id),
      );
      this.toasts.success(`Token #${order.displayToken} served.`, { key: `served-${order.id}` });
    } catch (e) {
      if (this.destroyed) return;
      const error = ApiError.from(e);
      this._orders.update((list) => upsertOrder(removeOrder(list, order.id), original));
      if (error.status === 401) return; // the interceptor sends us to the login
      if (error.status === 409 || error.status === 404) {
        this.toasts.info(`Token #${order.displayToken} was updated elsewhere. List refreshed.`, {
          key: `waiter-conflict-${order.id}`,
        });
        this.setPending(order.id, false);
        void this.refresh();
        return;
      }
      this.toasts.error(`Couldn't mark token #${order.displayToken} as served. ${error.message}`, {
        key: `waiter-serve-${order.id}`,
      });
    } finally {
      if (!this.destroyed) this.setPending(order.id, false);
    }
  }

  /** Visible for tests. */
  onEvent(event: KitchenRealtimeEvent): void {
    if (this.destroyed || !event) return;
    const id = event.orderId ?? event.order?.id;
    if (id != null && this._pending().has(id)) {
      void this.refresh();
      return;
    }
    switch (event.type) {
      case 'ORDER_STATUS_CHANGED':
        if (id != null && event.status === 'READY' && event.order) {
          this._orders.update((list) => upsertOrder(list, { ...event.order!, status: 'READY' }));
          this.noteReady([id]);
        } else if (id != null && event.status) {
          const status = event.status;
          this._orders.update((list) => applyStatus(list, id, status, Date.now()));
        }
        break;
      case 'ORDER_CONFIRMED':
        if (event.order && isKitchenStatus(event.order.status)) {
          const order = event.order;
          this._orders.update((list) => upsertOrder(list, order));
        }
        break;
      case 'ORDER_CANCELLED':
        if (id != null) this._orders.update((list) => removeOrder(list, id));
        break;
      default:
        break;
    }
    void this.refresh();
  }

  private async fetchOrders(): Promise<void> {
    try {
      const fetched = await firstValueFrom(this.api.orders(KITCHEN_STATUSES, silentErrors()));
      if (this.destroyed) return;
      const merged = mergeFetched(fetched, this._orders(), this._pending());
      this._orders.set(merged);
      this._error.set(null);
      const readyIds = merged.filter((o) => o.status === 'READY').map((o) => o.id);
      if (this.initialLoadDone) {
        this.noteReady(readyIds);
      } else {
        this.initialLoadDone = true;
      }
      this.knownReady = new Set(readyIds);
    } catch (e) {
      if (this.destroyed) return;
      const error = ApiError.from(e);
      if (error.status === 401) return;
      if (!this.initialLoadDone) this._error.set(error);
      else this.toasts.warning(`Couldn't refresh orders. ${error.message}`, { key: 'waiter-sync' });
    } finally {
      if (!this.destroyed) this._loading.set(false);
    }
  }

  /** Announces ids that were not READY before (each order once). */
  private noteReady(ids: readonly number[]): void {
    const fresh = ids.filter((id) => !this.knownReady.has(id));
    if (!fresh.length) return;
    for (const id of fresh) this.knownReady.add(id);
    const now = Date.now();
    this._now.set(now);
    this._arrivals.update((map) => {
      const next = new Map<number, number>();
      for (const [id, at] of map) if (now - at < READY_HIGHLIGHT_MS) next.set(id, at);
      for (const id of fresh) next.set(id, now);
      return next;
    });
    this.readySubject.next(fresh);
  }

  private setPending(id: number, value: boolean): void {
    this._pending.update((set) => {
      const next = new Set(set);
      if (value) next.add(id);
      else next.delete(id);
      return next;
    });
  }
}
