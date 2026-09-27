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
}
