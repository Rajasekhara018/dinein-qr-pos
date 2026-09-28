import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal,
} from '@angular/core';

export type ImageRatio = 'square' | '4/3' | '3/2' | '16/9';

const RATIO_CLASS: Record<ImageRatio, string> = {
  square: 'aspect-square',
  '4/3': 'aspect-[4/3]',
  '3/2': 'aspect-[3/2]',
  '16/9': 'aspect-video',
};

/**
 * Fixed-aspect-ratio image box (no layout shift) using `NgOptimizedImage` in `fill` mode. Images come from the
 * same origin (`/api/v1/images/{id}` or `/thumb`) with immutable caching, so no image loader is configured.
 * Shows a neutral placeholder while loading, when there is no image, or if it fails to load. Lazy by default;
 * set `priority` for the LCP image.
 */
@Component({
  selector: 'app-image-box',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': 'hostClasses()',
  },
  templateUrl: './image-box.html',
})
export class ImageBox {
  readonly src = input<string | null | undefined>(null);
  readonly alt = input('');
  readonly ratio = input<ImageRatio>('square');
  /** `sizes` attribute for the browser (the box's rendered width). */
  readonly sizes = input('(min-width: 768px) 12.5rem, 30vw');
  readonly priority = input(false, { transform: booleanAttribute });

  protected readonly loaded = signal(false);
  protected readonly failed = signal(false);

  protected readonly hostClasses = computed(
    () => `relative block overflow-hidden bg-surface-muted ${RATIO_CLASS[this.ratio()]}`,
  );
}
