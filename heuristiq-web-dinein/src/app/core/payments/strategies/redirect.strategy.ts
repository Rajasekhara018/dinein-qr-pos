import { inject, Injectable } from '@angular/core';
import { CheckoutResponse, RedirectCheckoutPayload } from '../../api/models';
import { BrowserNavigator } from '../../util/browser-navigator';
import { CheckoutOutcome, CheckoutStrategy, PaymentNotCompletedError } from '../checkout.types';

/** `mode: REDIRECT`: navigates to the provider-hosted URL. */
@Injectable({ providedIn: 'root' })
export class RedirectStrategy implements CheckoutStrategy {
  private readonly navigator = inject(BrowserNavigator);

  supports(response: CheckoutResponse): boolean {
    return response.mode === 'REDIRECT';
  }

  async start(response: CheckoutResponse): Promise<CheckoutOutcome> {
    const url = (response.checkout as RedirectCheckoutPayload | undefined)?.url;
    if (!url || !/^https?:\/\//i.test(url)) {
      throw new PaymentNotCompletedError(
        'unsupported',
        response.orderId,
        'Payment link is missing.',
      );
    }
    this.navigator.assign(url);
    return { kind: 'navigating' };
  }
}
