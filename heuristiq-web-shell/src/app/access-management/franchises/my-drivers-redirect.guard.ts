import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, catchError, map, of } from 'rxjs';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { FranchiseApi } from './franchise-api';
import { FRANCHISE_DRIVERS_ROUTE, MY_FRANCHISES_ROUTE } from './franchise-drivers.constants';

/**
 * The "My drivers" menu entry has no franchise in its address, so it never activates: it sends an owner with exactly one
 * franchise straight to that franchise's drivers, and anyone else (several franchises, none, or the list unreadable) to My
 * franchises, where each franchise has its own Drivers action. The drivers screen and My franchises keep their own guards.
 */
export const myDriversRedirectGuard: CanActivateFn = (): Observable<UrlTree> => {
  const router = inject(Router);
  const myFranchises = router.createUrlTree([ACCESS_MANAGEMENT_BASE_PATH, MY_FRANCHISES_ROUTE]);
  return inject(FranchiseApi).mine().pipe(
    map((franchises) => (franchises.length === 1
      ? router.createUrlTree([ACCESS_MANAGEMENT_BASE_PATH, 'franchises', franchises[0].unitId, FRANCHISE_DRIVERS_ROUTE])
      : myFranchises)),
    catchError(() => of(myFranchises)),
  );
};
