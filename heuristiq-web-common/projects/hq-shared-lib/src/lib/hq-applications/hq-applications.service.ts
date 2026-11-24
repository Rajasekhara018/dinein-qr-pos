import { HttpClient } from '@angular/common/http';
import { Inject, Injectable, signal } from '@angular/core';
import { BehaviorSubject, Observable, catchError, defer, finalize, map, of, share, switchMap, tap, throwError } from 'rxjs';
import { HQ_CONFIG, HqSharedLibConfig, hqUrls } from '../hq-common.config';
import { HqCommonResponse } from '../hq-common.models';
import { HqApplication } from './hq-application.model';
import { HQ_APPLICATIONS_CACHE_TTL_MS, HQ_APPLICATIONS_LOOKUP_PATH } from './hq-applications.constants';

/**
 * The applications of the organization the session works in, for pickers and labels on every screen. Loads
 * GET {uam}/identity/applications once and reuses the list for {@link HQ_APPLICATIONS_CACHE_TTL_MS}; {@link refresh} reloads
 * it at once and {@link clear} forgets it (sign-out, organization switch). Concurrent reads share one request.
 *
 * <p>The list is offered three ways: {@link applications$} (loads when stale on subscribe, then follows every change),
 * the {@link applications} signal, and {@link load} (one answer; its error reaches the caller). A failed load keeps the last
 * good list and sets {@link loadFailed}, so a screen can show its own error text.
 */
@Injectable({ providedIn: 'root' })
export class HqApplicationsService {
  private readonly url: string;
  private readonly state = new BehaviorSubject<HqApplication[]>([]);
  private loadedAt: number | null = null;
  private inFlight: Observable<HqApplication[]> | null = null;

  /** The current list (empty until the first load). */
  readonly applications = signal<HqApplication[]>([]);
  /** True after the last load failed (the last good list stays in place). */
  readonly loadFailed = signal(false);
  /** True while a request is on its way. */
  readonly loading = signal(false);

  /** Loads the list when it is missing or stale, then emits it and every later change. Never errors. */
  readonly applications$: Observable<HqApplication[]> = defer(() =>
    this.load().pipe(catchError(() => of(this.state.value))),
  ).pipe(switchMap(() => this.state.asObservable()));

  constructor(private http: HttpClient, @Inject(HQ_CONFIG) config: HqSharedLibConfig) {
    this.url = `${hqUrls(config).uam}${HQ_APPLICATIONS_LOOKUP_PATH}`;
  }

  /** The list, from the cache while it is fresh, otherwise from UAM. Errors (offline, 401, 5xx) reach the subscriber. */
  load(): Observable<HqApplication[]> {
    if (this.isFresh()) {
      return of(this.state.value);
    }
    return this.fetch();
  }

  /** Reloads the list now, whatever its age. */
  refresh(): Observable<HqApplication[]> {
    this.loadedAt = null;
    return this.fetch();
  }

  /** Forgets the list (for example on sign-out or when the session switches organization). */
  clear(): void {
    this.loadedAt = null;
    this.publish([]);
    this.loadFailed.set(false);
  }

  /** The display name of a code from the current list; the code itself when it is not in the list. */
  nameOf(code: string | null | undefined): string {
    if (!code) return '';
    return this.find(code)?.name ?? code;
  }

  /** The application of a code from the current list, if any. */
  find(code: string | null | undefined): HqApplication | undefined {
    return code ? this.state.value.find((app) => app.code === code) : undefined;
  }

  private isFresh(): boolean {
    return this.loadedAt !== null && Date.now() - this.loadedAt < HQ_APPLICATIONS_CACHE_TTL_MS;
  }

  private fetch(): Observable<HqApplication[]> {
    if (this.inFlight) {
      return this.inFlight;
    }
    this.loading.set(true);
    this.inFlight = this.http.get<HqCommonResponse<HqApplication[]>>(this.url).pipe(
      map((res) => {
        if (!res?.success) {
          throw { error: { message: res?.message || 'Applications request failed' } };
        }
        return [...(res.responseObject ?? [])].sort((a, b) => a.displayOrder - b.displayOrder || a.code.localeCompare(b.code));
      }),
      tap((list) => {
        this.loadedAt = Date.now();
        this.loadFailed.set(false);
        this.publish(list);
      }),
      catchError((err: unknown) => {
        this.loadFailed.set(true);
        return throwError(() => err);
      }),
      finalize(() => {
        this.inFlight = null;
        this.loading.set(false);
      }),
      share(),
    );
    return this.inFlight;
  }

  private publish(list: HqApplication[]): void {
    this.applications.set(list);
    this.state.next(list);
  }
}
