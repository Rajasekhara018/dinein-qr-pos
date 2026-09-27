import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { CheckoutResponse, GuestOrderView, PaymentVerifyRequest } from '../api/models';

/**
 * Where a checkout runs. The guest flow uses the defaults; staff flows (waiter screen, admin counter orders) verify
 * SDK payments with `POST /api/v1/waiter/payments/verify` and land on their own order page.
 */
export interface CheckoutContext {
  /** Server-side verification of an SDK success response. Default: `POST /api/v1/public/payments/verify`. */
  verify?: (body: PaymentVerifyRequest) => Observable<GuestOrderView>;
  /** Router commands of the order page (used for `not-payable`). Default: `/menu/orders/{id}`. */
  orderPage?: (orderId: number) => unknown[];
}

/** Result of handing a CheckoutResponse to CheckoutService. */
export type CheckoutOutcome =
  /** Paid and verified in-page (SDK providers). The cart can be cleared. */
  | { kind: 'paid'; order: GuestOrderView }
  /** The browser is leaving for the provider's hosted page (FORM_POST / REDIRECT). */
  | { kind: 'navigating' }
  /** The order was not payable any more (e.g. idempotent replay after payment); we navigated to its page. */
  | { kind: 'not-payable'; orderId: number };

export type PaymentNotCompletedReason =
  /** The guest closed the provider's modal. */
  | 'dismissed'
  /** The provider reported a failed attempt. */
  | 'failed'
  /** The provider script could not be loaded (offline, blocked). */
  | 'script-load-failed'
  /** The provider reported success but server verification failed. */
  | 'verification-failed'
  /** No strategy supports the response's mode/provider, or the payload is malformed. */
  | 'unsupported';

/** Rejection reason of CheckoutService.pay(); the order stays PENDING_PAYMENT and can be retried. */
export class PaymentNotCompletedError extends Error {
  override readonly name = 'PaymentNotCompletedError';

  constructor(
    readonly reason: PaymentNotCompletedReason,
    readonly orderId: number,
    message: string,
    override readonly cause?: unknown,
  ) {
    super(message);
  }
}

/**
 * One way of starting a payment. To support a new provider/mode, implement this (usually a few lines) and add it to
 * {@link CHECKOUT_STRATEGIES}.
 */
export interface CheckoutStrategy {
  /** Whether this strategy handles the response (by `mode` and optionally `provider`). */
  supports(response: CheckoutResponse): boolean;
  /** Starts the payment. Rejects with {@link PaymentNotCompletedError} when the guest did not complete it. */
  start(response: CheckoutResponse, context?: CheckoutContext): Promise<CheckoutOutcome>;
}

/** Ordered list of strategies; the first one whose `supports()` returns true wins. */
export const CHECKOUT_STRATEGIES = new InjectionToken<readonly CheckoutStrategy[]>(
  'CHECKOUT_STRATEGIES',
);
