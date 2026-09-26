import { DOCUMENT, inject, Injectable } from '@angular/core';
import { RazorpaySuccessResponse } from '../api/models';

/** Subset of Razorpay Checkout.js options we use (see https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/). */
export interface RazorpayOptions {
  key: string;
  order_id: string;
  amount: number;
  currency: string;
  name: string;
  description?: string;
  image?: string;
  prefill?: { name?: string; contact?: string; email?: string; method?: string };
  notes?: Record<string, string>;
  theme?: { color?: string; backdrop_color?: string };
  modal?: {
    ondismiss?: () => void;
    escape?: boolean;
    backdropclose?: boolean;
    confirm_close?: boolean;
    animation?: boolean;
  };
  retry?: { enabled: boolean; max_count?: number };
  config?: {
    display?: {
      blocks?: Record<
        string,
        { name: string; instruments: { method: string; [k: string]: unknown }[] }
      >;
      sequence?: string[];
      preferences?: { show_default_blocks?: boolean };
      hide?: { method: string }[];
    };
  };
  handler?: (response: RazorpaySuccessResponse) => void;
  [extra: string]: unknown;
}

export interface RazorpayFailure {
  error?: {
    code?: string;
    description?: string;
    source?: string;
    step?: string;
    reason?: string;
    metadata?: { order_id?: string; payment_id?: string };
  };
}

export interface RazorpayInstance {
  open(): void;
  close(): void;
  on(event: 'payment.failed', handler: (response: RazorpayFailure) => void): void;
}

export type RazorpayConstructor = new (options: RazorpayOptions) => RazorpayInstance;

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor;
  }
}

export type RazorpayErrorReason = 'dismissed' | 'failed' | 'script-load-failed';

export class RazorpayCheckoutError extends Error {
  override readonly name = 'RazorpayCheckoutError';

  constructor(
    readonly reason: RazorpayErrorReason,
    message: string,
    readonly failure?: RazorpayFailure,
  ) {
    super(message);
  }
}

export const RAZORPAY_SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js';

/**
 * Typed, promise-based wrapper around Razorpay Checkout.js. The script is loaded lazily, once.
 */
@Injectable({ providedIn: 'root' })
export class RazorpayService {
  private readonly document = inject(DOCUMENT);
  private scriptLoad: Promise<RazorpayConstructor> | null = null;

  /** Loads Checkout.js from `scriptUrl` once (no-op when `window.Razorpay` already exists). */
  load(scriptUrl: string = RAZORPAY_SCRIPT_URL): Promise<RazorpayConstructor> {
    const win = this.document.defaultView;
    if (win?.Razorpay) return Promise.resolve(win.Razorpay);
    this.scriptLoad ??= new Promise<RazorpayConstructor>((resolve, reject) => {
      const script = this.document.createElement('script');
      script.src = scriptUrl;
      script.async = true;
      script.onload = () => {
        const ctor = this.document.defaultView?.Razorpay;
        if (ctor) resolve(ctor);
        else reject(new RazorpayCheckoutError('script-load-failed', 'Razorpay did not initialise'));
      };
      script.onerror = () => {
        script.remove();
        reject(
          new RazorpayCheckoutError(
            'script-load-failed',
            'Could not load the payment window. Check your connection and try again.',
          ),
        );
      };
      this.document.head.appendChild(script);
    }).catch((error: unknown) => {
      this.scriptLoad = null; // allow a retry
      throw error;
    });
    return this.scriptLoad;
  }

  /**
   * Opens the checkout modal. Resolves with the success handler's response; rejects with
   * {@link RazorpayCheckoutError} (`dismissed` | `failed` | `script-load-failed`).
   * Any `handler` / `modal.ondismiss` in `options` are replaced.
   */
  async open(options: RazorpayOptions, scriptUrl?: string): Promise<RazorpaySuccessResponse> {
    const Razorpay = await this.load(scriptUrl);
    return new Promise<RazorpaySuccessResponse>((resolve, reject) => {
      let settled = false;
      const settle = (fn: () => void) => {
        if (!settled) {
          settled = true;
          fn();
        }
      };
      const instance = new Razorpay({
        ...options,
        handler: (response) => settle(() => resolve(response)),
        modal: {
          ...options.modal,
          ondismiss: () =>
            settle(() => reject(new RazorpayCheckoutError('dismissed', 'Payment was cancelled.'))),
        },
      });
      instance.on('payment.failed', (failure) =>
        settle(() => {
          try {
            instance.close();
          } catch {
            // ignore
          }
          reject(
            new RazorpayCheckoutError(
              'failed',
              failure?.error?.description || 'The payment failed.',
              failure,
            ),
          );
        }),
      );
      instance.open();
    });
  }
}
