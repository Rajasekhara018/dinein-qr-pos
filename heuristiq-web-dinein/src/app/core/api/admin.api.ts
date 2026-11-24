import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { silentErrors } from '../http/http-context';
import { API_BASE } from './api-base';
import {
  AdminOrderSearchParams,
  AdminOrderSummary,
  AdminOrderView,
  CategoryRequest,
  CategoryResponse,
  CheckoutResponse,
  CreateKioskDeviceRequest,
  CreateStaffRequest,
  Dashboard,
  DeviceResponse,
  IsoLocalDate,
  ItemRequest,
  ItemResponse,
  ItemSearchParams,
  KioskBrandingResponse,
  KioskDeviceResponse,
  KioskPairingResponse,
  KioskUpsellRequest,
  KioskUpsellView,
  KitchenStationRequest,
  KitchenStationResponse,
  NotificationSearchParams,
  NotificationView,
  OfflinePaymentMethod,
  OrderStatus,
  PageResponse,
  PriceRequest,
  SalesSummary,
  SettingsResponse,
  StaffPlaceOrderRequest,
  StaffResponse,
  MergeTablesRequest,
  MoveOrderRequest,
  ReserveTableRequest,
  SplitOrderRequest,
  TableRequest,
  TableResponse,
  UpdateKioskBrandingRequest,
  UpdateSettingsRequest,
  UnreadCount,
  UpdateStaffRequest,
  UploadResult,
} from './models';

function toParams(
  values: Record<
    string,
    string | number | boolean | readonly (string | number)[] | null | undefined
  >,
): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '') continue;
    params = params.set(key, Array.isArray(value) ? value.join(',') : String(value));
  }
  return params;
}

/** Menu admin: `/api/v1/admin/categories`, `/api/v1/admin/items`, `/api/v1/admin/images`. */
@Injectable({ providedIn: 'root' })
export class AdminMenuApi {
  private readonly http = inject(HttpClient);

  categories(): Observable<CategoryResponse[]> {
    return this.http.get<CategoryResponse[]>(`${API_BASE}/admin/categories`);
  }

  createCategory(body: CategoryRequest): Observable<CategoryResponse> {
    return this.http.post<CategoryResponse>(`${API_BASE}/admin/categories`, body);
  }

  updateCategory(id: number, body: CategoryRequest): Observable<CategoryResponse> {
    return this.http.put<CategoryResponse>(`${API_BASE}/admin/categories/${id}`, body);
  }

  setCategoryActive(id: number, active: boolean): Observable<CategoryResponse> {
    return this.http.patch<CategoryResponse>(`${API_BASE}/admin/categories/${id}/status`, { active });
  }

  reorderCategories(ids: number[]): Observable<CategoryResponse[]> {
    return this.http.patch<CategoryResponse[]>(`${API_BASE}/admin/categories/reorder`, { ids });
  }

  kitchenStations(): Observable<KitchenStationResponse[]> {
    return this.http.get<KitchenStationResponse[]>(`${API_BASE}/admin/kitchen-stations`);
  }

  createKitchenStation(body: KitchenStationRequest): Observable<KitchenStationResponse> {
    return this.http.post<KitchenStationResponse>(`${API_BASE}/admin/kitchen-stations`, body);
  }

  updateKitchenStation(id: number, body: KitchenStationRequest): Observable<KitchenStationResponse> {
    return this.http.put<KitchenStationResponse>(`${API_BASE}/admin/kitchen-stations/${id}`, body);
  }

  deleteKitchenStation(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/admin/kitchen-stations/${id}`);
  }

  items(search: ItemSearchParams = {}): Observable<PageResponse<ItemResponse>> {
    return this.http.get<PageResponse<ItemResponse>>(`${API_BASE}/admin/items`, {
      params: toParams({ ...search }),
    });
  }

  item(id: number): Observable<ItemResponse> {
    return this.http.get<ItemResponse>(`${API_BASE}/admin/items/${id}`);
  }

  createItem(body: ItemRequest): Observable<ItemResponse> {
    return this.http.post<ItemResponse>(`${API_BASE}/admin/items`, body);
  }

  updateItem(id: number, body: ItemRequest): Observable<ItemResponse> {
    return this.http.put<ItemResponse>(`${API_BASE}/admin/items/${id}`, body);
  }

  setItemAvailability(id: number, available: boolean): Observable<ItemResponse> {
    return this.http.patch<ItemResponse>(`${API_BASE}/admin/items/${id}/availability`, { available });
  }

  updateItemPrice(id: number, body: PriceRequest): Observable<ItemResponse> {
    return this.http.patch<ItemResponse>(`${API_BASE}/admin/items/${id}/price`, body);
  }

  deleteItem(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/admin/items/${id}`);
  }

  /** Multipart upload (field `file`); subscribe to events for progress. */
  uploadImage(file: Blob, fileName = 'image.jpg'): Observable<HttpEvent<UploadResult>> {
    const form = new FormData();
    form.append('file', file, fileName);
    return this.http.post<UploadResult>(`${API_BASE}/admin/images`, form, {
      reportProgress: true,
      observe: 'events',
    });
  }
}

