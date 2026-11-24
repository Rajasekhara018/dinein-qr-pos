import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApiService } from '../../shared/services/api-service';
import { QA_ENDPOINTS_EXPORT_PATH, QA_ENDPOINTS_FILTERS_PATH, QA_ENDPOINTS_PATH } from './qa-endpoint-inventory.constants';
import { endpointParams, normalizeEndpointDetail, normalizeEndpointFilters, normalizeEndpointPage } from './qa-endpoint-inventory.helpers';
import { QaEndpointDetail, QaEndpointFilterOptions, QaEndpointPage, QaEndpointQuery } from './qa-endpoint-inventory.models';

/**
 * Reads the API endpoint inventory through the gateway (Rider_ConfigService, /api/v1/config/qa/endpoints): which
 * documented endpoints the automated API tests call, how often and by which test cases. The catalog is synced by the
 * Jenkins publish, so the portal only reads.
 */
@Injectable({ providedIn: 'root' })
export class QaEndpointService {
  private readonly api = inject(ApiService);

  /** One page of endpoints with the coverage of the whole catalog. */
  list(query: QaEndpointQuery, page: number, size: number): Observable<QaEndpointPage> {
    const params = endpointParams(query, page, size);
    return this.api
      .get<Partial<QaEndpointPage> | undefined>(QA_ENDPOINTS_PATH, params)
      .pipe(map((raw) => normalizeEndpointPage(raw, Number(params['page']), Number(params['size']))));
  }

  /** One endpoint with the test cases that called it. */
  detail(id: string): Observable<QaEndpointDetail> {
    return this.api
      .get<Partial<QaEndpointDetail> | undefined>(`${QA_ENDPOINTS_PATH}/${encodeURIComponent(id)}`)
      .pipe(map(normalizeEndpointDetail));
  }

  /** The modules (with sub-modules) and methods the filter bar offers. */
  filters(): Observable<QaEndpointFilterOptions> {
    return this.api.get<Partial<QaEndpointFilterOptions> | undefined>(QA_ENDPOINTS_FILTERS_PATH).pipe(map(normalizeEndpointFilters));
  }

  /** The filtered list as CSV (the server caps it at 5000 rows). */
  exportCsv(query: QaEndpointQuery): Observable<Blob> {
    return this.api.getBlob(QA_ENDPOINTS_EXPORT_PATH, endpointParams(query));
  }
}
