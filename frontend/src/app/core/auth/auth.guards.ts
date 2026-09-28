import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';
import { StaffRole } from '../api/models';
import { ADMIN_PATHS, KITCHEN_PATHS, WAITER_PATHS } from './auth-paths';
import { AuthStore } from './auth.store';
import { DeviceAuthStore } from './device-auth.store';

/** Roles that may use the waiter screen (`/api/v1/waiter/**` allows the same). */
export const WAITER_SCREEN_ROLES: readonly StaffRole[] = ['WAITER', 'MANAGER', 'OWNER'];

/**
 * Admin area: requires a session (restoring it from the refresh cookie after a reload), otherwise redirects to
 * `/admin/login?returnUrl=…`. Waiter accounts share the staff session but have no admin access, so they are sent
 * to the waiter screen. Use on the admin shell route (canActivate + canActivateChild).
 */
export const adminAuthGuard: CanActivateFn & CanActivateChildFn = async (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (await auth.ensureSession()) {
    return auth.role() === 'WAITER' ? router.createUrlTree([WAITER_PATHS.home]) : true;
  }
  return router.createUrlTree([ADMIN_PATHS.login], { queryParams: { returnUrl: state.url } });
};

/**
 * Forces the first-login password change: while `mustChangePassword` is true every admin route except the
 * change-password page redirects there. Put it after `adminAuthGuard`.
 */
export const passwordChangeGuard: CanActivateFn & CanActivateChildFn = (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (!auth.mustChangePassword() || state.url.startsWith(ADMIN_PATHS.changePassword)) return true;
  return router.createUrlTree([ADMIN_PATHS.changePassword]);
};

/** OWNER-only pages (settings, staff & devices, reports). Managers are sent to the admin home. */
export const ownerGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  return auth.isOwner() ? true : router.createUrlTree([ADMIN_PATHS.home]);
};

/** Platform-admin-only pages (`/admin/platform`). Everyone else is sent to the admin home. */
export const platformAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  return auth.isPlatformAdmin() ? true : router.createUrlTree([ADMIN_PATHS.home]);
};

/** Redirects an already signed-in admin away from the login page. */
export const adminGuestOnlyGuard: CanActivateFn = async () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  return (await auth.ensureSession()) ? router.createUrlTree([ADMIN_PATHS.home]) : true;
};

/** Kitchen board: requires a registered (non-expired) device token, else `/kitchen/login`. */
export const kitchenAuthGuard: CanActivateFn & CanActivateChildFn = () => {
  const devices = inject(DeviceAuthStore);
  const router = inject(Router);
  return devices.isRegistered() ? true : router.createUrlTree([KITCHEN_PATHS.login]);
};

/**
 * Waiter screen: requires a staff session with role WAITER, MANAGER or OWNER (restored from the refresh cookie after
 * a reload), otherwise `/waiter/login?returnUrl=…`. Any other role (a kitchen account) is signed out and sent to the
 * login with `reason=role`. Owners/managers who still have to change their bootstrap password go to the admin
 * change-password page first.
 */
export const waiterAuthGuard: CanActivateFn & CanActivateChildFn = async (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (!(await auth.ensureSession())) {
    return router.createUrlTree([WAITER_PATHS.login], { queryParams: { returnUrl: state.url } });
  }
  const role = auth.role();
  if (!role || !WAITER_SCREEN_ROLES.includes(role)) {
    await auth.logout();
    return router.createUrlTree([WAITER_PATHS.login], { queryParams: { reason: 'role' } });
  }
  if (auth.mustChangePassword()) return router.createUrlTree([ADMIN_PATHS.changePassword]);
  return true;
};

/** Skips the waiter login when a usable staff session already exists. */
export const waiterGuestOnlyGuard: CanActivateFn = async () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (!(await auth.ensureSession())) return true;
  const role = auth.role();
  return role && WAITER_SCREEN_ROLES.includes(role) ? router.createUrlTree([WAITER_PATHS.home]) : true;
};
