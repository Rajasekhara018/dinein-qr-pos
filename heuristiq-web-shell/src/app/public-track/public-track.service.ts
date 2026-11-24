import { HttpBackend, HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, Subscription, timer } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  HTTP_STATUS_NOT_FOUND,
  HTTP_STATUS_TOO_MANY_REQUESTS,
  PUBLIC_TRACK_API_PATH,
  PUBLIC_TRACK_ENDED_STATUS,
  PUBLIC_TRACK_POLL_INTERVAL_MS,
  SHARE_TOKEN_HEADER,
} from './public-track.constants';
import { nextBackoffMs, retryAfterMs } from './public-track.helpers';
import { PublicTrackResponse, PublicTrackUpdate } from './public-track.models';

/**
 * Reads the shared trip for the public `/track` page. Requests go straight to the HTTP backend, past the app's
 * interceptors: they carry no `Authorization` header even when a staff session exists in this browser, and a failed
 * read can never trigger the session refresh or the sign-out redirect. The token travels only in `X-Share-Token`.
 */
@Injectable({ providedIn: 'root' })
export class PublicTrackService {
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly url = `${environment.gatewayUrl}${PUBLIC_TRACK_API_PATH}`;

  /** One read of the shared trip. */
  read(token: string): Observable<PublicTrackResponse> {
    return this.http.get<PublicTrackResponse>(this.url, {
      headers: new HttpHeaders({ [SHARE_TOKEN_HEADER]: token }),
    });
  }

  /**
   * Polls the shared trip: every {@link PUBLIC_TRACK_POLL_INTERVAL_MS} while live; completes after ENDED or a 404
   * (unknown, revoked or expired link); after a 429, a network error or a server error it backs off (doubling,
   * honouring `Retry-After`) and keeps trying. Unsubscribing stops it.
   */
  watch(token: string): Observable<PublicTrackUpdate> {
    return new Observable<PublicTrackUpdate>((subscriber) => {
      let backoff = 0;
      // Kept apart: a read that answers synchronously schedules the next wait before its own subscribe() returns.
      let request: Subscription | null = null;
      let wait: Subscription | null = null;

      const schedule = (delayMs: number): void => {
        if (subscriber.closed) return;
        wait = timer(delayMs).subscribe(() => poll());
      };

      const poll = (): void => {
        request = this.read(token).subscribe({
          next: (data) => {
            backoff = 0;
            if ((data?.status ?? '').toUpperCase() === PUBLIC_TRACK_ENDED_STATUS) {
              subscriber.next({ kind: 'ended' });
              subscriber.complete();
              return;
            }
            subscriber.next({ kind: 'live', data });
            schedule(PUBLIC_TRACK_POLL_INTERVAL_MS);
          },
          error: (err: unknown) => {
            const status = err instanceof HttpErrorResponse ? err.status : 0;
            if (status === HTTP_STATUS_NOT_FOUND) {
              subscriber.next({ kind: 'invalid' });
              subscriber.complete();
              return;
            }
            const rateLimited = status === HTTP_STATUS_TOO_MANY_REQUESTS;
            const hinted = rateLimited && err instanceof HttpErrorResponse ? retryAfterMs(err.headers?.get('Retry-After')) : 0;
            backoff = Math.max(nextBackoffMs(backoff), hinted);
            subscriber.next({ kind: 'unavailable', retryInMs: backoff, rateLimited });
            schedule(backoff);
          },
        });
      };

      poll();
      return () => {
        request?.unsubscribe();
        wait?.unsubscribe();
      };
    });
  }
}
