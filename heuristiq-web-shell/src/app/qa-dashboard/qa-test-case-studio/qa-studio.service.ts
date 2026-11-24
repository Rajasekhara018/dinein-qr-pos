import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApiService } from '../../shared/services/api-service';
import { QA_STUDIO_CASES_PATH } from './qa-studio.constants';
import { normalizeStudioCase, normalizeStudioPage, studioParams } from './qa-studio.helpers';
import { QaStudioCase, QaStudioInput, QaStudioPage, QaStudioQuery } from './qa-studio.models';

/**
 * The Test Case Studio's cases through the gateway (Rider_ConfigService, /api/v1/config/qa/studio/cases): the list,
 * one case, create, replace (with the version, so a stale edit is refused), switch on or off, and delete. The Jenkins
 * runner reads the enabled cases itself; the portal never runs them.
 */
@Injectable({ providedIn: 'root' })
export class QaStudioService {
  private readonly api = inject(ApiService);

  list(query: QaStudioQuery, page: number, size: number): Observable<QaStudioPage> {
    const params = studioParams(query, page, size);
    return this.api.getAll<Partial<QaStudioPage> | undefined>("WEB_QA_STUDIO_CASE", params)
      .pipe(map((raw) => normalizeStudioPage(raw, Number(params['page']), Number(params['size']))));
  }

  get(id: string): Observable<QaStudioCase> {
    return this.api.get<Partial<QaStudioCase> | undefined>(this.path(id)).pipe(map(normalizeStudioCase));
  }

  create(input: QaStudioInput): Observable<QaStudioCase> {
    return this.api.post<Partial<QaStudioCase>>(QA_STUDIO_CASES_PATH, input).pipe(map(normalizeStudioCase));
  }

  /** Replaces a case. The input carries the version the edit started from; the server answers 409 when it is out of date. */
  update(id: string, input: QaStudioInput): Observable<QaStudioCase> {
    return this.api.put<Partial<QaStudioCase>>(this.path(id), input).pipe(map(normalizeStudioCase));
  }

  /** Switches a case on or off; the API answers with the case as it is now. */
  setEnabled(id: string, enabled: boolean): Observable<QaStudioCase> {
    return this.api.patch<Partial<QaStudioCase>>(`${this.path(id)}/enabled`, { enabled }).pipe(map(normalizeStudioCase));
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(this.path(id));
  }

  private path(id: string): string {
    return `${QA_STUDIO_CASES_PATH}/${encodeURIComponent(id)}`;
  }
}
