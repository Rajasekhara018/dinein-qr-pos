import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { silentErrors } from '../http/http-context';
import { API_BASE } from './api-base';
import {
  CheckoutResponse,
  GuestOrderView,
  KITCHEN_STATUSES,
  KioskCounterOrder,
  KioskCounterPaymentMethod,
  KitchenOrderView,
  MenuResponse,
  NotificationSearchParams,
  NotificationView,
  OrderStatus,
  PageResponse,
  PaymentVerifyRequest,
  StaffPlaceOrderRequest,
  UnreadCount,
  WaiterConfig,
  WaiterTableView,
} from './models';

/**
 * Waiter screen (`/api/v1/waiter/**`, roles WAITER / MANAGER / OWNER). The staff access JWT is attached by
 * `authInterceptor` (same session as the admin panel).
 */
@Injectable({ providedIn: 'root' })
export class WaiterApi {
  private readonly http = inject(HttpClient);

  config(): Observable<WaiterConfig> {
    return this.http.get<WaiterConfig>(`${API_BASE}/waiter/config`);
  }

  tables(context?: HttpContext): Observable<WaiterTableView[]> {
    return this.http.get<WaiterTableView[]>(`${API_BASE}/waiter/tables`, { context });
  }

  /** Active orders, oldest paid first; READY orders stay until served. */
  orders(
    statuses: readonly OrderStatus[] = KITCHEN_STATUSES,
    context?: HttpContext,
  ): Observable<KitchenOrderView[]> {
    const params = new HttpParams().set('status', statuses.join(','));
    return this.http.get<KitchenOrderView[]>(`${API_BASE}/waiter/orders`, { params, context });
  }

  /** One order with bill and latest payment. */
  order(id: number, context?: HttpContext): Observable<GuestOrderView> {
    return this.http.get<GuestOrderView>(`${API_BASE}/waiter/orders/${id}`, { context });
  }

  /** READY → COMPLETED (409 ILLEGAL_TRANSITION otherwise). Errors are handled by the caller. */
  serve(id: number): Observable<KitchenOrderView> {
    return this.http.patch<KitchenOrderView>(`${API_BASE}/waiter/orders/${id}/serve`, null, {
      context: silentErrors(),
    });
  }

  /** The guest menu including unavailable items (`available: false`). ETag-revalidated by the browser. */
  menu(): Observable<MenuResponse> {
    return this.http.get<MenuResponse>(`${API_BASE}/waiter/menu`);
  }

  /** Staff-assisted order (offline payment → CONFIRMED right away; ONLINE → a normal checkout). */
  placeOrder(body: StaffPlaceOrderRequest): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${API_BASE}/waiter/orders`, body, {
      context: silentErrors(),
    });
  }

  /** New checkout for an unpaid staff-assisted ONLINE order (409 NOT_STAFF_ORDER / ORDER_NOT_PAYABLE). */
  retryPayment(orderId: number): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(
      `${API_BASE}/waiter/orders/${orderId}/retry-payment`,
      null,
      { context: silentErrors() },
    );
  }

  /** SDK checkout success handler for orders paid on the staff device. */
  verifyPayment(body: PaymentVerifyRequest): Observable<GuestOrderView> {
    return this.http.post<GuestOrderView>(`${API_BASE}/waiter/payments/verify`, body, {
      context: silentErrors(),
    });
  }

  /** Unpaid kiosk orders (PENDING_PAYMENT / EXPIRED), oldest first. Background polling: no global error toast. */
  kioskOrders(): Observable<KioskCounterOrder[]> {
    return this.http.get<KioskCounterOrder[]>(`${API_BASE}/waiter/kiosk-orders`, {
      context: silentErrors(),
    });
  }

  /** Takes payment at the counter (409 ALREADY_PAID / ORDER_NOT_PAYABLE / PAYMENT_FLAGGED / NOT_A_KIOSK_ORDER). */
  payKioskOrder(id: number, method: KioskCounterPaymentMethod): Observable<void> {
    return this.http.post<void>(`${API_BASE}/waiter/kiosk-orders/${id}/pay`, { method }, { context: silentErrors() });
  }

  notifications(search: NotificationSearchParams = {}): Observable<PageResponse<NotificationView>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(search)) {
      if (value !== undefined && value !== null) params = params.set(key, String(value));
    }
    return this.http.get<PageResponse<NotificationView>>(`${API_BASE}/waiter/notifications`, {
      params,
    });
  }

  /** Background refresh: no global error toast. */
  unreadCount(): Observable<UnreadCount> {
    return this.http.get<UnreadCount>(`${API_BASE}/waiter/notifications/unread-count`, {
      context: silentErrors(),
    });
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${API_BASE}/waiter/notifications/${id}/read`, null);
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${API_BASE}/waiter/notifications/read-all`, null);
  }
}
