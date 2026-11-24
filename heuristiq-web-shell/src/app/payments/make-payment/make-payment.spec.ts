import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { afterEach, vi } from 'vitest';
import { CreatePaymentResponse } from '../../shared/models/payment.models';
import { AuthService } from '../../shared/services/auth-service';
import { BrandingService } from '../../shared/services/branding-service';
import { NotificationService } from '../../shared/services/notification-service';
import { PaymentService } from '../../shared/services/payment-service';
import { MakePayment } from './make-payment';
import { PAYMENT_CHECKOUT_STRATEGIES } from './payment-checkout-strategy';

describe('MakePayment', () => {
  const payments = { createPayment: vi.fn() };
  const router = { navigate: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  let user: { id: string; name: string; email: string } | null;
  let query: Record<string, string>;
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };
  const win = window as unknown as Record<string, unknown>;

  function create(): ComponentFixture<MakePayment> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [MakePayment],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: PaymentService, useValue: payments },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: { get currentUser() { return user; } } },
        { provide: NotificationService, useValue: notify },
        { provide: BrandingService, useValue: { branding: { appName: 'TestApp' } } },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
      ],
    });
    const fixture = TestBed.createComponent(MakePayment);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    user = { id: 'cust1', name: 'Asha', email: 'a@x.io' };
    query = {};
    delete win['Razorpay'];
    delete win['Cashfree'];
    document.getElementById('razorpay-checkout-sdk')?.remove();
    document.getElementById('cashfree-checkout-sdk')?.remove();
    payments.createPayment.mockReturnValue(of({ paymentId: 'p1', gateway: 'TOUCAN', status: 'CREATED' }));
  });

  afterEach(() => vi.restoreAllMocks());

  describe('starting values', () => {
    it('prefills the payer from the signed-in user', () => {
      const component = create().componentInstance;

      expect([component.customerName, component.customerEmailId]).toEqual(['Asha', 'a@x.io']);
    });

    it('copes with no signed-in user', () => {
      user = null;
      const component = create().componentInstance;

      expect([component.customerName, component.customerEmailId]).toEqual(['', '']);
    });

    it('takes the ride and the formatted amount from the link', () => {
      query = { rideId: 'ride9', amount: '1234567.5' };
      const component = create().componentInstance;

      expect(component.rideId).toBe('ride9');
      expect(component.amountDisplay).toBe('12,34,567.50');
      expect(component.amount).toBe('1234567.50');
      expect(component.amountOutOfRange).toBe(false);
    });

    it('lists the payment channels and the checklist in the user language', () => {
      const component = create().componentInstance;

      expect(component.paymentChannels.map((c) => c.label)).toEqual([
        'payments.makePayment.channels.bankDebit.label',
        'payments.makePayment.channels.card.label',
        'payments.makePayment.channels.upiWallet.label',
      ]);
      expect(component.checkpoints.length).toBe(3);
    });
  });

  describe('amount input', () => {
    it('groups digits the Indian way and keeps at most two decimals', () => {
      const component = create().componentInstance;

      component.onAmountInput('1234567');
      expect(component.amountDisplay).toBe('12,34,567');
      component.onAmountInput('12.');
      expect(component.amountDisplay).toBe('12.');
      component.onAmountInput('12.3456');
      expect(component.amountDisplay).toBe('12.34');
      component.onAmountInput('007');
      expect(component.amountDisplay).toBe('7');
      component.onAmountInput('abc');
      expect(component.amountDisplay).toBe('');
      component.onAmountInput('.5');
      expect(component.amountDisplay).toBe('0.5');
    });

    it('flags amounts outside 1 to 1,00,00,000', () => {
      const component = create().componentInstance;

      component.onAmountInput('0.5');
      expect(component.amountOutOfRange).toBe(true);
      component.onAmountInput('10000001');
      expect(component.amountOutOfRange).toBe(true);
      component.onAmountInput('10000000');
      expect(component.amountOutOfRange).toBe(false);
    });

    it('on blur shows two decimals, or clears an unusable amount', () => {
      const component = create().componentInstance;

      component.onAmountInput('250');
      component.onAmountBlur();
      expect(component.amountDisplay).toBe('250.00');
      expect(component.amount).toBe('250.00');

      component.onAmountInput('');
      component.onAmountBlur();
      expect(component.amountDisplay).toBe('');
      expect(component.amountOutOfRange).toBe(true);
    });
  });

  describe('makePay validation', () => {
    it('refuses an out-of-range amount', () => {
      const component = create().componentInstance;
      component.rideId = 'r1';

      component.makePay();

      expect(notify.error).toHaveBeenCalledWith('payments.makePayment.form.amount.outOfRange');
      expect(payments.createPayment).not.toHaveBeenCalled();
    });

    it('refuses without a session', () => {
      const component = create().componentInstance;
      component.onAmountInput('100');
      user = null;

      component.makePay();

      expect(notify.error).toHaveBeenCalledWith('payments.makePayment.messages.sessionExpired');
    });

    it('refuses without a ride id', () => {
      const component = create().componentInstance;
      component.onAmountInput('100');
      component.rideId = '  ';

      component.makePay();

      expect(notify.error).toHaveBeenCalledWith('payments.makePayment.form.rideId.required');
      expect(payments.createPayment).not.toHaveBeenCalled();
    });
  });

  describe('makePay request', () => {
    const ready = () => {
      const component = create().componentInstance;
      component.onAmountInput('1,500.50');
      component.rideId = ' ride1 ';
      return component;
    };

    it('creates the payment with the typed details and a fresh idempotency key', () => {
      const component = ready();
      component.customerName = ' Asha R ';
      component.customerMobileNo = ' 9876543210 ';
      component.testTransaction = true;

      component.makePay();

      const request = payments.createPayment.mock.calls[0][0];
      expect(request).toEqual(expect.objectContaining({
        rideId: 'ride1', customerId: 'cust1', amount: { code: 'INR', value: 1500.5 }, method: 'RAZORPAY', gateway: 'RAZORPAY',
        testTransaction: true, customerName: 'Asha R', customerEmail: 'a@x.io', customerMobile: '9876543210',
      }));
      expect(request.idempotencyKey).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    });

    it('omits empty optional fields and uses a different key each time', () => {
      const component = ready();
      component.customerName = '';
      component.customerEmailId = '';

      component.makePay();
      component.makePay();

      const [first, second] = payments.createPayment.mock.calls.map((c) => c[0]);
      expect(first.customerName).toBeUndefined();
      expect(first.customerEmail).toBeUndefined();
      expect(first.testTransaction).toBeUndefined();
      expect(first.idempotencyKey).not.toBe(second.idempotencyKey);
    });

    it('goes to the invoice when no checkout strategy applies', () => {
      const component = ready();

      component.makePay();

      expect(router.navigate).toHaveBeenCalledWith(['/payments/invoice', 'p1']);
      expect(component.submitting).toBe(false);
    });

    it('shows the server detail or message, and the translated default for client/transport errors, when creating fails', () => {
      const component = ready();

      payments.createPayment.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Ride not found' } })));
      component.makePay();
      expect(notify.error).toHaveBeenLastCalledWith('Ride not found');

      payments.createPayment.mockReturnValueOnce(throwError(() => ({ error: { message: 'Gateway down' } })));
      component.makePay();
      expect(notify.error).toHaveBeenLastCalledWith('Gateway down');

      // A top-level transport message (e.g. HttpErrorResponse text) is never shown; apiErrorMessage reads only the response body.
      payments.createPayment.mockReturnValueOnce(throwError(() => ({ message: 'Network' })));
      component.makePay();
      expect(notify.error).toHaveBeenLastCalledWith('payments.makePayment.messages.paymentFailed');

      payments.createPayment.mockReturnValueOnce(throwError(() => ({ status: 500, error: { message: 'NullPointerException' } })));
      component.makePay();
      expect(notify.error).toHaveBeenLastCalledWith('payments.makePayment.messages.paymentFailed');

      payments.createPayment.mockReturnValueOnce(throwError(() => ({})));
      component.makePay();
      expect(notify.error).toHaveBeenLastCalledWith('payments.makePayment.messages.paymentFailed');
      expect(component.submitting).toBe(false);
    });

    it('hands a Razorpay response to the Razorpay checkout', () => {
      const component = ready();
      const open = vi.spyOn(component, 'openRazorpayCheckout').mockResolvedValue();
      payments.createPayment.mockReturnValue(of({ paymentId: 'p2', gateway: 'razorpay', status: 'CREATED', checkoutConfig: { key: 'k' } }));

      component.makePay();

      expect(open).toHaveBeenCalledWith('p2', { key: 'k' });
    });

    it('hands a Cashfree response to the Cashfree checkout', () => {
      const component = ready();
      const open = vi.spyOn(component, 'openCashfreeCheckout').mockResolvedValue();
      payments.createPayment.mockReturnValue(of({ paymentId: 'p3', gateway: 'CASHFREE', status: 'CREATED', checkoutConfig: { paymentSessionId: 's' } }));

      component.makePay();

      expect(open).toHaveBeenCalledWith('p3', { paymentSessionId: 's' });
    });

    it('hands an auto-submit form response (PayU) to the form checkout', () => {
      const component = ready();
      const submit = vi.spyOn(component, 'submitAutoFormCheckout').mockImplementation(() => undefined);
      const config = { autoSubmit: true, action: 'https://pay.example/x' };
      payments.createPayment.mockReturnValue(of({ paymentId: 'p4', gateway: 'PAYU', status: 'CREATED', checkoutConfig: config }));

      component.makePay();

      expect(submit).toHaveBeenCalledWith(config);
    });
  });

  describe('checkout strategies', () => {
    const response = (extra: Partial<CreatePaymentResponse>): CreatePaymentResponse => ({ paymentId: 'p', gateway: 'X', status: 'CREATED', ...extra });
    const applies = (gateway: string, r: CreatePaymentResponse) => PAYMENT_CHECKOUT_STRATEGIES.map((s) => s.supports(gateway, r));

    it('picks Razorpay only for Razorpay with a config', () => {
      expect(applies('RAZORPAY', response({ checkoutConfig: {} }))[0]).toBe(true);
      expect(applies('RAZORPAY', response({}))[0]).toBe(false);
      expect(applies('PAYU', response({ checkoutConfig: {} }))[0]).toBe(false);
    });

    it('picks Cashfree only for Cashfree with a config', () => {
      expect(applies('CASHFREE', response({ checkoutConfig: {} }))[1]).toBe(true);
      expect(applies('CASHFREE', response({}))[1]).toBe(false);
    });

    it('picks the auto form for any gateway whose config says autoSubmit', () => {
      expect(applies('ANY', response({ checkoutConfig: { autoSubmit: true } }))[2]).toBe(true);
      expect(applies('ANY', response({ checkoutConfig: { autoSubmit: false } }))[2]).toBe(false);
    });

    it('picks the redirect for a response with a redirect url', () => {
      expect(applies('TOUCAN', response({ redirectUrl: 'https://x' }))[3]).toBe(true);
      expect(applies('TOUCAN', response({}))[3]).toBe(false);
    });

    it('executes each strategy against the component', () => {
      const component = { openRazorpayCheckout: vi.fn(), openCashfreeCheckout: vi.fn(), submitAutoFormCheckout: vi.fn() } as unknown as MakePayment;
      PAYMENT_CHECKOUT_STRATEGIES[0].execute(component, response({ paymentId: 'a', checkoutConfig: { k: 1 } }));
      PAYMENT_CHECKOUT_STRATEGIES[1].execute(component, response({ paymentId: 'b', checkoutConfig: { k: 2 } }));
      PAYMENT_CHECKOUT_STRATEGIES[2].execute(component, response({ checkoutConfig: { k: 3 } }));

      expect(component.openRazorpayCheckout).toHaveBeenCalledWith('a', { k: 1 });
      expect(component.openCashfreeCheckout).toHaveBeenCalledWith('b', { k: 2 });
      expect(component.submitAutoFormCheckout).toHaveBeenCalledWith({ k: 3 });
    });
  });

  describe('Razorpay checkout', () => {
    it('reports an SDK that cannot be loaded', async () => {
      const component = create().componentInstance;
      const pending = component.openRazorpayCheckout('p1', {});
      document.getElementById('razorpay-checkout-sdk')?.dispatchEvent(new Event('error'));

      await pending;

      expect(notify.error).toHaveBeenCalledWith('payments.makePayment.messages.razorpaySdkFailed');
    });

    it('opens the checkout with the payer prefilled, the app name and a theme color, and goes to the invoice on completion', async () => {
      const opened: Record<string, any>[] = [];
      win['Razorpay'] = class { constructor(options: Record<string, any>) { opened.push(options); } open = vi.fn(); };
      const component = create().componentInstance;
      component.customerMobileNo = '99999';
      document.documentElement.style.setProperty('--theme-hero-mid', '#abc');

      await component.openRazorpayCheckout('p1', { key: 'k', prefill: { name: 'Old' } });

      const options = opened[0];
      expect(options['name']).toBe('TestApp');
      expect(options['prefill']).toEqual(expect.objectContaining({ name: 'Asha', email: 'a@x.io', contact: '99999' }));
      expect(options['theme'].color).toBe('#aabbcc');
      options['handler']();
      expect(router.navigate).toHaveBeenCalledWith(['/payments/invoice', 'p1']);
      router.navigate.mockClear();
      options['modal'].ondismiss();
      expect(router.navigate).toHaveBeenCalledWith(['/payments/invoice', 'p1']);
      document.documentElement.style.removeProperty('--theme-hero-mid');
    });

    it('falls back to the gateway prefill and the default color', async () => {
      user = null;
      const opened: Record<string, any>[] = [];
      win['Razorpay'] = class { constructor(options: Record<string, any>) { opened.push(options); } open = vi.fn(); };
      const component = create().componentInstance;

      await component.openRazorpayCheckout('p1', { prefill: { name: 'Gw', email: 'g@x.io', contact: '1' }, theme: { hide: true } });

      expect(opened[0]['prefill']).toEqual({ name: 'Gw', email: 'g@x.io', contact: '1' });
      expect(opened[0]['theme']).toEqual({ hide: true, color: '#2563eb' });
    });

    it('understands rgb theme colors', async () => {
      const opened: Record<string, any>[] = [];
      win['Razorpay'] = class { constructor(options: Record<string, any>) { opened.push(options); } open = vi.fn(); };
      const component = create().componentInstance;
      document.documentElement.style.setProperty('--theme-hero-mid', 'rgb(255, 0, 16)');

      await component.openRazorpayCheckout('p1', {});

      expect(opened[0]['theme'].color).toBe('#ff0010');
      document.documentElement.style.removeProperty('--theme-hero-mid');
    });

    it('loads the SDK script once and waits for it when it is already being loaded', async () => {
      const component = create().componentInstance;
      const first = component.openRazorpayCheckout('p1', {});
      const second = component.openRazorpayCheckout('p1', {});
      expect(document.querySelectorAll('#razorpay-checkout-sdk').length).toBe(1);
      win['Razorpay'] = class { open = vi.fn(); };
      document.getElementById('razorpay-checkout-sdk')?.dispatchEvent(new Event('load'));

      await Promise.all([first, second]);

      expect(notify.error).not.toHaveBeenCalled();
    });
  });

  describe('Cashfree checkout', () => {
    it('reports an SDK that cannot be loaded', async () => {
      const component = create().componentInstance;
      const pending = component.openCashfreeCheckout('p1', {});
      document.getElementById('cashfree-checkout-sdk')?.dispatchEvent(new Event('error'));

      await pending;

      expect(notify.error).toHaveBeenCalledWith('payments.makePayment.messages.cashfreeSdkFailed');
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('opens the in-page checkout in the configured mode and then shows the invoice', async () => {
      const checkout = vi.fn().mockResolvedValue({});
      const factory = vi.fn(() => ({ checkout }));
      win['Cashfree'] = factory;
      const component = create().componentInstance;

      await component.openCashfreeCheckout('p1', { mode: 'production', paymentSessionId: 's1' });

      expect(factory).toHaveBeenCalledWith({ mode: 'production' });
      expect(checkout).toHaveBeenCalledWith({ paymentSessionId: 's1', redirectTarget: '_modal' });
      expect(router.navigate).toHaveBeenCalledWith(['/payments/invoice', 'p1']);
    });

    it('uses sandbox mode unless production is configured', async () => {
      const factory = vi.fn(() => ({ checkout: vi.fn().mockResolvedValue(undefined) }));
      win['Cashfree'] = factory;

      await create().componentInstance.openCashfreeCheckout('p1', { mode: 'test' });

      expect(factory).toHaveBeenCalledWith({ mode: 'sandbox' });
    });

    it('reports a checkout error result, with a default message when it has none', async () => {
      const checkout = vi.fn().mockResolvedValueOnce({ error: { message: 'Card declined' } }).mockResolvedValueOnce({ error: {} });
      win['Cashfree'] = () => ({ checkout });
      const component = create().componentInstance;

      await component.openCashfreeCheckout('p1', {});
      expect(notify.error).toHaveBeenLastCalledWith('Card declined');
      await component.openCashfreeCheckout('p1', {});
      expect(notify.error).toHaveBeenLastCalledWith('payments.makePayment.messages.cashfreeNotCompleted');
    });

    it('reports a crash while opening the checkout and still shows the invoice', async () => {
      win['Cashfree'] = () => { throw new Error('boom'); };

      await create().componentInstance.openCashfreeCheckout('p1', {});

      expect(notify.error).toHaveBeenCalledWith('payments.makePayment.messages.cashfreeOpenFailed');
      expect(router.navigate).toHaveBeenCalledWith(['/payments/invoice', 'p1']);
    });

    it('waits for a script that is already loading', async () => {
      const component = create().componentInstance;
      const first = component.openCashfreeCheckout('p1', {});
      const second = component.openCashfreeCheckout('p1', {});
      win['Cashfree'] = () => ({ checkout: vi.fn().mockResolvedValue({}) });
      document.getElementById('cashfree-checkout-sdk')?.dispatchEvent(new Event('load'));

      await Promise.all([first, second]);

      expect(document.querySelectorAll('#cashfree-checkout-sdk').length).toBe(1);
    });
  });

  describe('auto form checkout', () => {
    it('posts the signed fields as hidden inputs to the gateway', () => {
      const submit = vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => undefined);
      const component = create().componentInstance;

      component.submitAutoFormCheckout({ action: 'https://pay.example/go', method: 'POST', fields: { key: 'k', empty: null } });

      const form = document.body.querySelector('form[action="https://pay.example/go"]') as HTMLFormElement;
      expect(form.method).toBe('post');
      expect(Array.from(form.querySelectorAll('input')).map((i) => [i.name, i.value])).toEqual([['key', 'k'], ['empty', '']]);
      expect(submit).toHaveBeenCalled();
      form.remove();
    });

    it('defaults to post with no fields', () => {
      vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => undefined);

      create().componentInstance.submitAutoFormCheckout({ action: 'https://pay.example/plain' });

      const form = document.body.querySelector('form[action="https://pay.example/plain"]') as HTMLFormElement;
      expect(form.method).toBe('post');
      expect(form.querySelectorAll('input').length).toBe(0);
      form.remove();
    });
  });

  describe('Pay button privilege', () => {
    it('is hidden without the create-payment privilege', () => {
      granted = new Set();

      expect(create().nativeElement.querySelector('button.btn-primary')).toBeNull();
    });

    it('is shown with the privilege and clicking it validates the amount first', () => {
      granted = new Set(['RIDER_PAYMENT_CREATE']);
      const button = create().nativeElement.querySelector('button.btn-primary') as HTMLButtonElement;

      expect(button).not.toBeNull();
      button.click();
      expect(notify.error).toHaveBeenCalledWith('payments.makePayment.form.amount.outOfRange');
      expect(payments.createPayment).not.toHaveBeenCalled();
    });
  });
});
