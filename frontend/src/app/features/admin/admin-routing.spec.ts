import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { StaffInfo } from '../../core/api/models';
import { AuthStore } from '../../core/auth/auth.store';
import { visibleNav } from './data/admin-nav';
import { ADMIN_ROUTES } from './admin-routing-module';

/** Routing-level check: the real admin route config with a signed-in MANAGER / OWNER (no rendering). */
describe('admin routes – owner-only sections', () => {
  const user = signal<StaffInfo | null>(null);
  const authStub = {
    user,
    role: computed(() => user()?.role ?? null),
    isOwner: computed(() => user()?.role === 'OWNER'),
    isAuthenticated: computed(() => user() !== null),
    mustChangePassword: computed(() => user()?.mustChangePassword ?? false),
    accessToken: signal('jwt'),
    expiresAt: signal(null),
    ensureSession: async () => user() !== null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'admin', children: ADMIN_ROUTES }]),
        { provide: AuthStore, useValue: authStub },
      ],
    });
  });

  const signIn = (role: 'OWNER' | 'MANAGER', mustChangePassword = false) =>
    user.set({ id: 7, username: 'sam', role, mustChangePassword });

  for (const section of ['reports', 'settings', 'staff']) {
    it(`redirects a manager away from /admin/${section}`, async () => {
      signIn('MANAGER');
      const router = TestBed.inject(Router);
      await router.navigateByUrl(`/admin/${section}`);
      expect(router.url).toBe('/admin');
    });

    it(`lets an owner open /admin/${section}`, async () => {
      signIn('OWNER');
      const router = TestBed.inject(Router);
      await router.navigateByUrl(`/admin/${section}`);
      expect(router.url).toBe(`/admin/${section}`);
    });
  }

  it('lets a manager open shared sections', async () => {
    signIn('MANAGER');
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/admin/orders');
    expect(router.url).toBe('/admin/orders');
  });

  it('forces the password change first', async () => {
    signIn('OWNER', true);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/admin/reports');
    expect(router.url).toBe('/admin/change-password');
  });

  it('sends anonymous users to the login with a returnUrl', async () => {
    user.set(null);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/admin/menu/items');
    expect(router.url).toBe('/admin/login?returnUrl=%2Fadmin%2Fmenu%2Fitems');
  });

  it('hides owner-only navigation for managers', () => {
    expect(visibleNav(false).map((n) => n.label)).not.toContain('Reports');
    expect(visibleNav(false).some((n) => n.ownerOnly)).toBe(false);
    expect(visibleNav(true).filter((n) => n.ownerOnly).map((n) => n.label)).toEqual([
      'Reports',
      'Settings',
      'Staff & devices',
    ]);
  });
});
