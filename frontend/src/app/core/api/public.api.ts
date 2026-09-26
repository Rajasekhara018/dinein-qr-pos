import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { silentErrors } from '../http/http-context';
import {
  CheckoutResponse,
  GuestOrderSummary,
  GuestOrderView,
  MenuResponse,
  PaymentVerifyRequest,
  PlaceOrderRequest,
  SessionResponse,
} from './models';

/**
 * Guest-facing endpoints (`/api/public/**`). Authenticated by the HttpOnly `dinein_gs` cookie the browser sends
 * automatically; mutations carry the CSRF header (see csrfInterceptor).
 */
@Injectable({ providedIn: 'root' })
export class PublicApi {
  private readonly http = inject(HttpClient);

  /**
   * With a QR token: validates the table and issues/renews the guest cookie.
   * Without: resumes from the existing cookie (401 GUEST_SESSION_REQUIRED if there is none).
   */
  session(qrToken?: string | null): Observable<SessionResponse> {
    const params = qrToken ? new HttpParams().set('t', qrToken) : undefined;
    return this.http.get<SessionResponse>('/api/public/session', {
      params,
      context: silentErrors(),
    });
  }

  /**
   * The menu. Served with `ETag` + `Cache-Control: no-cache`, so the browser revalidates with `If-None-Match`
   * and transparently turns a 304 into the cached 200 — do not add cache-busting params.
   */
  menu(): Observable<MenuResponse> {
    return this.http.get<MenuResponse>('/api/public/menu');
  }

  /** Places an order. `idempotencyKey` must be stable per checkout attempt. Errors are handled by the caller. */
  placeOrder(body: PlaceOrderRequest, idempotencyKey: string): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>('/api/public/orders', body, {
      headers: { 'Idempotency-Key': idempotencyKey },
      context: silentErrors(),
    });
  }

  /** New checkout for a PENDING_PAYMENT order (reuses the same order and provider order). */
  retryPayment(orderId: number): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`/api/public/orders/${orderId}/retry-payment`, null, {
      context: silentErrors(),
    });
  }

  /** Client-side verification after an SDK checkout (e.g. the Razorpay success handler response). */
  verifyPayment(body: PaymentVerifyRequest): Observable<GuestOrderView> {
    return this.http.post<GuestOrderView>('/api/public/payments/verify', body, {
      context: silentErrors(),
    });
  }

  order(orderId: number): Observable<GuestOrderView> {
    return this.http.get<GuestOrderView>(`/api/public/orders/${orderId}`);
  }

  myOrders(): Observable<GuestOrderSummary[]> {
    return this.http.get<GuestOrderSummary[]>('/api/public/orders');
  }
}
