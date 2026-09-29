import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { KitchenConfig, KitchenOrderView } from '../../../core/api/models';
import { COLUMN_LABELS, KitchenColumn } from '../data/board-state';

const EMPTY_TEXT: Record<KitchenColumn, string> = {
  CONFIRMED: 'No new orders',
  PREPARING: 'Nothing cooking',
  READY: 'Nothing waiting to be served',
};

/** One board column (New / Preparing / Ready), oldest ticket first. */
@Component({
  selector: 'app-board-column',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex min-h-0 min-w-0 flex-col' },
  templateUrl: './board-column.html',
})
export class BoardColumn {
  readonly column = input.required<KitchenColumn>();
  readonly orders = input.required<readonly KitchenOrderView[]>();
  readonly now = input.required<number>();
  readonly config = input.required<KitchenConfig>();
  readonly pending = input<ReadonlySet<number>>(new Set());
  readonly highlighted = input<ReadonlySet<number>>(new Set());

  readonly advance = output<KitchenOrderView>();
  readonly togglePriority = output<KitchenOrderView>();

  protected readonly label = computed(() => COLUMN_LABELS[this.column()]);
  protected readonly emptyText = computed(() => EMPTY_TEXT[this.column()]);
  protected readonly accent = computed(() => {
    switch (this.column()) {
      case 'CONFIRMED':
        return 'bg-sky-500';
      case 'PREPARING':
        return 'bg-amber-500';
      default:
        return 'bg-emerald-500';
    }
  });
}
