import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth-service';
import { authInterceptorFn } from './auth-interceptor';

describe('authInterceptorFn token refresh', () => {
  const refresh = vi.fn();
  const navigate = vi.fn();
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem('access_token', 'old-token');
    localStorage.setItem('refresh_token', 'refresh-token');
    refresh.mockReset().mockReturnValue(of({ accessToken: 'new-token' }));
    navigate.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptorFn])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { refresh } },
        { provide: Router, useValue: { navigate } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  it('refreshes the token and replays a matrimony API call that came back 401, instead of signing out', () => {
    const url = `${environment.matrimonyApiUrl}/ops/analytics/summary`;
    let body: unknown;
    http.get(url).subscribe((value) => (body = value));

    const first = backend.expectOne(url);
    expect(first.request.headers.get('Authorization')).toBe('Bearer old-token');
    first.flush({}, { status: 401, statusText: 'Unauthorized' });

    const replay = backend.expectOne(url);
    expect(replay.request.headers.get('Authorization')).toBe('Bearer new-token');
    replay.flush({ ok: true });

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(navigate).not.toHaveBeenCalled();
    expect(body).toEqual({ ok: true });
  });

  it('keeps the session when the refresh works but the API still refuses the new token', () => {
    const url = `${environment.matrimonyApiUrl}/ops/analytics/summary`;
    let status = 0;
    http.get(url).subscribe({ error: (e) => (status = e.status) });

    backend.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(navigate).not.toHaveBeenCalled();
    expect(localStorage.getItem('access_token')).toBe('old-token');
  });

  it('signs out when the refresh itself fails', () => {
    refresh.mockReturnValue(throwError(() => new Error('refresh denied')));
    const url = `${environment.matrimonyApiUrl}/ops/analytics/summary`;
    http.get(url).subscribe({ error: () => undefined });

    backend.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navigate).toHaveBeenCalledWith(['/auth/login']);
  });

  it('sends the token to the platform API but never to a signed storage link', () => {
    http.get(environment.apiUrl + '/v1/x').subscribe();
    expect(backend.expectOne(environment.apiUrl + '/v1/x').request.headers.get('Authorization')).toBe('Bearer old-token');

    const signed = 'https://bucket.s3.ap-south-1.amazonaws.com/default/doc.png?X-Amz-Signature=abc';
    http.get(signed, { responseType: 'blob' }).subscribe();
    expect(backend.expectOne(signed).request.headers.has('Authorization')).toBe(false);
  });

  it('still leaves a 401 from an unrelated origin alone', () => {
    const url = 'https://elsewhere.example/api/x';
    let status = 0;
    http.get(url).subscribe({ error: (e) => (status = e.status) });
    backend.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(status).toBe(401);
    expect(refresh).not.toHaveBeenCalled();
  });
});
