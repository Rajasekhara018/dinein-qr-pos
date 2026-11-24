import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { CheckoutResponse } from '../api/models';
import {
  CHECKOUT_STRATEGIES,
  CheckoutContext,
  CheckoutOutcome,
  CheckoutStrategy,
  PaymentNotCompletedError,
} from './checkout.types';
import { FormPostStrategy } from './strategies/form-post.strategy';
import { RazorpaySdkStrategy } from './strategies/razorpay-sdk.strategy';
import { RedirectStrategy } from './strategies/redirect.strategy';

/** Default strategies; override/extend by providing CHECKOUT_STRATEGIES. */
export function defaultCheckoutStrategies(): readonly CheckoutStrategy[] {
  return [inject(RazorpaySdkStrategy), inject(FormPostStrategy), inject(RedirectStrategy)];
}

/** Guest order page path (also the landing page of redirect providers). */
export const orderPagePath = (orderId: number) => ['/menu', 'orders', orderId];

/**
 * Provider-agnostic checkout. Hand it the `CheckoutResponse` from `POST /api/v1/public/orders` or
 * `/retry-payment` (or a staff-assisted ONLINE order, with a {@link CheckoutContext}):
 * - `status !== PENDING_PAYMENT` → navigates straight to the order page (`not-payable`).
 * - otherwise the first matching {@link CheckoutStrategy} starts the payment (SDK modal, form post, redirect).
 *
 * Rejects with {@link PaymentNotCompletedError} when the guest did not complete the payment.
 */
@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly router = inject(Router);
  private readonly strategies =
    inject(CHECKOUT_STRATEGIES, { optional: true }) ?? defaultCheckoutStrategies();

  async pay(response: CheckoutResponse, context?: CheckoutContext): Promise<CheckoutOutcome> {
    if (response.status !== 'PENDING_PAYMENT') {
      await this.router.navigate((context?.orderPage ?? orderPagePath)(response.orderId));
      return { kind: 'not-payable', orderId: response.orderId };
    }
    const strategy = this.strategies.find((s) => s.supports(response));
    if (!strategy) {
      throw new PaymentNotCompletedError(
        'unsupported',
        response.orderId,
        `Payment method ${response.provider ?? ''} ${response.mode ?? ''} is not supported.`.replace(
          /\s+/g,
          ' ',
        ),
      );
    }
    return context ? strategy.start(response, context) : strategy.start(response);
  }
}