/** `/api/v1/admin/tables` */
@Injectable({ providedIn: 'root' })
export class AdminTablesApi {
  private readonly http = inject(HttpClient);

  list(): Observable<TableResponse[]> {
    return this.http.get<TableResponse[]>(`${API_BASE}/admin/tables`);
  }

  create(body: TableRequest): Observable<TableResponse> {
    return this.http.post<TableResponse>(`${API_BASE}/admin/tables`, body);
  }

  update(id: number, body: TableRequest): Observable<TableResponse> {
    return this.http.put<TableResponse>(`${API_BASE}/admin/tables/${id}`, body);
  }

  regenerateQr(id: number): Observable<TableResponse> {
    return this.http.post<TableResponse>(`${API_BASE}/admin/tables/${id}/regenerate-qr`, null);
  }

  reserve(id: number, body: ReserveTableRequest): Observable<TableResponse> {
    return this.http.put<TableResponse>(`${API_BASE}/admin/tables/${id}/reservation`, body);
  }

  clearReservation(id: number): Observable<TableResponse> {
    return this.http.delete<TableResponse>(`${API_BASE}/admin/tables/${id}/reservation`);
  }

  /** Moves this table's open order(s) to an empty, unreserved table. */
  moveOrders(id: number, body: MoveOrderRequest): Observable<TableResponse> {
    return this.http.post<TableResponse>(`${API_BASE}/admin/tables/${id}/move`, body);
  }

  /** Combines two tables' bills onto `toTableId`; `fromTableId` ends up free. */
  merge(body: MergeTablesRequest): Observable<TableResponse> {
    return this.http.post<TableResponse>(`${API_BASE}/admin/tables/merge`, body);
  }

  /** Printable A4 PDF (all tables when `ids` is empty). Returned as a Blob because it needs the bearer token. */
  qrPdf(ids: number[] = []): Observable<Blob> {
    return this.http.get(`${API_BASE}/admin/tables/qr.pdf`, {
      params: toParams({ ids }),
      responseType: 'blob',
    });
  }

  /** `path` is a table's `qrImageUrl` as the list/create/update response gives it: already relative, already
   *  versioned by the table's current QR token so the browser can cache it immutably and a regenerate always
   *  gets a fresh URL instead of risking a stale cached response under the old one. */
  qrPng(path: string): Observable<Blob> {
    return this.http.get(path, { responseType: 'blob' });
  }
}

/** `/api/v1/admin/orders` */
@Injectable({ providedIn: 'root' })
export class AdminOrdersApi {
  private readonly http = inject(HttpClient);

  list(search: AdminOrderSearchParams = {}): Observable<PageResponse<AdminOrderSummary>> {
    return this.http.get<PageResponse<AdminOrderSummary>>(`${API_BASE}/admin/orders`, {
      params: toParams({ ...search }),
    });
  }

  get(id: number): Observable<AdminOrderView> {
    return this.http.get<AdminOrderView>(`${API_BASE}/admin/orders/${id}`);
  }

  changeStatus(id: number, status: OrderStatus): Observable<AdminOrderView> {
    return this.http.patch<AdminOrderView>(`${API_BASE}/admin/orders/${id}/status`, { status });
  }

  /** Counter order placed by an owner/manager (same contract as `POST /api/v1/waiter/orders`). */
  place(body: StaffPlaceOrderRequest): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${API_BASE}/admin/orders`, body, {
      context: silentErrors(),
    });
  }

  /**
   * Settles a PENDING_PAYMENT / EXPIRED / PAYMENT_FAILED order at the counter and sends it to the kitchen.
   * 409 ALREADY_PAID / ORDER_NOT_PAYABLE / PAYMENT_FLAGGED are handled by the caller (silent).
   */
  markPaidOffline(id: number, method: OfflinePaymentMethod): Observable<AdminOrderView> {
    return this.http.post<AdminOrderView>(
      `${API_BASE}/admin/orders/${id}/mark-paid-offline`,
      { method },
      { context: silentErrors() },
    );
  }

  /**
   * Cancels a paid order and refunds it in full (calling again retries a failed refund). Errors are silent (no
   * global toast): a `REFUND_FAILED` (502) is expected and handled by the caller.
   */
  cancel(id: number, reason?: string): Observable<AdminOrderView> {
    return this.http.post<AdminOrderView>(
      `${API_BASE}/admin/orders/${id}/cancel`,
      { reason: reason || null },
      { context: silentErrors() },
    );
  }

  /** Splits an unpaid order into separate bills, one per item group. 409 NOT_SPLITTABLE once paid/confirmed. */
  split(id: number, body: SplitOrderRequest): Observable<AdminOrderView[]> {
    return this.http.post<AdminOrderView[]>(`${API_BASE}/admin/orders/${id}/split`, body);
  }
}

/** Dashboard + OWNER reports. */
@Injectable({ providedIn: 'root' })
export class AdminReportsApi {
  private readonly http = inject(HttpClient);

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${API_BASE}/admin/dashboard`);
  }

  summary(from: IsoLocalDate, to: IsoLocalDate): Observable<SalesSummary> {
    return this.http.get<SalesSummary>(`${API_BASE}/admin/reports/summary`, {
      params: toParams({ from, to }),
    });
  }

  ordersCsv(from: IsoLocalDate, to: IsoLocalDate): Observable<Blob> {
    return this.http.get(`${API_BASE}/admin/reports/orders.csv`, {
      params: toParams({ from, to }),
      responseType: 'blob',
    });
  }
}

