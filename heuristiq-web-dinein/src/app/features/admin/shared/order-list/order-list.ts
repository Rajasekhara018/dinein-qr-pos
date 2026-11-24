import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AdminOrderSummary } from '../../../../core/api/models';
import { paymentChannelLabel } from '../../../../core/util/order-labels';

/**
 * Orders as a real table on ≥ 768px and stacked cards below. Rows link to the order detail.
 *
 * Pagination is optional: when the caller (the orders list page) passes `page`/`pageCount`/`total`/`pageSize`,
 * a pager is rendered as part of the same card (embedded in the desktop table, its own small card on mobile)
 * instead of floating separately below. Callers that just want a plain list (e.g. the dashboard's "recent
 * orders" widget) leave these unset and get no pager at all.
 */
@Component({
  selector: 'app-order-list',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  templateUrl: './order-list.html',
})
export class OrderList {
  readonly orders = input.required<readonly AdminOrderSummary[]>();
  readonly caption = input('Orders');
  /** Show the date as well as the time (multi-day lists). */
  readonly showDate = input(false);

  readonly page = input<number | null>(null);
  readonly pageCount = input(1);
  readonly total = input(0);
  readonly pageSize = input(1);
  readonly pageSizeOptions = input<readonly number[]>([]);
  readonly pageChange = output<number>();
  readonly pageSizeChange = output<number>();

  /** "Cash at counter", "UPI at counter", "Card at counter" or the gateway (e.g. "Razorpay · UPI"). */
  protected paidWith(order: AdminOrderSummary): string {
    if (!order.paymentProvider && !order.paymentMethod) return '';
    return paymentChannelLabel(order.paymentProvider, order.paymentMethod);
  }

  protected timeOf(order: AdminOrderSummary): string | undefined {
    return order.paidAt ?? order.placedAt;
  }
}
