import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { KitchenOrderView } from '../../../core/api/models';
import { WaiterAlerts } from '../data/waiter-alerts';
import { WaiterBoardStore } from '../data/waiter-board.store';

/** `/waiter` (default tab): READY orders, the one waiting longest first, each with a big "Served" button. */
@Component({
  selector: 'app-waiter-ready-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ready-page.html',
})
export class ReadyPage {
  protected readonly board = inject(WaiterBoardStore);
  protected readonly alerts = inject(WaiterAlerts);

  protected serve(order: KitchenOrderView): void {
    void this.board.serve(order);
  }
}
