import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  model,
} from '@angular/core';

/**
 * Filter/selection chip: `<app-chip [(selected)]="isVeg" label="Veg only" />`. Renders as a `role="checkbox"`
 * toggle button (a chip is a stand-in for a checkbox, not a link), 44px touch target, `aria-pressed` for screen
 * readers plus a visible check mark when selected. Use `removable` for a tag-style chip with a remove (×) action
 * instead of a selection state.
 */
@Component({
  selector: 'app-chip',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex' },
  templateUrl: './chip.html',
})
export class Chip {
  readonly label = input.required<string>();
  readonly selected = model(false);
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly removable = input(false, { transform: booleanAttribute });
  readonly removeLabel = input('');

  protected readonly classes = computed(() => {
    const base =
      'inline-flex min-h-touch items-center gap-1.5 whitespace-nowrap rounded-full border px-3.5 text-sm font-medium ' +
      'transition-colors disabled:cursor-not-allowed disabled:opacity-50 ' +
      'focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-focus';
    return this.selected()
      ? `${base} border-brand bg-brand-soft text-brand-ink`
      : `${base} border-line-strong bg-surface text-ink hover:bg-surface-muted`;
  });

  protected toggle(): void {
    if (this.disabled()) return;
    this.selected.set(!this.selected());
  }
}
