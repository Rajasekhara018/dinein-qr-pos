import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KitchenOrderView, KitchenRealtimeEvent, WaiterConfig } from '../../../core/api/models';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ToastService } from '../../../core/ui/toast.service';
import { kOrder, MIN, T0 } from '../../kitchen/data/test-fixtures';
import { filterActive, readyOldestFirst, WaiterBoardStore } from './waiter-board.store';

const tick = () => new Promise((resolve) => setTimeout(resolve));
const iso = (ms: number) => new Date(ms).toISOString();

const config: WaiterConfig = {
  restaurantName: 'Spice Route',
  acceptingOrders: true,
  openNow: true,
  takeawayEnabled: true,
  pricesIncludeGst: false,
  onlinePaymentsAvailable: true,
  kitchenWarnMinutes: 10,
  kitchenAlertMinutes: 20,
  staff: { id: 9, username: 'ravi', role: 'WAITER', mustChangePassword: false },
};

const ready = (id: number, readyMinutesAgo: number, extra: Partial<KitchenOrderView> = {}) =>
  kOrder({ id, status: 'READY', readyAt: iso(T0 - readyMinutesAgo * MIN), ...extra }, 30);

describe('WaiterBoardStore', () => {
  let store: WaiterBoardStore;
  let http: HttpTestingController;
  let toasts: ToastService;
  let events: Subject<KitchenRealtimeEvent>;
  let connected: Subject<void>;
  let realtime: { watch: ReturnType<typeof vi.fn>; onConnected: (cb: () => void, ref: DestroyRef) => void };

  beforeEach(() => {
    events = new Subject();
    connected = new Subject();
    realtime = {
      watch: vi.fn(() => events.asObservable()),
      onConnected: (cb, ref) => {
        connected.pipe(takeUntilDestroyed(ref)).subscribe(cb);
      },
    };
    TestBed.configureTestingModule({
      providers: [
        WaiterBoardStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RealtimeService, useValue: realtime },
      ],
    });
    store = TestBed.inject(WaiterBoardStore);
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
  });

  afterEach(() => {
    toasts.clear();
    TestBed.resetTestingModule();
  });

  const flushOrders = async (orders: KitchenOrderView[]) => {
    http.expectOne((r) => r.url === '/api/v1/waiter/orders').flush(orders);
    await tick();
  };

  async function start(orders: KitchenOrderView[]) {
    store.start();
    http.expectOne('/api/v1/waiter/config').flush(config);
    const req = http.expectOne((r) => r.url === '/api/v1/waiter/orders');
    expect(req.request.params.get('status')).toBe('CONFIRMED,PREPARING,READY');
    req.flush(orders);
    await tick();
  }

  it('loads config + active orders and lists READY ones oldest first', async () => {
    await start([kOrder({ id: 1 }), ready(2, 1), ready(3, 8), kOrder({ id: 4, status: 'PREPARING' })]);
    expect(store.config()?.restaurantName).toBe('Spice Route');
    expect(realtime.watch).toHaveBeenCalledWith('/topic/kitchen/orders');
    expect(store.ready().map((o) => o.id)).toEqual([3, 2]);
    expect(store.readyCount()).toBe(2);
    expect(store.orders()).toHaveLength(4);
    http.verify();
  });

  it('inserts a READY order pushed over STOMP at once and announces it (once)', async () => {
    const arrived: number[][] = [];
    store.readyArrived$.subscribe((ids) => arrived.push([...ids]));
    await start([kOrder({ id: 1, status: 'PREPARING' })]);
    expect(arrived).toEqual([]); // nothing on the first load

    const pushed = ready(7, 0, { tableLabel: 'T7', orderType: 'TAKEAWAY' });
    events.next({ type: 'ORDER_STATUS_CHANGED', orderId: 7, status: 'READY', order: pushed, at: '' });
    expect(store.ready().map((o) => o.id)).toEqual([7]);
    expect(store.highlighted().has(7)).toBe(true);
    expect(arrived).toEqual([[7]]);

    // The reconciling refetch sees it again: no second alert.
    await flushOrders([kOrder({ id: 1, status: 'PREPARING' }), pushed]);
    expect(arrived).toEqual([[7]]);
    expect(store.ready().map((o) => o.id)).toEqual([7]);
  });

  it('announces orders that became READY while disconnected (refetch on reconnect)', async () => {
    const arrived: number[][] = [];
    store.readyArrived$.subscribe((ids) => arrived.push([...ids]));
    await start([kOrder({ id: 1, status: 'PREPARING' }), ready(2, 3)]);
    connected.next();
    await flushOrders([ready(1, 0), ready(2, 3)]);
    expect(arrived).toEqual([[1]]);
  });

  it('applies other status events locally and refetches', async () => {
    await start([kOrder({ id: 1 }), ready(2, 1)]);
    events.next({ type: 'ORDER_STATUS_CHANGED', orderId: 1, status: 'PREPARING', at: '' });
    expect(store.orders().find((o) => o.id === 1)?.status).toBe('PREPARING');
    await flushOrders([kOrder({ id: 1, status: 'PREPARING' }), ready(2, 1)]);

    events.next({ type: 'ORDER_STATUS_CHANGED', orderId: 2, status: 'COMPLETED', at: '' });
    expect(store.ready()).toEqual([]);
    await flushOrders([kOrder({ id: 1, status: 'PREPARING' })]);
    http.verify();
  });

  it('Served removes the order optimistically and keeps it removed on success', async () => {
    const order = ready(5, 2);
    await start([order]);
    const done = store.serve(order);
    expect(store.ready()).toEqual([]);
    expect(store.pending().has(5)).toBe(true);

    const req = http.expectOne('/api/v1/waiter/orders/5/serve');
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...order, status: 'COMPLETED' });
    await done;
    expect(store.orders()).toEqual([]);
    expect(store.pending().size).toBe(0);
  });

  it('Served reverts and shows an error toast when the server fails', async () => {
    const order = ready(5, 2);
    await start([order]);
    const done = store.serve(order);
    expect(store.ready()).toEqual([]);
    http
      .expectOne('/api/v1/waiter/orders/5/serve')
      .flush({ code: 'INTERNAL_ERROR', message: 'Boom' }, { status: 500, statusText: 'Error' });
    await done;
    expect(store.ready().map((o) => o.id)).toEqual([5]);
    expect(store.pending().size).toBe(0);
    expect(toasts.toasts().some((t) => t.kind === 'error' && t.message.includes('#5'))).toBe(true);
  });

  it('Served reverts and refetches on 409 ILLEGAL_TRANSITION', async () => {
    const order = ready(5, 2);
    await start([order]);
    const done = store.serve(order);
    http
      .expectOne('/api/v1/waiter/orders/5/serve')
      .flush({ code: 'ILLEGAL_TRANSITION', message: 'No' }, { status: 409, statusText: 'Conflict' });
    await done;
    expect(store.ready().map((o) => o.id)).toEqual([5]);
    expect(toasts.toasts().some((t) => t.kind === 'info')).toBe(true);
    await flushOrders([]);
    expect(store.orders()).toEqual([]);
  });

  it('keeps an order being served out of a refetch that lands mid-flight', async () => {
    const order = ready(5, 2);
    await start([order]);
    const done = store.serve(order);
    connected.next();
    await flushOrders([order]);
    expect(store.ready()).toEqual([]);
    http.expectOne('/api/v1/waiter/orders/5/serve').flush({ ...order, status: 'COMPLETED' });
    await done;
  });

  it('filters active orders by table and takeaway', () => {
    const orders = [
      kOrder({ id: 1, tableLabel: 'T1' }),
      kOrder({ id: 2, tableLabel: 'T2' }),
      kOrder({ id: 3, tableLabel: undefined, orderType: 'TAKEAWAY' }),
    ];
    expect(filterActive(orders, 'T2').map((o) => o.id)).toEqual([2]);
    expect(filterActive(orders, 'TAKEAWAY').map((o) => o.id)).toEqual([3]);
    expect(filterActive(orders, null)).toHaveLength(3);
    expect(readyOldestFirst(orders)).toEqual([]);
  });
});
