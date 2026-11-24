import { booleanAttribute, ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { WaiterTableView } from '../../../core/api/models';

/**
 * Tables as big tiles (plus an optional Takeaway tile), with open / preparing / ready counts. Used to pick where a
 * staff-assisted order goes, and as the waiter's Tables tab.
 */
@Component({
  selector: 'app-table-grid',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './table-grid.html',
})
export class TableGrid {
  readonly tables = input.required<readonly WaiterTableView[]>();
  readonly takeawayEnabled = input(false, { transform: booleanAttribute });
  readonly selectedTableId = input<number | null>(null);
  readonly takeawaySelected = input(false, { transform: booleanAttribute });
  readonly showCounts = input(true, { transform: booleanAttribute });

  readonly pickTable = output<WaiterTableView>();
  readonly pickTakeaway = output<void>();
}
