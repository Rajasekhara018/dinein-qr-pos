import { Injectable, inject } from '@angular/core';
import { HqDocumentService } from 'hq-shared-lib';
import { Observable } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';

import { ApiService } from '../shared/services/api-service';
import { QA_DOCUMENTS_PATH, QA_MAX_PAGE_SIZE, QA_RUNS_PAGE_SIZE, QA_RUNS_PATH } from './system-qa.constants';
import { QaDocument, QaDocumentSummary, QaPdfFile, QaRunDetail, QaRunPage, QaRunSummary } from './system-qa.models';

/**
 * Reads QA runs and QA documents through the gateway (Rider_ConfigService, /api/v1/config/qa) and fetches a run's PDF
 * from DMS. Runs are uploaded by Jenkins, so the screen only reads.
 */
@Injectable({ providedIn: 'root' })
export class SystemQaService {
  private readonly api = inject(ApiService);
  private readonly documents = inject(HqDocumentService);

  /** One page of run summaries, newest first, optionally for one environment ('' = all). */
  runs(page = 0, size = QA_RUNS_PAGE_SIZE, environment = ''): Observable<QaRunPage> {
    const safeSize = Math.min(Math.max(size, 1), QA_MAX_PAGE_SIZE);
    const safePage = Math.max(page, 0);
    const params: Record<string, string | number> = { page: safePage, size: safeSize };
    if (environment) params['environment'] = environment;
    return this.api.getAll<Partial<QaRunPage> | undefined>("WEB_QA_RUN", params).pipe(
      map((res) => ({
        content: res?.content ?? [],
        totalElements: res?.totalElements ?? 0,
        totalPages: res?.totalPages ?? 0,
        size: res?.size ?? safeSize,
        number: res?.number ?? safePage,
      })),
    );
  }

  /** The newest runs of one environment, for the pass-rate trend. */
  recentRuns(environment: string, count: number): Observable<QaRunSummary[]> {
    return this.runs(0, count, environment).pipe(map((p) => p.content));
  }

  /** One run with its scenarios. */
  run(runId: string): Observable<QaRunDetail> {
    return this.api
      .get<QaRunDetail>(`${QA_RUNS_PATH}/${encodeURIComponent(runId)}`)
      .pipe(map((detail) => ({ ...detail, scenarios: detail?.scenarios ?? [] })));
  }

  /** The documents (name, title, date, size), without their content. */
  documentList(): Observable<QaDocumentSummary[]> {
    return this.api.getAll<QaDocumentSummary[] | { content?: QaDocumentSummary[] } | undefined>("WEB_QA_DOCUMENT")
      .pipe(map((res) => (Array.isArray(res) ? res : (res?.content ?? []))));
  }

  /** One document with its markdown. */
  document(name: string): Observable<QaDocument> {
    return this.api.get<QaDocument>(`${QA_DOCUMENTS_PATH}/${encodeURIComponent(name)}`);
  }

  /** The PDF of a run: DMS metadata first (for the real content type and file name), then the bytes. */
  pdf(documentId: string): Observable<QaPdfFile> {
    return this.documents.getMetadata(documentId).pipe(
      switchMap((doc) =>
        this.documents.fetchFile(doc).pipe(map((blob): QaPdfFile => ({ blob, fileName: doc.originalFileName }))),
      ),
    );
  }
}
