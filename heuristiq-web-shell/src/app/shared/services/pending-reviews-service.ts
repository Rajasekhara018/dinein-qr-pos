import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subscription, of, timer } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { PENDING_REVIEWS_PATH, PENDING_REVIEWS_POLL_MS, TOTAL_COUNT_HEADER } from './pending-reviews.constants';

/**
 * How many maker-checker change requests wait for the signed-in user's decision, for the sidebar badge.
 * Only the total is read (a one-item page and the X-Total-Count header). A failed call keeps the last
 * known count: the badge is a hint and must never raise an error or block the menu.
 */
@Injectable({ providedIn: 'root' })
export class PendingReviewsService implements OnDestroy {
  private readonly base = `${environment.workflowApiUrl}${PENDING_REVIEWS_PATH}`;
  private readonly countSubject = new BehaviorSubject<number>(0);
  private polling: Subscription | null = null;

  readonly count$: Observable<number> = this.countSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  /** Starts refreshing the count now and then every minute; a second call is a no-op. */
  start(): void {
    if (this.polling) return;
    this.polling = timer(0, PENDING_REVIEWS_POLL_MS)
      .pipe(switchMap(() => this.fetch()))
      .subscribe((n) => {
        if (n !== null) this.countSubject.next(n);
      });
  }

  /** Stops refreshing and clears the badge (sign-out, or a user who cannot review). */
  stop(): void {
    this.polling?.unsubscribe();
    this.polling = null;
    this.countSubject.next(0);
  }

  ngOnDestroy(): void {
    this.stop();
    this.countSubject.complete();
  }

  /** The total of pending requests, or null when it could not be read. */
  private fetch(): Observable<number | null> {
    const params = new HttpParams().set('pendingForMe', 'true').set('page', '0').set('size', '1');
    return this.http.get<unknown>(this.base, { params, observe: 'response' }).pipe(
      map((res: HttpResponse<unknown>) => {
        const total = Number(res.headers.get(TOTAL_COUNT_HEADER));
        return res.headers.has(TOTAL_COUNT_HEADER) && Number.isFinite(total) && total >= 0 ? total : null;
      }),
      catchError(() => of(null)),
    );
  }
}
