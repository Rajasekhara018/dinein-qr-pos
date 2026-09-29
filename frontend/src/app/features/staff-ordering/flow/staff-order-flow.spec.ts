import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CheckoutResponse, GuestOrderView, WaiterConfig, WaiterTableView } from '../../../core/api/models';
import { CheckoutService } from '../../../core/payments/checkout.service';
import { CheckoutContext, PaymentNotCompletedError } from '../../../core/payments/checkout.types';
import { SheetService } from '../../../core/ui/sheet.service';
import { menu } from '../../guest/data/test-fixtures';
import { StaffOrderingModule } from '../staff-ordering-module';
import { StaffOrderFlow } from './staff-order-flow';

const config: WaiterConfig = {
  restaurantName: 'Spice Route',
  acceptingOrders: true,
  openNow: true,
  takeawayEnabled: true,
  pricesIncludeGst: false,
  onlinePaymentsAvailable: true,
  kitchenWarnMinutes: 10,
  kitchenAlertMinutes: 20,
  staff: { id: 9, username: 'ravi', role: 'WAITER', mustChangePassword: false, platformAdmin: false, restaurantId: 1 },
};

const tables: WaiterTableView[] = [
  { id: 1, label: 'T1', openOrders: 0, confirmed: 0, preparing: 0, ready: 0 },
  { id: 2, label: 'T2', openOrders: 1, confirmed: 0, preparing: 1, ready: 0 },
];

const cashResponse: CheckoutResponse = {
  orderId: 42,
  orderNumber: '260927-042',
  displayToken: 17,
  status: 'CONFIRMED',
  provider: 'OFFLINE',
  amountPaise: 25200,
  currency: 'INR',
};

const onlineResponse: CheckoutResponse = {
  orderId: 43,
  orderNumber: '260927-043',
  displayToken: 18,
  status: 'PENDING_PAYMENT',
  provider: 'RAZORPAY',
  mode: 'SDK',
  amountPaise: 25200,
  checkout: { key: 'rzp_test', order_id: 'order_1' },
};

