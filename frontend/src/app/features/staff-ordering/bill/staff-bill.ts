import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { GuestOrderView } from '../../../core/api/models';
import { paymentChannelLabel } from '../../../core/util/order-labels';

/** The server bill of an order (lines as charged, GST split, total) and how it was paid. */
@Component({
  selector: 'app-staff-bill',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  templateUrl: './staff-bill.html',
})
export class StaffBill {
  readonly order = input.required<GuestOrderView>();

  protected readonly paidWith = computed(() => {
    const payment = this.order().payment;
    return payment ? paymentChannelLabel(payment.provider, payment.method) : '';
  });
}
