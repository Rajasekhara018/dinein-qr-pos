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
  templateUrl: './veg-marker.html',
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
