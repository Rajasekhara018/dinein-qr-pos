import { booleanAttribute, ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderType } from '../../core/api/models';
import { orderTypeLabel } from '../../core/util/order-labels';

/**
 * "Dine-in" / "Takeaway" pill. Takeaway is high-contrast (it changes how the food is packed); dine-in is muted and
 * can be hidden with `hideDineIn` where it is the obvious default.
 */
@Component({
  selector: 'app-order-type-badge',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex' },
  templateUrl: './order-type-badge.html',
})
export class OrderTypeBadge {
  readonly type = input<OrderType | null | undefined>('DINE_IN');
  readonly hideDineIn = input(false, { transform: booleanAttribute });

  protected readonly takeaway = computed(() => this.type() === 'TAKEAWAY');
  protected readonly label = computed(() => orderTypeLabel(this.type()));
}
