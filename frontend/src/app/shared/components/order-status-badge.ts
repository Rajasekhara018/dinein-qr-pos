import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderStatus } from '../../core/api/models';
import { orderStatusLabel, orderStatusToneClasses, StatusAudience } from '../order-status';

/** Re-exported for existing call sites (`orderStatusLabel` from this module); prefer importing from `shared/order-status`. */
export { orderStatusLabel };

/** Pill showing an order status (text + colour), from the `shared/order-status` single source of truth. */
@Component({
  selector: 'app-order-status-badge',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'classes()' },
  templateUrl: './order-status-badge.html',
})
export class OrderStatusBadge {
  readonly status = input.required<OrderStatus>();
  /** Who is reading this badge — changes wording (not just colour). Defaults to `staff` (kitchen/waiter/admin). */
  readonly audience = input<StatusAudience>('staff');

  protected readonly label = computed(() => orderStatusLabel(this.status(), this.audience()));
  protected readonly classes = computed(
    () =>
      `inline-flex items-center whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${orderStatusToneClasses(this.status(), this.audience())}`,
  );
}
