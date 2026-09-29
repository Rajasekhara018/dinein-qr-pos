import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CartStore } from './cart.store';
import { GuestSessionStore } from './guest-session.store';

/**
 * Entry guard for the guest app.
 * - `/menu?t=<qr token>&r=<restaurant id>` → validates the table (sets the guest cookie), then drops `t`/`r` from
 *   the URL so reloads resume from the cookie. `r` is optional: the backend checks it against the token's own
 *   restaurant when present, as a defense-in-depth check on top of the token itself.
 * - no `t` → resumes from the cookie.
 * - invalid/inactive table or no session → `/menu/scan` ("Please scan the QR code on your table").
 * - network/server error → lets the shell render an error state with Retry (the token stays in the URL).
 */
export const guestSessionGuard: CanActivateFn = async (route, state) => {
  const store = inject(GuestSessionStore);
  const cart = inject(CartStore);
  const router = inject(Router);
  const token = route.queryParamMap.get('t');
  const restaurantId = route.queryParamMap.get('r');

  if (!token && store.status() === 'ready') return true;

  const outcome = await store.start(token, restaurantId);
  if (outcome === 'invalid') return router.createUrlTree(['/menu', 'scan']);

  const table = store.table();
  if (table) cart.bindTable(table.id);

  // Keep `t`/`r` on errors so the shell's Retry can use them again.
  if (token && outcome === 'ready') {
    const tree = router.parseUrl(state.url);
    delete tree.queryParams['t'];
    delete tree.queryParams['r'];
    return tree;
  }
  return true;
};
