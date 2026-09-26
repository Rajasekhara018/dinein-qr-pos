import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { MenuItem } from '../../../core/api/models';
import { isCustomizable, startingPrice } from '../data/cart.models';

/**
 * Menu item card: thumbnail, name, veg marker, clamped description, price ("from ₹X" for variants),
 * Add → `− qty +` stepper; unavailable items are greyed out with "Out of stock".
 */
@Component({
  selector: 'app-menu-item-card',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  templateUrl: './menu-item-card.html',
})
export class MenuItemCard {
  readonly item = input.required<MenuItem>();
  readonly quantity = input(0);
  readonly disabled = input(false, { transform: booleanAttribute });

  /** Add tapped (or "+" on customisable items): the page quick-adds or opens the sheet. */
  readonly add = output<void>();
  readonly quantityChange = output<number>();

  protected readonly customizable = computed(() => isCustomizable(this.item()));
  protected readonly price = computed(() => startingPrice(this.item()));
  protected readonly hasVariantRange = computed(() => {
    const prices = new Set(this.item().variants.map((v) => v.price));
    return prices.size > 1;
  });
}
