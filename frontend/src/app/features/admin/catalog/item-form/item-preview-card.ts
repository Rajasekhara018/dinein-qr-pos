import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MenuItem } from '../../../../core/api/models';

/**
 * Live preview of the guest menu card (mirrors `features/guest/menu/menu-item-card` markup with the shared UI kit,
 * without cart behaviour), so admins see exactly how the dish will look.
 */
@Component({
  selector: 'app-item-preview-card',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  templateUrl: './item-preview-card.html',
})
export class ItemPreviewCard {
  readonly item = input.required<MenuItem>();

  protected readonly customizable = computed(
    () => this.item().variants.length > 0 || this.item().addons.length > 0,
  );
  protected readonly price = computed(() => this.item().displayPrice ?? this.item().basePrice ?? 0);
  protected readonly hasVariantRange = computed(
    () => new Set(this.item().variants.map((v) => v.price)).size > 1,
  );
  protected readonly startingPrice = computed(() => {
    const variants = this.item().variants;
    return variants.length ? Math.min(...variants.map((v) => v.price)) : this.price();
  });
}
