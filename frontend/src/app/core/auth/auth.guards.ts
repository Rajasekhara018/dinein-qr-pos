import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';
import { ADMIN_PATHS, KITCHEN_PATHS } from './auth-paths';
import { AuthStore } from './auth.store';
import { DeviceAuthStore } from './device-auth.store';

/**
 * Admin area: requires a session (restoring it from the refresh cookie after a reload), otherwise redirects to
 * `/admin/login?returnUrl=…`. Use on the admin shell route (canActivate + canActivateChild).
 */
export const adminAuthGuard: CanActivateFn & CanActivateChildFn = async (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (await auth.ensureSession()) return true;
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
