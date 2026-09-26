import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CartLine } from '../data/cart.models';
import { CartStore } from '../data/cart.store';
import { MenuStore } from '../data/menu.store';
import { ItemSheetLauncher } from '../item-sheet/item-sheet-launcher';
import { LineQuantityChange } from '../cart/cart-lines';

/** Persistent cart on desktop (≥ 1024px) next to the menu grid. */
@Component({
  selector: 'app-cart-sidebar',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './cart-sidebar.html',
})
export class CartSidebar {
  protected readonly cart = inject(CartStore);
  private readonly menu = inject(MenuStore);
  private readonly sheet = inject(ItemSheetLauncher);

  protected edit(line: CartLine): void {
    const item = this.menu.itemsById().get(line.itemId);
    if (item) void this.sheet.open(item, line);
  }

  protected setQuantity(change: LineQuantityChange): void {
    this.cart.setQuantity(change.line.key, change.quantity);
  }
}
