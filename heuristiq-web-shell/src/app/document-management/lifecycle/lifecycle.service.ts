import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApiService } from '../../shared/services/api-service';
import { buildDocumentParams, normalizePageOf } from './lifecycle.helpers';
import { LIFECYCLE_PATH } from './lifecycle.constants';
import {
  LifecycleDocumentDetail,
  LifecycleDocumentFilter,
  LifecycleDocumentRow,
  LifecyclePage,
} from './lifecycle.models';

/** Read-only calls to Rider_DMS /v1/dms/lifecycle through the gateway (search and detail). The history comes from the audit trail, see LifecycleAuditService. */
@Injectable({ providedIn: 'root' })
export class LifecycleService {
  private readonly api = inject(ApiService);

  searchDocuments(filter: LifecycleDocumentFilter, page: number, size: number): Observable<LifecyclePage<LifecycleDocumentRow>> {
    const params = buildDocumentParams(filter, page, size);
    return this.api.getAll<LifecyclePage<LifecycleDocumentRow> | LifecycleDocumentRow[]>("WEB_DMS_LIFECYCLE_DOCUMENT", params)
      .pipe(map((res) => normalizePageOf(res, page, Number(params['size']))));
  }

  getDocument(id: string): Observable<LifecycleDocumentDetail> {
    return this.api.get<LifecycleDocumentDetail>(`${LIFECYCLE_PATH}/documents/${encodeURIComponent(id)}`);
  }
}
