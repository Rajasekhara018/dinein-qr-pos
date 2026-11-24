import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TranslateService } from '@ngx-translate/core';
import { hqAmountCode, hqAmountValue } from 'hq-shared-lib';
import {
  CreatePaymentResponse, PaymentLifecycleStep, PaymentStatusRequest, PaymentTransaction,
  RiderCreatePaymentRequest, RiderPaymentResponse,
} from '../models/payment.models';

/** Rider_PaymentService's envelope (shared/dto/ResponseObject). */
interface RiderResponseObject<T> {
  status: boolean;
  object?: T;
  errorMsg?: string;
}

/**
 * Talks to Rider_PaymentService through the API Gateway's rider-payment route
 * (environment.paymentApiUrl -> /api/v1/payments/**). Requests are plain JSON bodies and responses
 * come back in RiderResponseObject's { status, object, errorMsg } envelope.
 */
@Injectable({ providedIn: 'root' })
export class PaymentService {
  private base = environment.paymentApiUrl;

  constructor(private http: HttpClient, private translate: TranslateService) {}

  /**
   * Rider_PaymentService POST /api/v1/payments (gateway: /api/v1/rider/payment/payments). The body
   * is the plain CreatePaymentRequest -- no { requestObject } wrapper. The response is normalized
   * into CreatePaymentResponse so make-payment's checkout strategies can open the gateway.
   */
  createPayment(payload: RiderCreatePaymentRequest): Observable<CreatePaymentResponse> {
    return this.http.post<RiderResponseObject<RiderPaymentResponse>>(`${this.base}/payments`, payload)
      .pipe(map((res) => this.toCheckout(this.unwrapRider(res))));
  }

  /** Rider_PaymentService GET /api/v1/payments/{paymentId}, in the shape the invoice page reads. */
  getByPaymentId(paymentId: string): Observable<PaymentTransaction> {
    return this.http.get<RiderResponseObject<RiderPaymentResponse>>(`${this.base}/payments/${paymentId}`)
      .pipe(map((res) => this.toTransaction(this.unwrapRider(res))));
  }

  /** The payment's lifecycle, oldest step first. */
  getTransitions(paymentId: string): Observable<PaymentLifecycleStep[]> {
    return this.http.get<RiderResponseObject<PaymentLifecycleStep[]>>(`${this.base}/payments/${encodeURIComponent(paymentId)}/transitions`)
      .pipe(map((res) => this.unwrapRider(res)));
  }

  /**
   * Reconciles one payment against the live gateway (Rider_PaymentService POST /payments/{id}/reconcile)
   * instead of waiting on a webhook that may never arrive -- without this a paid Razorpay payment
   * stays INITIATED. The payment id is the payload's messageID; callers re-read the payment afterwards.
   */
  checkStatus(payload: PaymentStatusRequest): Observable<unknown> {
    return this.http.post<RiderResponseObject<RiderPaymentResponse>>(
      `${this.base}/payments/${encodeURIComponent(payload.messageID)}/reconcile`, {})
      .pipe(map((res) => this.unwrapRider(res)));
  }

  private unwrapRider<T>(res: RiderResponseObject<T>): T {
    if (!res?.status) {
      const message = res?.errorMsg || this.translate.instant('shared.errors.requestFailed');
      throw { error: { detail: message, message } };
    }
    return res.object as T;
  }

  // razorpayOrder / gatewaySession -> the checkoutConfig / redirectUrl the checkout strategies use.
  private toCheckout(p: RiderPaymentResponse): CreatePaymentResponse {
    const response: CreatePaymentResponse = { paymentId: p.paymentId, gateway: p.gateway || '', status: p.status };
    if (p.razorpayOrder) {
      response.checkoutConfig = {
        key: p.razorpayOrder.keyId,
        amount: p.razorpayOrder.amountInPaise,
        currency: p.razorpayOrder.currency,
        order_id: p.razorpayOrder.orderId,
      };
    } else if (p.gatewaySession?.['redirectUrl']) {
      response.redirectUrl = p.gatewaySession['redirectUrl'];
    } else if (p.gatewaySession?.['action']) {
      response.checkoutConfig = { ...p.gatewaySession, autoSubmit: true };
    }
    return response;
  }

  private toTransaction(p: RiderPaymentResponse): PaymentTransaction {
    return {
      paymentId: p.paymentId,
      invoiceNumber: p.invoiceNumber,
      receiptNumber: p.receiptNumber,
      rideId: p.rideId,
      customerName: p.customerName,
      email: p.customerEmail,
      mobile: p.customerMobile,
      transactionId: p.providerPaymentId || p.providerOrderId,
      gateway: p.gateway,
      paymentMode: p.method,
      transactionAmount: hqAmountValue(p.amount) ?? undefined,
      transactionCurrency: hqAmountCode(p.amount, environment.defaultCurrency) || undefined,
      status: this.toInvoiceStatus(p.status),
      createdAt: p.createdAt,
      updatedAt: p.updatedAt,
    } as PaymentTransaction;
  }

  // Rider_PaymentService's PaymentStatus -> the states the invoice page understands.
  private toInvoiceStatus(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'CAPTURED':
      case 'SETTLED':
        return 'SUCCESS';
      case 'INITIATED':
      case 'AUTHORIZED':
        return 'PENDING';
      case 'REVERSED':
      case 'REFUNDED':
      case 'PARTIALLY_REFUNDED':
        return 'CANCELLED';
      default:
        return (status || '').toUpperCase();
    }
  }
}