/** OWNER: settings, staff and kitchen devices. */
@Injectable({ providedIn: 'root' })
export class AdminSettingsApi {
  private readonly http = inject(HttpClient);

  settings(): Observable<SettingsResponse> {
    return this.http.get<SettingsResponse>(`${API_BASE}/admin/settings`);
  }

  updateSettings(body: UpdateSettingsRequest): Observable<SettingsResponse> {
    return this.http.put<SettingsResponse>(`${API_BASE}/admin/settings`, body);
  }

  staff(): Observable<StaffResponse[]> {
    return this.http.get<StaffResponse[]>(`${API_BASE}/admin/staff`);
  }

  createStaff(body: CreateStaffRequest): Observable<StaffResponse> {
    return this.http.post<StaffResponse>(`${API_BASE}/admin/staff`, body);
  }

  updateStaff(id: number, body: UpdateStaffRequest): Observable<StaffResponse> {
    return this.http.put<StaffResponse>(`${API_BASE}/admin/staff/${id}`, body);
  }

  devices(): Observable<DeviceResponse[]> {
    return this.http.get<DeviceResponse[]>(`${API_BASE}/admin/devices`);
  }

  revokeDevice(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/admin/devices/${id}`);
  }
}

/** Staff inbox: `/api/v1/admin/notifications` (owner/manager). */
@Injectable({ providedIn: 'root' })
export class AdminNotificationsApi {
  private readonly http = inject(HttpClient);

  list(search: NotificationSearchParams = {}): Observable<PageResponse<NotificationView>> {
    return this.http.get<PageResponse<NotificationView>>(`${API_BASE}/admin/notifications`, {
      params: toParams({ ...search }),
    });
  }

  /** Background poll/refresh: no global error toast. */
  unreadCount(): Observable<UnreadCount> {
    return this.http.get<UnreadCount>(`${API_BASE}/admin/notifications/unread-count`, {
      context: silentErrors(),
    });
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${API_BASE}/admin/notifications/${id}/read`, null);
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${API_BASE}/admin/notifications/read-all`, null);
  }
}

/** Self-order kiosks (owner/manager): `/api/v1/admin/kiosk-devices` and `/api/v1/admin/kiosk-branding`. */
@Injectable({ providedIn: 'root' })
export class AdminKioskApi {
  private readonly http = inject(HttpClient);

  devices(): Observable<KioskDeviceResponse[]> {
    return this.http.get<KioskDeviceResponse[]>(`${API_BASE}/admin/kiosk-devices`);
  }

  createDevice(body: CreateKioskDeviceRequest): Observable<KioskPairingResponse> {
    return this.http.post<KioskPairingResponse>(`${API_BASE}/admin/kiosk-devices`, body);
  }

  /** Issues a fresh code and invalidates the device's current token so the tablet can be re-paired. */
  newPairingCode(id: number): Observable<KioskPairingResponse> {
    return this.http.post<KioskPairingResponse>(`${API_BASE}/admin/kiosk-devices/${id}/pairing-code`, {});
  }

  revokeDevice(id: number): Observable<void> {
    return this.http.post<void>(`${API_BASE}/admin/kiosk-devices/${id}/revoke`, {});
  }

  branding(): Observable<KioskBrandingResponse> {
    return this.http.get<KioskBrandingResponse>(`${API_BASE}/admin/kiosk-branding`);
  }

  updateBranding(body: UpdateKioskBrandingRequest): Observable<KioskBrandingResponse> {
    return this.http.put<KioskBrandingResponse>(`${API_BASE}/admin/kiosk-branding`, body);
  }

  upsells(): Observable<KioskUpsellView[]> {
    return this.http.get<KioskUpsellView[]>(`${API_BASE}/admin/kiosk-upsells`);
  }

  createUpsell(body: KioskUpsellRequest): Observable<KioskUpsellView> {
    return this.http.post<KioskUpsellView>(`${API_BASE}/admin/kiosk-upsells`, body, {
      context: silentErrors(),
    });
  }

  updateUpsell(id: number, body: KioskUpsellRequest): Observable<KioskUpsellView> {
    return this.http.put<KioskUpsellView>(`${API_BASE}/admin/kiosk-upsells/${id}`, body, {
      context: silentErrors(),
    });
  }

  deleteUpsell(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/admin/kiosk-upsells/${id}`);
  }
}
