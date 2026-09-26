import { booleanAttribute, ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Rupee amount with an optional "from" prefix (items with variants) and struck-through original price. */
@Component({
  selector: 'app-price',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex items-baseline gap-1 whitespace-nowrap tabular-nums' },
  template: `
    @if (from()) {
      <span class="text-xs font-normal text-ink-muted">from</span>
    }
    <span>{{ amount() | inr: (whole() ? 'whole' : 'rupees') }}</span>
    @if (original() !== null && original() !== amount()) {
      <s class="text-xs font-normal text-ink-subtle">
        <span class="sr-only">was </span>{{ original() | inr: (whole() ? 'whole' : 'rupees') }}
      </s>
    }
  `,
})
export class Price {
  readonly amount = input.required<number>();
  readonly from = input(false, { transform: booleanAttribute });
  /** Drop ".00" for whole rupees (menu cards). */
  readonly whole = input(false, { transform: booleanAttribute });
  readonly original = input<number | null>(null);
}
