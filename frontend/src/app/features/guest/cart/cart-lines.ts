import { booleanAttribute, ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { toPaise } from '../../../core/util/money';
import { CartLine } from '../data/cart.models';

export interface LineQuantityChange {
  line: CartLine;
  quantity: number;
}

/** Cart lines with quantity steppers, edit and remove; highlights lines that can no longer be ordered. */
@Component({
  selector: 'app-cart-lines',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './cart-lines.html',
})
export class CartLines {
  readonly lines = input.required<readonly CartLine[]>();
  readonly compact = input(false, { transform: booleanAttribute });
  readonly editable = input(true, { transform: booleanAttribute });

  readonly edit = output<CartLine>();
  readonly remove = output<CartLine>();
  readonly quantityChange = output<LineQuantityChange>();

  protected lineTotal(line: CartLine): number {
    return toPaise(line.unitPrice) * line.quantity;
  }
}
