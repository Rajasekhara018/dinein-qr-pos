import { booleanAttribute, ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

export type ImageRatio = 'square' | '4/3' | '3/2' | '16/9';

const RATIO_CLASS: Record<ImageRatio, string> = {
  square: 'aspect-square',
  '4/3': 'aspect-[4/3]',
  '3/2': 'aspect-[3/2]',
  '16/9': 'aspect-video',
};

/**
 * Fixed-aspect-ratio image box (no layout shift) using `NgOptimizedImage` in `fill` mode. Images come from the
 * same origin (`/api/images/{id}` or `/thumb`) with immutable caching, so no image loader is configured.
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
  template: `
    @if (!src() || failed()) {
      <div class="absolute inset-0 flex items-center justify-center text-ink-subtle" aria-hidden="true">
        <svg viewBox="0 0 24 24" class="size-1/3 max-h-10 max-w-10" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M3 11h18M5 11a7 7 0 0 1 14 0M12 4V3M4 15h16l-1 3H5l-1-3Z" />
        </svg>
      </div>
      @if (alt()) {
        <span class="sr-only">{{ alt() }}</span>
      }
    } @else {
      <img
        [ngSrc]="src()!"
        [alt]="alt()"
        fill
        [sizes]="sizes()"
        [priority]="priority()"
        class="object-cover transition-opacity duration-300"
        [class.opacity-0]="!loaded()"
        (load)="loaded.set(true)"
        (error)="failed.set(true)"
      />
    }
  `,
})
export class ImageBox {
  readonly src = input<string | null | undefined>(null);
  readonly alt = input('');
  readonly ratio = input<ImageRatio>('square');
  /** `sizes` attribute for the browser (the box's rendered width). */
  readonly sizes = input('(min-width: 768px) 200px, 30vw');
  readonly priority = input(false, { transform: booleanAttribute });

  protected readonly loaded = signal(false);
  protected readonly failed = signal(false);

  protected readonly hostClasses = computed(
    () => `relative block overflow-hidden bg-surface-muted ${RATIO_CLASS[this.ratio()]}`,
  );
}
