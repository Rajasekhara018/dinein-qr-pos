import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { UamService } from './uam-service';

const BASE = environment.uamApiUrl;
const wrapped = (requestObject: unknown) => ({
  requestObject, doesInputHaveEncryptedData: false, success: false, pageNo: 0, pageSize: 0, useOrOperator: false,
});

interface Case {
  name: string;
  call: (s: UamService) => unknown;
  method: string;
  path: string;
  body?: unknown;
  params?: Record<string, string>;
}

const cases: Case[] = [
  { name: 'listUsers', call: (s) => s.listUsers(1, 10), method: 'GET', path: '/users', params: { page: '1', size: '10' } },
  { name: 'getUser', call: (s) => s.getUser('u1'), method: 'GET', path: '/users/u1' },
  { name: 'createUser', call: (s) => s.createUser({ a: 1 } as never), method: 'POST', path: '/users', body: wrapped({ a: 1 }) },
  { name: 'updateUser', call: (s) => s.updateUser('u1', { a: 1 } as never), method: 'PUT', path: '/users/u1', body: wrapped({ a: 1 }) },
  { name: 'deleteUser', call: (s) => s.deleteUser('u1'), method: 'DELETE', path: '/users/u1' },
  { name: 'setUserStatus', call: (s) => s.setUserStatus('u1', 'LOCKED'), method: 'PATCH', path: '/users/u1/status', body: null, params: { status: 'LOCKED' } },
  { name: 'setUserPassword', call: (s) => s.setUserPassword('u1', { p: 1 } as never), method: 'PATCH', path: '/users/u1/password', body: wrapped({ p: 1 }) },
  { name: 'setTotpRequired', call: (s) => s.setTotpRequired('u1', true), method: 'PATCH', path: '/users/u1/totp-required', body: null, params: { required: 'true' } },
  { name: 'getMyProfile', call: (s) => s.getMyProfile(), method: 'GET', path: '/identity/me/profile' },
  { name: 'updateMyProfile', call: (s) => s.updateMyProfile({ n: 1 } as never), method: 'PUT', path: '/identity/me/profile', body: wrapped({ n: 1 }) },
  { name: 'getMyPreferences', call: (s) => s.getMyPreferences(), method: 'GET', path: '/identity/me/preferences' },
  { name: 'getMyPreferences(app)', call: (s) => s.getMyPreferences('app1'), method: 'GET', path: '/identity/me/preferences', params: { applicationId: 'app1' } },
  { name: 'updateMyPreferences', call: (s) => s.updateMyPreferences({ x: 1 } as never), method: 'PUT', path: '/identity/me/preferences', body: wrapped({ x: 1 }) },
  { name: 'getMySessions', call: (s) => s.getMySessions(), method: 'GET', path: '/identity/me/sessions' },
  { name: 'revokeMySession', call: (s) => s.revokeMySession('s1'), method: 'DELETE', path: '/identity/me/sessions/s1' },
  { name: 'revokeAllMySessions', call: (s) => s.revokeAllMySessions(), method: 'DELETE', path: '/identity/me/sessions' },
  { name: 'getMyDevices', call: (s) => s.getMyDevices(), method: 'GET', path: '/identity/me/devices' },
  { name: 'trustMyDevice', call: (s) => s.trustMyDevice('d1', false), method: 'PATCH', path: '/identity/me/devices/d1/trust', body: null, params: { trusted: 'false' } },
  { name: 'blockMyDevice', call: (s) => s.blockMyDevice('d1', true, 'lost'), method: 'PATCH', path: '/identity/me/devices/d1/block', body: null, params: { blocked: 'true', reason: 'lost' } },
  { name: 'blockMyDevice (no reason)', call: (s) => s.blockMyDevice('d1', false), method: 'PATCH', path: '/identity/me/devices/d1/block', body: null, params: { blocked: 'false' } },
  { name: 'deleteMyDevice', call: (s) => s.deleteMyDevice('d1'), method: 'DELETE', path: '/identity/me/devices/d1' },
  { name: 'getMyMfa', call: (s) => s.getMyMfa(), method: 'GET', path: '/identity/me/mfa' },
  { name: 'enrollMyTotp', call: (s) => s.enrollMyTotp(), method: 'POST', path: '/identity/me/mfa/totp/enroll', body: null },
  { name: 'verifyMyMfaEnrollment', call: (s) => s.verifyMyMfaEnrollment('m1', '123456'), method: 'POST', path: '/identity/me/mfa/m1/verify', body: wrapped({ code: '123456' }) },
  { name: 'disableMyMfa', call: (s) => s.disableMyMfa('m1'), method: 'PATCH', path: '/identity/me/mfa/m1/disable', body: null },
  { name: 'deleteMyMfa', call: (s) => s.deleteMyMfa('m1'), method: 'DELETE', path: '/identity/me/mfa/m1' },
  { name: 'getMyLoginSecurity', call: (s) => s.getMyLoginSecurity(), method: 'GET', path: '/identity/me/login-security' },
  { name: 'getUserMfa', call: (s) => s.getUserMfa('u1'), method: 'GET', path: '/users/u1/mfa' },
  { name: 'deleteMfa', call: (s) => s.deleteMfa('m1'), method: 'DELETE', path: '/mfa/m1' },
  { name: 'disableMfa', call: (s) => s.disableMfa('m1'), method: 'PATCH', path: '/mfa/m1/disable', body: null },
  { name: 'getUserSessions', call: (s) => s.getUserSessions('u1'), method: 'GET', path: '/users/u1/sessions' },
  { name: 'revokeSession', call: (s) => s.revokeSession('s1'), method: 'DELETE', path: '/sessions/s1' },
  { name: 'revokeAllSessions', call: (s) => s.revokeAllSessions('u1'), method: 'DELETE', path: '/users/u1/sessions' },
  { name: 'getUserDevices', call: (s) => s.getUserDevices('u1'), method: 'GET', path: '/users/u1/devices' },
  { name: 'deleteDevice', call: (s) => s.deleteDevice('d1'), method: 'DELETE', path: '/devices/d1' },
  { name: 'trustDevice', call: (s) => s.trustDevice('d1', true), method: 'PATCH', path: '/devices/d1/trust', body: null, params: { trusted: 'true' } },
  { name: 'blockDevice', call: (s) => s.blockDevice('d1', true, 'why'), method: 'PATCH', path: '/devices/d1/block', body: null, params: { blocked: 'true', reason: 'why' } },
  { name: 'blockDevice (no reason)', call: (s) => s.blockDevice('d1', false), method: 'PATCH', path: '/devices/d1/block', body: null, params: { blocked: 'false' } },
  { name: 'getLoginSecurity', call: (s) => s.getLoginSecurity('u1'), method: 'GET', path: '/users/u1/login-security' },
  { name: 'unlockAccount', call: (s) => s.unlockAccount('u1'), method: 'POST', path: '/users/u1/login-security/unlock', body: null },
  { name: 'getUserCredentials', call: (s) => s.getUserCredentials('u1'), method: 'GET', path: '/users/u1/credentials' },
  { name: 'deleteCredential', call: (s) => s.deleteCredential('c1'), method: 'DELETE', path: '/credentials/c1' },
  { name: 'getRefreshTokens', call: (s) => s.getRefreshTokens('p1'), method: 'GET', path: '/principals/p1/refresh-tokens' },
  { name: 'revokeRefreshToken', call: (s) => s.revokeRefreshToken('r1'), method: 'DELETE', path: '/refresh-tokens/r1' },
  { name: 'revokeAllRefreshTokens', call: (s) => s.revokeAllRefreshTokens('p1'), method: 'DELETE', path: '/principals/p1/refresh-tokens' },
  { name: 'getPasswordHistory', call: (s) => s.getPasswordHistory('u1', 0, 5), method: 'GET', path: '/users/u1/password-history', params: { page: '0', size: '5' } },
  { name: 'getUserPreference', call: (s) => s.getUserPreference('x'), method: 'GET', path: '/user-preferences/x' },
  { name: 'getUserPreferenceByUser', call: (s) => s.getUserPreferenceByUser('u1'), method: 'GET', path: '/user-preferences/by-user/u1' },
  { name: 'getUserPreferenceByUser(app)', call: (s) => s.getUserPreferenceByUser('u1', 'a'), method: 'GET', path: '/user-preferences/by-user/u1', params: { applicationId: 'a' } },
  { name: 'upsertUserPreference', call: (s) => s.upsertUserPreference({ k: 1 } as never), method: 'POST', path: '/user-preferences', body: wrapped({ k: 1 }) },
  { name: 'deleteUserPreference', call: (s) => s.deleteUserPreference('x'), method: 'DELETE', path: '/user-preferences/x' },
  { name: 'rotateClientSecret', call: (s) => s.rotateClientSecret('c1'), method: 'POST', path: '/clients/c1/rotate-secret', body: null },
  { name: 'createClient', call: (s) => s.createClient({ n: 1 } as never), method: 'POST', path: '/clients', body: wrapped({ n: 1 }) },
  { name: 'registerCustomer', call: (s) => s.registerCustomer({ a: 1 } as never), method: 'POST', path: '/identity/auth/register', body: wrapped({ a: 1, userType: 'CUSTOMER' }) },
  { name: 'sendPhoneOtp', call: (s) => s.sendPhoneOtp({ phone: '1' } as never), method: 'POST', path: '/identity/auth/phone/send-otp', body: wrapped({ phone: '1', userType: 'CUSTOMER' }) },
  { name: 'verifyPhoneOtp', call: (s) => s.verifyPhoneOtp({ otp: '1' } as never), method: 'POST', path: '/identity/auth/phone/verify-otp', body: wrapped({ otp: '1', userType: 'CUSTOMER' }) },
  { name: 'sendEmailOtp', call: (s) => s.sendEmailOtp({ email: 'a@b' } as never), method: 'POST', path: '/identity/auth/email/send-otp', body: wrapped({ email: 'a@b', userType: 'OPS' }) },
  { name: 'verifyEmailOtp', call: (s) => s.verifyEmailOtp({ otp: '1' } as never), method: 'POST', path: '/identity/auth/email/verify-otp', body: wrapped({ otp: '1', userType: 'OPS' }) },
  { name: 'sendPasswordResetOtp', call: (s) => s.sendPasswordResetOtp('a@b'), method: 'POST', path: '/identity/auth/email/send-otp', body: wrapped({ email: 'a@b', userType: 'OPS' }) },
  { name: 'resetPassword', call: (s) => s.resetPassword('a@b', '999', 'pw'), method: 'POST', path: '/identity/auth/reset-password', body: wrapped({ email: 'a@b', otp: '999', newPassword: 'pw', userType: 'OPS' }) },
  { name: 'get', call: (s) => s.get('/identity/me/organizations'), method: 'GET', path: '/identity/me/organizations' },
  { name: 'list', call: (s) => s.list('/roles', 2, 3), method: 'GET', path: '/roles', params: { page: '2', size: '3' } },
  { name: 'create', call: (s) => s.create('/roles', { r: 1 }), method: 'POST', path: '/roles', body: wrapped({ r: 1 }) },
  { name: 'update', call: (s) => s.update('/roles', 'r1', { r: 2 }), method: 'PUT', path: '/roles/r1', body: wrapped({ r: 2 }) },
  { name: 'remove', call: (s) => s.remove('/roles', 'r1'), method: 'DELETE', path: '/roles/r1' },
];

