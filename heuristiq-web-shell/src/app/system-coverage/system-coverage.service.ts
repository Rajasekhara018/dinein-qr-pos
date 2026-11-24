import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

import { ApiService } from '../shared/services/api-service';
import { COVERAGE_HISTORY_PAGE_SIZE, COVERAGE_RUNS_PATH } from './system-coverage.constants';
import { averageLinesPct } from './system-coverage.helpers';
import {
  CoverageDocument,
  CoverageRunDetail,
  CoverageRunPage,
  CoverageRunSummary,
  CoverageTrendValue,
  CoverageUploadResult,
} from './system-coverage.models';

/** The API caps a page at 50 runs. */
const MAX_PAGE_SIZE = 50;

/**
 * Reads and uploads code-coverage runs through the gateway (Rider_ConfigService, /api/v1/config/coverage). A stored run
 * never changes, so a run that was read once is kept for the next look (the cache is dropped after an upload).
 */
@Injectable({ providedIn: 'root' })
export class SystemCoverageService {
  private readonly api = inject(ApiService);
  private readonly cache = new Map<string, CoverageRunDetail>();

  /** One page of run summaries, newest first. */
  runs(page = 0, size = COVERAGE_HISTORY_PAGE_SIZE): Observable<CoverageRunPage> {
    const safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    const safePage = Math.max(page, 0);
    return this.api
      .get<Partial<CoverageRunPage> | undefined>(COVERAGE_RUNS_PATH, { page: safePage, size: safeSize })
      .pipe(
        map((res) => ({
          content: res?.content ?? [],
          totalElements: res?.totalElements ?? 0,
          totalPages: res?.totalPages ?? 0,
          size: res?.size ?? safeSize,
          number: res?.number ?? safePage,
        })),
      );
  }

  /** One run with its document. */
  run(runId: string): Observable<CoverageRunDetail> {
    const cached = this.cache.get(runId);
    if (cached) return of(cached);
    return this.api
      .get<CoverageRunDetail>(`${COVERAGE_RUNS_PATH}/${encodeURIComponent(runId)}`)
      .pipe(tap((detail) => this.cache.set(runId, detail)));
  }

  /** Sends the collector document as the raw body (it is not wrapped in a request object). */
  upload(document: CoverageDocument): Observable<CoverageUploadResult> {
    return this.api.post<CoverageUploadResult>(COVERAGE_RUNS_PATH, document).pipe(tap(() => this.cache.clear()));
  }

  /**
   * Average line coverage of each given run (newest first in, oldest first out). A run that cannot be read, or has
   * nothing measured, is left out instead of failing the whole chart.
   */
  trend(runs: readonly CoverageRunSummary[]): Observable<CoverageTrendValue[]> {
    if (runs.length === 0) return of([]);
    return forkJoin(
      runs.map((r) =>
        this.run(r.runId).pipe(
          map((d): CoverageTrendValue | null => {
            const value = averageLinesPct(d.document);
            return value === null ? null : { runId: r.runId, createdAt: r.createdAt, value };
          }),
          catchError(() => of(null)),
        ),
      ),
    ).pipe(map((values) => values.filter((v): v is CoverageTrendValue => v !== null).reverse()));
  }
}
