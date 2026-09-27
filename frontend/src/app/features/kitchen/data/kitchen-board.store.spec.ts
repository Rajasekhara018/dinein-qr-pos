import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DestroyRef, signal, WritableSignal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KitchenOrderView, KitchenRealtimeEvent } from '../../../core/api/models';
import { DeviceAuthStore } from '../../../core/auth/device-auth.store';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ToastService } from '../../../core/ui/toast.service';
import { KitchenBoardStore, NEW_ORDER_HIGHLIGHT_MS, UNDO_WINDOW_MS } from './kitchen-board.store';
import { KitchenClock } from './kitchen-clock';
import { kOrder, MIN, T0, testConfig } from './test-fixtures';

// Fake timers so the 5s undo window (`advance()`'s setTimeout) can be fast-forwarded instead of really waited out.
const tick = () => vi.advanceTimersByTimeAsync(0);
/** Lets the undo window elapse. */
const passUndoWindow = () => vi.advanceTimersByTimeAsync(UNDO_WINDOW_MS + 5);

describe('KitchenBoardStore', () => {
  let store: KitchenBoardStore;
  let http: HttpTestingController;
  let toasts: ToastService;
  let now: WritableSignal<number>;
  let events: Subject<KitchenRealtimeEvent>;
  let connected: Subject<void>;
  let realtime: {
    watch: ReturnType<typeof vi.fn>;
    onConnected: (cb: () => void, ref: DestroyRef) => void;
    setAuthProvider: ReturnType<typeof vi.fn>;
    disconnect: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    vi.useFakeTimers();
    now = signal(T0);
    events = new Subject();
    connected = new Subject();
    realtime = {
      watch: vi.fn(() => events.asObservable()),
      onConnected: (cb, ref) => {
        connected.pipe(takeUntilDestroyed(ref)).subscribe(cb);
      },
      setAuthProvider: vi.fn(),
      disconnect: vi.fn(async () => undefined),
    };
    TestBed.configureTestingModule({
      providers: [
        KitchenBoardStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: KitchenClock, useValue: { now } },
        { provide: RealtimeService, useValue: realtime },
        { provide: DeviceAuthStore, useValue: { token: signal('dvc_abc') } },
      ],
    });
    store = TestBed.inject(KitchenBoardStore);
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
  });

  afterEach(() => {
    toasts.clear();
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  const flushOrders = async (orders: KitchenOrderView[]) => {
    http.expectOne((r) => r.url === '/api/v1/kitchen/orders').flush(orders);
    await tick();
  };

  async function start(orders: KitchenOrderView[]) {
    store.start();
    http.expectOne('/api/v1/kitchen/config').flush(testConfig);
    const req = http.expectOne((r) => r.url === '/api/v1/kitchen/orders');
    expect(req.request.params.get('status')).toBe('CONFIRMED,PREPARING,READY');
    req.flush(orders);
    await tick();
  }

  it('loads config + orders, groups columns oldest first, and authenticates STOMP with the device token', async () => {
    await start([
      kOrder({ id: 1 }, 2),
      kOrder({ id: 2, status: 'PREPARING' }, 5),
      kOrder({ id: 3 }, 9),
      kOrder({ id: 4, status: 'READY', readyAt: new Date(T0).toISOString() }, 12),
    ]);
    expect(store.config()).toEqual(testConfig);
    expect(store.columns().CONFIRMED.map((o) => o.id)).toEqual([3, 1]);
    expect(store.counts()).toEqual({ CONFIRMED: 2, PREPARING: 1, READY: 1 });
    expect(realtime.watch).toHaveBeenCalledWith('/topic/kitchen/orders');
    const provider = realtime.setAuthProvider.mock.calls[0][0] as () => string;
    expect(provider()).toBe('Bearer dvc_abc');
    http.verify();
  });

  it('auto-hides READY orders client-side as the ticker advances', async () => {
    await start([kOrder({ id: 4, status: 'READY', readyAt: new Date(T0).toISOString() })]);
    expect(store.counts().READY).toBe(1);
    now.set(T0 + 5 * MIN);
    expect(store.counts().READY).toBe(0);
  });

  it('refetches on every (re)connect and flags orders that arrived meanwhile', async () => {
    const arrived: number[][] = [];
    store.arrived$.subscribe((ids) => arrived.push([...ids]));
    await start([kOrder({ id: 1 })]);
    expect(arrived).toEqual([]); // not on the initial load

    connected.next();
    now.set(Date.now());
    await flushOrders([kOrder({ id: 1 }), kOrder({ id: 2 })]);
    expect(arrived).toEqual([[2]]);
    expect(store.highlighted().has(2)).toBe(true);
    now.set(Date.now() + NEW_ORDER_HIGHLIGHT_MS + 1000);
    expect(store.highlighted().has(2)).toBe(false);
  });

  it('inserts ORDER_CONFIRMED payloads immediately, then reconciles with REST', async () => {
    const arrived: number[][] = [];
    store.arrived$.subscribe((ids) => arrived.push([...ids]));
    await start([kOrder({ id: 1 }, 3)]);

    events.next({ type: 'ORDER_CONFIRMED', orderId: 7, order: kOrder({ id: 7 }, 5), at: '' });
    expect(store.columns().CONFIRMED.map((o) => o.id)).toEqual([7, 1]);
    expect(arrived).toEqual([[7]]);
    await flushOrders([kOrder({ id: 1 }, 3), kOrder({ id: 7 }, 5)]);
    expect(arrived).toEqual([[7]]); // no duplicate chime
  });

  it('applies status/cancel events locally and refetches', async () => {
    await start([kOrder({ id: 1 }), kOrder({ id: 2 })]);
    events.next({ type: 'ORDER_STATUS_CHANGED', orderId: 1, status: 'PREPARING', at: '' });
    expect(store.columns().PREPARING.map((o) => o.id)).toEqual([1]);
    await flushOrders([kOrder({ id: 1, status: 'PREPARING' }), kOrder({ id: 2 })]);

    events.next({ type: 'ORDER_CANCELLED', orderId: 2, status: 'CANCELLED', at: '' });
    expect(store.visible().map((o) => o.id)).toEqual([1]);
    await flushOrders([kOrder({ id: 1, status: 'PREPARING' })]);
    http.verify();
  });

  it('coalesces refetches while one is in flight', async () => {
    await start([kOrder({ id: 1 })]);
    connected.next();
    connected.next();
    connected.next();
    await flushOrders([kOrder({ id: 1 })]);
    await flushOrders([kOrder({ id: 1 })]); // exactly one queued follow-up
    http.verify();
  });

  it('moves optimistically, marks pending, offers a 5s Undo, then applies the server response', async () => {
    const order = kOrder({ id: 1 });
    await start([order]);
    const done = store.advance(order);
    expect(store.columns().PREPARING.map((o) => o.id)).toEqual([1]);
    expect(store.pending().has(1)).toBe(true);
    expect(http.match('/api/v1/kitchen/orders/1/status')).toHaveLength(0); // not sent yet

    const toast = toasts.toasts().find((t) => t.key === 'kitchen-advance-1')!;
    expect(toast.message).toContain('#1');
    expect(toast.action?.label).toBe('Undo');
    expect(toast.durationMs).toBe(UNDO_WINDOW_MS);

    await passUndoWindow();
    const req = http.expectOne('/api/v1/kitchen/orders/1/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'PREPARING' });
    req.flush({ ...order, status: 'PREPARING', preparingAt: '2026-09-26T07:00:05Z' });
    await done;
    expect(store.pending().has(1)).toBe(false);
    expect(store.orders()[0].preparingAt).toBe('2026-09-26T07:00:05Z');
  });

  it('Undo reverts the optimistic move and never calls the API', async () => {
    const order = kOrder({ id: 1 });
    await start([order]);
    const done = store.advance(order);
    expect(store.columns().PREPARING.map((o) => o.id)).toEqual([1]);

    const toast = toasts.toasts().find((t) => t.key === 'kitchen-advance-1')!;
    toast.action!.run();
    await done;

    expect(store.columns().CONFIRMED.map((o) => o.id)).toEqual([1]);
    expect(store.pending().has(1)).toBe(false);
    // Undo dismisses the window; waiting it out afterwards must not fire a PATCH.
    await passUndoWindow();
    expect(http.match('/api/v1/kitchen/orders/1/status')).toHaveLength(0);
  });

  it('keeps the optimistic version when a refetch lands mid-flight', async () => {
    const order = kOrder({ id: 1 });
    await start([order]);
    const done = store.advance(order);
    connected.next();
    await flushOrders([order]);
    expect(store.orders()[0].status).toBe('PREPARING');
    await passUndoWindow();
    http.expectOne('/api/v1/kitchen/orders/1/status').flush({ ...order, status: 'PREPARING' });
    await done;
  });

  it('Served removes the ticket', async () => {
    const order = kOrder({ id: 1, status: 'READY', readyAt: new Date(T0).toISOString() });
    await start([order]);
    const done = store.advance(order);
    expect(store.visible()).toEqual([]);
    await passUndoWindow();
    http.expectOne('/api/v1/kitchen/orders/1/status').flush({ ...order, status: 'COMPLETED' });
    await done;
    expect(store.orders()).toEqual([]);
  });

  it('rolls back and shows a toast when the change fails', async () => {
    const order = kOrder({ id: 1 });
    await start([order]);
    const done = store.advance(order);
    await passUndoWindow();
    http
      .expectOne('/api/v1/kitchen/orders/1/status')
      .flush({ code: 'INTERNAL_ERROR', message: 'Boom' }, { status: 500, statusText: 'Error' });
    await done;
    expect(store.columns().CONFIRMED.map((o) => o.id)).toEqual([1]);
    expect(store.pending().size).toBe(0);
    expect(toasts.toasts().some((t) => t.kind === 'error' && t.message.includes('#1'))).toBe(true);
  });

  it('rolls back and refetches on 409 conflicts', async () => {
    const order = kOrder({ id: 1 });
    await start([order]);
    const done = store.advance(order);
    await passUndoWindow();
    http
      .expectOne('/api/v1/kitchen/orders/1/status')
      .flush(
        { code: 'ILLEGAL_TRANSITION', message: 'Nope' },
        { status: 409, statusText: 'Conflict' },
      );
    await done;
    expect(store.orders()[0].status).toBe('CONFIRMED');
    await flushOrders([kOrder({ id: 1, status: 'READY', readyAt: new Date(T0).toISOString() })]);
    expect(store.columns().READY.map((o) => o.id)).toEqual([1]);
  });

  it('ignores a second tap while pending', async () => {
    const order = kOrder({ id: 1 });
    await start([order]);
    void store.advance(order);
    void store.advance(store.orders()[0]);
    await passUndoWindow();
    expect(http.match('/api/v1/kitchen/orders/1/status')).toHaveLength(1);
  });

  it('reports a load error only when the first load fails', async () => {
    store.start();
    http.expectOne('/api/v1/kitchen/config').flush(testConfig);
    http
      .expectOne((r) => r.url === '/api/v1/kitchen/orders')
      .flush(null, { status: 500, statusText: 'x' });
    await tick();
    expect(store.error()).not.toBeNull();
    void store.refresh();
    await flushOrders([kOrder({ id: 1 })]);
    expect(store.error()).toBeNull();
  });

  it('stopRealtime disconnects and clears the auth provider', async () => {
    await store.stopRealtime();
    expect(realtime.disconnect).toHaveBeenCalled();
    expect(realtime.setAuthProvider).toHaveBeenLastCalledWith(null);
  });
});
