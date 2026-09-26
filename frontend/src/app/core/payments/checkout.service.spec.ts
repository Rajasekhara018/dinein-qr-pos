import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CheckoutResponse, GuestOrderView } from '../api/models';
import { BrowserNavigator } from '../util/browser-navigator';
import { CheckoutService } from './checkout.service';
import { CHECKOUT_STRATEGIES, CheckoutStrategy, PaymentNotCompletedError } from './checkout.types';
import { RazorpayOptions } from './razorpay.service';

type Behaviour = 'success' | 'dismiss' | 'fail';

/** Stand-in for Checkout.js: records options and simulates the guest's action on open(). */
class FakeRazorpay {
  static behaviour: Behaviour = 'success';
  static last: FakeRazorpay | null = null;
  private failedHandler?: (r: unknown) => void;
  closed = false;

  constructor(readonly options: RazorpayOptions) {
    FakeRazorpay.last = this;
  }

  on(_event: string, handler: (r: unknown) => void): void {
    this.failedHandler = handler;
  }

  close(): void {
    this.closed = true;
  }

  open(): void {
    queueMicrotask(() => {
      if (FakeRazorpay.behaviour === 'success') {
        this.options.handler?.({
          razorpay_order_id: 'order_R1',
          razorpay_payment_id: 'pay_P1',
          razorpay_signature: 'sig',
        });
      } else if (FakeRazorpay.behaviour === 'dismiss') {
        this.options.modal?.ondismiss?.();
      } else {
        this.failedHandler?.({
          error: { code: 'BAD_REQUEST_ERROR', description: 'Card declined' },
        });
      }
    });
  }
}

const sdkResponse: CheckoutResponse = {
  orderId: 42,
  orderNumber: '260926-042',
  displayToken: 42,
  status: 'PENDING_PAYMENT',
  provider: 'RAZORPAY',
  mode: 'SDK',
  amountPaise: 21000,
  currency: 'INR',
  restaurantName: 'Spice Route',
  checkout: {
    key: 'rzp_test_1',
    order_id: 'order_R1',
    amount: 21000,
    currency: 'INR',
    name: 'Spice Route',
    theme: { color: '#c2410c' },
    scriptUrl: 'https://checkout.razorpay.com/v1/checkout.js',
  },
};

const paidOrder = { id: 42, status: 'CONFIRMED' } as GuestOrderView;

