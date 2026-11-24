import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { DEFAULT_INTERRUPTSOURCES, Idle } from '@ng-idle/core';
import { BehaviorSubject } from 'rxjs';
import { HqIdleTimeoutDialog, HqIdleTimeoutDialogData, HqIdleTimeoutDialogResult } from 'hq-shared-lib';
import { AuthService } from './auth-service';
import { IDLE_TIMEOUT_CONFIG, IDLE_TIMEOUT_PANEL_CLASS } from './idle-timeout.constants';
import { isPublicPath } from '../public-routes.helpers';

/**
 * Signs the user out after a period of inactivity. While a user is signed in it watches for
 * activity; after {@link IDLE_TIMEOUT_CONFIG.idleSeconds} of none it opens a warning with a
 * countdown, and if the user does not choose "stay signed in" within
 * {@link IDLE_TIMEOUT_CONFIG.warningSeconds} the session is ended and a "timed out" notice is shown.
 *
 * Once the warning is open, mouse or keyboard activity no longer resets it: the user must choose
 * explicitly, so a stray mouse move cannot silently keep an unattended session alive.
 */
@Injectable({ providedIn: 'root' })
export class IdleTimeoutService {
  private readonly idle = inject(Idle);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  private readonly secondsLeft$ = new BehaviorSubject<number>(IDLE_TIMEOUT_CONFIG.warningSeconds);
  private warningRef: MatDialogRef<HqIdleTimeoutDialog, HqIdleTimeoutDialogResult> | null = null;
  private started = false;

  /** Idempotent: wires the idle watcher to the signed-in state. Call once at app start. */
  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;

    this.idle.setIdle(IDLE_TIMEOUT_CONFIG.idleSeconds);
    this.idle.setTimeout(IDLE_TIMEOUT_CONFIG.warningSeconds);

    this.idle.onIdleStart.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.openWarning());
    this.idle.onTimeoutWarning
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((seconds) => this.secondsLeft$.next(seconds));
    this.idle.onTimeout.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.expire());

    this.auth.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.auth.isAuthenticated()) {
        // A background token refresh re-emits the user; that is not user activity, so it must
        // not restart a watcher that is already counting down.
        if (!this.idle.isRunning()) {
          this.watch();
        }
      } else {
        this.stopWatching();
      }
    });
  }

  private watch(): void {
    this.idle.setInterrupts(DEFAULT_INTERRUPTSOURCES);
    this.idle.watch();
  }

  private stopWatching(): void {
    this.idle.stop();
    this.closeWarning();
  }

  private openWarning(): void {
    if (!this.auth.isAuthenticated()) {
      this.stopWatching();
      return;
    }
    // A public page (the shared-trip page) uses no session, so this tab is not working in it: no warning and no
    // sign-out redirect there; the countdown starts again and applies once the tab is back in the portal.
    if (isPublicPath(this.router.url)) {
      this.watch();
      return;
    }
    // Activity must not dismiss the warning (see class doc): only an explicit choice does.
    this.idle.clearInterrupts();
    this.secondsLeft$.next(IDLE_TIMEOUT_CONFIG.warningSeconds);
    this.closeWarning();

    const data: HqIdleTimeoutDialogData = { mode: 'warning', secondsLeft$: this.secondsLeft$.asObservable() };
    this.warningRef = this.dialog.open<HqIdleTimeoutDialog, HqIdleTimeoutDialogData, HqIdleTimeoutDialogResult>(
      HqIdleTimeoutDialog,
      { disableClose: true, autoFocus: 'dialog', panelClass: IDLE_TIMEOUT_PANEL_CLASS, data },
    );
    const ref = this.warningRef;
    ref.afterClosed().subscribe((choice) => {
      if (this.warningRef !== ref) {
        return; // closed by the service itself (timeout or sign-out), not by the user
      }
      this.warningRef = null;
      if (choice === 'logout') {
        this.idle.stop();
        this.auth.logout();
      } else {
        this.watch();
      }
    });
  }

  private expire(): void {
    this.closeWarning();
    this.idle.stop();
    if (!this.auth.isAuthenticated()) {
      return;
    }
    this.auth.logout();
    this.dialog.open<HqIdleTimeoutDialog, HqIdleTimeoutDialogData>(HqIdleTimeoutDialog, {
      disableClose: true,
      autoFocus: 'dialog',
      panelClass: IDLE_TIMEOUT_PANEL_CLASS,
      data: { mode: 'expired' },
    });
  }

  private closeWarning(): void {
    const ref = this.warningRef;
    this.warningRef = null;
    ref?.close();
  }
}
