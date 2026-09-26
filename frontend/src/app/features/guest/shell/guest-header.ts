import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CartStore } from '../data/cart.store';
import { GuestSessionStore } from '../data/guest-session.store';

@Component({
  selector: 'app-guest-header',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block border-b border-line bg-surface' },
  templateUrl: './guest-header.html',
})
export class GuestHeader {
  private readonly session = inject(GuestSessionStore);
  protected readonly cart = inject(CartStore);
  protected readonly restaurant = this.session.restaurant;
  protected readonly table = this.session.table;
  protected readonly initial = computed(() =>
    (this.restaurant()?.name ?? 'D').trim().charAt(0).toUpperCase(),
  );
}