describe('UamService', () => {
  let service: UamService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useValue: { instant: (k: string) => k } }],
    });
    service = TestBed.inject(UamService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it.each(cases)('$name sends $method $path in the expected shape and returns the unwrapped object', (c) => {
    let result: unknown;
    (c.call(service) as { subscribe: (fn: (v: unknown) => void) => void }).subscribe((v) => (result = v));
    const req = http.expectOne((r) => r.url === `${BASE}${c.path}`);
    expect(req.request.method).toBe(c.method);
    if ('body' in c) expect(req.request.body).toEqual(c.body);
    if (c.params) {
      for (const [k, v] of Object.entries(c.params)) expect(req.request.params.get(k)).toBe(v);
    } else {
      expect(req.request.params.keys()).toEqual([]);
    }
    req.flush({ success: true, responseObject: { marker: c.name } });
    expect(result).toEqual({ marker: c.name });
  });

  it('raises the server message when the envelope reports failure', () => {
    let err: unknown;
    service.getUser('u1').subscribe({ error: (e) => (err = e) });
    http.expectOne(`${BASE}/users/u1`).flush({ success: false, message: 'User not found' });
    expect(err).toEqual({ error: { detail: 'User not found', message: 'User not found' } });
  });

  it('falls back to the translated generic message when the server gave none', () => {
    let err: unknown;
    service.getUser('u1').subscribe({ error: (e) => (err = e) });
    http.expectOne(`${BASE}/users/u1`).flush({ success: false });
    expect(err).toEqual({ error: { detail: 'shared.errors.requestFailed', message: 'shared.errors.requestFailed' } });
  });
});
