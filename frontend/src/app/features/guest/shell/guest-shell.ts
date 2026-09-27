import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  DOCUMENT,
  effect,
  inject,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { filter } from 'rxjs';
import { RealtimeEvent, TOPICS } from '../../../core/api/models';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ToastService } from '../../../core/ui/toast.service';
import { brandPalette } from '../../../core/util/color';
import { CartStore } from '../data/cart.store';
import { GuestPrefs } from '../data/guest-prefs';
import { GuestSessionStore } from '../data/guest-session.store';
import { MenuStore } from '../data/menu.store';

/**
 * Layout + lifetime of the guest app: applies the restaurant's brand colour and theme, loads the menu, keeps it
 * fresh via `/topic/menu` (MENU_UPDATED) and on every reconnect, and reconciles the cart with each new menu.
 */
@Component({
  selector: 'app-guest-shell',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.dark]': 'prefs.theme() === "dark"' },
  templateUrl: './guest-shell.html',
})
export class GuestShell {
  protected readonly session = inject(GuestSessionStore);
  protected readonly prefs = inject(GuestPrefs);
  private readonly menu = inject(MenuStore);
  private readonly cart = inject(CartStore);
  private readonly realtime = inject(RealtimeService);
  private readonly toasts = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  constructor() {
    this.applyBrandColour();
    this.applyTheme();

    effect(() => {
      if (this.session.status() === 'ready') untracked(() => void this.menu.load());
    });

    effect(() => {
      const menu = this.menu.menu();
      if (!menu) return;
      untracked(() => {
        const result = this.cart.reconcile(menu);
        if (result.unavailable > 0) {
          this.toasts.warning('Some items in your cart are no longer available.', {
            key: 'cart-reconcile',
          });
        } else if (result.repriced > 0) {
          this.toasts.info('Prices in your cart were updated.', { key: 'cart-reconcile' });
        }
      });
    });

    this.realtime
      .watch<RealtimeEvent>(TOPICS.menu)
      .pipe(
        filter((event) => event.type === 'MENU_UPDATED'),
        takeUntilDestroyed(),
      )
      .subscribe(() => void this.menu.load());

    // REST is the source of truth: refetch after every reconnect.
    this.realtime.reconnected$.pipe(takeUntilDestroyed()).subscribe(() => {
      void this.menu.load();
      void this.session.refresh();
    });
  }

  protected async retry(): Promise<void> {
    const token = this.route.snapshot.queryParamMap.get('t');
    const outcome = await this.session.start(token);
    if (outcome === 'invalid') {
      await this.router.navigate(['/menu', 'scan']);
      return;
    }
    const table = this.session.table();
    if (outcome === 'ready' && table) {
      this.cart.bindTable(table.id);
      if (token)
        await this.router.navigate([], {
          queryParams: { t: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });
    }
  }

  /** Also reflect the theme on <html> so document-level surfaces (dialogs, toasts, the print stylesheet) match. */
  private applyTheme(): void {
    const root = this.document.documentElement;
    const hadDark = root.classList.contains('dark');
    effect(() => root.classList.toggle('dark', this.prefs.theme() === 'dark'));
    inject(DestroyRef).onDestroy(() => root.classList.toggle('dark', hadDark));
  }

  private applyBrandColour(): void {
    const root = this.document.documentElement;
    const themeMeta = this.document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    const originalTheme = themeMeta?.content;
    effect((onCleanup) => {
      const palette = brandPalette(this.session.restaurant()?.brandColor);
      if (!palette) return;
      root.style.setProperty('--brand', palette.brand);
      root.style.setProperty('--brand-contrast', palette.contrast);
      root.style.setProperty('--brand-ink', palette.ink);
      if (themeMeta) themeMeta.content = palette.brand;
      onCleanup(() => {
        root.style.removeProperty('--brand');
        root.style.removeProperty('--brand-contrast');
        root.style.removeProperty('--brand-ink');
        if (themeMeta && originalTheme) themeMeta.content = originalTheme;
      });
    });
  }
}
