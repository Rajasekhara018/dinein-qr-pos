import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom, Subject } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { KitchenApi } from '../../../core/api/kitchen.api';
import {
  KITCHEN_STATUSES,
  KitchenConfig,
  KitchenOrderView,
  KitchenRealtimeEvent,
  TOPICS,
} from '../../../core/api/models';
import { DeviceAuthStore } from '../../../core/auth/device-auth.store';
import { silentErrors } from '../../../core/http/http-context';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ToastService } from '../../../core/ui/toast.service';
import {
  applyStatus,
  DEFAULT_KITCHEN_CONFIG,
  groupByColumn,
  isKitchenStatus,
  mergeFetched,
  nextAction,
  removeOrder,
  upsertOrder,
  visibleOrders,
} from './board-state';
import { KitchenClock } from './kitchen-clock';

/** How long a newly arrived ticket keeps its "new" highlight. */
export const NEW_ORDER_HIGHLIGHT_MS = 10_000;

/**
 * How long a tap on the action button stays reversible. The card moves immediately (so the board always shows
 * "what the kitchen believes right now"), but the PATCH that actually commits the move to the server — and to
 * every other screen watching the order — is held for this long, so a mis-tap can be taken back with no backend
 * support needed for reverse transitions (the state machine is forward-only: CONFIRMED→PREPARING→READY→COMPLETED).
 */
export const UNDO_WINDOW_MS = 5_000;

/**
 * Kitchen board state. REST is the source of truth; STOMP events are hints:
 * - `ORDER_CONFIRMED` inserts the pushed order immediately (chime + flash), then reconciles with a refetch;
 * - any other event triggers a refetch; every (re)connect triggers a full refetch.
 * Status changes are optimistic with rollback. Provided by the board component, so subscriptions die with it.
 */
@Injectable()
export class KitchenBoardStore {
  private readonly api = inject(KitchenApi);
  private readonly realtime = inject(RealtimeService);
  private readonly devices = inject(DeviceAuthStore);
  private readonly toasts = inject(ToastService);
  private readonly clock = inject(KitchenClock);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _config = signal<KitchenConfig>(DEFAULT_KITCHEN_CONFIG);
  private readonly _orders = signal<readonly KitchenOrderView[]>([]);
  private readonly _loading = signal(true);
  private readonly _error = signal<ApiError | null>(null);
  private readonly _pending = signal<ReadonlySet<number>>(new Set());
  private readonly _arrivals = signal<ReadonlyMap<number, number>>(new Map());

  readonly config = this._config.asReadonly();
  /** All kitchen orders as last known (including READY ones due to auto-hide). */
  readonly orders = this._orders.asReadonly();
  readonly loading = this._loading.asReadonly();
  /** Set only when the board could not load at all. */
  readonly error = this._error.asReadonly();
  readonly pending = this._pending.asReadonly();

  readonly visible = computed(() =>
    visibleOrders(this._orders(), this.clock.now(), this._config()),
  );
  readonly columns = computed(() => groupByColumn(this.visible()));
  readonly counts = computed(() => {
    const c = this.columns();
    return { CONFIRMED: c.CONFIRMED.length, PREPARING: c.PREPARING.length, READY: c.READY.length };
  });
  /** Ids still inside their "new order" highlight window. */
  readonly highlighted = computed(() => {
    const now = this.clock.now();
    const ids = new Set<number>();
    for (const [id, at] of this._arrivals()) {
      if (now - at < NEW_ORDER_HIGHLIGHT_MS) ids.add(id);
    }
    return ids;
  });

  private readonly arrivedSubject = new Subject<readonly number[]>();
  /** Emits ids of orders that just arrived (not on the initial load). Chime here. */
  readonly arrived$ = this.arrivedSubject.asObservable();

  private readonly knownIds = new Set<number>();
  private initialLoadDone = false;
  private inflight: Promise<void> | null = null;
  private refetchQueued = false;
  private started = false;
  private destroyed = false;

