import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApiService } from '../shared/services/api-service';
import {
  ChangeRequestAck,
  DocumentTypeFilter,
  DocumentTypePage,
  DocumentTypeRequest,
  DocumentTypeResponse,
  FileCheckResult,
} from './document-type.models';
import { DOCUMENT_TYPES_MAX_PAGE_SIZE, DOCUMENT_TYPES_PATH } from './document-types.constants';

/**
 * Talks to Rider_DMS through the gateway's dms-api route (/api/v1/dms/**). Creating or changing a
 * document type never changes anything straight away: the API answers 202 and a second person
 * approves the change in Review changes (maker-checker).
 */
@Injectable({ providedIn: 'root' })
export class DocumentTypesService {
  private readonly api = inject(ApiService);

  list(filter: DocumentTypeFilter): Observable<DocumentTypePage> {
    const size = Math.min(Math.max(filter.size, 1), DOCUMENT_TYPES_MAX_PAGE_SIZE);
    return this.api.getAll<DocumentTypePage | DocumentTypeResponse[]>("WEB_DMS_DOCUMENT_TYPE", {
        category: filter.category.trim(),
        flow: filter.flow.trim(),
        active: filter.active,
        page: filter.page,
        size,
      })
      .pipe(map((res) => normalizePage(res, filter.page, size)));
  }

  getById(id: string): Observable<DocumentTypeResponse> {
    return this.api.get<DocumentTypeResponse>(`${DOCUMENT_TYPES_PATH}/${encodeURIComponent(id)}`);
  }

  create(request: DocumentTypeRequest): Observable<ChangeRequestAck> {
    return this.api.post<ChangeRequestAck | undefined>(DOCUMENT_TYPES_PATH, request).pipe(map(normalizeAck));
  }

  update(id: string, request: DocumentTypeRequest): Observable<ChangeRequestAck> {
    return this.api
      .put<ChangeRequestAck | undefined>(`${DOCUMENT_TYPES_PATH}/${encodeURIComponent(id)}`, request)
      .pipe(map(normalizeAck));
  }

  /** Sends a file to be checked against the type's rule. The file is not stored. */
  checkFile(file: File, documentTypeCode: string, category?: string): Observable<FileCheckResult> {
    const body = new FormData();
    body.append('file', file, file.name);
    body.append('documentType', documentTypeCode);
    if (category) body.append('category', category);
    return this.api
      .post<FileCheckResult>(`${DOCUMENT_TYPES_PATH}/check-file`, body)
      .pipe(map((res) => ({ accepted: !!res?.accepted, reasons: res?.reasons ?? [], rule: res?.rule })));
  }
}

/** The list answer is a page; a bare array is tolerated. */
export function normalizePage(res: DocumentTypePage | DocumentTypeResponse[] | null | undefined, page: number, size: number): DocumentTypePage {
  if (Array.isArray(res)) {
    return { content: res, page, size, totalElements: res.length, totalPages: 1 };
  }
  const content = res?.content ?? [];
  return {
    content,
    page: res?.page ?? page,
    size: res?.size ?? size,
    totalElements: res?.totalElements ?? content.length,
    totalPages: res?.totalPages ?? 1,
  };
}

/**
 * ApiService treats any object with a "status" field as an envelope, so a bare
 * {changeRequestId, status} body comes back undefined; the 202 itself is the success.
 */
export function normalizeAck(res: ChangeRequestAck | undefined | null): ChangeRequestAck {
  return { changeRequestId: res?.changeRequestId ?? null, status: res?.status ?? 'PENDING' };
}
