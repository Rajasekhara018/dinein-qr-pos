import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { GuestOrderView, PublicRestaurantInfo } from '../../../core/api/models';
import { billViewToAmounts } from '../data/cart-pricing';

/**
 * Printable bill built from the order SNAPSHOT (lines, prices, GST as charged) plus restaurant GSTIN/FSSAI.
 * `@media print` shows only this block (see order-bill.css).
 */
@Component({
  selector: 'app-order-bill',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-bill.html',
  styleUrl: './order-bill.css',
})
export class OrderBill {
  readonly order = input.required<GuestOrderView>();
  readonly restaurant = input<PublicRestaurantInfo | null>(null);

  protected readonly amounts = computed(() => billViewToAmounts(this.order().bill));
  protected readonly gstRates = computed(() => {
    const rates = [...new Set(this.order().items.map((l) => l.gstPercent))].sort((a, b) => a - b);
    return rates.map((r) => `${r}%`).join(', ');
  });
}