describe('CheckoutService', () => {
  let service: CheckoutService;
  let controller: HttpTestingController;
  let navigator: {
    assign: ReturnType<typeof vi.fn>;
    submitForm: ReturnType<typeof vi.fn>;
    print: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(() => {
    navigator = { assign: vi.fn(), submitForm: vi.fn(), print: vi.fn() };
    window.Razorpay = FakeRazorpay as never;
    FakeRazorpay.behaviour = 'success';
    FakeRazorpay.last = null;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BrowserNavigator, useValue: navigator },
      ],
    });
    service = TestBed.inject(CheckoutService);
    controller = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    controller.verify();
    delete window.Razorpay;
  });

  async function flushVerify(): Promise<void> {
    await vi
      .waitFor(() => controller.expectOne('/api/public/payments/verify'), { timeout: 1000 })
      .then((req) => {
        expect(req.request.body).toEqual({
          razorpay_order_id: 'order_R1',
          razorpay_payment_id: 'pay_P1',
          razorpay_signature: 'sig',
        });
        req.flush(paidOrder);
      });
  }

  describe('SDK (Razorpay)', () => {
    it('opens Checkout.js with the server options and verifies the payment', async () => {
      const result = service.pay(sdkResponse);
      await flushVerify();
      await expect(result).resolves.toEqual({ kind: 'paid', order: paidOrder });

      const options = FakeRazorpay.last!.options;
      expect(options).toMatchObject({
        key: 'rzp_test_1',
        order_id: 'order_R1',
        amount: 21000,
        name: 'Spice Route',
      });
      expect(options['scriptUrl']).toBeUndefined();
      expect(options.retry).toEqual({ enabled: false });
      expect(typeof options.modal?.ondismiss).toBe('function');
    });

    it('rejects with reason "dismissed" when the modal is closed', async () => {
      FakeRazorpay.behaviour = 'dismiss';
      const error = await service.pay(sdkResponse).catch((e: unknown) => e);
      expect(error).toBeInstanceOf(PaymentNotCompletedError);
      expect(error).toMatchObject({ reason: 'dismissed', orderId: 42 });
    });

    it('rejects with reason "failed" on payment.failed and closes the modal', async () => {
      FakeRazorpay.behaviour = 'fail';
      const error = await service.pay(sdkResponse).catch((e: unknown) => e);
      expect(error).toMatchObject({ reason: 'failed', message: 'Card declined' });
      expect(FakeRazorpay.last!.closed).toBe(true);
    });

    it('rejects with reason "verification-failed" when the server cannot verify', async () => {
      const result = service.pay(sdkResponse).catch((e: unknown) => e);
      const req = await vi.waitFor(() => controller.expectOne('/api/public/payments/verify'));
      req.flush(
        { code: 'PAYMENT_PROVIDER_ERROR', message: 'Could not verify' },
        { status: 502, statusText: 'Bad Gateway' },
      );
      expect(await result).toMatchObject({ reason: 'verification-failed' });
    });
  });

  it('FORM_POST submits a hidden form to the provider', async () => {
    const response: CheckoutResponse = {
      ...sdkResponse,
      provider: 'PAYU',
      mode: 'FORM_POST',
      checkout: {
        action: 'https://test.payu.in/_payment',
        method: 'POST',
        fields: { key: 'k', hash: 'h', amount: '210.00' },
      },
    };
    await expect(service.pay(response)).resolves.toEqual({ kind: 'navigating' });
    expect(navigator.submitForm).toHaveBeenCalledWith(
      'https://test.payu.in/_payment',
      { key: 'k', hash: 'h', amount: '210.00' },
      'POST',
    );
  });

  it('REDIRECT navigates to the provider URL', async () => {
    const response: CheckoutResponse = {
      ...sdkResponse,
      provider: 'PINELABS',
      mode: 'REDIRECT',
      checkout: { url: 'https://pay.example.com/x' },
    };
    await expect(service.pay(response)).resolves.toEqual({ kind: 'navigating' });
    expect(navigator.assign).toHaveBeenCalledWith('https://pay.example.com/x');
  });

  it('goes straight to the order page when the order is no longer payable', async () => {
    const replay: CheckoutResponse = {
      orderId: 42,
      orderNumber: '260926-042',
      displayToken: 42,
      status: 'CONFIRMED',
    };
    await expect(service.pay(replay)).resolves.toEqual({ kind: 'not-payable', orderId: 42 });
    expect(router.navigate).toHaveBeenCalledWith(['/menu', 'orders', 42]);
  });

  it('rejects unsupported modes', async () => {
    const error = await service
      .pay({ ...sdkResponse, mode: 'QR' as never })
      .catch((e: unknown) => e);
    expect(error).toMatchObject({ reason: 'unsupported' });
  });
});

describe('CheckoutService with custom strategies', () => {
  it('uses the first strategy that supports the response (one class per new provider)', async () => {
    const custom: CheckoutStrategy = {
      supports: (r) => r.provider === 'NEWPAY',
      start: vi.fn().mockResolvedValue({ kind: 'navigating' }),
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: CHECKOUT_STRATEGIES, useValue: [custom] }],
    });
    const service = TestBed.inject(CheckoutService);
    await expect(service.pay({ ...sdkResponse, provider: 'NEWPAY' })).resolves.toEqual({
      kind: 'navigating',
    });
    expect(custom.start).toHaveBeenCalled();
  });
});
