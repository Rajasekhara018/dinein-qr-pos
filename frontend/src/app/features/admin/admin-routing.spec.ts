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
    user.set({ id: 7, username: 'sam', role, mustChangePassword, platformAdmin: false, restaurantId: 1 });

  for (const section of ['reports', 'settings', 'staff']) {
    // The first lazy child module loaded in this whole file (usually 'reports', since it's first in the array)
    // pays a one-off cold dynamic-import cost. Isolated, that's ~1-2s; running alongside every other spec file's
    // own concurrent dynamic imports (28 files, each its own worker) it has measured up to ~35s here. The redirect
    // itself is fast once the guard runs -- a longer timeout, not a different assertion, is the right fix.
    it(
      `redirects a manager away from /admin/${section}`,
      async () => {
        signIn('MANAGER');
        const router = TestBed.inject(Router);
        await router.navigateByUrl(`/admin/${section}`);
        expect(router.url).toBe('/admin');
      },
      60_000,
    );

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

  for (const path of ['/admin/kiosks', '/admin/kiosks/appearance', '/admin/kiosks/upsells']) {
    it(`lets a manager open ${path}`, async () => {
      signIn('MANAGER');
      const router = TestBed.inject(Router);
      await router.navigateByUrl(path);
      expect(router.url).toBe(path);
    }, 60_000);
  }

  it('shows the kiosk navigation to managers too', () => {
    const labels = visibleNav(false, false).map((n) => n.label);
    expect(labels).toContain('Kiosks');
    expect(labels).toContain('Kiosk appearance');
    expect(labels).toContain('Kiosk upsells');
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
    expect(visibleNav(false, false).map((n) => n.label)).not.toContain('Reports');
    expect(visibleNav(false, false).some((n) => n.ownerOnly)).toBe(false);
    expect(visibleNav(true, false).filter((n) => n.ownerOnly).map((n) => n.label)).toEqual([
      'Reports',
      'Settings',
      'Staff & devices',
    ]);
  });

  it('shows the Platform nav item only for platform-admin accounts', () => {
    expect(visibleNav(true, false).map((n) => n.label)).not.toContain('Restaurants');
    expect(visibleNav(true, true).map((n) => n.label)).toContain('Restaurants');
  });
});
