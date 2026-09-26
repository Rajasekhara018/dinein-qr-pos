import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { GuestOrderView } from '../../../core/api/models';

interface Step {
  key: 'paid' | 'preparing' | 'ready';
  label: string;
  hint: string;
  at?: string;
}

/** Paid → Preparing → Ready stepper for paid orders (COMPLETED shows all steps done). */
@Component({
  selector: 'app-order-progress',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-progress.html',
})
export class OrderProgress {
  readonly order = input.required<GuestOrderView>();

  /** Index of the current step (0 paid, 1 preparing, 2 ready); 3 = everything done (served). */
  protected readonly current = computed(() => {
    switch (this.order().status) {
      case 'CONFIRMED':
        return 0;
      case 'PREPARING':
        return 1;
      case 'READY':
        return 2;
      case 'COMPLETED':
        return 3;
      default:
        return -1;
    }
  });

  protected readonly steps = computed<Step[]>(() => {
    const o = this.order();
    return [
      { key: 'paid', label: 'Paid', hint: 'Order sent to the kitchen', at: o.paidAt },
      { key: 'preparing', label: 'Preparing', hint: 'The kitchen is cooking your food', at: o.preparingAt },
      { key: 'ready', label: 'Ready', hint: o.status === 'COMPLETED' ? 'Served — enjoy your meal!' : 'Ready to serve', at: o.readyAt },
    ];
  });

  protected stateOf(index: number): 'done' | 'current' | 'upcoming' {
    const current = this.current();
    if (index < current || current === 3 || (index === current && index === 2)) return 'done';
    if (index === current) return 'current';
    return 'upcoming';
  }
}
