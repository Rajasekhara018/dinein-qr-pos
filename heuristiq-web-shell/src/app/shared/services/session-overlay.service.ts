import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { HqIdleTimeoutDialog } from 'hq-shared-lib';

import { SIGNED_OUT_ROUTE_PREFIX } from './session-overlay.constants';

/**
 * Closes what the ended session left on screen. Material dialogs and snackbars live in the page-level overlay, not in the
 * screen that opened them, so they survive the navigation to the sign-in page (idle timeout, refresh refused, sign-out
 * in another tab): a review dialog of the old session stayed open over the login form. Whenever the app arrives on a
 * signed-out route, every open dialog (this app's and Rider_AdminWeb's alike: the remote's dialogs share the host's root
 * dialog list) is closed and the snackbar dismissed. The idle-timeout "you were signed out" notice is kept: it is opened
 * on purpose right after signing out. `hq-modal` / `hq-drawer` need nothing: they close with their screen.
 */
@Injectable({ providedIn: 'root' })
export class SessionOverlayService {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  /** Starts watching navigation; called once at app start. */
  start(): void {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        filter((event) => event.urlAfterRedirects.startsWith(SIGNED_OUT_ROUTE_PREFIX)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.closeSessionOverlays());
  }

  /** Closes every open dialog except the idle-timeout notice, and dismisses the snackbar. */
  closeSessionOverlays(): void {
    for (const ref of [...this.dialog.openDialogs]) {
      if (ref.componentInstance instanceof HqIdleTimeoutDialog) continue;
      ref.close();
    }
    this.snackBar.dismiss();
  }
}
