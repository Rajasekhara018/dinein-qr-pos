import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, combineLatest, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiService } from '../shared/services/api-service';
import { InAppNotificationService } from '../shared/services/in-app-notification-service';
import { PendingReviewsService } from '../shared/services/pending-reviews-service';
import { WorkflowService } from '../workflow/workflow-service';
import { WorkspaceService } from '../workspace/workspace.service';
import { FranchiseApi } from '../access-management/franchises/franchise-api';
import { TODAY_AGENT_DESK_PATH } from './today.constants';
import { agentDeskFigures, earningsFigures } from './today.helpers';
import { TodayAgentDesk, TodaySource, SourceData } from './today.models';

/** Endpoints below the gateway (ApiService base), one per application dashboard; all exist already. */
const TRAIN_SUMMARY_PATH = '/v1/train/admin/dashboard/summary';
const OPERATIONS_SUMMARY_PATH = '/v1/admin/dashboard/summary';
const WALLET_OVERVIEW_PATH = '/v1/wallet/admin/overview';
/** matrimonial-api is reached directly (see environment.matrimonyApiUrl); it has no response envelope. */
const MATRIMONY_SUMMARY_PATH = '/ops/analytics/summary';

/**
 * Reads the figures of the Today screen from the existing summary endpoints of each application. Every call is
 * read-only and a failing source never fails another: the screen shows an error on that card alone. A figure the
 * user may not read is never requested (the screen decides that from privileges).
 */
@Injectable({ providedIn: 'root' })
export class TodayService {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);
  private readonly workflow = inject(WorkflowService);
  private readonly workspace = inject(WorkspaceService);
  private readonly pendingReviews = inject(PendingReviewsService);
  private readonly notifications = inject(InAppNotificationService);
  private readonly franchises = inject(FranchiseApi);

  /** The raw response of one source. Emits again when a live counter (reviews, notifications) changes. */
  fetch(source: TodaySource): Observable<SourceData> {
    switch (source) {
      case 'waiting':
        return this.waiting();
      case 'agentDesk':
        return this.http.get<TodayAgentDesk>(`${environment.matrimonyApiUrl}${TODAY_AGENT_DESK_PATH}`).pipe(map(agentDeskFigures));
      case 'earnings':
        return this.franchises.myEarnings().pipe(map(earningsFigures));
      case 'workspace':
        return this.workspace.summary() as unknown as Observable<SourceData>;
      case 'matrimony':
        return this.http.get<SourceData>(`${environment.matrimonyApiUrl}${MATRIMONY_SUMMARY_PATH}`);
      case 'train':
        return this.api.get<SourceData>(TRAIN_SUMMARY_PATH);
      case 'operations':
        return this.api.get<SourceData>(OPERATIONS_SUMMARY_PATH);
      case 'wallet':
        return this.api.get<SourceData>(WALLET_OVERVIEW_PATH);
    }
  }

  /**
   * What is waiting for this user: their open tasks (mine, unassigned in their queues, overdue), the change requests
   * they may decide and their unread notifications. A task count the user cannot read comes back as null.
   */
  private waiting(): Observable<SourceData> {
    const total = (view: 'MINE' | 'UNASSIGNED' | 'OVERDUE'): Observable<number | null> =>
      this.workflow.tasks(undefined, undefined, view, 0, 1).pipe(
        map((page) => page.total),
        catchError(() => of(null)),
      );
    return combineLatest([
      forkJoin([total('MINE'), total('UNASSIGNED'), total('OVERDUE')]),
      this.pendingReviews.count$,
      this.notifications.unreadCount$,
    ]).pipe(
      map(([[mine, unassigned, overdue], reviews, unread]) => ({ mine, unassigned, overdue, reviews, unread })),
    );
  }
}
