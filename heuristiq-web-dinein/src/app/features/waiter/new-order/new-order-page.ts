import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderType } from '../../../core/api/models';

/**
 * `/waiter/new?table=<id>` or `?type=TAKEAWAY`: the shared staff ordering flow in waiter mode. The flow is re-created
 * when the query changes (e.g. tapping another table), so it always starts from the chosen table.
 */
@Component({
  selector: 'app-waiter-new-order-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './new-order-page.html',
})
export class WaiterNewOrderPage {
  /** Query params (bound by the router). */
  readonly table = input<string | undefined>();
  readonly type = input<string | undefined>();

  protected readonly tableId = computed(() => {
    const id = Number(this.table());
    return this.table() && Number.isInteger(id) && id > 0 ? id : null;
  });
  protected readonly orderType = computed<OrderType | null>(() =>
    this.type() === 'TAKEAWAY' ? 'TAKEAWAY' : null,
  );
  protected readonly flowKey = computed(() => `${this.tableId() ?? ''}|${this.orderType() ?? ''}`);
}
