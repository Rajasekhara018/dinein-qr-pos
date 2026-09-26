import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStore } from './auth.store';
import { adminAuthGuard, kitchenAuthGuard, ownerGuard, passwordChangeGuard } from './auth.guards';
import { DeviceAuthStore } from './device-auth.store';

const route = {} as ActivatedRouteSnapshot;
const state = (url: string) => ({ url }) as RouterStateSnapshot;

describe('auth guards', () => {
  const authMock = {
    ensureSession: vi.fn<() => Promise<boolean>>(),
    mustChangePassword: signal(false),
    isOwner: signal(false),
  };
  const deviceMock = { isRegistered: signal(false) };

  beforeEach(() => {
    authMock.ensureSession.mockReset();
    authMock.mustChangePassword.set(false);
    authMock.isOwner.set(false);
    deviceMock.isRegistered.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthStore, useValue: authMock },
        { provide: DeviceAuthStore, useValue: deviceMock },
      ],
    });
  });

  const run = <T>(fn: () => T) => TestBed.runInInjectionContext(fn);
  const asUrl = (result: unknown) => (result instanceof UrlTree ? result.toString() : result);

  describe('adminAuthGuard', () => {
    it('allows when a session exists or can be restored', async () => {
      authMock.ensureSession.mockResolvedValue(true);
      expect(await run(() => adminAuthGuard(route, state('/admin/items')))).toBe(true);
    });

    it('redirects to the login with a returnUrl otherwise', async () => {
      authMock.ensureSession.mockResolvedValue(false);
      const result = await run(() => adminAuthGuard(route, state('/admin/items')));
      expect(asUrl(result)).toBe('/admin/login?returnUrl=%2Fadmin%2Fitems');
    });
  });

  describe('passwordChangeGuard', () => {
    it('forces the change-password page while mustChangePassword is set', () => {
      authMock.mustChangePassword.set(true);
      expect(asUrl(run(() => passwordChangeGuard(route, state('/admin/items'))))).toBe(
        '/admin/change-password',
      );
      expect(run(() => passwordChangeGuard(route, state('/admin/change-password')))).toBe(true);
    });

    it('allows everything once the password was changed', () => {
      expect(run(() => passwordChangeGuard(route, state('/admin/items')))).toBe(true);
    });
  });

  describe('ownerGuard', () => {
    it('allows owners and sends managers to the admin home', () => {
      expect(asUrl(run(() => ownerGuard(route, state('/admin/settings'))))).toBe('/admin');
      authMock.isOwner.set(true);
      expect(run(() => ownerGuard(route, state('/admin/settings')))).toBe(true);
    });
  });

  describe('kitchenAuthGuard', () => {
    it('requires a registered device', () => {
      expect(asUrl(run(() => kitchenAuthGuard(route, state('/kitchen'))))).toBe('/kitchen/login');
      deviceMock.isRegistered.set(true);
      expect(run(() => kitchenAuthGuard(route, state('/kitchen')))).toBe(true);
    });
  });
});
