import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AdminOrderSummary } from '../../../../core/api/models';

/** Orders as a real table on ≥ 768px and stacked cards below. Rows link to the order detail. */
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

  protected timeOf(order: AdminOrderSummary): string | undefined {
    return order.paidAt ?? order.placedAt;
  }
}
