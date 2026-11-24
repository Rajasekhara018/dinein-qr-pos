import type { CreatePaymentResponse } from '../../shared/models/payment.models';
import type { MakePayment } from './make-payment';

// Strategy pattern for per-gateway checkout dispatch. Each strategy owns the "does this apply"
// predicate for one gateway/response shape and the corresponding checkout action; makePay() just
// resolves the first matching strategy instead of branching on selectedGateway inline.
export interface PaymentCheckoutStrategy {
  supports(gateway: string, response: CreatePaymentResponse): boolean;
  execute(component: MakePayment, response: CreatePaymentResponse): void;
}

class RazorpayCheckoutStrategy implements PaymentCheckoutStrategy {
  supports(gateway: string, response: CreatePaymentResponse): boolean {
    return gateway === 'RAZORPAY' && !!response.checkoutConfig;
  }

  execute(component: MakePayment, response: CreatePaymentResponse): void {
    component.openRazorpayCheckout(response.paymentId, response.checkoutConfig!);
  }
}

class CashfreeCheckoutStrategy implements PaymentCheckoutStrategy {
  supports(gateway: string, response: CreatePaymentResponse): boolean {
    return gateway === 'CASHFREE' && !!response.checkoutConfig;
  }

  execute(component: MakePayment, response: CreatePaymentResponse): void {
    component.openCashfreeCheckout(response.paymentId, response.checkoutConfig!);
  }
}

// PayU (and any gateway using the hosted-form pattern) returns a checkoutConfig with
// autoSubmit=true instead of a usable redirectUrl -- the transaction is only actually
// initiated by POSTing its signed fields straight to the gateway's own payment-url.
class AutoFormCheckoutStrategy implements PaymentCheckoutStrategy {
  supports(_gateway: string, response: CreatePaymentResponse): boolean {
    return !!response.checkoutConfig?.['autoSubmit'];
  }

  execute(component: MakePayment, response: CreatePaymentResponse): void {
    component.submitAutoFormCheckout(response.checkoutConfig!);
  }
}

// Generic redirect fallback used by Toucan/PineLabs and any other gateway that just hands back
// a redirectUrl to navigate the browser to.
class RedirectCheckoutStrategy implements PaymentCheckoutStrategy {
  supports(_gateway: string, response: CreatePaymentResponse): boolean {
    return !!response.redirectUrl;
  }

  execute(_component: MakePayment, response: CreatePaymentResponse): void {
    window.location.href = response.redirectUrl!;
  }
}

export const PAYMENT_CHECKOUT_STRATEGIES: readonly PaymentCheckoutStrategy[] = [
  new RazorpayCheckoutStrategy(),
  new CashfreeCheckoutStrategy(),
  new AutoFormCheckoutStrategy(),
  new RedirectCheckoutStrategy(),
];
