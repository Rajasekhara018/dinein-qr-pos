import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CheckoutResponse } from '../../../core/api/models';

/** Offline-paid order placed: the big token for the guest, the server-confirmed amount, and what to do next. */
@Component({
  selector: 'app-staff-order-success',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './staff-order-success.html',
})
export class StaffOrderSuccess {
  readonly response = input.required<CheckoutResponse>();
  /** "Table T1" / "Takeaway". */
  readonly whereLabel = input('');
  /** "Cash", "UPI at counter"… */
  readonly methodLabel = input('');
  /** Router commands of the order page. */
  readonly orderLink = input.required<unknown[]>();
  /** Secondary link (e.g. back to tables), optional. */
  readonly backLink = input<unknown[] | null>(null);
  readonly backLabel = input('Back');

  readonly newOrder = output<void>();
}
