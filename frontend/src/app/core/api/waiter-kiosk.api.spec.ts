import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { KioskCounterOrder } from './models';
import { WaiterApi } from './waiter.api';

describe('WaiterApi kiosk orders', () => {
  let api: WaiterApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(WaiterApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists unpaid kiosk orders', () => {
    let result: KioskCounterOrder[] = [];
    api.kioskOrders().subscribe((r) => (result = r));
    const req = http.expectOne('/api/v1/waiter/kiosk-orders');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 4,
        orderNumber: 'K-4',
        displayToken: 12,
        orderType: 'TAKEAWAY',
        status: 'EXPIRED',
        grandTotal: 540,
        placedAt: '2026-09-30T10:00:00Z',
        items: [{ name: 'Burger', variantName: null, quantity: 1, notes: null, addons: [] }],
      },
    ]);
    expect(result[0].displayToken).toBe(12);
  });

  it('posts the payment method', () => {
    let done = false;
    api.payKioskOrder(4, 'UPI_AT_COUNTER').subscribe(() => (done = true));
    const req = http.expectOne('/api/v1/waiter/kiosk-orders/4/pay');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ method: 'UPI_AT_COUNTER' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(done).toBe(true);
  });

  it('surfaces a 409 to the caller', () => {
    let status = 0;
    api.payKioskOrder(4, 'CASH').subscribe({ error: (e) => (status = e.status) });
    http
      .expectOne('/api/v1/waiter/kiosk-orders/4/pay')
      .flush(
        { code: 'ALREADY_PAID', message: 'Already paid.' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(status).toBe(409);
  });
});
