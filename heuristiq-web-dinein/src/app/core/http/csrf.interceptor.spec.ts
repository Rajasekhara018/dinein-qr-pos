import {
  HttpClient,
  HttpXsrfTokenExtractor,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { csrfInterceptor, requiresCsrf, resetCsrfState } from './csrf.interceptor';
import { HttpRequest } from '@angular/common/http';

class FakeExtractor extends HttpXsrfTokenExtractor {
  token: string | null = null;
  getToken(): string | null {
    return this.token;
  }
}

describe('csrfInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let extractor: FakeExtractor;

  beforeEach(() => {
    resetCsrfState();
    extractor = new FakeExtractor();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([csrfInterceptor])),
        provideHttpClientTesting(),
        { provide: HttpXsrfTokenExtractor, useValue: extractor },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('knows which endpoints are cookie-authenticated', () => {
    expect(requiresCsrf(new HttpRequest('POST', '/api/v1/public/orders', {}))).toBe(true);
    expect(requiresCsrf(new HttpRequest('POST', '/api/v1/auth/refresh', null))).toBe(true);
    expect(requiresCsrf(new HttpRequest('GET', '/api/v1/public/menu'))).toBe(false);
    expect(requiresCsrf(new HttpRequest('POST', '/api/v1/public/payments/payu/callback', {}))).toBe(
      false,
    );
    expect(requiresCsrf(new HttpRequest('POST', '/api/v1/admin/items', {}))).toBe(false);
  });

  it('fetches the CSRF cookie first when it is missing', () => {
    http.post('/api/v1/public/orders', {}).subscribe();
    const csrf = controller.expectOne('/api/v1/auth/csrf');
    extractor.token = 'tok-1';
    csrf.flush(null);
    const req = controller.expectOne('/api/v1/public/orders');
    expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('tok-1');
    req.flush({});
  });

  it('uses the existing cookie without an extra request', () => {
    extractor.token = 'tok-2';
    http.post('/api/v1/public/orders', {}).subscribe();
    controller.expectNone('/api/v1/auth/csrf');
    const req = controller.expectOne('/api/v1/public/orders');
    expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('tok-2');
    req.flush({});
  });

  it('refetches the token and retries once on 403 CSRF_INVALID', () => {
    extractor.token = 'stale';
    http.post('/api/v1/public/orders', {}).subscribe();
    controller
      .expectOne('/api/v1/public/orders')
      .flush({ code: 'CSRF_INVALID' }, { status: 403, statusText: 'Forbidden' });
    extractor.token = 'fresh';
    controller.expectOne('/api/v1/auth/csrf').flush(null);
    const retry = controller.expectOne('/api/v1/public/orders');
    expect(retry.request.headers.get('X-XSRF-TOKEN')).toBe('fresh');
    retry.flush({});
  });
});
