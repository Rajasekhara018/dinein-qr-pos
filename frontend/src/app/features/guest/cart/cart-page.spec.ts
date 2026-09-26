import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CheckoutResponse } from '../../../core/api/models';
import { CheckoutService } from '../../../core/payments/checkout.service';
import { PaymentNotCompletedError } from '../../../core/payments/checkout.types';
import { SharedModule } from '../../../shared/shared-module';
import { CartStore } from '../data/cart.store';
import { GuestSessionStore } from '../data/guest-session.store';
import { biryani, dosa } from '../data/test-fixtures';
import { BillSummary } from './bill-summary';
import { CartLines } from './cart-lines';
import { CartPage } from './cart-page';

const checkoutResponse: CheckoutResponse = {
  orderId: 42,
  orderNumber: '260926-042',
  displayToken: 42,
  status: 'PENDING_PAYMENT',
  provider: 'RAZORPAY',
  mode: 'SDK',
  amountPaise: 61478,
  checkout: {},
};

describe('CartPage', () => {
  let fixture: ComponentFixture<CartPage>;
  let el: HTMLElement;
  let cart: CartStore;
  let controller: HttpTestingController;
  let checkout: { pay: ReturnType<typeof vi.fn> };
  const canOrder = signal(true);

  beforeEach(() => {
    localStorage.clear();
    canOrder.set(true);
    checkout = { pay: vi.fn() };
    TestBed.configureTestingModule({
      declarations: [CartPage, CartLines, BillSummary],
      imports: [SharedModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CheckoutService, useValue: checkout },
        {
          provide: GuestSessionStore,
          useValue: {
            table: signal({ id: 7, label: 'T7' }),
            canOrder,
            closedReason: computed(() => (canOrder() ? null : 'not-accepting')),
            openingHours: signal(''),
            refresh: vi.fn(),
          },
        },
      ],
    });
    cart = TestBed.inject(CartStore);
    cart.bindTable(7);
    cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 2 });
    cart.add(biryani, { variantId: 22, addonIds: [32], notes: '', quantity: 1 });
    controller = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(CartPage);
    fixture.detectChanges();
    el = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => {
    controller.verify();
    localStorage.clear();
  });

  const text = (id: string) => el.querySelector(`[data-testid="${id}"]`)?.textContent?.trim();
  const payButton = () => el.querySelector<HTMLButtonElement>('[data-testid="pay-button"]')!;

  function typeInto(selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  it('shows the estimated bill breakdown', () => {
    expect(text('bill-subtotal')).toBe('₹585.50');
    expect(text('bill-cgst')).toBe('₹14.64');
    expect(text('bill-sgst')).toBe('₹14.64');
    expect(text('bill-total')).toBe('₹614.78');
    expect(el.textContent).toContain('Estimated total');
    expect(payButton().textContent).toContain('Pay ₹614.78');
  });

  it('validates the mobile number inline and blocks payment', async () => {
    typeInto('#customer-phone', '12345');
    expect(text('phone-error')).toContain('valid 10-digit Indian mobile');
    payButton().click();
    await fixture.whenStable();
    controller.expectNone('/api/public/orders');

    typeInto('#customer-phone', '5876543210');
    expect(text('phone-error')).toBeTruthy();

    typeInto('#customer-phone', '9876543210');
    expect(text('phone-error')).toBeFalsy();
    expect(cart.customerPhone()).toBe('9876543210');
  });

  it('strips non-digits from the phone input', () => {
    typeInto('#customer-phone', '98-765 43210');
    expect(el.querySelector<HTMLInputElement>('#customer-phone')!.value).toBe('9876543210');
  });

  it('limits order notes to 300 characters', () => {
    const notes = el.querySelector<HTMLTextAreaElement>('#order-notes')!;
    expect(notes.maxLength).toBe(300);
  });

  it('places the order with an Idempotency-Key and hands the response to CheckoutService', async () => {
    checkout.pay.mockResolvedValue({ kind: 'paid', order: { id: 42 } });
    typeInto('#customer-name', 'Asha');
    const paying = fixture.componentInstance.pay();

    const req = controller.expectOne('/api/public/orders');
    expect(req.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(req.request.body.customerName).toBe('Asha');
    expect(req.request.body.items).toEqual([
      { itemId: 1, variantId: null, addonIds: [], quantity: 2, notes: null },
      { itemId: 2, variantId: 22, addonIds: [32], quantity: 1, notes: null },
    ]);
    req.flush(checkoutResponse);
    await paying;

    expect(checkout.pay).toHaveBeenCalledWith(checkoutResponse);
    expect(cart.isEmpty()).toBe(true);
  });

  it('reuses the same key for a retried attempt and ignores double taps', async () => {
    const first = fixture.componentInstance.pay();
    const doubleTap = fixture.componentInstance.pay();
    const req1 = controller.expectOne('/api/public/orders');
    req1.error(new ProgressEvent('error'), { status: 0 });
    await Promise.all([first, doubleTap]);

    const second = fixture.componentInstance.pay();
    const req2 = controller.expectOne('/api/public/orders');
    expect(req2.request.headers.get('Idempotency-Key')).toBe(req1.request.headers.get('Idempotency-Key'));
    req2.error(new ProgressEvent('error'), { status: 0 });
    await second;

    cart.increment(cart.lines()[0].key);
    const third = fixture.componentInstance.pay();
    const req3 = controller.expectOne('/api/public/orders');
    expect(req3.request.headers.get('Idempotency-Key')).not.toBe(req1.request.headers.get('Idempotency-Key'));
    req3.error(new ProgressEvent('error'), { status: 0 });
    await third;
  });

  it('highlights unavailable lines on ITEM_UNAVAILABLE and disables Pay', async () => {
    const paying = fixture.componentInstance.pay();
    controller.expectOne('/api/public/orders').flush(
      {
        code: 'ITEM_UNAVAILABLE',
        message: 'Some items are unavailable',
        details: [{ lineIndex: 1, itemId: 2, variantId: 22, name: 'Chicken Biryani', reason: 'VARIANT_UNAVAILABLE' }],
      },
      { status: 409, statusText: 'Conflict' },
    );
    await paying;
    controller.match('/api/public/menu').forEach((r) => r.flush({ version: 'v', pricesIncludeGst: false, categories: [] }));
    fixture.detectChanges();

    expect(cart.lines()[1].issue?.reason).toBe('VARIANT_UNAVAILABLE');
    expect(text('checkout-error')).toContain('no longer available');
    expect(el.textContent).toContain('This size is no longer available');
    expect(payButton().disabled).toBe(true);
  });

  it('shows "Payment not completed" with Retry payment and Edit cart when the guest dismisses the modal', async () => {
    checkout.pay.mockRejectedValue(new PaymentNotCompletedError('dismissed', 42, 'Payment was cancelled.'));
    const paying = fixture.componentInstance.pay();
    controller.expectOne('/api/public/orders').flush(checkoutResponse);
    await paying;
    fixture.detectChanges();

    const panel = el.querySelector('[data-testid="payment-failed"]')!;
    expect(panel.textContent).toContain('Payment not completed');
    expect(panel.textContent).toContain('Retry payment');
    expect(panel.textContent).toContain('Edit cart');
    expect(cart.isEmpty()).toBe(false);

    checkout.pay.mockResolvedValue({ kind: 'paid', order: { id: 42 } });
    const retry = fixture.componentInstance.retryPayment();
    controller.expectOne('/api/public/orders/42/retry-payment').flush(checkoutResponse);
    await retry;
    expect(checkout.pay).toHaveBeenCalledTimes(2);
    expect(cart.isEmpty()).toBe(true);
  });

  it('disables Pay while ordering is closed', () => {
    canOrder.set(false);
    fixture.detectChanges();
    expect(payButton().disabled).toBe(true);
    expect(el.textContent).toContain('Ordering is closed right now.');
  });

  it('shows a message on ORDERING_CLOSED', async () => {
    const paying = fixture.componentInstance.pay();
    controller.expectOne('/api/public/orders').flush(
      { code: 'ORDERING_CLOSED', message: 'We are not accepting orders right now' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    await paying;
    fixture.detectChanges();
    expect(text('checkout-error')).toBe('We are not accepting orders right now');
  });
});
