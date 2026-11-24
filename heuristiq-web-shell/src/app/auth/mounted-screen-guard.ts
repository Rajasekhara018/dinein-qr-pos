import { ActivatedRouteSnapshot, CanActivateChildFn } from '@angular/router';
import { checkScreenPermission } from './permission-guard';

/**
 * A canActivateChild guard for a federated portal whose own routes carry no permission keys (inventory, food): it checks the
 * permission key of the screen being opened, named by the first URL segment under the mount (`/food/orders/7/edit` -> `orders`).
 * Only the route that consumes that segment is checked (a static path such as `items`, or a parameter such as `:resource`), so a
 * screen is checked once per navigation; deeper routes, segments with no key and routes outside the mount pass.
 */
export function mountedScreenGuard(mountPath: string, permissions: Readonly<Record<string, string>>): CanActivateChildFn {
  return (childRoute: ActivatedRouteSnapshot) => {
    const chain = childRoute.pathFromRoot ?? [];
    const mountIndex = chain.findIndex((route) => route.routeConfig?.path === mountPath);
    const segment = childRoute.url?.[0]?.path;
    if (mountIndex < 0 || !segment) {
      return true;
    }
    // A route between the mount and this one already consumed a segment: this is a deeper route of a screen checked before.
    if (chain.slice(mountIndex + 1, -1).some((route) => (route.url?.length ?? 0) > 0)) {
      return true;
    }
    const permission = Object.prototype.hasOwnProperty.call(permissions, segment) ? permissions[segment] : null;
    return permission ? checkScreenPermission(permission) : true;
  };
}
