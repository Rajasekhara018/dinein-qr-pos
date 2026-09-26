import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Surface container. `padding="none"` for edge-to-edge content (images, lists). */
@Component({
  selector: 'app-card',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'classes()' },
  template: '<ng-content />',
})
export class Card {
  readonly padding = input<'none' | 'sm' | 'md' | 'lg'>('md');
  readonly elevated = input(false);

  protected readonly classes = computed(() => {
    const pad = { none: '', sm: 'p-3', md: 'p-4', lg: 'p-4 sm:p-6' }[this.padding()];
    return `block min-w-0 rounded-card border border-line bg-surface ${this.elevated() ? 'shadow-raised' : 'shadow-card'} ${pad}`;
  });
}
