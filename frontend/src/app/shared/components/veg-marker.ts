import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FoodType } from '../../core/api/models';

const LABELS: Record<FoodType, string> = {
  VEG: 'Vegetarian',
  NON_VEG: 'Non-vegetarian',
  EGG: 'Contains egg',
};

/**
 * FSSAI-style food-type marker — colour AND shape, so it works for colour-blind users:
 * VEG = green square with a dot, NON_VEG = brown square with a triangle, EGG = amber square with a dot-in-ring.
 */
@Component({
  selector: 'app-veg-marker',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'inline-flex shrink-0',
    role: 'img',
    '[attr.aria-label]': 'label()',
    '[attr.title]': 'label()',
  },
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 16 16" aria-hidden="true" [class]="colorClass()">
      <rect x="1" y="1" width="14" height="14" rx="2" fill="none" stroke="currentColor" stroke-width="1.6" />
      @switch (type()) {
        @case ('NON_VEG') {
          <path d="M8 4 L12.2 11.5 H3.8 Z" fill="currentColor" />
        }
        @case ('EGG') {
          <circle cx="8" cy="8" r="3.9" fill="none" stroke="currentColor" stroke-width="1.4" />
          <circle cx="8" cy="8" r="2" fill="currentColor" />
        }
        @default {
          <circle cx="8" cy="8" r="3.4" fill="currentColor" />
        }
      }
    </svg>
  `,
})
export class VegMarker {
  readonly type = input.required<FoodType>();
  readonly size = input(16);

  protected readonly label = computed(() => LABELS[this.type()] ?? this.type());
  protected readonly colorClass = computed(() => {
    switch (this.type()) {
      case 'NON_VEG':
        return 'text-nonveg';
      case 'EGG':
        return 'text-egg';
      default:
        return 'text-veg';
    }
  });
}
