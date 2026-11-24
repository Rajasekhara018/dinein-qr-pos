import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { toPaise } from '../../../core/util/money';
import { BillEstimate } from '../../guest/data/cart-pricing';
import { CartLine } from '../../guest/data/cart.models';

export interface StaffLineQuantity {
  line: CartLine;
  quantity: number;
}

/**
 * Lines of a staff-assisted order with steppers, edit and remove, and the bill ESTIMATE (clearly labelled: the
 * server prices the order and its total is shown once placed).
 */
@Component({
  selector: 'app-staff-cart-panel',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  templateUrl: './staff-cart-panel.html',
})
export class StaffCartPanel {
  readonly lines = input.required<readonly CartLine[]>();
  readonly bill = input.required<BillEstimate>();
  readonly pricesIncludeGst = input(false);

  readonly quantityChange = output<StaffLineQuantity>();
  readonly edit = output<CartLine>();
  readonly removeUnavailable = output<void>();

  protected readonly hasIssues = computed(() => this.lines().some((l) => !!l.issue));

  protected lineTotal(line: CartLine): number {
    return toPaise(line.unitPrice) * line.quantity;
  }
}
