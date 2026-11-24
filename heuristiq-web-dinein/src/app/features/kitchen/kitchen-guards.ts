import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KITCHEN_PATHS } from '../../core/auth/auth-paths';
import { DeviceAuthStore } from '../../core/auth/device-auth.store';

/** The login page is skipped when this device is already registered. */
export const kitchenLoginGuard: CanActivateFn = () => {
  const devices = inject(DeviceAuthStore);
  const router = inject(Router);
  return devices.isRegistered() ? router.createUrlTree([KITCHEN_PATHS.home]) : true;
};
