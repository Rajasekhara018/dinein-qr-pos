import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { KitchenConfig, KitchenOrderView } from '../../../core/api/models';
import {
  ageLevel,
  DEFAULT_KITCHEN_CONFIG,
  describeElapsed,
  elapsedMs,
  formatElapsed,
  nextAction,
} from '../data/board-state';

const ACTION_CLASSES: Record<string, string> = {
  Start:
    'bg-sky-600 text-white hover:bg-sky-700 dark:bg-sky-500 dark:text-sky-950 dark:hover:bg-sky-400',
  Ready:
    'bg-emerald-600 text-white hover:bg-emerald-700 dark:bg-emerald-500 dark:text-emerald-950 dark:hover:bg-emerald-400',
  Served: 'bg-surface-muted text-ink ring-1 ring-line-strong hover:bg-surface-sunken',
};

/**
 * One order ticket. Stateless: elapsed time comes from the board's shared ticker (`now`), thresholds from
 * `/api/kitchen/config`. Age is conveyed by colour AND a text/icon badge.
 */
@Component({
  selector: 'app-ticket-card',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'block',
  },
  templateUrl: './ticket-card.html',
  styleUrl: './ticket-card.css',
})
export class TicketCard {
  readonly order = input.required<KitchenOrderView>();
  /** Epoch millis from the shared 1 s ticker. */
  readonly now = input.required<number>();
  readonly config = input<KitchenConfig>(DEFAULT_KITCHEN_CONFIG);
  readonly pending = input(false);
  /** Just arrived: flash (or a static highlight with reduced motion). */
  readonly highlighted = input(false);

  readonly advance = output<KitchenOrderView>();

  protected readonly action = computed(() => nextAction(this.order().status));
  protected readonly elapsed = computed(() => elapsedMs(this.order(), this.now()));
  protected readonly elapsedText = computed(() => formatElapsed(this.elapsed()));
  protected readonly elapsedLabel = computed(() => describeElapsed(this.elapsed()));
  readonly level = computed(() => ageLevel(this.order(), this.now(), this.config()));

  protected readonly cardClass = computed(() => {
    const classes = [`ticket--${this.level()}`];
    if (this.highlighted()) classes.push('ticket--new');
    if (this.order().status === 'READY') classes.push('ticket--ready');
    return classes.join(' ');
  });

  protected readonly actionClass = computed(() => {
    const action = this.action();
    return action ? ACTION_CLASSES[action.label] : '';
  });

  protected readonly itemCount = computed(() =>
    this.order().items.reduce((sum, line) => sum + line.quantity, 0),
  );

  protected onAction(): void {
    if (!this.pending()) this.advance.emit(this.order());
  }
}
