import { Injectable, inject } from '@angular/core';
import { NavigationEnd, NavigationError, Router } from '@angular/router';
import { isStaleChunkError } from '../utils/stale-bundle.helpers';

/** Session flag that stops a page that keeps failing from reloading in a loop. */
const RELOAD_FLAG_KEY = 'railcab_stale_bundle_reload';

/**
 * After a new version is deployed, a tab that was already open still asks for the old code files, which no longer
 * exist, so opening a screen fails. This reloads the page once at the screen the person was opening, which fetches
 * the new version. A second failure in the same session is left to show normally.
 */
@Injectable({ providedIn: 'root' })
export class StaleBundleRecovery {
  private readonly router = inject(Router);

  start(): void {
    this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        this.clearFlag();
      } else if (event instanceof NavigationError && isStaleChunkError(event.error) && this.markReload()) {
        window.location.assign(event.url);
      }
    });
  }

  /** True the first time in a session; false once a reload has already been tried. */
  private markReload(): boolean {
    try {
      if (sessionStorage.getItem(RELOAD_FLAG_KEY)) return false;
      sessionStorage.setItem(RELOAD_FLAG_KEY, '1');
    } catch {
      return false;
    }
    return true;
  }

  private clearFlag(): void {
    try {
      sessionStorage.removeItem(RELOAD_FLAG_KEY);
    } catch {
      // Storage can be unavailable (private mode); the reload guard then simply never re-arms.
    }
  }
}
