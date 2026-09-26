import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Loading placeholder. Size it with classes: `<app-skeleton class="h-4 w-2/3" />`. */
@Component({
  selector: 'app-skeleton',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': 'classes()',
    'aria-hidden': 'true',
  },
  templateUrl: './skeleton.html',
})
export class Skeleton {
  readonly shape = input<'line' | 'block' | 'circle'>('line');

  protected readonly classes = computed(() => {
    const base = 'block animate-pulse bg-surface-sunken motion-reduce:animate-none';
    switch (this.shape()) {
      case 'circle':
        return `${base} rounded-full`;
      case 'block':
        return `${base} rounded-card`;
      default:
        return `${base} h-4 rounded`;
    }
  });
}
