import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApiService } from '../shared/services/api-service';
import {
  QA_EXECUTIONS_TEST_SETS_PATH,
  QA_EXECUTIVE_SUMMARY_PATH,
  QA_FAILURES_PATH,
  QA_FILTERS_PATH,
  QA_OVERVIEW_PATH,
  QA_TEST_CASES_EXPORT_PATH,
  QA_TEST_CASES_PATH,
  QA_TEST_SETS_PATH,
  QA_TRIGGER_PATH,
} from './qa-dashboard.constants';
import {
  clampPage,
  filterParams,
  normalizeExecutiveSummary,
  normalizeFacets,
  normalizeFailures,
  normalizeOverview,
  normalizeRow,
  normalizeTestCasePage,
} from './qa-dashboard.helpers';
import {
  QaExecutiveSummary,
  QaFacets,
  QaFailures,
  QaFilters,
  QaOverview,
  QaOverviewMode,
  QaTestCaseDetail,
  QaTestCasePage,
  QaTestSet,
  QaTestSetExecution,
  QaTestSetInput,
  QaTriggerRequest,
  QaTriggerResult,
  QaWorkspace,
} from './qa-dashboard.models';

type ListOrPage<T> = T[] | { content?: T[] } | null | undefined;

/**
 * Reads the QA dashboard's data through the gateway (Rider_ConfigService, /api/v1/config/qa), keeps the test sets and
 * starts Jenkins runs. Every call carries the workspace; the server never invents data, so an empty answer is an empty screen.
 */
@Injectable({ providedIn: 'root' })
export class QaDashboardService {
  private readonly api = inject(ApiService);

  overview(workspace: QaWorkspace, filters: QaFilters, mode: QaOverviewMode): Observable<QaOverview> {
    return this.api
      .get<Partial<QaOverview> | undefined>(QA_OVERVIEW_PATH, filterParams(workspace, filters, { mode }))
      .pipe(map(normalizeOverview));
  }

  facets(workspace: QaWorkspace): Observable<QaFacets> {
    return this.api.get<Partial<QaFacets> | undefined>(QA_FILTERS_PATH, { workspace }).pipe(map(normalizeFacets));
  }

  testCases(workspace: QaWorkspace, filters: QaFilters, page: number, size: number): Observable<QaTestCasePage> {
    const safe = clampPage(page, size);
    return this.api
      .get<Partial<QaTestCasePage> | undefined>(QA_TEST_CASES_PATH, filterParams(workspace, filters, safe))
      .pipe(map((raw) => normalizeTestCasePage(raw, safe.page, safe.size)));
  }

  testCase(id: string): Observable<QaTestCaseDetail> {
    return this.api.get<Partial<QaTestCaseDetail> | undefined>(`${QA_TEST_CASES_PATH}/${encodeURIComponent(id)}`).pipe(
      map((raw) => ({ testCase: normalizeRow(raw?.testCase ?? {}), history: raw?.history ?? [] })),
    );
  }

  deleteTestCase(id: string): Observable<void> {
    return this.api.delete<void>(`${QA_TEST_CASES_PATH}/${encodeURIComponent(id)}`);
  }

  /** The filtered table as CSV (the server caps it at 5000 rows). */
  exportCsv(workspace: QaWorkspace, filters: QaFilters): Observable<Blob> {
    return this.api.getBlob(QA_TEST_CASES_EXPORT_PATH, filterParams(workspace, filters));
  }

  failures(workspace: QaWorkspace, filters: QaFilters): Observable<QaFailures> {
    return this.api
      .get<Partial<QaFailures> | undefined>(QA_FAILURES_PATH, filterParams(workspace, filters))
      .pipe(map(normalizeFailures));
  }

  /** The newest run of every test set (the API has no workspace filter for it). */
  testSetExecutions(): Observable<QaTestSetExecution[]> {
    return this.api.get<ListOrPage<QaTestSetExecution>>(QA_EXECUTIONS_TEST_SETS_PATH).pipe(map(toList));
  }

  executiveSummary(workspace: QaWorkspace, filters: QaFilters): Observable<QaExecutiveSummary> {
    return this.api
      .get<Partial<QaExecutiveSummary> | undefined>(QA_EXECUTIVE_SUMMARY_PATH, filterParams(workspace, filters))
      .pipe(map(normalizeExecutiveSummary));
  }

  /** Every test set (the API lists them all; the screens keep the ones of their workspace). */
  testSets(): Observable<QaTestSet[]> {
    return this.api.getAll<ListOrPage<QaTestSet>>("WEB_QA_TEST_SET").pipe(map(toList));
  }

  createTestSet(input: QaTestSetInput): Observable<QaTestSet> {
    return this.api.post<QaTestSet>(QA_TEST_SETS_PATH, input);
  }

  updateTestSet(id: string, input: QaTestSetInput): Observable<QaTestSet> {
    return this.api.put<QaTestSet>(`${QA_TEST_SETS_PATH}/${encodeURIComponent(id)}`, input);
  }

  deleteTestSet(id: string): Observable<void> {
    return this.api.delete<void>(`${QA_TEST_SETS_PATH}/${encodeURIComponent(id)}`);
  }

  /** Starts a Jenkins run of a saved test set in one environment. */
  runTestSet(id: string, environment: string): Observable<QaTriggerResult> {
    return this.api.post<QaTriggerResult>(`${QA_TEST_SETS_PATH}/${encodeURIComponent(id)}/run`, { environment });
  }

  /** Starts a Jenkins run of a tag expression that is not a saved test set. */
  trigger(request: QaTriggerRequest): Observable<QaTriggerResult> {
    return this.api.post<QaTriggerResult>(QA_TRIGGER_PATH, request);
  }
}

function toList<T>(res: ListOrPage<T>): T[] {
  return Array.isArray(res) ? res : (res?.content ?? []);
}
