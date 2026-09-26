import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StaffInfo, TokenResponse } from '../api/models';
import { AuthStore } from '../auth/auth.store';
import { DeviceAuthStore } from '../auth/device-auth.store';
import { authInterceptor } from './auth.interceptor';
import { withAuth } from './http-context';

const owner: StaffInfo = { id: 1, username: 'owner', role: 'OWNER', mustChangePassword: false };
const tokens = (accessToken: string): TokenResponse => ({ accessToken, expiresIn: 900, user: owner });

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: AuthStore;
  let router: Router;

  async function signIn(token = 'jwt-1'): Promise<void> {
    const login = auth.login('owner', 'secret');
    controller.expectOne('/api/auth/login').flush(tokens(token));
    await login;
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthStore);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => {
    controller.verify();
    localStorage.clear();
  });

  it('attaches the admin bearer token to /api/admin/**, /api/auth/me and change-password', async () => {
    await signIn();
    http.get('/api/admin/categories').subscribe();
    http.get('/api/auth/me').subscribe();
    http.post('/api/auth/change-password', {}).subscribe();
    for (const url of ['/api/admin/categories', '/api/auth/me', '/api/auth/change-password']) {
      const req = controller.expectOne(url);
      expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-1');
      req.flush({});
    }
  });

  it('does not attach credentials to public endpoints', async () => {
    await signIn();
    http.get('/api/public/menu').subscribe();
    const req = controller.expectOne('/api/public/menu');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('attaches the kitchen device token to /api/kitchen/** (and on demand via withAuth)', async () => {
    const devices = TestBed.inject(DeviceAuthStore);
    const register = devices.registerDevice({ username: 'chef', pin: '1234' });
    controller.expectOne('/api/auth/kitchen-device').flush({
      deviceToken: 'dvc_abc',
      expiresAt: new Date(Date.now() + 86_400_000).toISOString(),
      user: { ...owner, role: 'KITCHEN' },
    });
    await register;

    http.get('/api/kitchen/orders').subscribe();
    http.get('/api/auth/me', { context: withAuth('device') }).subscribe();
    for (const url of ['/api/kitchen/orders', '/api/auth/me']) {
      const req = controller.expectOne(url);
      expect(req.request.headers.get('Authorization')).toBe('Bearer dvc_abc');
      req.flush({});
    }
  });

  it('refreshes ONCE for concurrent 401s and retries each request with the new token', async () => {
    await signIn('expired');
    const a = firstValueFrom(http.get<{ ok: string }>('/api/admin/items'));
    const b = firstValueFrom(http.get<{ ok: string }>('/api/admin/tables'));

    controller.expectOne('/api/admin/items').flush({ code: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });
    controller.expectOne('/api/admin/tables').flush({ code: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });

    const refresh = controller.match('/api/auth/refresh');
    expect(refresh.length).toBe(1);
    expect(refresh[0].request.headers.has('Authorization')).toBe(false);
    refresh[0].flush(tokens('fresh'));
    await Promise.resolve();
    await new Promise((r) => setTimeout(r));

    const retriedItems = controller.expectOne('/api/admin/items');
    const retriedTables = controller.expectOne('/api/admin/tables');
    expect(retriedItems.request.headers.get('Authorization')).toBe('Bearer fresh');
    expect(retriedTables.request.headers.get('Authorization')).toBe('Bearer fresh');
    retriedItems.flush({ ok: 'items' });
    retriedTables.flush({ ok: 'tables' });

    await expect(a).resolves.toEqual({ ok: 'items' });
    await expect(b).resolves.toEqual({ ok: 'tables' });
    expect(auth.accessToken()).toBe('fresh');
  });

  it('logs out and redirects to the admin login when the refresh fails', async () => {
    await signIn('expired');
    const call = firstValueFrom(http.get('/api/admin/items'));
    controller.expectOne('/api/admin/items').flush({}, { status: 401, statusText: 'Unauthorized' });
    controller.expectOne('/api/auth/refresh').flush({ code: 'SESSION_EXPIRED' }, { status: 401, statusText: 'Unauthorized' });

    await expect(call).rejects.toBeTruthy();
    expect(auth.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/admin/login'], expect.anything());
  });

  it('does not refresh-loop when the retried request is also unauthorized', async () => {
    await signIn('expired');
    const call = firstValueFrom(http.get('/api/admin/items'));
    controller.expectOne('/api/admin/items').flush({}, { status: 401, statusText: 'Unauthorized' });
    controller.expectOne('/api/auth/refresh').flush(tokens('fresh'));
    await new Promise((r) => setTimeout(r));
    controller.expectOne('/api/admin/items').flush({}, { status: 401, statusText: 'Unauthorized' });
    await expect(call).rejects.toBeTruthy();
    controller.expectNone('/api/auth/refresh');
  });

  it('forgets the kitchen device on 401', async () => {
    const devices = TestBed.inject(DeviceAuthStore);
    const register = devices.registerDevice({ username: 'chef', password: 'pw' });
    controller.expectOne('/api/auth/kitchen-device').flush({
      deviceToken: 'dvc_revoked',
      expiresAt: new Date(Date.now() + 86_400_000).toISOString(),
      user: { ...owner, role: 'KITCHEN' },
    });
    await register;

    const call = firstValueFrom(http.get('/api/kitchen/orders'));
    controller.expectOne('/api/kitchen/orders').flush({}, { status: 401, statusText: 'Unauthorized' });
    await expect(call).rejects.toBeTruthy();
    expect(devices.isRegistered()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/kitchen/login');
  });
});
