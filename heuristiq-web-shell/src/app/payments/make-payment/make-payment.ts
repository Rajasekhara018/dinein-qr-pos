import { Component, OnInit, inject } from '@angular/core';
import { ValidationErrors } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { PaymentService } from '../../shared/services/payment-service';
import { AuthService } from '../../shared/services/auth-service';
import { NotificationService } from '../../shared/services/notification-service';
import { PaymentGateway, RiderCreatePaymentRequest } from '../../shared/models/payment.models';
import { PAYMENT_CHECKOUT_STRATEGIES } from './payment-checkout-strategy';
import { hqAmountOrNull } from 'hq-shared-lib';
import { MAKE_PAYMENT_CURRENCY } from './make-payment.constants';

import { BrandingService } from '../../shared/services/branding-service';
import { TranslateService } from '@ngx-translate/core';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
declare const Razorpay: any;
declare const Cashfree: any;

@Component({
  selector: 'app-make-payment',
  standalone: false,
  templateUrl: './make-payment.html',
  styleUrl: './make-payment.scss',
})
export class MakePayment implements OnInit {
  /** Per-deployment branding, loaded at runtime by BrandingService. */
  private readonly branding = inject(BrandingService).branding;
  /** Rider_PaymentService attaches every payment to a ride (it looks the ride up to find the driver). */
  rideId = '';
  customerName = '';
  customerMobileNo = '';
  customerEmailId = '';
  gateway: PaymentGateway = 'RAZORPAY';
  amountDisplay = '';
  amount = '';
  /** Currency of the amount (shown in the preview and sent inside the amount). */
  readonly currency = MAKE_PAYMENT_CURRENCY;
  amountOutOfRange = false;
  submitting = false;
  /** Skips Rider_PaymentService's ride-existence check -- for trying out a gateway's checkout
   *  flow with a scratch/non-existent ride id. Leave unchecked for real payments. */
  testTransaction = false;

  readonly gateways: { label: string; value: PaymentGateway }[] = [
    { label: 'Razorpay', value: 'RAZORPAY' },
    { label: 'PayU', value: 'PAYU' },
    { label: 'Toucan', value: 'TOUCAN' },
    { label: 'Pine Labs', value: 'PINELABS' },
  ];

  get paymentChannels(): { label: string; note: string }[] {
    return [
      { label: this.translate.instant('payments.makePayment.channels.bankDebit.label'), note: this.translate.instant('payments.makePayment.channels.bankDebit.note') },
      { label: this.translate.instant('payments.makePayment.channels.card.label'), note: this.translate.instant('payments.makePayment.channels.card.note') },
      { label: this.translate.instant('payments.makePayment.channels.upiWallet.label'), note: this.translate.instant('payments.makePayment.channels.upiWallet.note') },
    ];
  }

  get checkpoints(): string[] {
    return [
      this.translate.instant('payments.makePayment.checklist.items.payerIdentity'),
      this.translate.instant('payments.makePayment.checklist.items.contactAccuracy'),
      this.translate.instant('payments.makePayment.checklist.items.reviewAmount'),
    ];
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private paymentService: PaymentService,
    private auth: AuthService,
    private notify: NotificationService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    const user = this.auth.currentUser;
    this.customerName = user?.name || '';
    this.customerEmailId = user?.email || '';

    // Linked from a ride, e.g. /payments/make-payment?rideId=...&amount=...
    const params = this.route.snapshot.queryParamMap;
    this.rideId = params.get('rideId') || '';
    const amount = params.get('amount');
    if (amount) {
      this.onAmountInput(amount);
      this.onAmountBlur();
    }
  }

  onAmountInput(value: string): void {
    const formatted = this.formatIndianAmount(value);
    this.amountDisplay = formatted;
    this.amount = formatted.replace(/,/g, '');
    this.validateAmountRange();
  }

  /** Translation key of the one message the amount box shows (required, format, then range), or null. */
  amountErrorKey(errors: ValidationErrors | null, touched: boolean | null): string | null {
    if (!touched) return null;
    if (errors?.['required']) return 'payments.makePayment.form.amount.required';
    if (errors?.['pattern']) return 'payments.makePayment.form.amount.invalid';
    return this.amountOutOfRange ? 'payments.makePayment.form.amount.outOfRange' : null;
  }

