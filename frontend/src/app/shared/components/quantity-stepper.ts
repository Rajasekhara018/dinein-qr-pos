import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';

/**
 * `− qty +` control with 44px targets. Two-way bind `[(quantity)]`, or listen to `(quantityChange)`.
 * With `min = 0`, decrementing from 1 emits 0 (callers usually remove the line).
 * `(increment)` fires instead of changing the value when `incrementOnly` is set (e.g. "customise again" flows).
 */
@Component({
  selector: 'app-quantity-stepper',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'inline-flex items-center rounded-control border border-brand/40 bg-surface text-brand-ink',
    role: 'group',
    '[attr.aria-label]': '"Quantity" + (itemName() ? " of " + itemName() : "")',
  },
  templateUrl: './quantity-stepper.html',
})
export class QuantityStepper {
  readonly quantity = model.required<number>();
  readonly min = input(0);
  readonly max = input(50);
  readonly itemName = input('');
  readonly disabled = input(false);
  readonly showTrash = input(true);
  /** When true, "+" emits `increment` without changing the value. */
  readonly incrementOnly = input(false);

  readonly increment = output<void>();

  protected readonly canIncrement = computed(() => this.quantity() < this.max());

  protected step(delta: 1 | -1): void {
    if (delta === 1 && this.incrementOnly()) {
      this.increment.emit();
      return;
    }
    const next = Math.min(this.max(), Math.max(this.min(), this.quantity() + delta));
    if (next !== this.quantity()) this.quantity.set(next);
  }
}
