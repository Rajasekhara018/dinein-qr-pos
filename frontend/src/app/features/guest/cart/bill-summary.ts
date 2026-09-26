import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { BillAmounts } from '../data/cart-pricing';

/** Subtotal / CGST / SGST / Total breakdown (amounts in paise). Labelled "estimated" until the server has priced it. */
@Component({
  selector: 'app-bill-summary',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './bill-summary.html',
})
export class BillSummary {
  readonly bill = input.required<BillAmounts>();
  readonly estimated = input(true);
  readonly pricesIncludeGst = input(false);
}
