import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HqTimezoneService, HQ_PERMISSION_PRIVILEGES } from 'hq-shared-lib';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth-service';
import { FcmService } from './fcm-service';
import { PrivilegeService } from './privilege-service';
import { ORGANIZATION_HEADER } from './organization-context.constants';

describe('AuthService', () => {
  let http: HttpTestingController;
  let router: { navigate: ReturnType<typeof vi.fn> };
  let privileges: { restore: ReturnType<typeof vi.fn>; clear: ReturnType<typeof vi.fn>; load: ReturnType<typeof vi.fn>; isPending: boolean; hasAny: ReturnType<typeof vi.fn> };
  let fcm: { unregisterBeforeLogout: ReturnType<typeof vi.fn> };
  const LOGIN = `${environment.uamApiUrl}/identity/auth/login`;

  function create(): AuthService {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
        { provide: FcmService, useValue: fcm },
        { provide: PrivilegeService, useValue: privileges },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => {
    localStorage.clear();
    router = { navigate: vi.fn().mockResolvedValue(true) };
    fcm = { unregisterBeforeLogout: vi.fn() };
    privileges = { restore: vi.fn(), clear: vi.fn(), load: vi.fn().mockReturnValue(of(undefined)), isPending: false, hasAny: vi.fn() };
  });

  afterEach(() => {
    TestBed.inject(HqTimezoneService).reset();
    localStorage.clear();
  });

  describe('session restore', () => {
    it('restores the user and the saved privileges when a token and user data are stored', () => {
      localStorage.setItem('access_token', 't');
      localStorage.setItem('user_data', JSON.stringify({ id: 'u1', role: 'STAFF' }));
      const auth = create();
      expect(auth.currentUser).toEqual({ id: 'u1', role: 'STAFF' });
      expect(auth.isAuthenticated()).toBe(true);
      expect(privileges.restore).toHaveBeenCalled();
    });

    it('stays signed out when nothing is stored or the stored user is corrupt', () => {
      localStorage.setItem('access_token', 't');
      localStorage.setItem('user_data', '{bad');
      const auth = create();
      expect(auth.currentUser).toBeNull();
      expect(auth.isAuthenticated()).toBe(false);
    });

    it('emits the user through user$', () => {
      const auth = create();
      const seen: unknown[] = [];
      auth.user$.subscribe((u) => seen.push(u));
      auth.applySession({ userId: 'u1', accessToken: 'x', role: 'A' } as never);
      expect(seen).toEqual([null, expect.objectContaining({ id: 'u1' })]);
    });
  });

  describe('privileges', () => {
    it('refreshPrivileges only loads when signed in', () => {
      const auth = create();
      auth.refreshPrivileges();
      expect(privileges.load).not.toHaveBeenCalled();
      auth.applySession({ userId: 'u', accessToken: 't' } as never);
      privileges.load.mockClear();
      auth.refreshPrivileges();
      expect(privileges.load).toHaveBeenCalledTimes(1);
    });

    it('ensurePrivileges fetches while pending and signed in, otherwise completes immediately', () => {
      const auth = create();
      let done = 0;
      auth.ensurePrivileges().subscribe(() => done++);
      expect(done).toBe(1);
      auth.applySession({ userId: 'u', accessToken: 't' } as never);
      privileges.load.mockClear();
      privileges.isPending = true;
      auth.ensurePrivileges().subscribe();
      expect(privileges.load).toHaveBeenCalledTimes(1);
    });

    it('canAccess is open for an empty or unmapped permission and otherwise follows the privileges', () => {
      const auth = create();
      expect(auth.canAccess('')).toBe(true);
      expect(auth.canAccess('no_such_permission_key')).toBe(true);
      const key = Object.keys(HQ_PERMISSION_PRIVILEGES)[0];
      privileges.hasAny.mockReturnValue(false);
      expect(auth.canAccess(key)).toBe(false);
      expect(privileges.hasAny).toHaveBeenCalledWith(HQ_PERMISSION_PRIVILEGES[key]);
      privileges.hasAny.mockReturnValue(true);
      expect(auth.canAccess(key)).toBe(true);
    });

    it('hasRole matches the current role only', () => {
      const auth = create();
      expect(auth.hasRole('A')).toBe(false);
      auth.applySession({ userId: 'u', accessToken: 't', role: 'A' } as never);
      expect(auth.hasRole('B', 'A')).toBe(true);
      expect(auth.hasRole('B')).toBe(false);
    });
  });

  describe('login', () => {
    it('posts the credentials in the request envelope and starts the session', () => {
      const auth = create();
      let res: unknown;
      auth.login({ email: 'a@b.c', password: 'pw', totpCode: '123456' }).subscribe((r) => (res = r));
      const req = http.expectOne(LOGIN);
      expect(req.request.body).toEqual({ requestObject: { username: 'a@b.c', password: 'pw', totpCode: '123456' } });
      req.flush({ success: true, responseObject: { userId: 'u1', accessToken: 'jwt', refreshToken: 'rt', username: 'asha', roles: ['DRIVER_OPS'], forcePasswordChange: true } });
      expect(res).toBeTruthy();
      expect(localStorage.getItem('access_token')).toBe('jwt');
      expect(localStorage.getItem('refresh_token')).toBe('rt');
      expect(auth.currentUser).toMatchObject({ id: 'u1', email: 'asha', name: 'asha', role: 'DRIVER_OPS', mustChangePassword: true });
      expect(privileges.load).toHaveBeenCalled();
    });

    it('does not start a session when two-factor is required', () => {
      const auth = create();
      auth.login({ email: 'a', password: 'b' }).subscribe();
      http.expectOne(LOGIN).flush({ success: true, responseObject: { mfaRequired: true } });
      expect(auth.currentUser).toBeNull();
      expect(localStorage.getItem('access_token')).toBeNull();
    });

    it('turns a failed response into an error carrying the server message, or a translated fallback', () => {
      const auth = create();
      const errors: unknown[] = [];
      auth.login({ email: 'a', password: 'b' }).subscribe({ error: (e) => errors.push(e) });
      http.expectOne(LOGIN).flush({ success: false, message: 'Bad credentials' });
      auth.login({ email: 'a', password: 'b' }).subscribe({ error: (e) => errors.push(e) });
      http.expectOne(LOGIN).flush({ success: false });
      auth.login({ email: 'a', password: 'b' }).subscribe({ error: (e) => errors.push(e) });
      http.expectOne(LOGIN).flush({ success: true });
      expect(errors).toEqual([
        { error: { detail: 'Bad credentials', message: 'Bad credentials' } },
        { error: { detail: 'login.messages.loginFailed', message: 'login.messages.loginFailed' } },
        { error: { detail: 'login.messages.loginFailed', message: 'login.messages.loginFailed' } },
      ]);
    });

    it('loginWithGoogle sends the ID token as an OPS sign-in', () => {
      const auth = create();
      auth.loginWithGoogle('idtok').subscribe();
      const req = http.expectOne(LOGIN);
      expect(req.request.body).toEqual({ requestObject: { idToken: 'idtok', userType: 'OPS' } });
      req.flush({ success: true, responseObject: { userId: 'g1', token: 'jwt2' } });
      expect(auth.currentUser?.id).toBe('g1');
      expect(auth.currentUser?.role).toBe('CUSTOMER');
    });
  });

  describe('refresh', () => {
    it('throws when there is no refresh token', () => {
      const auth = create();
      expect(() => auth.refresh()).toThrow('No refresh token available');
    });

    it('rotates the tokens, naming an organization only when asked', () => {
      localStorage.setItem('refresh_token', 'old');
      const auth = create();
      auth.refresh().subscribe();
      const plain = http.expectOne(`${environment.uamApiUrl}/identity/auth/refresh`);
      expect(plain.request.body).toEqual({ requestObject: { refreshToken: 'old' } });
      expect(plain.request.headers.has(ORGANIZATION_HEADER)).toBe(false);
      plain.flush({ success: true, responseObject: { userId: 'u', accessToken: 'new', refreshToken: 'newr' } });
      expect(localStorage.getItem('access_token')).toBe('new');
      expect(localStorage.getItem('refresh_token')).toBe('newr');

      auth.refresh('ACME').subscribe();
      const named = http.expectOne(`${environment.uamApiUrl}/identity/auth/refresh`);
      expect(named.request.headers.get(ORGANIZATION_HEADER)).toBe('ACME');
      named.flush({ success: true, responseObject: { userId: 'u', accessToken: 'n2' } });
      expect(localStorage.getItem('access_token')).toBe('n2');
    });
  });

  describe('applySession', () => {
    it('rejects a response without an access token', () => {
      const auth = create();
      expect(() => auth.applySession({ userId: 'u' } as never)).toThrow('Login response did not include an access token');
      expect(auth.currentUser).toBeNull();
    });

    it('applies the timezones the server returned and ignores absent ones', () => {
      const auth = create();
      const tz = TestBed.inject(HqTimezoneService);
      const user = vi.spyOn(tz, 'setUserTimezone');
      const org = vi.spyOn(tz, 'setOrganizationTimezone');
      auth.applySession({ userId: 'u', accessToken: 't' } as never);
      expect(user).not.toHaveBeenCalled();
      expect(org).not.toHaveBeenCalled();
      auth.applySession({ userId: 'u', accessToken: 't', timezone: 'Asia/Dubai', organizationTimeZone: null } as never);
      expect(user).toHaveBeenCalledWith('Asia/Dubai');
      expect(org).toHaveBeenCalledWith(null);
    });
  });

  describe('logout', () => {
    it('unregisters push, calls the server, clears local state and goes to login', () => {
      const auth = create();
      auth.applySession({ userId: 'u', accessToken: 't', refreshToken: 'r' } as never);
      auth.logout();
      expect(fcm.unregisterBeforeLogout).toHaveBeenCalled();
      const req = http.expectOne(`${environment.uamApiUrl}/identity/auth/logout`);
      expect(req.request.method).toBe('POST');
      req.flush('boom', { status: 500, statusText: 'err' });
      expect(localStorage.getItem('access_token')).toBeNull();
      expect(localStorage.getItem('refresh_token')).toBeNull();
      expect(localStorage.getItem('user_data')).toBeNull();
      expect(privileges.clear).toHaveBeenCalled();
      expect(auth.currentUser).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
    });
  });
});
