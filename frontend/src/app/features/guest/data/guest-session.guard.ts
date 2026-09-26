import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CartStore } from './cart.store';
import { GuestSessionStore } from './guest-session.store';

/**
 * Entry guard for the guest app.
 * - `/menu?t=<qr token>` → validates the table (sets the guest cookie), then drops `t` from the URL so reloads
 *   resume from the cookie.
 * - no `t` → resumes from the cookie.
 * - invalid/inactive table or no session → `/menu/scan` ("Please scan the QR code on your table").
 * - network/server error → lets the shell render an error state with Retry (the token stays in the URL).
 */
export const guestSessionGuard: CanActivateFn = async (route, state) => {
  const store = inject(GuestSessionStore);
  const cart = inject(CartStore);
  const router = inject(Router);
  const token = route.queryParamMap.get('t');

  if (!token && store.status() === 'ready') return true;

  const outcome = await store.start(token);
  if (outcome === 'invalid') return router.createUrlTree(['/menu', 'scan']);

  const table = store.table();
  if (table) cart.bindTable(table.id);

  // Keep `t` on errors so the shell's Retry can use it again.
  if (token && outcome === 'ready') {
    const tree = router.parseUrl(state.url);
    delete tree.queryParams['t'];
    return tree;
  }
  return true;
};