  onAmountBlur(): void {
    const numeric = Number((this.amount || '').replace(/,/g, ''));
    if (!numeric || Number.isNaN(numeric)) {
      this.amountDisplay = '';
      this.amount = '';
      this.amountOutOfRange = true;
      return;
    }
    this.amountDisplay = new Intl.NumberFormat('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(numeric);
    this.amount = numeric.toFixed(2);
    this.validateAmountRange();
  }

  makePay(): void {
    this.validateAmountRange();
    if (this.amountOutOfRange) {
      this.notify.error(this.translate.instant('payments.makePayment.form.amount.outOfRange'));
      return;
    }

    const customerId = this.auth.currentUser?.id;
    if (!customerId) {
      this.notify.error(this.translate.instant('payments.makePayment.messages.sessionExpired'));
      return;
    }
    if (!this.rideId.trim()) {
      this.notify.error(this.translate.instant('payments.makePayment.form.rideId.required'));
      return;
    }

    const payload: RiderCreatePaymentRequest = {
      rideId: this.rideId.trim(),
      customerId,
      amount: hqAmountOrNull(Number(this.amount.replace(/,/g, '')), MAKE_PAYMENT_CURRENCY),
      // Rider_PaymentService: any online payment is method RAZORPAY; `gateway` picks the provider.
      method: 'RAZORPAY',
      gateway: this.gateway,
      idempotencyKey: this.newIdempotencyKey(),
      testTransaction: this.testTransaction || undefined,
      customerName: this.customerName.trim() || undefined,
      customerEmail: this.customerEmailId.trim() || undefined,
      customerMobile: this.customerMobileNo.trim() || undefined,
    };

    this.submitting = true;
    this.paymentService.createPayment(payload).subscribe({
      next: (response) => {
        this.submitting = false;
        const selectedGateway = (response.gateway || this.gateway).toUpperCase();
        const strategy = PAYMENT_CHECKOUT_STRATEGIES.find((candidate) =>
          candidate.supports(selectedGateway, response),
        );
        if (strategy) {
          strategy.execute(this, response);
          return;
        }
        this.router.navigate(['/payments/invoice', response.paymentId]);
      },
      error: (err: any) => {
        this.submitting = false;
        const msg = apiErrorMessage(err, this.translate.instant('payments.makePayment.messages.paymentFailed'));
        this.notify.error(msg);
      },
    });
  }

  // crypto.randomUUID() exists only in secure contexts (HTTPS/localhost); UAT is plain HTTP,
  // so build a v4 UUID from getRandomValues, which is available everywhere.
  private newIdempotencyKey(): string {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  private formatIndianAmount(value: string): string {
    const sanitized = (value || '').replace(/[^\d.]/g, '');
    if (!sanitized) {
      return '';
    }

    const dotIndex = sanitized.indexOf('.');
    let integerPart = dotIndex >= 0 ? sanitized.substring(0, dotIndex) : sanitized;
    const decimalRaw = dotIndex >= 0 ? sanitized.substring(dotIndex + 1).replace(/\./g, '') : '';
    const decimalPart = decimalRaw.slice(0, 2);
    const hasTrailingDot = dotIndex >= 0 && decimalRaw.length === 0;

    integerPart = integerPart.replace(/^0+(?=\d)/, '');
    const formattedInteger = integerPart
      ? new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 }).format(Number(integerPart))
      : '0';

    if (hasTrailingDot) {
      return `${formattedInteger}.`;
    }
    if (decimalPart.length > 0) {
      return `${formattedInteger}.${decimalPart}`;
    }
    return formattedInteger;
  }

  private validateAmountRange(): void {
    const parsed = Number((this.amount || '').replace(/,/g, ''));
    this.amountOutOfRange = !parsed || parsed < 1 || parsed > 10000000;
  }

  async openRazorpayCheckout(paymentId: string, checkoutConfig: Record<string, any>): Promise<void> {
    const sdkLoaded = await this.loadRazorpaySdk();
    if (!sdkLoaded) {
      this.notify.error(this.translate.instant('payments.makePayment.messages.razorpaySdkFailed'));
      return;
    }
    const options = {
      ...checkoutConfig,
      name: this.branding.appName,
      prefill: {
        ...(checkoutConfig['prefill'] || {}),
        name: this.customerName || checkoutConfig['prefill']?.name,
        email: this.customerEmailId || checkoutConfig['prefill']?.email,
        contact: this.customerMobileNo || checkoutConfig['prefill']?.contact,
      },
      theme: {
        ...(checkoutConfig['theme'] || {}),
        color: this.resolveRazorpayThemeColor(),
      },
      handler: () => this.router.navigate(['/payments/invoice', paymentId]),
      modal: {
        ondismiss: () => this.router.navigate(['/payments/invoice', paymentId]),
      },
    };

    const razorpay = new Razorpay(options);
    razorpay.open();
  }

