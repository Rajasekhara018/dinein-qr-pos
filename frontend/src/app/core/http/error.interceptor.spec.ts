import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../api/api-error';
import { ToastService } from '../ui/toast.service';
import { errorInterceptor } from './error.interceptor';
import { silentErrors } from './http-context';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let toasts: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
  });

  afterEach(() => {
    controller.verify();
    toasts.clear();
  });

  it('normalises the backend error body into a typed ApiError', async () => {
    const call = firstValueFrom(http.post('/api/public/orders', {}));
    controller.expectOne('/api/public/orders').flush(
      {
        code: 'ITEM_UNAVAILABLE',
        message: 'Some items are unavailable',
        details: [{ lineIndex: 0, itemId: 3, name: 'Tea', reason: 'ITEM_UNAVAILABLE' }],
        traceId: 'abc123',
      },
      { status: 409, statusText: 'Conflict' },
    );
    const error = (await call.catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 409, code: 'ITEM_UNAVAILABLE', traceId: 'abc123' });
    expect(error.cartProblems[0]).toMatchObject({ lineIndex: 0, itemId: 3 });
  });

  it('does not toast handled 4xx errors', async () => {
    const call = firstValueFrom(http.get('/api/public/orders/9'));
    controller.expectOne('/api/public/orders/9').flush({ code: 'NOT_FOUND', message: 'Order not found' }, { status: 404, statusText: 'Not Found' });
    await call.catch(() => undefined);
    expect(toasts.toasts()).toEqual([]);
  });

  it('toasts unexpected 5xx errors', async () => {
    const call = firstValueFrom(http.get('/api/public/menu'));
    controller.expectOne('/api/public/menu').flush({ code: 'INTERNAL_ERROR', message: 'Something went wrong' }, { status: 500, statusText: 'Server Error' });
    await call.catch(() => undefined);
    expect(toasts.toasts().map((t) => t.kind)).toEqual(['error']);
  });

  it('maps network failures to NETWORK_ERROR and toasts them', async () => {
    const call = firstValueFrom(http.get('/api/public/menu'));
    controller.expectOne('/api/public/menu').error(new ProgressEvent('error'), { status: 0 });
    const error = (await call.catch((e: unknown) => e)) as ApiError;
    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.isNetworkError).toBe(true);
    expect(toasts.toasts().length).toBe(1);
  });

  it('stays silent for 5xx when the caller opted out', async () => {
    const call = firstValueFrom(http.post('/api/public/orders', {}, { context: silentErrors() }));
    controller.expectOne('/api/public/orders').flush({ code: 'PAYMENTS_NOT_CONFIGURED', message: 'x' }, { status: 503, statusText: 'Unavailable' });
    const error = (await call.catch((e: unknown) => e)) as ApiError;
    expect(error.code).toBe('PAYMENTS_NOT_CONFIGURED');
    expect(toasts.toasts()).toEqual([]);
  });

  it('falls back to a generic error when the body is not JSON', async () => {
    const call = firstValueFrom(http.get('/api/admin/x'));
    controller.expectOne('/api/admin/x').flush('<html>Bad gateway</html>', { status: 502, statusText: 'Bad Gateway' });
    const error = (await call.catch((e: unknown) => e)) as ApiError;
    expect(error).toMatchObject({ status: 502, code: 'INTERNAL_ERROR' });
  });
});
