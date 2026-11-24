import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UamCommonResponse } from '../../shared/models/uam.models';
import { IDEMPOTENCY_KEY_HEADER } from './wallet-float.constants';
import { AlertThresholdRequest, FloatAlertPage, FloatJournal, FloatSummary, SettlementRequest } from './wallet-float.models';

/** The partner float endpoints of Rider_WalletService (gateway /api/v1/wallet/**). The organization always comes from the token. */
@Injectable({ providedIn: 'root' })
export class WalletFloatApi {
  private readonly base = environment.walletApiUrl;

  constructor(private http: HttpClient, private translate: TranslateService) {}

  /** The signed-in person's own float. */
  mine(): Observable<FloatSummary> {
    return this.unwrapped(this.http.get<UamCommonResponse<FloatSummary>>(`${this.base}/float/me`));
  }

  /** Any holder's float of the organization (administrators). */
  byUser(userId: string): Observable<FloatSummary> {
    return this.unwrapped(this.http.get<UamCommonResponse<FloatSummary>>(`${this.base}/admin/partner-floats/${encodeURIComponent(userId)}`));
  }

  /** Floats below their alert threshold, lowest balance first. */
  alerts(page: number, size: number): Observable<FloatAlertPage> {
    return this.unwrapped(this.http.get<UamCommonResponse<FloatAlertPage>>(`${this.base}/admin/partner-float-alerts?page=${page}&size=${size}`));
  }

  setAlertThreshold(userId: string, body: AlertThresholdRequest): Observable<FloatSummary> {
    return this.unwrapped(this.http.put<UamCommonResponse<FloatSummary>>(
      `${this.base}/admin/partner-floats/${encodeURIComponent(userId)}/alert-threshold`, body));
  }

  /** A settlement posting; the same {@code idempotencyKey} on a retry returns the original journal. */
  settle(userId: string, body: SettlementRequest, idempotencyKey: string): Observable<FloatJournal> {
    return this.unwrapped(this.http.post<UamCommonResponse<FloatJournal>>(
      `${this.base}/admin/partner-floats/${encodeURIComponent(userId)}/settlements`, body,
      { headers: new HttpHeaders({ [IDEMPOTENCY_KEY_HEADER]: idempotencyKey }) }));
  }

  private unwrapped<T>(call: Observable<UamCommonResponse<T>>): Observable<T> {
    return call.pipe(map((res) => {
      if (!res.success) {
        const fallback = this.translate.instant('shared.errors.requestFailed');
        throw { error: { detail: res.message || fallback, message: res.message || fallback } };
      }
      return res.responseObject as T;
    }));
  }
}
