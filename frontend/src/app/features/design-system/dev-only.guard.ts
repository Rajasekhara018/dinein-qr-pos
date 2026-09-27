import { isDevMode } from '@angular/core';
import { CanActivateFn } from '@angular/router';

/**
 * Keeps `/design-system` out of production. `isDevMode()` is Angular's own dev/prod signal — it is `false` once the
 * app is built with the `production` configuration (`ng build`, no `--configuration development`), because that
 * build strips `ngDevMode`. We chose this over a `environment.ts`/`fileReplacements` split (the project has none
 * today; see docs/DECISIONS.md) — it needs no new build config and the redirect happens at the route level, so the
 * page's code is still lazy-loaded and never touches the initial bundle regardless of environment.
 */
export const devOnlyGuard: CanActivateFn = () => {
  if (isDevMode()) return true;
  // Production: bounce to the guest menu, the app's default route.
  return '/menu';
};
