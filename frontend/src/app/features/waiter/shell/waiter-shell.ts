import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  DOCUMENT,
  effect,
  inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { WAITER_PATHS } from '../../../core/auth/auth-paths';
import { AuthStore } from '../../../core/auth/auth.store';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { WaiterAlerts } from '../data/waiter-alerts';
import { WaiterBoardStore } from '../data/waiter-board.store';
import { WaiterNotificationsStore } from '../data/waiter-notifications.store';
import { WaiterPrefs } from '../data/waiter-prefs';

/** setTimeout's max delay (≈ 24.8 days). */
const MAX_TIMER = 2_147_483_647;

export interface WaiterNavItem {
  label: string;
  path: string;
  exact: boolean;
  icon: string;
  testId: string;
}

export const WAITER_NAV: readonly WaiterNavItem[] = [
  { label: 'Ready', path: '/waiter', exact: true, testId: 'nav-ready', icon: 'm5 12 5 5L20 7' },
  {
    label: 'Active',
    path: '/waiter/active',
    exact: false,
    testId: 'nav-active',
    icon: 'M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 5h6M9 12h6M9 16h4',
  },
  {
    label: 'Tables',
    path: '/waiter/tables',
    exact: false,
    testId: 'nav-tables',
    icon: 'M4 4h7v7H4V4Zm9 0h7v7h-7V4ZM4 13h7v7H4v-7Zm9 0h7v7h-7v-7Z',
  },
  {
    label: 'New order',
    path: '/waiter/new',
    exact: false,
    testId: 'nav-new',
    icon: 'M12 5v14M5 12h14',
  },
];

/**
 * Waiter app layout (mobile first): header (restaurant, staff, connection, bell, sound, sign out), the tab bar
 * (bottom on phones, top from 768px) and the routed tab. Owns the waiter's realtime session (STOMP auth header,
 * live orders, notifications) and keeps the access token fresh while the screen is open.
 */
@Component({
  selector: 'app-waiter-shell',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [WaiterBoardStore, WaiterNotificationsStore],
  host: {
    class: 'block min-h-dvh bg-bg text-ink',
    '[class.dark]': 'prefs.theme() === "dark"',
    '(document:pointerdown)': 'onGesture()',
  },
  templateUrl: './waiter-shell.html',
})
export class WaiterShell {
  protected readonly board = inject(WaiterBoardStore);
  protected readonly prefs = inject(WaiterPrefs);
  private readonly notifications = inject(WaiterNotificationsStore);
  private readonly alerts = inject(WaiterAlerts);
  private readonly auth = inject(AuthStore);
  private readonly realtime = inject(RealtimeService);
  private readonly router = inject(Router);
  private readonly confirmSvc = inject(ConfirmService);

  protected readonly nav = WAITER_NAV;
  private loggingOut = false;

  /** Same function instance for the shell's lifetime (changing it forces a reconnect). */
  private readonly authHeader = (): string | null => {
    const token = this.auth.accessToken();
    return token ? `Bearer ${token}` : null;
  };

  constructor() {
    this.realtime.setAuthProvider(this.authHeader);
    this.board.start();
    this.notifications.start();
    void this.alerts.probe();

    // Also reflect the theme on <html> so document-level surfaces (dialogs, toasts) match.
    const root = inject(DOCUMENT).documentElement;
    const hadDark = root.classList.contains('dark');
    effect(() => root.classList.toggle('dark', this.prefs.theme() === 'dark'));
    inject(DestroyRef).onDestroy(() => root.classList.toggle('dark', hadDark));

    this.board.readyArrived$
      .pipe(takeUntilDestroyed())
      .subscribe(() => void this.alerts.orderReady());

    // Proactive silent refresh ~1 min before the access token expires, so live updates keep working.
    effect((onCleanup) => {
      const expiresAt = this.auth.expiresAt();
      if (!expiresAt) return;
      const delay = Math.min(MAX_TIMER, Math.max(5_000, expiresAt - Date.now() - 60_000));
      const timer = setTimeout(() => void this.auth.refresh(), delay);
      onCleanup(() => clearTimeout(timer));
    });

    // Session gone (refresh failed, revoked elsewhere) → back to the login.
    effect(() => {
      if (!this.auth.isAuthenticated() && !this.loggingOut) {
        void this.router.navigate([WAITER_PATHS.login], {
          queryParams: { returnUrl: this.router.url },
        });
      }
    });

    inject(DestroyRef).onDestroy(() => {
      this.notifications.stop();
      void this.realtime.disconnect().then(() => this.realtime.setAuthProvider(null));
    });
  }

  protected onGesture(): void {
    void this.alerts.resumeFromGesture();
  }

  async signOut(): Promise<void> {
    const result = await this.confirmSvc.confirm({
      title: 'Sign out?',
      message: 'Sign out of the waiter screen?',
      confirmLabel: 'Sign out',
      tone: 'primary',
    });
    if (!result) return;
    this.loggingOut = true;
    this.notifications.stop();
    void this.realtime.disconnect();
    await this.auth.logout();
    await this.router.navigateByUrl(WAITER_PATHS.login);
  }
}
