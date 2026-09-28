import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { OrdersModule } from './orders-module';
import { OrdersPage } from './orders-page';

function orders(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    displayToken: i + 1,
    orderNumber: `26010${i + 1}-001`,
    tableId: null,
    tableLabel: 'T1',
    status: 'SERVED',
    orderType: 'DINE_IN',
    itemCount: 1,
    customerName: null,
    paymentProvider: null,
    paymentMethod: 'CASH',
    paymentFlagged: false,
    grandTotal: 100,
    placedAt: new Date().toISOString(),
    paidAt: new Date().toISOString(),
  }));
}

describe('OrdersPage rows-per-page', () => {
  let fixture: ComponentFixture<OrdersPage>;
  let el: HTMLElement;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OrdersModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RealtimeService, useValue: { watch: () => NEVER, connected$: NEVER, connectionState: () => 'connected' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrdersPage);
    el = fixture.nativeElement;
  });

  afterEach(() => http.verify());

  it('re-requests with the new size and shows fewer rows when Rows is changed', async () => {
    fixture.detectChanges();
    const first = http.expectOne((r) => r.url === '/api/v1/admin/orders' && r.params.get('size') === '10');
    first.flush({ content: orders(9), page: 0, size: 10, totalElements: 9, totalPages: 1 });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelectorAll('[data-testid^="order-row-"]').length).toBe(9);

    const select: HTMLSelectElement | null = el.querySelector('select[aria-label="Rows per page"]');
    expect(select).toBeTruthy();
    select!.value = '5';
    select!.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();

    const second = http.expectOne((r) => r.url === '/api/v1/admin/orders' && r.params.get('size') === '5');
    second.flush({ content: orders(5), page: 0, size: 5, totalElements: 9, totalPages: 2 });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelectorAll('[data-testid^="order-row-"]').length).toBe(5);
    expect(el.textContent).toContain('Showing 1–5 of 9');
  });
});
