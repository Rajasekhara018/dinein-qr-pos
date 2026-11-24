import { HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { environment } from '../../environments/environment';
import {
  PUBLIC_TRACK_API_PATH,
  PUBLIC_TRACK_BACKOFF_INITIAL_MS,
  PUBLIC_TRACK_POLL_INTERVAL_MS,
  SHARE_TOKEN_HEADER,
} from './public-track.constants';
import { PublicTrackUpdate } from './public-track.models';
import { PublicTrackService } from './public-track.service';

const URL = `${environment.gatewayUrl}${PUBLIC_TRACK_API_PATH}`;
const TOKEN = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJ_-0123';

describe('PublicTrackService', () => {
  let http: HttpTestingController;
  let service: PublicTrackService;
  const intercepted: HttpRequest<unknown>[] = [];

  beforeEach(() => {
    vi.useFakeTimers();
    intercepted.length = 0;
    localStorage.setItem('access_token', 'staff-session-token');
    TestBed.configureTestingModule({
      providers: [
        // An app interceptor that would add the staff bearer token: the public reads must never pass through it.
        provideHttpClient(withInterceptors([(req, next) => { intercepted.push(req); return next(req); }])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(PublicTrackService);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem('access_token');
    vi.useRealTimers();
  });

  function collect(): { updates: PublicTrackUpdate[]; completed: () => boolean; stop: () => void } {
    const updates: PublicTrackUpdate[] = [];
    let done = false;
    const sub = service.watch(TOKEN).subscribe({ next: (u) => updates.push(u), complete: () => (done = true) });
    return { updates, completed: () => done, stop: () => sub.unsubscribe() };
  }

  it('sends the token only in X-Share-Token, without Authorization and past the app interceptors', () => {
    service.read(TOKEN).subscribe();
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get(SHARE_TOKEN_HEADER)).toBe(TOKEN);
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.urlWithParams).not.toContain(TOKEN);
    expect(intercepted).toHaveLength(0);
    req.flush({ status: 'RIDE_STARTED' });
  });

  it('polls every interval while live and stops when the trip ends', () => {
    const run = collect();
    http.expectOne(URL).flush({ status: 'RIDE_STARTED', lat: 1, lng: 2 });
    expect(run.updates).toEqual([{ kind: 'live', data: { status: 'RIDE_STARTED', lat: 1, lng: 2 } }]);
    vi.advanceTimersByTime(PUBLIC_TRACK_POLL_INTERVAL_MS - 1);
    http.expectNone(URL);
    vi.advanceTimersByTime(1);
    http.expectOne(URL).flush({ status: 'ENDED' });
    expect(run.updates[1]).toEqual({ kind: 'ended' });
    expect(run.completed()).toBe(true);
    vi.advanceTimersByTime(PUBLIC_TRACK_POLL_INTERVAL_MS * 3);
    http.expectNone(URL);
  });

  it('reports an invalid link on 404 and stops', () => {
    const run = collect();
    http.expectOne(URL).flush(null, { status: 404, statusText: 'Not Found' });
    expect(run.updates).toEqual([{ kind: 'invalid' }]);
    expect(run.completed()).toBe(true);
    vi.advanceTimersByTime(PUBLIC_TRACK_BACKOFF_INITIAL_MS * 10);
    http.expectNone(URL);
  });

  it('backs off on 429 (honouring Retry-After) and doubles on further failures, then recovers', () => {
    const run = collect();
    http.expectOne(URL).flush(null, { status: 429, statusText: 'Too Many', headers: { 'Retry-After': '30' } });
    expect(run.updates[0]).toEqual({ kind: 'unavailable', retryInMs: 30000, rateLimited: true });
    vi.advanceTimersByTime(29999);
    http.expectNone(URL);
    vi.advanceTimersByTime(1);
    http.expectOne(URL).error(new ProgressEvent('error'));
    expect(run.updates[1]).toEqual({ kind: 'unavailable', retryInMs: 60000, rateLimited: false });
    vi.advanceTimersByTime(60000);
    http.expectOne(URL).flush({ status: 'DRIVER_ARRIVING' });
    expect(run.updates[2].kind).toBe('live');
    vi.advanceTimersByTime(PUBLIC_TRACK_POLL_INTERVAL_MS);
    http.expectOne(URL).flush(null, { status: 503, statusText: 'Down' });
    // a success resets the back-off
    expect(run.updates[3]).toEqual({ kind: 'unavailable', retryInMs: PUBLIC_TRACK_BACKOFF_INITIAL_MS, rateLimited: false });
    run.stop();
  });

  it('stops polling when unsubscribed', () => {
    const run = collect();
    http.expectOne(URL).flush({ status: 'RIDE_STARTED' });
    run.stop();
    vi.advanceTimersByTime(PUBLIC_TRACK_POLL_INTERVAL_MS * 2);
    http.expectNone(URL);
  });
});