describe('StaffOrderFlow', () => {
  let fixture: ComponentFixture<StaffOrderFlow>;
  let el: HTMLElement;
  let http: HttpTestingController;
  let checkout: { pay: ReturnType<typeof vi.fn> };
  let router: Router;

  function create(mode: 'waiter' | 'admin' = 'waiter', initialTableId: number | null = null) {
    checkout = { pay: vi.fn() };
    TestBed.configureTestingModule({
      imports: [StaffOrderingModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CheckoutService, useValue: checkout },
        { provide: SheetService, useValue: { open: vi.fn(() => ({ closed: of(undefined) })) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(StaffOrderFlow);
    fixture.componentRef.setInput('mode', mode);
    fixture.componentRef.setInput('initialTableId', initialTableId);
    el = fixture.nativeElement;
    fixture.detectChanges();
  }

  async function settle() {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function load() {
    http.expectOne('/api/v1/waiter/config').flush(config);
    http.expectOne('/api/v1/waiter/tables').flush(tables);
    http.expectOne('/api/v1/waiter/menu').flush(menu());
    await settle();
  }

  const byTestId = (id: string) => el.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const click = async (element: HTMLElement | null) => {
    element!.click();
    await settle();
  };
  const addButton = (name: string) =>
    el.querySelector<HTMLButtonElement>(`button[aria-label="Add ${name}"]`);

  afterEach(() => http.verify());

  it('picks a table, adds items and places a cash order with the exact body', async () => {
    create();
    await load();
    expect(byTestId('table-tile-takeaway')).not.toBeNull();
    await click(byTestId('table-tile-T1'));
    expect(byTestId('staff-order-where')!.textContent).toContain('Table T1');

    await click(addButton('Masala Dosa'));
    await click(addButton('Masala Dosa'));
    expect(byTestId('staff-estimate-total')!.textContent).toContain('₹252.00');

    const placing = fixture.componentInstance.place();
    const req = http.expectOne('/api/v1/waiter/orders');
    const key = req.request.body.idempotencyKey as string;
    expect(key).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
    expect(req.request.body).toEqual({
      tableId: 1,
      orderType: 'DINE_IN',
      items: [{ itemId: 1, variantId: null, addonIds: [], quantity: 2, notes: null }],
      note: null,
      customerName: null,
      customerPhone: null,
      paymentMethod: 'CASH',
      idempotencyKey: key,
    });
    req.flush(cashResponse);
    await placing;
    fixture.detectChanges();

    expect(checkout.pay).not.toHaveBeenCalled();
    expect(byTestId('staff-success-token')!.textContent).toContain('17');
    expect(byTestId('staff-success-amount')!.textContent).toContain('₹252.00');
    // The first spec in this file walks the whole flow and also pays the module's first render; under a full parallel
    // run that exceeds Vitest's 5 s default.
  }, 20_000);

  it('online: hands the checkout to CheckoutService with the waiter context', async () => {
    create('waiter', 2);
    await load();
    await click(addButton('Masala Dosa'));
    (fixture.componentInstance as unknown as { paymentMethod: { set(v: string): void } }).paymentMethod.set(
      'ONLINE',
    );
    checkout.pay.mockResolvedValue({ kind: 'paid', order: { id: 43 } as GuestOrderView });

    const placing = fixture.componentInstance.place();
    const req = http.expectOne('/api/v1/waiter/orders');
    expect(req.request.body.paymentMethod).toBe('ONLINE');
    expect(req.request.body.tableId).toBe(2);
    req.flush(onlineResponse);
    await placing;

    expect(checkout.pay).toHaveBeenCalledWith(onlineResponse, expect.anything());
    const context = checkout.pay.mock.calls[0][1] as CheckoutContext;
    expect(context.orderPage!(43)).toEqual(['/waiter/orders', 43]);
    context.verify!({ razorpay_order_id: 'order_1' }).subscribe();
    http.expectOne('/api/v1/waiter/payments/verify').flush({});
    expect(router.navigate).toHaveBeenCalledWith(['/waiter/orders', 43]);
  });

  it('online not completed: offers Retry payment through the waiter endpoint', async () => {
    create('waiter', 1);
    await load();
    await click(addButton('Masala Dosa'));
    (fixture.componentInstance as unknown as { paymentMethod: { set(v: string): void } }).paymentMethod.set(
      'ONLINE',
    );
    checkout.pay.mockRejectedValueOnce(new PaymentNotCompletedError('dismissed', 43, 'Cancelled'));
    const placing = fixture.componentInstance.place();
    http.expectOne('/api/v1/waiter/orders').flush(onlineResponse);
    await placing;
    fixture.detectChanges();
    expect(byTestId('staff-payment-failed')!.textContent).toContain('Payment not completed');

    checkout.pay.mockResolvedValue({ kind: 'navigating' });
    const retry = fixture.componentInstance.retryPayment();
    http.expectOne('/api/v1/waiter/orders/43/retry-payment').flush(onlineResponse);
    await retry;
    expect(checkout.pay).toHaveBeenCalledTimes(2);
  });

  it('admin mode places with POST /api/v1/admin/orders (takeaway without a table)', async () => {
    create('admin');
    await load();
    await click(byTestId('table-tile-takeaway'));
    await click(addButton('Masala Dosa'));
    const placing = fixture.componentInstance.place();
    const req = http.expectOne('/api/v1/admin/orders');
    expect(req.request.body).toMatchObject({ tableId: null, orderType: 'TAKEAWAY' });
    req.flush(cashResponse);
    await placing;
    expect(fixture.componentInstance.orderLink(42)).toEqual(['/admin/orders', 42]);
  });

  it('shows unavailable items greyed and not addable', async () => {
    create('waiter', 1);
    const soldOut = menu([
      { ...menu().categories[0].items[0], id: 9, name: 'Egg Bhurji', available: false },
    ]);
    http.expectOne('/api/v1/waiter/config').flush(config);
    http.expectOne('/api/v1/waiter/tables').flush(tables);
    http.expectOne('/api/v1/waiter/menu').flush(soldOut);
    await settle();
    const row = byTestId('staff-item-9')!;
    expect(row.textContent).toContain('Not available');
    expect(row.querySelector<HTMLButtonElement>('button')!.disabled).toBe(true);
  });

  it('keeps the same idempotency key for a retried attempt and goes back to the table step on TABLE_REQUIRED', async () => {
    create('waiter', 1);
    await load();
    await click(addButton('Masala Dosa'));
    const first = fixture.componentInstance.place();
    const req1 = http.expectOne('/api/v1/waiter/orders');
    req1.error(new ProgressEvent('error'), { status: 0 });
    await first;
    const second = fixture.componentInstance.place();
    const req2 = http.expectOne('/api/v1/waiter/orders');
    expect(req2.request.body.idempotencyKey).toBe(req1.request.body.idempotencyKey);
    req2.flush(
      { code: 'TABLE_REQUIRED', message: 'Choose a table' },
      { status: 400, statusText: 'Bad Request' },
    );
    await second;
    fixture.detectChanges();
    expect(byTestId('table-tile-T1')).not.toBeNull();
    expect(byTestId('staff-order-error')!.textContent).toContain('Choose a table');
  });
});
