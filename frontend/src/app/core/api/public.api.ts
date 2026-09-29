import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { silentErrors } from '../http/http-context';
import { API_BASE } from './api-base';
import {
  CheckoutResponse,
  DisplayBoardView,
  GuestOrderSummary,
  GuestOrderView,
  MenuResponse,
  PaymentVerifyRequest,
  PlaceOrderRequest,
  SessionResponse,
} from './models';

/**
 * Guest-facing endpoints (`/api/v1/public/**`). Authenticated by the HttpOnly `dinein_gs` cookie the browser sends
 * automatically; mutations carry the CSRF header (see csrfInterceptor).
 */
@Injectable({ providedIn: 'root' })
export class PublicApi {
  private readonly http = inject(HttpClient);

  /**
   * With a QR token: validates the table and issues/renews the guest cookie.
   * Without: resumes from the existing cookie (401 GUEST_SESSION_REQUIRED if there is none).
   */
  session(qrToken?: string | null, restaurantId?: string | null): Observable<SessionResponse> {
    let params = qrToken ? new HttpParams().set('t', qrToken) : undefined;
    if (qrToken && restaurantId) params = params!.set('r', restaurantId);
    return this.http.get<SessionResponse>(`${API_BASE}/public/session`, {
      params,
      context: silentErrors(),
    });
  }

  /**
   * The menu. Served with `ETag` + `Cache-Control: no-cache`, so the browser revalidates with `If-None-Match`
   * and transparently turns a 304 into the cached 200 — do not add cache-busting params.
   */
  menu(): Observable<MenuResponse> {
    return this.http.get<MenuResponse>(`${API_BASE}/public/menu`);
  }

  /** Customer display board snapshot (no session/cookie needed; runs unattended on a screen). */
  displayBoard(restaurantId: number): Observable<DisplayBoardView> {
    return this.http.get<DisplayBoardView>(`${API_BASE}/public/display`, {
      params: new HttpParams().set('restaurantId', restaurantId),
    });
  }

  /** Places an order. `idempotencyKey` must be stable per checkout attempt. Errors are handled by the caller. */
  placeOrder(body: PlaceOrderRequest, idempotencyKey: string): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${API_BASE}/public/orders`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
      context: silentErrors(),
    });
  }

  /** New checkout for a PENDING_PAYMENT order (reuses the same order and provider order). */
  retryPayment(orderId: number): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${API_BASE}/public/orders/${orderId}/retry-payment`, null, {
      context: silentErrors(),
    });
  }

  /** Client-side verification after an SDK checkout (e.g. the Razorpay success handler response). */
  verifyPayment(body: PaymentVerifyRequest): Observable<GuestOrderView> {
    return this.http.post<GuestOrderView>(`${API_BASE}/public/payments/verify`, body, {
      context: silentErrors(),
    });
  }

  order(orderId: number): Observable<GuestOrderView> {
    return this.http.get<GuestOrderView>(`${API_BASE}/public/orders/${orderId}`);
  }

  myOrders(): Observable<GuestOrderSummary[]> {
    return this.http.get<GuestOrderSummary[]>(`${API_BASE}/public/orders`);
  }
}
