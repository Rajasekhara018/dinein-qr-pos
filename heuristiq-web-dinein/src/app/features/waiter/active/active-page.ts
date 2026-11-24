import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { KitchenOrderView } from '../../../core/api/models';
import {
  ActiveFilter,
  filterActive,
  TAKEAWAY_FILTER,
  WaiterBoardStore,
} from '../data/waiter-board.store';

/** `/waiter/active`: every paid order not yet served (new, cooking, ready), filterable by table or takeaway. */
@Component({
  selector: 'app-waiter-active-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './active-page.html',
})
export class ActivePage {
  protected readonly board = inject(WaiterBoardStore);

  /** `?table=T1` pre-selects a table (bound by the router). */
  readonly table = input<string | undefined>();

  private readonly chosen = signal<ActiveFilter | undefined>(undefined);
  protected readonly filter = computed<ActiveFilter>(() => {
    const chosen = this.chosen();
    return chosen !== undefined ? chosen : (this.table() ?? null);
  });
  protected readonly takeawayFilter = TAKEAWAY_FILTER;

  protected readonly orders = computed(() => filterActive(this.board.orders(), this.filter()));
  protected readonly counts = computed(() => {
    const list = this.orders();
    return {
      confirmed: list.filter((o) => o.status === 'CONFIRMED').length,
      preparing: list.filter((o) => o.status === 'PREPARING').length,
      ready: list.filter((o) => o.status === 'READY').length,
    };
  });

  protected select(filter: ActiveFilter): void {
    this.chosen.set(filter);
  }

  protected serve(order: KitchenOrderView): void {
    void this.board.serve(order);
  }
}
