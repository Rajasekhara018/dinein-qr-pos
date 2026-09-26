import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../api/api-error';
import { CheckoutResponse, RazorpayCheckoutPayload } from '../../api/models';
import { PublicApi } from '../../api/public.api';
import { BreakpointService } from '../../ui/breakpoint.service';
import { CheckoutOutcome, CheckoutStrategy, PaymentNotCompletedError } from '../checkout.types';
import { RazorpayCheckoutError, RazorpayOptions, RazorpayService } from '../razorpay.service';

/** `mode: SDK` + `provider: RAZORPAY` — opens Checkout.js, then verifies server-side. */
@Injectable({ providedIn: 'root' })
export class RazorpaySdkStrategy implements CheckoutStrategy {
  private readonly razorpay = inject(RazorpayService);
  private readonly api = inject(PublicApi);
  private readonly breakpoints = inject(BreakpointService);

  supports(response: CheckoutResponse): boolean {
    return response.mode === 'SDK' && (response.provider ?? 'RAZORPAY') === 'RAZORPAY';
  }

  async start(response: CheckoutResponse): Promise<CheckoutOutcome> {
    const payload = response.checkout as RazorpayCheckoutPayload | undefined;
    if (!payload?.key || !payload.order_id) {
      throw new PaymentNotCompletedError('unsupported', response.orderId, 'Payment details are missing.');
    }
    const { scriptUrl, ...options } = payload;

    let success;
    try {
      success = await this.razorpay.open(this.buildOptions(options as RazorpayOptions), scriptUrl);
    } catch (error) {
      const reason = error instanceof RazorpayCheckoutError ? error.reason : 'failed';
      const message = error instanceof Error ? error.message : 'The payment was not completed.';
      throw new PaymentNotCompletedError(reason, response.orderId, message, error);
    }

    try {
      const order = await firstValueFrom(this.api.verifyPayment({ ...success }));
      return { kind: 'paid', order };
    } catch (error) {
      // The webhook may still confirm it; the order page polls/listens, so surface a retryable state.
      throw new PaymentNotCompletedError(
        'verification-failed',
        response.orderId,
        ApiError.from(error).message || 'We could not confirm your payment yet.',
        error,
      );
    }
  }

  private buildOptions(options: RazorpayOptions): RazorpayOptions {
    const preferUpi = this.breakpoints.isHandset() || this.breakpoints.coarsePointer();
    return {
      ...options,
      // Our own "Payment not completed → Retry payment" flow replaces Razorpay's in-modal retry.
      retry: { enabled: false },
      modal: { ...(options.modal ?? {}), confirm_close: true, escape: true },
      ...(preferUpi
        ? {
            config: {
              display: {
                blocks: {
                  upi: { name: 'Pay using UPI', instruments: [{ method: 'upi' }] },
                },
                sequence: ['block.upi'],
                preferences: { show_default_blocks: true },
              },
            },
          }
        : {}),
    };
  }
}
