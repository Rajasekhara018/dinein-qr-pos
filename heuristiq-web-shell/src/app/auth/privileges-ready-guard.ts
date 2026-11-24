import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from '../shared/services/auth-service';

/**
 * Holds navigation until the signed-in user's privileges are known. Put on routes whose screens decide
 * access themselves from those privileges (the AdminWeb remote under /admin), so a direct page load does not
 * race the privileges request. Never blocks: if they cannot be fetched the screens fall back to role hints.
 */
export const privilegesReadyGuard: CanActivateFn = () => {
  return inject(AuthService).ensurePrivileges().pipe(map(() => true));
};
