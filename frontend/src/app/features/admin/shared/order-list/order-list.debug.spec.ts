import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { AdminSharedModule } from '../admin-shared-module';
import { OrderList } from './order-list';
import { AdminOrderSummary } from '../../../../core/api/models';

const order: AdminOrderSummary = {
  id: 1,
  displayToken: '1',
  orderNumber: '250101-001',
  tableId: null,
  tableLabel: null,
  status: 'CONFIRMED',
  orderType: 'DINE_IN',
  itemCount: 1,
  customerName: null,
  paymentProvider: null,
  paymentMethod: null,
  paymentFlagged: false,
  grandTotal: 100,
  placedAt: new Date().toISOString(),
  paidAt: null,
} as unknown as AdminOrderSummary;

describe('OrderList pager passthrough', () => {
  it('forwards pageSizeChange from the embedded desktop pager', () => {
    TestBed.configureTestingModule({ imports: [AdminSharedModule], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(OrderList);
    fixture.componentRef.setInput('orders', [order]);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('pageCount', 3);
    fixture.componentRef.setInput('total', 23);
    fixture.componentRef.setInput('pageSize', 10);
    fixture.componentRef.setInput('pageSizeOptions', [5, 10, 20]);
    fixture.detectChanges();

    let emitted: number | null = null;
    fixture.componentInstance.pageSizeChange.subscribe((v) => (emitted = v));

    const selects: HTMLSelectElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('select[aria-label="Rows per page"]'),
    );
    console.log('select count', selects.length);
    expect(selects.length).toBeGreaterThan(0);
    selects[0].value = '5';
    selects[0].dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(emitted).toBe(5);
  });
});
