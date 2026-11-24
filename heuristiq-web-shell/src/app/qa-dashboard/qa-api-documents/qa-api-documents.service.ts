import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, from } from 'rxjs';
import { catchError, map, mergeMap, toArray } from 'rxjs/operators';

import { ApiService } from '../../shared/services/api-service';
import { QaEndpointService } from '../qa-endpoint-inventory/qa-endpoint-inventory.service';
import { emptyEndpointQuery } from '../qa-endpoint-inventory/qa-endpoint-inventory.helpers';
import { QA_API_DOCUMENTS_PATH, QA_API_DOC_CATEGORY, QA_API_DOC_COUNT_CONCURRENCY } from './qa-api-documents.constants';
import { normalizeApiDocInfo, normalizeApiDocument } from './qa-api-documents.helpers';
import { QaApiDocInfo, QaApiDocInput, QaApiDocument } from './qa-api-documents.models';

type ListOrPage<T> = T[] | { content?: T[] } | null | undefined;

/** The API documents (category API of the QA documents) through the gateway, and how many endpoints each module has. */
@Injectable({ providedIn: 'root' })
export class QaApiDocumentsService {
  private readonly api = inject(ApiService);
  private readonly endpoints = inject(QaEndpointService);

  /** The API documents, without their markdown. */
  list(): Observable<QaApiDocInfo[]> {
    return this.api.getAll<ListOrPage<Partial<QaApiDocInfo>>>("WEB_QA_DOCUMENT", { category: QA_API_DOC_CATEGORY })
      .pipe(map((res) => (Array.isArray(res) ? res : (res?.content ?? [])).map(normalizeApiDocInfo)));
  }

  get(name: string): Observable<QaApiDocument> {
    return this.api.get<Partial<QaApiDocument> | undefined>(this.path(name)).pipe(map(normalizeApiDocument));
  }

  /** Creates or replaces a module's document. */
  save(input: QaApiDocInput): Observable<QaApiDocument> {
    return this.api.post<Partial<QaApiDocument>>(QA_API_DOCUMENTS_PATH, input).pipe(map(normalizeApiDocument));
  }

  delete(name: string): Observable<void> {
    return this.api.delete<void>(this.path(name));
  }

  /** The number of endpoints of each module, asked for a few at a time; a module whose count cannot be read is left out. */
  endpointCounts(modules: readonly string[]): Observable<Map<string, number>> {
    return from(modules).pipe(
      mergeMap(
        (module) =>
          this.endpoints.list(emptyEndpointQuery({ module }), 0, 1).pipe(
            map((page): [string, number] => [module, page.totalElements]),
            catchError(() => EMPTY),
          ),
        QA_API_DOC_COUNT_CONCURRENCY,
      ),
      toArray(),
      map((pairs) => new Map(pairs)),
    );
  }

  private path(name: string): string {
    return `${QA_API_DOCUMENTS_PATH}/${encodeURIComponent(name)}`;
  }
}
