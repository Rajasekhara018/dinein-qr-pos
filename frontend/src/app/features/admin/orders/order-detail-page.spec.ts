import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminOrderView } from '../../../core/api/models';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { SheetService } from '../../../core/ui/sheet.service';
import { ToastService } from '../../../core/ui/toast.service';
import { OrdersModule } from './orders-module';
import { OrderDetailPage } from './order-detail-page';
import { canMarkPaidOffline, refundStatusLabel } from './order-actions';

function adminOrder(overrides: Partial<AdminOrderView> = {}): AdminOrderView {
  return {
    id: 42,
    orderNumber: '260927-042',
    displayToken: 42,
    status: 'EXPIRED',
    orderType: 'DINE_IN',
    tableLabel: 'T3',
    items: [
      {
        itemId: 1,
        name: 'Masala Dosa',
        foodType: 'VEG',
        addons: [],
        unitPrice: 120,
        quantity: 2,
        gstPercent: 5,
        lineTotal: 240,
      },
    ],
    bill: { subtotal: 240, taxTotal: 12, cgst: 6, sgst: 6, grandTotal: 252, pricesIncludeGst: false },
    payments: [{ provider: 'RAZORPAY', status: 'FAILED', method: 'upi' }],
    paymentFlagged: false,
    manualRefundDue: false,
    placedAt: '2026-09-27T07:00:00Z',
    ...overrides,
  };
}

describe('OrderDetailPage', () => {
  let fixture: ComponentFixture<OrderDetailPage>;
  let el: HTMLElement;
  let http: HttpTestingController;
  let toasts: ToastService;
  let sheetResult: unknown;
  let sheets: { open: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    sheetResult = 'CASH';
    sheets = { open: vi.fn(() => ({ closed: of(sheetResult) })) };
    TestBed.configureTestingModule({
      imports: [OrdersModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RealtimeService, useValue: { watch: () => NEVER, connected$: NEVER } },
        { provide: SheetService, useValue: sheets },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
    fixture = TestBed.createComponent(OrderDetailPage);
    fixture.componentRef.setInput('id', '42');
    el = fixture.nativeElement;
  });

  afterEach(() => {
    http.verify();
    toasts.clear();
  });

  async function render(order: AdminOrderView) {
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/orders/42').flush(order);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const byTestId = (id: string) => el.querySelector<HTMLElement>(`[data-testid="${id}"]`);

  it('marks an expired order paid offline after picking the method', async () => {
    await render(adminOrder());
    const button = byTestId('order-mark-paid')!;
    expect(button.textContent).toContain('Mark paid (offline)');
    button.click();
    await fixture.whenStable();

    expect(sheets.open).toHaveBeenCalled();
    const req = http.expectOne('/api/v1/admin/orders/42/mark-paid-offline');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ method: 'CASH' });
    req.flush(
      adminOrder({
        status: 'CONFIRMED',
        payments: [
          { provider: 'OFFLINE', status: 'CAPTURED', method: 'CASH', amountPaise: 25200, recordedByStaffId: 1 },
        ],
      }),
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(byTestId('order-mark-paid')).toBeNull();
    expect(el.textContent).toContain('Cash at counter');
    expect(byTestId('payment-recorded-by')!.textContent).toContain('Staff #1');
    expect(toasts.toasts().some((t) => t.kind === 'success' && t.message.includes('Cash'))).toBe(true);
  });

  it('does nothing when the method dialog is dismissed', async () => {
    sheetResult = undefined;
    await render(adminOrder({ status: 'PENDING_PAYMENT' }));
    byTestId('order-mark-paid')!.click();
    await fixture.whenStable();
    http.expectNone('/api/v1/admin/orders/42/mark-paid-offline');
  });

  it('reloads with a warning on 409 ALREADY_PAID', async () => {
    await render(adminOrder({ status: 'PAYMENT_FAILED' }));
    byTestId('order-mark-paid')!.click();
    await fixture.whenStable();
    http
      .expectOne('/api/v1/admin/orders/42/mark-paid-offline')
      .flush({ code: 'ALREADY_PAID', message: 'Already paid' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    http.expectOne('/api/v1/admin/orders/42').flush(adminOrder({ status: 'CONFIRMED' }));
    expect(toasts.toasts().some((t) => t.kind === 'warning' && t.message.includes('already been paid'))).toBe(
      true,
    );
  });

  it('hides "Mark paid" for flagged and paid orders', async () => {
    await render(adminOrder({ paymentFlagged: true, flagReason: 'Amount mismatch' }));
    expect(byTestId('order-mark-paid')).toBeNull();
    expect(canMarkPaidOffline(adminOrder({ status: 'CONFIRMED' }))).toBe(false);
    expect(canMarkPaidOffline(adminOrder({ status: 'PENDING_PAYMENT' }))).toBe(true);
  });

  it('shows the manual refund banner and the MANUAL refund status', async () => {
    await render(
      adminOrder({
        status: 'CANCELLED',
        manualRefundDue: true,
        orderType: 'TAKEAWAY',
        placedByStaffId: 9,
        placedByStaffName: 'Ravi',
        payments: [
          {
            provider: 'OFFLINE',
            status: 'CAPTURED',
            method: 'CASH',
            amountPaise: 25200,
            refundStatus: 'MANUAL',
            recordedByStaffId: 9,
          },
        ],
      }),
    );
    const banner = byTestId('manual-refund-banner')!;
    expect(banner.textContent).toContain('Manual refund due: hand back ₹252.00 in cash');
    expect(byTestId('refund-status-MANUAL')!.textContent).toContain('Manual refund');
    expect(byTestId('payment-recorded-by')!.textContent).toContain('Ravi');
    expect(byTestId('order-detail-placed-by')!.textContent).toContain('Placed by Ravi');
    expect(el.querySelector('[data-testid="order-type-badge"]')!.textContent).toContain('Takeaway');
    expect(byTestId('order-mark-paid')).toBeNull();
    expect(refundStatusLabel('MANUAL')).toContain('Manual refund');
  });

  it('shows no banner when no manual refund is due', async () => {
    await render(adminOrder({ status: 'CONFIRMED', payments: [] }));
    expect(byTestId('manual-refund-banner')).toBeNull();
    expect(byTestId('order-detail-placed-by')!.textContent).toContain('guest');
  });
});
