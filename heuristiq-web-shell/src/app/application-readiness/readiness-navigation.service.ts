import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { NAV_ITEMS } from '../layout/nav-items.constants';
import { AuthService } from '../shared/services/auth-service';
import { navPermissionFor } from './application-readiness.helpers';
import { ReadinessFixLink } from './application-readiness.models';

/**
 * Where readiness buttons lead. A Fix button is shown only when the user may open its screen: the menu entry owning the path
 * is checked with the same screen permissions the sidebar uses. A path no menu entry owns cannot be checked and is allowed
 * (its own route guard still decides).
 */
@Injectable({ providedIn: 'root' })
export class ReadinessNavigationService {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  canOpen(path: string): boolean {
    const permission = navPermissionFor(path, NAV_ITEMS);
    return !permission || this.auth.canAccess(permission);
  }

  /** Opens a fix screen with its query parameters (for example `?application=<CODE>`). */
  open(link: ReadinessFixLink): Promise<boolean> {
    return this.router.navigateByUrl(this.router.createUrlTree([link.path], { queryParams: link.queryParams }));
  }
}
