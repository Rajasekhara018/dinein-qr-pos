import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderStatus } from '../../core/api/models';

const META: Record<OrderStatus, { label: string; classes: string }> = {
  PENDING_PAYMENT: { label: 'Awaiting payment', classes: 'bg-amber-100 text-amber-900 dark:bg-amber-900 dark:text-amber-50' },
  CONFIRMED: { label: 'Paid', classes: 'bg-blue-100 text-blue-900 dark:bg-blue-900 dark:text-blue-50' },
  PREPARING: { label: 'Preparing', classes: 'bg-orange-100 text-orange-900 dark:bg-orange-900 dark:text-orange-50' },
  READY: { label: 'Ready', classes: 'bg-green-100 text-green-900 dark:bg-green-900 dark:text-green-50' },
  COMPLETED: { label: 'Served', classes: 'bg-stone-200 text-stone-800 dark:bg-stone-700 dark:text-stone-100' },
  EXPIRED: { label: 'Expired', classes: 'bg-stone-200 text-stone-700 dark:bg-stone-700 dark:text-stone-200' },
  PAYMENT_FAILED: { label: 'Payment failed', classes: 'bg-red-100 text-red-900 dark:bg-red-900 dark:text-red-50' },
  CANCELLED: { label: 'Cancelled', classes: 'bg-red-100 text-red-900 dark:bg-red-900 dark:text-red-50' },
};

export function orderStatusLabel(status: OrderStatus): string {
  return META[status]?.label ?? status;
}

/** Pill showing an order status (text + colour). */
@Component({
  selector: 'app-order-status-badge',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'classes()' },
  template: '{{ label() }}',
})
export class OrderStatusBadge {
  readonly status = input.required<OrderStatus>();

  protected readonly label = computed(() => orderStatusLabel(this.status()));
  protected readonly classes = computed(
    () =>
      `inline-flex items-center whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${META[this.status()]?.classes ?? ''}`,
  );
}
