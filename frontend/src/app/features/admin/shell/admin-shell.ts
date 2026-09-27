import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { AdminSettingsApi } from '../../../core/api/admin.api';
import { SettingsResponse } from '../../../core/api/models';
import { ADMIN_PATHS } from '../../../core/auth/auth-paths';
import { AuthStore } from '../../../core/auth/auth.store';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ToastService } from '../../../core/ui/toast.service';
import { BreakpointService } from '../../../core/ui/breakpoint.service';
import { SafeStorage } from '../../../core/util/storage';
import { errorMessage } from '../shared/form-errors';
import { AdminPrefs } from '../data/admin-prefs';
import { visibleNavGroups } from '../data/admin-nav';
import { AdminNotificationsStore } from '../data/notifications.store';
import { settingsResponseToRequest } from '../settings/settings-form';

const COLLAPSED_KEY = 'dinein.admin.sidebarCollapsed.v1';
/** setTimeout's max delay (≈ 24.8 days). */
const MAX_TIMER = 2_147_483_647;

/**
 * Admin layout: collapsible sidebar on ≥ 1024px, hamburger drawer below; header with breadcrumbs, quick-navigate
 * (command palette), "Accepting orders" switch, theme toggle, notifications bell and user menu; "Reconnecting…"
 * banner. Owns the admin realtime session (STOMP auth header, notifications) and keeps the access token fresh
 * while the panel is open.
 */
@Component({
  selector: 'app-admin-shell',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'admin-scope font-admin-sans block min-h-dvh' },
  templateUrl: './admin-shell.html',
})
export class AdminShell {
  protected readonly auth = inject(AuthStore);
  protected readonly prefs = inject(AdminPrefs);
  private readonly router = inject(Router);
  private readonly realtime = inject(RealtimeService);
  private readonly storage = inject(SafeStorage);
  private readonly notifications = inject(AdminNotificationsStore);
  private readonly settingsApi = inject(AdminSettingsApi);
  private readonly toasts = inject(ToastService);
  protected readonly isDesktop = inject(BreakpointService).isDesktop;

  protected readonly navGroups = computed(() => visibleNavGroups(this.auth.isOwner()));
  protected readonly collapsed = signal(this.storage.getItem(COLLAPSED_KEY) === '1');
  protected readonly drawerOpen = signal(false);
  protected readonly userMenuOpen = signal(false);
  protected readonly paletteOpen = signal(false);
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

  /** Breadcrumb trail from the current route's `data.breadcrumb` chain, dot-separated, skipping the current page
   *  (its own title is the `<h1>` from `PageHeader`, not repeated here). */
  protected readonly breadcrumbs = signal<string[]>([]);

  /** `null` while loading, or when the account can't read settings (manager). */
  protected readonly acceptingOrders = signal<boolean | null>(null);
  protected readonly acceptingOrdersBusy = signal(false);
  private lastSettings: SettingsResponse | null = null;

  /** Same function instance for the lifetime of the shell (changing it forces a reconnect). */
  private readonly authHeader = (): string | null => {
    const token = this.auth.accessToken();
    return token ? `Bearer ${token}` : null;
  };

  constructor() {
    this.realtime.setAuthProvider(this.authHeader);
    this.notifications.start();

    if (this.auth.isOwner()) {
      this.settingsApi.settings().subscribe({
        next: (s) => {
          this.lastSettings = s;
          this.acceptingOrders.set(s.acceptingOrders);
        },
        // Managers get 403 here; the switch simply stays hidden (acceptingOrders is left null).
        error: () => undefined,
      });
    }

    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.drawerOpen.set(false);
        this.userMenuOpen.set(false);
        this.breadcrumbs.set(this.computeBreadcrumbs());
      });
    this.breadcrumbs.set(this.computeBreadcrumbs());

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

  private computeBreadcrumbs(): string[] {
    const trail: string[] = [];
    let node: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    while (node) {
      const crumb = node.data['breadcrumb'] as string | undefined;
      if (crumb) trail.push(crumb);
      node = node.firstChild;
    }
    // The last crumb duplicates the page's own <h1> (PageHeader title) — skip it.
    return trail.slice(0, -1);
  }

  @HostListener('document:keydown', ['$event'])
  protected onGlobalKeydown(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.paletteOpen.set(true);
    }
  }

  protected toggleCollapsed(): void {
    const next = !this.collapsed();
    this.collapsed.set(next);
    this.storage.setItem(COLLAPSED_KEY, next ? '1' : '0');
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected toggleAcceptingOrders(): void {
    const current = this.lastSettings;
    if (!current || this.acceptingOrdersBusy()) return;
    const next = !current.acceptingOrders;
    this.acceptingOrdersBusy.set(true);
    this.acceptingOrders.set(next);
    this.settingsApi.updateSettings(settingsResponseToRequest(current, { acceptingOrders: next })).subscribe({
      next: (s: SettingsResponse) => {
        this.lastSettings = s;
        this.acceptingOrders.set(s.acceptingOrders);
        this.acceptingOrdersBusy.set(false);
        this.toasts.success(s.acceptingOrders ? 'Now accepting orders.' : 'Ordering paused.');
      },
      error: (error: unknown) => {
        this.acceptingOrders.set(current.acceptingOrders);
        this.acceptingOrdersBusy.set(false);
        this.toasts.error(errorMessage(error, 'Could not change ordering status.'));
      },
    });
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
