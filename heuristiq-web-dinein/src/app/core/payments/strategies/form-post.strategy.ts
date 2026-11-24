import { inject, Injectable } from '@angular/core';
import { CheckoutResponse, FormPostCheckoutPayload } from '../../api/models';
import { BrowserNavigator } from '../../util/browser-navigator';
import { CheckoutOutcome, CheckoutStrategy, PaymentNotCompletedError } from '../checkout.types';

/**
 * `mode: FORM_POST` (e.g. PayU): auto-submits a hidden form to the provider's hosted page. The provider posts back
 * to the backend callback, which 303-redirects to `/menu/orders/{id}?payment=return|failed`.
 */
@Injectable({ providedIn: 'root' })
export class FormPostStrategy implements CheckoutStrategy {
  private readonly navigator = inject(BrowserNavigator);

  supports(response: CheckoutResponse): boolean {
    return response.mode === 'FORM_POST';
  }

  async start(response: CheckoutResponse): Promise<CheckoutOutcome> {
    const payload = response.checkout as FormPostCheckoutPayload | undefined;
    if (!payload?.action || !payload.fields) {
      throw new PaymentNotCompletedError(
        'unsupported',
        response.orderId,
        'Payment details are missing.',
      );
    }
    this.navigator.submitForm(payload.action, payload.fields, payload.method ?? 'POST');
    return { kind: 'navigating' };
  }
}
