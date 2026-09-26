import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CartStore } from '../data/cart.store';
import { GuestSessionStore } from '../data/guest-session.store';

@Component({
  selector: 'app-guest-header',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block border-b border-line bg-surface' },
  template: `
    <header class="mx-auto flex max-w-7xl items-center gap-3 px-4 py-3 lg:px-6">
      <a routerLink="/menu" class="flex min-h-touch min-w-0 flex-1 items-center gap-3 rounded-control">
        @if (restaurant()?.logoUrl; as logo) {
          <img [ngSrc]="logo" width="44" height="44" priority alt="" class="size-11 shrink-0 rounded-full border border-line object-cover" />
        } @else {
          <span class="flex size-11 shrink-0 items-center justify-center rounded-full bg-brand font-display text-lg font-bold text-brand-contrast" aria-hidden="true">
            {{ initial() }}
          </span>
        }
        <span class="min-w-0">
          <span class="block truncate font-display text-lg font-semibold leading-tight sm:text-xl">{{ restaurant()?.name }}</span>
          @if (table(); as t) {
            <span class="block text-sm text-ink-muted">Table <strong class="font-semibold text-ink">{{ t.label }}</strong></span>
          }
        </span>
      </a>
      <nav aria-label="Guest" class="flex shrink-0 items-center gap-1">
        <a
          routerLink="/menu/orders"
          routerLinkActive="bg-surface-muted"
          class="inline-flex min-h-touch items-center gap-2 rounded-control px-3 text-sm font-semibold text-ink hover:bg-surface-muted"
        >
          <svg viewBox="0 0 24 24" class="size-5" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <path d="M8 4h8l1 2h2v14H5V6h2l1-2Z" /><path d="M9 11h6M9 15h4" />
          </svg>
          <span class="hidden sm:inline">My orders</span>
          <span class="sr-only sm:hidden">My orders</span>
        </a>
        <a
          routerLink="/menu/cart"
          class="relative inline-flex size-touch items-center justify-center rounded-control hover:bg-surface-muted lg:hidden"
          [attr.aria-label]="'Cart, ' + cart.itemCount() + ' items'"
        >
          <svg viewBox="0 0 24 24" class="size-6" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <path d="M3 4h2l2.4 11.2a2 2 0 0 0 2 1.6h7.7a2 2 0 0 0 2-1.5L21 8H6.2" /><circle cx="10" cy="20" r="1.3" /><circle cx="17" cy="20" r="1.3" />
          </svg>
          @if (cart.itemCount() > 0) {
            <span class="absolute right-0.5 top-0.5 min-w-5 rounded-full bg-brand px-1 text-center text-xs font-bold leading-5 text-brand-contrast" aria-hidden="true">
              {{ cart.itemCount() }}
            </span>
          }
        </a>
      </nav>
    </header>
  `,
})
export class GuestHeader {
  private readonly session = inject(GuestSessionStore);
  protected readonly cart = inject(CartStore);
  protected readonly restaurant = this.session.restaurant;
  protected readonly table = this.session.table;
  protected readonly initial = computed(() => (this.restaurant()?.name ?? 'D').trim().charAt(0).toUpperCase());
}
