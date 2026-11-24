import { inject } from '@angular/core';
import { CanActivateFn, Router, ActivatedRouteSnapshot, UrlTree } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HQ_PERMISSION_PRIVILEGES } from 'hq-shared-lib';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService } from '../shared/services/auth-service';
import { NotificationService } from '../shared/services/notification-service';

/**
 * Lets the user in when they hold a privilege behind {@code permission}; otherwise sends them home and says which privilege
 * the screen needs. Must run in an injection context (a guard). Shared by permissionGuard and the guards of federated portals
 * whose own routes carry no permission key (inventoryScreenGuard).
 */
export function checkScreenPermission(permission: string | undefined | null): true | Observable<true | UrlTree> {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notify = inject(NotificationService);
  const translate = inject(TranslateService);

  if (!permission) {
    return true;
  }
  // On a direct load the privileges may not have arrived yet; wait for them rather than deciding too early.
  return auth.ensurePrivileges().pipe(
    map(() => {
      if (auth.canAccess(permission)) {
        return true as const;
      }
      const needed = HQ_PERMISSION_PRIVILEGES[permission] ?? [];
      notify.error(translate.instant('shared.errors.noAccess', { codes: needed.join(', ') || permission }));
      return router.createUrlTree(['/today']);
    }),
  );
}

/**
 * Lets the user in when they hold a privilege behind {@code data.permission}; otherwise sends them home and says which
 * privilege the screen needs, so a missing grant is something a person can see rather than a silent redirect.
 */
export const permissionGuard: CanActivateFn = (route: ActivatedRouteSnapshot) =>
  checkScreenPermission(route.data['permission'] as string | undefined);
