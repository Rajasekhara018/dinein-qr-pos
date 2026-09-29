import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from './api-base';
import { KITCHEN_STATUSES, KitchenConfig, KitchenOrderView, OrderStatus } from './models';

/** `/api/v1/kitchen/**` — the device token (or an owner/manager JWT via `withAuth('admin')`) is attached by authInterceptor. */
@Injectable({ providedIn: 'root' })
export class KitchenApi {
  private readonly http = inject(HttpClient);

  config(): Observable<KitchenConfig> {
    return this.http.get<KitchenConfig>(`${API_BASE}/kitchen/config`);
  }

  orders(
    statuses: readonly OrderStatus[] = KITCHEN_STATUSES,
    context?: HttpContext,
  ): Observable<KitchenOrderView[]> {
    const params = new HttpParams().set('status', statuses.join(','));
    return this.http.get<KitchenOrderView[]>(`${API_BASE}/kitchen/orders`, { params, context });
  }

  /** CONFIRMED → PREPARING → READY → COMPLETED (Start / Ready / Served). */
  changeStatus(
    orderId: number,
    status: OrderStatus,
    context?: HttpContext,
  ): Observable<KitchenOrderView> {
    return this.http.patch<KitchenOrderView>(
      `${API_BASE}/kitchen/orders/${orderId}/status`,
      { status },
      { context },
    );
  }

  /** Flags/unflags an order for the kitchen to work first. */
  setPriority(orderId: number, priority: boolean, context?: HttpContext): Observable<KitchenOrderView> {
    return this.http.patch<KitchenOrderView>(
      `${API_BASE}/kitchen/orders/${orderId}/priority`,
      { priority },
      { context },
    );
  }

  /** Keeps this device's last-seen timestamp fresh independent of other traffic; no-op for a staff JWT session. */
  heartbeat(context?: HttpContext): Observable<void> {
    return this.http.post<void>(`${API_BASE}/kitchen/heartbeat`, {}, { context });
  }
}
