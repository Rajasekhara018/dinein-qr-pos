import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdminOrderSearchParams,
  AdminOrderSummary,
  AdminOrderView,
  CategoryRequest,
  CategoryResponse,
  CreateStaffRequest,
  Dashboard,
  DeviceResponse,
  IsoLocalDate,
  ItemRequest,
  ItemResponse,
  ItemSearchParams,
  OrderStatus,
  PageResponse,
  PriceRequest,
  SalesSummary,
  SettingsResponse,
  StaffResponse,
  TableRequest,
  TableResponse,
  UpdateSettingsRequest,
  UpdateStaffRequest,
  UploadResult,
} from './models';

function toParams(values: Record<string, string | number | boolean | readonly (string | number)[] | null | undefined>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '') continue;
    params = params.set(key, Array.isArray(value) ? value.join(',') : String(value));
  }
  return params;
}

/** Menu admin: `/api/admin/categories`, `/api/admin/items`, `/api/admin/images`. */
@Injectable({ providedIn: 'root' })
export class AdminMenuApi {
  private readonly http = inject(HttpClient);

  categories(): Observable<CategoryResponse[]> {
    return this.http.get<CategoryResponse[]>('/api/admin/categories');
  }

  createCategory(body: CategoryRequest): Observable<CategoryResponse> {
    return this.http.post<CategoryResponse>('/api/admin/categories', body);
  }

  updateCategory(id: number, body: CategoryRequest): Observable<CategoryResponse> {
    return this.http.put<CategoryResponse>(`/api/admin/categories/${id}`, body);
  }

  setCategoryActive(id: number, active: boolean): Observable<CategoryResponse> {
    return this.http.patch<CategoryResponse>(`/api/admin/categories/${id}/status`, { active });
  }

  reorderCategories(ids: number[]): Observable<CategoryResponse[]> {
    return this.http.patch<CategoryResponse[]>('/api/admin/categories/reorder', { ids });
  }

  items(search: ItemSearchParams = {}): Observable<PageResponse<ItemResponse>> {
    return this.http.get<PageResponse<ItemResponse>>('/api/admin/items', {
      params: toParams({ ...search }),
    });
  }

  item(id: number): Observable<ItemResponse> {
    return this.http.get<ItemResponse>(`/api/admin/items/${id}`);
  }

  createItem(body: ItemRequest): Observable<ItemResponse> {
    return this.http.post<ItemResponse>('/api/admin/items', body);
  }

  updateItem(id: number, body: ItemRequest): Observable<ItemResponse> {
    return this.http.put<ItemResponse>(`/api/admin/items/${id}`, body);
  }

  setItemAvailability(id: number, available: boolean): Observable<ItemResponse> {
    return this.http.patch<ItemResponse>(`/api/admin/items/${id}/availability`, { available });
  }

  updateItemPrice(id: number, body: PriceRequest): Observable<ItemResponse> {
    return this.http.patch<ItemResponse>(`/api/admin/items/${id}/price`, body);
  }

  deleteItem(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/items/${id}`);
  }

  /** Multipart upload (field `file`); subscribe to events for progress. */
  uploadImage(file: Blob, fileName = 'image.jpg'): Observable<HttpEvent<UploadResult>> {
    const form = new FormData();
    form.append('file', file, fileName);
    return this.http.post<UploadResult>('/api/admin/images', form, {
      reportProgress: true,
      observe: 'events',
    });
  }
}

/** `/api/admin/tables` */
@Injectable({ providedIn: 'root' })
export class AdminTablesApi {
  private readonly http = inject(HttpClient);

  list(): Observable<TableResponse[]> {
    return this.http.get<TableResponse[]>('/api/admin/tables');
  }

  create(body: TableRequest): Observable<TableResponse> {
    return this.http.post<TableResponse>('/api/admin/tables', body);
  }

  update(id: number, body: TableRequest): Observable<TableResponse> {
    return this.http.put<TableResponse>(`/api/admin/tables/${id}`, body);
  }

  regenerateQr(id: number): Observable<TableResponse> {
    return this.http.post<TableResponse>(`/api/admin/tables/${id}/regenerate-qr`, null);
  }

  /** Printable A4 PDF (all tables when `ids` is empty). Returned as a Blob because it needs the bearer token. */
  qrPdf(ids: number[] = []): Observable<Blob> {
    return this.http.get('/api/admin/tables/qr.pdf', {
      params: toParams({ ids }),
      responseType: 'blob',
    });
  }

  qrPng(id: number): Observable<Blob> {
    return this.http.get(`/api/admin/tables/${id}/qr.png`, { responseType: 'blob' });
  }
}

/** `/api/admin/orders` */
@Injectable({ providedIn: 'root' })
export class AdminOrdersApi {
  private readonly http = inject(HttpClient);

  list(search: AdminOrderSearchParams = {}): Observable<PageResponse<AdminOrderSummary>> {
    return this.http.get<PageResponse<AdminOrderSummary>>('/api/admin/orders', {
      params: toParams({ ...search }),
    });
  }

  get(id: number): Observable<AdminOrderView> {
    return this.http.get<AdminOrderView>(`/api/admin/orders/${id}`);
  }

  changeStatus(id: number, status: OrderStatus): Observable<AdminOrderView> {
    return this.http.patch<AdminOrderView>(`/api/admin/orders/${id}/status`, { status });
  }

  /** Cancels a paid order and refunds it in full (calling again retries a failed refund). */
  cancel(id: number, reason?: string): Observable<AdminOrderView> {
    return this.http.post<AdminOrderView>(`/api/admin/orders/${id}/cancel`, { reason: reason || null });
  }
}

/** Dashboard + OWNER reports. */
@Injectable({ providedIn: 'root' })
export class AdminReportsApi {
  private readonly http = inject(HttpClient);

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/admin/dashboard');
  }

  summary(from: IsoLocalDate, to: IsoLocalDate): Observable<SalesSummary> {
    return this.http.get<SalesSummary>('/api/admin/reports/summary', { params: toParams({ from, to }) });
  }

  ordersCsv(from: IsoLocalDate, to: IsoLocalDate): Observable<Blob> {
    return this.http.get('/api/admin/reports/orders.csv', {
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
    return this.http.get<SettingsResponse>('/api/admin/settings');
  }

  updateSettings(body: UpdateSettingsRequest): Observable<SettingsResponse> {
    return this.http.put<SettingsResponse>('/api/admin/settings', body);
  }

  staff(): Observable<StaffResponse[]> {
    return this.http.get<StaffResponse[]>('/api/admin/staff');
  }

  createStaff(body: CreateStaffRequest): Observable<StaffResponse> {
    return this.http.post<StaffResponse>('/api/admin/staff', body);
  }

  updateStaff(id: number, body: UpdateStaffRequest): Observable<StaffResponse> {
    return this.http.put<StaffResponse>(`/api/admin/staff/${id}`, body);
  }

  devices(): Observable<DeviceResponse[]> {
    return this.http.get<DeviceResponse[]>('/api/admin/devices');
  }

  revokeDevice(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/devices/${id}`);
  }
}