  /** One entry per order with a tap still inside its undo window; `undo()` resolves the wait with `true`. */
  private readonly undoable = new Map<
    number,
    { timer: ReturnType<typeof setTimeout>; undo: () => void }
  >();

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      this.arrivedSubject.complete();
      for (const { timer } of this.undoable.values()) clearTimeout(timer);
      this.undoable.clear();
    });
  }

  /** Loads config + orders and subscribes to realtime. Idempotent. */
  start(): void {
    if (this.started) return;
    this.started = true;

    this.realtime.setAuthProvider(() => {
      const token = this.devices.token();
      return token ? `Bearer ${token}` : null;
    });
    this.realtime
      .watch<KitchenRealtimeEvent>(TOPICS.kitchenOrders)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.onEvent(event));
    // Full refetch on every (re)connect: events may have been missed while offline.
    this.realtime.onConnected(() => void this.refresh(), this.destroyRef);

    void this.loadConfig();
    void this.refresh();
  }

  /** Tears down the socket (sign-out / board destroyed). */
  async stopRealtime(): Promise<void> {
    await this.realtime.disconnect();
    this.realtime.setAuthProvider(null);
  }

  async loadConfig(): Promise<void> {
    try {
      const config = await firstValueFrom(this.api.config());
      if (!this.destroyed) this._config.set({ ...DEFAULT_KITCHEN_CONFIG, ...config });
    } catch {
      // Keep defaults; the orders call reports connectivity/auth problems.
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

  /**
   * Start / Ready / Served. The card moves right away, but the tap stays reversible for `UNDO_WINDOW_MS`: an
   * "Undo" toast lets the kitchen take back a mis-tap before anything is sent to the server. If it isn't undone,
   * the move commits (PATCH), with the usual rollback + toast on failure / refetch on conflicts.
   */
  async advance(order: KitchenOrderView): Promise<void> {
    const action = nextAction(order.status);
    if (!action || this._pending().has(order.id)) return;
    const id = order.id;
    const original = this._orders().find((o) => o.id === id) ?? order;

    this.setPending(id, true);
    this._orders.update((list) => applyStatus(list, id, action.target, Date.now()));

    const undone = await this.waitForUndo(id, order.displayToken, action.label);
    if (this.destroyed) return;
    if (undone) {
      this._orders.update((list) => upsertOrder(removeOrder(list, id), original));
      this.setPending(id, false);
      return;
    }

    try {
      const updated = await firstValueFrom(this.api.changeStatus(id, action.target, silentErrors()));
      if (this.destroyed) return;
      this._orders.update((list) =>
        isKitchenStatus(updated.status) ? upsertOrder(list, updated) : removeOrder(list, updated.id),
      );
    } catch (e) {
      if (this.destroyed) return;
      const error = ApiError.from(e);
      // Roll back to what we had before the tap.
      this._orders.update((list) => upsertOrder(removeOrder(list, id), original));
      if (error.status === 401) return; // revoked device: the interceptor sends us to the login page
      if (error.status === 409 || error.status === 404) {
        this.toasts.info(`Token #${order.displayToken} was updated elsewhere — board refreshed.`, {
          key: `kitchen-conflict-${id}`,
        });
        this.setPending(id, false);
        void this.refresh();
        return;
      }
      this.toasts.error(
        `Couldn't mark token #${order.displayToken} as ${action.label.toLowerCase()}. ${error.message}`,
        { key: `kitchen-advance-${id}` },
      );
    } finally {
      if (!this.destroyed) this.setPending(id, false);
    }
  }

  /** Shows the Undo toast and resolves once the window elapses (`false`) or Undo is tapped (`true`). */
  private waitForUndo(id: number, displayToken: number, actionLabel: string): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      const timer = setTimeout(() => {
        this.undoable.delete(id);
        resolve(false);
      }, UNDO_WINDOW_MS);
      this.undoable.set(id, {
        timer,
        undo: () => {
          clearTimeout(timer);
          this.undoable.delete(id);
          resolve(true);
        },
      });
      this.toasts.success(`Token #${displayToken}: ${actionLabel.toLowerCase()}.`, {
        key: `kitchen-advance-${id}`,
        durationMs: UNDO_WINDOW_MS,
        action: { label: 'Undo', run: () => this.undoable.get(id)?.undo() },
      });
    });
  }

  /** Visible for tests. */
  onEvent(event: KitchenRealtimeEvent): void {
    if (this.destroyed || !event) return;
    switch (event.type) {
      case 'ORDER_CONFIRMED': {
        const order = event.order;
        if (order && isKitchenStatus(order.status) && !this._pending().has(order.id)) {
          const isNew = !this.knownIds.has(order.id);
          this._orders.update((list) => upsertOrder(list, order));
          if (isNew) this.markArrived([order.id]);
        }
        break;
      }
      case 'ORDER_CANCELLED':
        if (event.orderId != null && !this._pending().has(event.orderId)) {
          this._orders.update((list) => removeOrder(list, event.orderId as number));
        }
        break;
      case 'ORDER_STATUS_CHANGED':
        if (event.orderId != null && event.status && !this._pending().has(event.orderId)) {
          const id = event.orderId;
          const status = event.status;
          this._orders.update((list) => applyStatus(list, id, status, Date.now()));
        }
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
      const fresh = merged.filter((o) => o.status === 'CONFIRMED' && !this.knownIds.has(o.id));
      this._orders.set(merged);
      this._error.set(null);
      if (this.initialLoadDone) {
        if (fresh.length) this.markArrived(fresh.map((o) => o.id));
      } else {
        for (const o of merged) this.knownIds.add(o.id);
        this.initialLoadDone = true;
      }
    } catch (e) {
      if (this.destroyed) return;
      const error = ApiError.from(e);
      if (error.status === 401) return;
      if (!this.initialLoadDone) {
        this._error.set(error);
      } else {
        this.toasts.warning(`Couldn't refresh orders. ${error.message}`, { key: 'kitchen-sync' });
      }
    } finally {
      if (!this.destroyed) this._loading.set(false);
    }
  }

  private markArrived(ids: readonly number[]): void {
    const now = Date.now();
    for (const id of ids) this.knownIds.add(id);
    this._arrivals.update((map) => {
      const next = new Map<number, number>();
      // Drop expired highlights so the map doesn't grow forever.
      for (const [id, at] of map) if (now - at < NEW_ORDER_HIGHLIGHT_MS) next.set(id, at);
      for (const id of ids) next.set(id, now);
      return next;
    });
    this.arrivedSubject.next(ids);
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
