import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Surface container. `padding="none"` for edge-to-edge content (images, lists).
 *
 * `elevated` gives the card a bit more visual weight (`shadow-md` instead of the resting `shadow-sm`) — for a
 * primary content card the eye should land on (e.g. the guest bill summary), not for every card indiscriminately.
 * Defaults to `false`, so existing call sites are unaffected.
 */
@Component({
  selector: 'app-card',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'classes()' },
  templateUrl: './card.html',
})
export class Card {
  readonly padding = input<'none' | 'sm' | 'md' | 'lg'>('md');
  readonly elevated = input(false);

  protected readonly classes = computed(() => {
    const pad = { none: '', sm: 'p-3', md: 'p-4', lg: 'p-4 sm:p-6' }[this.padding()];
    return `block min-w-0 rounded-card border border-line bg-surface ${this.elevated() ? 'shadow-md' : 'shadow-sm'} ${pad}`;
  });
}
