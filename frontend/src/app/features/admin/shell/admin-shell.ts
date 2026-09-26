import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { ADMIN_PATHS } from '../../../core/auth/auth-paths';
import { AuthStore } from '../../../core/auth/auth.store';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { BreakpointService } from '../../../core/ui/breakpoint.service';
import { SafeStorage } from '../../../core/util/storage';
import { visibleNav } from '../data/admin-nav';
import { AdminNotificationsStore } from '../data/notifications.store';

const COLLAPSED_KEY = 'dinein.admin.sidebarCollapsed.v1';
/** setTimeout's max delay (≈ 24.8 days). */
const MAX_TIMER = 2_147_483_647;

/**
 * Admin layout: collapsible sidebar on ≥ 1024px, hamburger drawer below; header with the notifications bell and
 * user menu; "Reconnecting…" banner. Owns the admin realtime session (STOMP auth header, notifications) and keeps
 * the access token fresh while the panel is open.
 */
@Component({
  selector: 'app-admin-shell',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-shell.html',
})
export class AdminShell {
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly realtime = inject(RealtimeService);
  private readonly storage = inject(SafeStorage);
  private readonly notifications = inject(AdminNotificationsStore);
  protected readonly isDesktop = inject(BreakpointService).isDesktop;

  protected readonly nav = computed(() => visibleNav(this.auth.isOwner()));
  protected readonly collapsed = signal(this.storage.getItem(COLLAPSED_KEY) === '1');
  protected readonly drawerOpen = signal(false);
  protected readonly userMenuOpen = signal(false);
  private loggingOut = false;

  protected readonly displayName = computed(() => {
    const user = this.auth.user();
    return user?.displayName || user?.username || '';
  });
  protected readonly roleLabel = computed(() =>
    this.auth.role() === 'OWNER' ? 'Owner' : this.auth.role() === 'MANAGER' ? 'Manager' : '',
  );
  protected readonly initials = computed(() =>
    this.displayName()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join(''),
  );

  /** Same function instance for the lifetime of the shell (changing it forces a reconnect). */
  private readonly authHeader = (): string | null => {
    const token = this.auth.accessToken();
    return token ? `Bearer ${token}` : null;
  };

  constructor() {
    this.realtime.setAuthProvider(this.authHeader);
    this.notifications.start();

    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.drawerOpen.set(false);
        this.userMenuOpen.set(false);
      });

    // Proactive silent refresh ~1 min before the access token expires, so live views keep working.
    effect((onCleanup) => {
      const expiresAt = this.auth.expiresAt();
      if (!expiresAt) return;
      const delay = Math.min(MAX_TIMER, Math.max(5_000, expiresAt - Date.now() - 60_000));
      const timer = setTimeout(() => void this.auth.refresh(), delay);
      onCleanup(() => clearTimeout(timer));
    });

    // Session gone (refresh failed / revoked elsewhere) → back to the login page.
    effect(() => {
      if (!this.auth.isAuthenticated() && !this.loggingOut) {
        void this.router.navigate([ADMIN_PATHS.login], {
          queryParams: { returnUrl: this.router.url },
        });
      }
    });

    inject(DestroyRef).onDestroy(() => {
      this.notifications.stop();
      void this.realtime.disconnect().then(() => this.realtime.setAuthProvider(null));
    });
  }

  protected toggleCollapsed(): void {
    const next = !this.collapsed();
    this.collapsed.set(next);
    this.storage.setItem(COLLAPSED_KEY, next ? '1' : '0');
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected async logout(): Promise<void> {
    this.loggingOut = true;
    this.userMenuOpen.set(false);
    this.notifications.stop();
    await this.realtime.disconnect();
    await this.auth.logout();
    await this.router.navigateByUrl(ADMIN_PATHS.login);
  }

  protected onUserMenuOutside(event: MouseEvent, trigger: HTMLElement): void {
    if (!trigger.contains(event.target as Node)) this.userMenuOpen.set(false);
  }
}
