import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { KitchenOrderView } from '../../../core/api/models';

/** "just now", "4 min", "1 h 5 min". */
export function formatAgo(fromIso: string | undefined, now: number): string {
  if (!fromIso) return '';
  const minutes = Math.max(0, Math.floor((now - Date.parse(fromIso)) / 60_000));
  if (!Number.isFinite(minutes)) return '';
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m ? `${h} h ${m} min ago` : `${h} h ago`;
}

/**
 * One order as a big card for a phone: token, table / takeaway, items, and (for READY orders) a large "Served"
 * button. Stateless; `now` comes from the board's shared ticker.
 */
@Component({
  selector: 'app-waiter-order-card',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  templateUrl: './waiter-order-card.html',
})
export class WaiterOrderCard {
  readonly order = input.required<KitchenOrderView>();
  readonly now = input(Date.now());
  readonly pending = input(false);
  readonly highlighted = input(false);
  /** Show the status badge (Active tab). */
  readonly showStatus = input(false, { transform: booleanAttribute });

  readonly serve = output<KitchenOrderView>();

  protected readonly isReady = computed(() => this.order().status === 'READY');
  protected readonly itemCount = computed(() =>
    this.order().items.reduce((sum, line) => sum + line.quantity, 0),
  );
  protected readonly since = computed(() => {
    const o = this.order();
    return o.status === 'READY'
      ? `Ready ${formatAgo(o.readyAt, this.now())}`
      : `Paid ${formatAgo(o.paidAt, this.now())}`;
  });

  protected onServe(): void {
    if (!this.pending() && this.isReady()) this.serve.emit(this.order());
  }
}