  // In-page Drop-in modal (redirectTarget: "_modal") -- the customer never leaves RailCab_Web.
  // Cashfree resolves the checkout() promise on both success and failure/dismissal, so either way
  // we navigate to the invoice page afterwards and let it fetch the authoritative status; the
  // webhook (CashfreeCallbackInteractor) is what actually persists the outcome server-side.
  async openCashfreeCheckout(paymentId: string, checkoutConfig: Record<string, any>): Promise<void> {
    const sdkLoaded = await this.loadCashfreeSdk();
    if (!sdkLoaded) {
      this.notify.error(this.translate.instant('payments.makePayment.messages.cashfreeSdkFailed'));
      return;
    }
    try {
      const cashfree = Cashfree({ mode: checkoutConfig['mode'] === 'production' ? 'production' : 'sandbox' });
      const result = await cashfree.checkout({
        paymentSessionId: checkoutConfig['paymentSessionId'],
        redirectTarget: '_modal',
      });
      if (result?.error) {
        this.notify.error(result.error.message || this.translate.instant('payments.makePayment.messages.cashfreeNotCompleted'));
      }
    } catch {
      this.notify.error(this.translate.instant('payments.makePayment.messages.cashfreeOpenFailed'));
    }
    this.router.navigate(['/payments/invoice', paymentId]);
  }

  private loadCashfreeSdk(): Promise<boolean> {
    const win = window as any;
    if (win.Cashfree) {
      return Promise.resolve(true);
    }
    return new Promise((resolve) => {
      const existing = document.getElementById('cashfree-checkout-sdk');
      if (existing) {
        if (win.Cashfree) {
          resolve(true);
          return;
        }
        existing.addEventListener('load', () => resolve(true), { once: true });
        existing.addEventListener('error', () => resolve(false), { once: true });
        return;
      }
      const script = document.createElement('script');
      script.id = 'cashfree-checkout-sdk';
      script.src = 'https://sdk.cashfree.com/js/v3/cashfree.js';
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });
  }

  submitAutoFormCheckout(checkoutConfig: Record<string, any>): void {
    const form = document.createElement('form');
    form.method = (checkoutConfig['method'] || 'post').toLowerCase();
    form.action = checkoutConfig['action'];
    form.style.display = 'none';

    const fields: Record<string, string> = checkoutConfig['fields'] || {};
    Object.entries(fields).forEach(([name, value]) => {
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = name;
      input.value = value ?? '';
      form.appendChild(input);
    });

    document.body.appendChild(form);
    form.submit();
  }

  private resolveRazorpayThemeColor(): string {
    return this.readThemeColorFromCss(
      ['--theme-hero-mid', '--button-accent-start', '--theme-hero-end', '--button-accent-end'],
      '#2563eb',
    );
  }

  private readThemeColorFromCss(tokens: string[], fallback: string): string {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
      return fallback;
    }
    const rootStyles = window.getComputedStyle(document.documentElement);
    for (const token of tokens) {
      const hex = this.toRazorpayHex(rootStyles.getPropertyValue(token).trim());
      if (hex) {
        return hex;
      }
    }
    return fallback;
  }

  private toRazorpayHex(value: string | undefined): string | null {
    const color = (value || '').trim();
    const shortHex = /^#([0-9a-f]{3})$/i.exec(color);
    if (shortHex) {
      return `#${shortHex[1].split('').map((char) => `${char}${char}`).join('')}`;
    }
    if (/^#[0-9a-f]{6}$/i.test(color)) {
      return color;
    }
    const rgb = /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})/i.exec(color);
    if (!rgb) {
      return null;
    }
    const channels = rgb.slice(1, 4).map((channel) => Math.max(0, Math.min(255, Number(channel))));
    return `#${channels.map((channel) => channel.toString(16).padStart(2, '0')).join('')}`;
  }

  private loadRazorpaySdk(): Promise<boolean> {
    const win = window as any;
    if (win.Razorpay) {
      return Promise.resolve(true);
    }
    return new Promise((resolve) => {
      const existing = document.getElementById('razorpay-checkout-sdk');
      if (existing) {
        if (win.Razorpay) {
          resolve(true);
          return;
        }
        existing.addEventListener('load', () => resolve(true), { once: true });
        existing.addEventListener('error', () => resolve(false), { once: true });
        return;
      }
      const script = document.createElement('script');
      script.id = 'razorpay-checkout-sdk';
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });
  }
}
