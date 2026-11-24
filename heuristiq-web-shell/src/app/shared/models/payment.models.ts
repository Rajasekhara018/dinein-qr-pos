// Mirrors com.railcab.paymentapi.payment.checkout.dto / status.dto / domain.model field-for-field
// (2026_RailCab_PaymentAPI), reached through the gateway's pay-api route (see environment.paymentApiUrl).
import { HqAmount } from 'hq-shared-lib';

export type PaymentGateway = 'RAZORPAY' | 'PAYU' | 'TOUCAN' | 'PINELABS' | 'CASHFREE';

export type PaymentStatusState = 'CREATED' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'EXPIRED';

export interface CreatePaymentRequest {
  customerName: string;
  customerMobileNo: string;
  customerEmailId?: string;
  amount: number;
  gateway: PaymentGateway;
  paymentMode?: string;
  paymentType?: string;
  orderId: string;
  terminalNo?: string;
  currency: string;
  paymentContext: string;
  merchantToken?: string;
  merchantUrl?: string;
  hash?: string;
  sdkRequest?: string;
  sdkName?: string;
  postalCode?: string;
  settlementCurrency?: string;
}

/** Body of Rider_PaymentService's POST /api/v1/payments (CreatePaymentRequest record). */
export interface RiderCreatePaymentRequest {
  rideId: string;
  customerId: string;
  /** `{ code, value }` (money stage 3). */
  amount: HqAmount | null;
  /** CASH, or RAZORPAY for any online payment -- `gateway` then picks the provider. */
  method: 'CASH' | 'RAZORPAY';
  /** Same key => the backend returns the payment it already created instead of a new one. */
  idempotencyKey: string;
  gateway?: PaymentGateway;
  /** When true, a rideId that doesn't exist in Rider_RideService no longer fails the payment
   *  with "Ride not found" -- for exercising a gateway's checkout flow with a scratch rideId. */
  testTransaction?: boolean;
  /** Payer details entered at checkout; stored with the payment and shown on its invoice. */
  customerName?: string;
  customerEmail?: string;
  customerMobile?: string;
}

/** Rider_PaymentService's PaymentResponse record. */
export interface RiderPaymentResponse {
  paymentId: string;
  providerPaymentId?: string;
  providerOrderId?: string;
  rideId: string;
  customerId: string;
  /** `{ code, value }`; the currency is inside it (read through hqAmountValue / hqAmountCode: older answers send a bare number). */
  amount: HqAmount | null;
  status: string;
  method: string;
  gateway?: string;
  invoiceNumber?: string;
  receiptNumber?: string;
  customerName?: string;
  customerEmail?: string;
  customerMobile?: string;
  createdAt?: string;
  updatedAt?: string;
  /** Only for gateway=RAZORPAY. */
  razorpayOrder?: { orderId: string; amountInPaise: number; currency: string; keyId: string };
  /** PAYU: { action, method, fields }. TOUCAN / PINELABS: { redirectUrl }. */
  gatewaySession?: Record<string, any>;
}

export interface CreatePaymentResponse {
  paymentId: string;
  gateway: string;
  status: string;
  redirectUrl?: string;
  callbackUrl?: string;
  sessionToken?: string;
  checkoutConfig?: Record<string, any>;
}

export interface PaymentStatusRequest {
  messageID: string;
  gateway: PaymentGateway | string;
  object: {
    rrn?: string;
    terminalNumber?: string;
    invoiceNumber?: string;
    merchantNumber?: string;
    orderNo?: string;
  };
}

// Full PaymentTransaction record, used to render the invoice/receipt page via getByPaymentId().
/** One step of a payment's lifecycle (GET /payments/{id}/transitions), oldest first. */
export interface PaymentLifecycleStep {
  fromStatus?: string;
  toStatus: string;
  /** CHECKOUT, STATUS_POLL, RECONCILE, SETTLEMENT, REFUND, PAYMENT_SERVICE or DERIVED. */
  source?: string;
  reason?: string;
  transitionedAt?: string;
}

export interface PaymentTransaction {
  id?: string;
  rideId?: string;
  receiptNumber?: string;
  paymentContext?: string;
  invoiceNumber?: string;
  paymentId: string;
  urn?: string;
  rrn?: string;
  transactionId?: string;
  authCode?: string;
  actionCode?: string;
  approvalCode?: string;
  customerName?: string;
  mobile?: string;
  email?: string;
  customerVpa?: string;
  paymentMode?: string;
  paymentType?: string;
  gateway?: string;
  processorName?: string;
  txnType?: string;
  txnSubType?: string;
  cardType?: string;
  recordLogId?: string;
  accountDetails?: string;
  merchantName?: string;
  merchantNumber?: string;
  terminalNumber?: string;
  merchantUrl?: string;
  transactionAmount?: number;
  transactionCurrency?: string;
  status: PaymentStatusState | string;
  errorMessage?: string;
  saleDateTime?: string;
  createdAt?: string;
  updatedAt?: string;
}
