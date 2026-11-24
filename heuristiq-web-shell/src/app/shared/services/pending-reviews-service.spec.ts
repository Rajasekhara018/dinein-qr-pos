import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { PENDING_REVIEWS_POLL_MS } from './pending-reviews.constants';
import { PendingReviewsService } from './pending-reviews-service';

describe('PendingReviewsService', () => {
  let service: PendingReviewsService;
  let http: HttpTestingController;
  let latest: number;

  const answer = (headers: Record<string, string>, status = 200) =>
    http.expectOne((r) => r.url.endsWith('/workflow/change-requests')).flush({ success: true }, { headers, status, statusText: 'x' });

  /** Starts polling; the first tick is a zero-delay timer, so the fake clock must run it. */
  const begin = () => {
    service.start();
    vi.advanceTimersByTime(0);
  };

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PendingReviewsService);
    http = TestBed.inject(HttpTestingController);
    latest = -1;
    service.count$.subscribe((n) => (latest = n));
  });

  afterEach(() => {
    service.stop();
    vi.useRealTimers();
  });

  it('asks for a one-item page of requests pending for the user and shows the total', () => {
    begin();
    const req = http.expectOne((r) => r.url.endsWith('/workflow/change-requests'));
    expect(req.request.params.get('pendingForMe')).toBe('true');
    expect(req.request.params.get('size')).toBe('1');
    req.flush({ success: true }, { headers: { 'X-Total-Count': '7' } });
    expect(latest).toBe(7);
  });

  it('refreshes every poll interval and starts only once', () => {
    begin();
    service.start();
    answer({ 'X-Total-Count': '2' });
    vi.advanceTimersByTime(PENDING_REVIEWS_POLL_MS);
    answer({ 'X-Total-Count': '3' });
    expect(latest).toBe(3);
  });

  it('keeps the last count when a refresh fails or has no total', () => {
    begin();
    answer({ 'X-Total-Count': '4' });
    vi.advanceTimersByTime(PENDING_REVIEWS_POLL_MS);
    answer({}, 500);
    expect(latest).toBe(4);
    vi.advanceTimersByTime(PENDING_REVIEWS_POLL_MS);
    answer({});
    expect(latest).toBe(4);
  });

  it('clears the badge and stops asking after stop()', () => {
    begin();
    answer({ 'X-Total-Count': '5' });
    service.stop();
    vi.advanceTimersByTime(PENDING_REVIEWS_POLL_MS * 3);
    http.expectNone((r) => r.url.endsWith('/workflow/change-requests'));
    expect(latest).toBe(0);
  });
});
