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
  template: `
    <button
      type="button"
      class="inline-flex size-touch items-center justify-center rounded-l-control hover:bg-brand-soft disabled:opacity-40"
      [disabled]="disabled() || quantity() <= min()"
      [attr.aria-label]="(quantity() <= 1 && min() === 0 ? 'Remove ' : 'Decrease ') + (itemName() || 'quantity')"
      (click)="step(-1)"
    >
      @if (quantity() <= 1 && min() === 0 && showTrash()) {
        <svg viewBox="0 0 20 20" class="size-4 fill-current" aria-hidden="true"><path d="M8 2h4l1 1h4v2H3V3h4l1-1Zm-3 5h10l-.8 10.2A2 2 0 0 1 12.2 19H7.8a2 2 0 0 1-2-1.8L5 7Z"/></svg>
      } @else {
        <svg viewBox="0 0 20 20" class="size-4 fill-current" aria-hidden="true"><path d="M4 9h12v2H4z"/></svg>
      }
    </button>
    <output class="min-w-8 px-1 text-center text-base font-bold tabular-nums" aria-live="polite">{{ quantity() }}</output>
    <button
      type="button"
      class="inline-flex size-touch items-center justify-center rounded-r-control hover:bg-brand-soft disabled:opacity-40"
      [disabled]="disabled() || !canIncrement()"
      [attr.aria-label]="'Increase ' + (itemName() || 'quantity')"
      (click)="step(1)"
    >
      <svg viewBox="0 0 20 20" class="size-4 fill-current" aria-hidden="true"><path d="M9 4h2v5h5v2h-5v5H9v-5H4V9h5z"/></svg>
    </button>
  `,
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
