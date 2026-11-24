import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CartStore } from '../data/cart.store';

/** Sticky bottom bar on phones/tablets: "3 items · ₹540 — View cart", padded for the iPhone home indicator. */
@Component({
  selector: 'app-cart-bar',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block print:hidden' },
  templateUrl: './cart-bar.html',
})
export class CartBar {
  protected readonly cart = inject(CartStore);
}
