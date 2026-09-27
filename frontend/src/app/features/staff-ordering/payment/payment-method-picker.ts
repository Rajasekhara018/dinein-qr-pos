import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { STAFF_PAYMENT_METHODS, StaffPaymentMethod } from '../../../core/api/models';

interface MethodOption {
  label: string;
  hint: string;
  /** SVG path data for a 24×24 stroke icon. */
  icon: string;
}

const OPTIONS: Record<StaffPaymentMethod, MethodOption> = {
  CASH: {
    label: 'Cash',
    hint: 'Collected now',
    icon: 'M3 6h18v12H3V6Zm9 3a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM6 9v.01M18 15v.01',
  },
  UPI_AT_COUNTER: {
    label: 'UPI at counter',
    hint: 'Restaurant QR',
    icon: 'M4 4h6v6H4V4Zm10 0h6v6h-6V4ZM4 14h6v6H4v-6Zm10 0h2v2h-2v-2Zm4 4h2v2h-2v-2Zm-4 2v-2',
  },
  CARD_AT_COUNTER: {
    label: 'Card at counter',
    hint: 'Card machine',
    icon: 'M3 6h18v12H3V6Zm0 4h18M7 15h4',
  },
  ONLINE: {
    label: 'Online',
    hint: 'Guest pays on this device',
    icon: 'M7 3h10v18H7V3Zm4 15h2',
  },
};

/** Radio cards for how a staff-assisted order is paid (also used for "Mark paid (offline)" without Online). */
@Component({
  selector: 'app-payment-method-picker',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  templateUrl: './payment-method-picker.html',
})
export class PaymentMethodPicker {
  readonly value = model<StaffPaymentMethod>('CASH');
  readonly methods = input<readonly StaffPaymentMethod[]>(STAFF_PAYMENT_METHODS);
  /** Online is shown but disabled when no payment gateway is configured. */
  readonly onlineAvailable = input(true);
  readonly legend = input('Payment');
  /** Radio group name (unique per page). */
  readonly name = input('staff-payment-method');

  protected option(method: StaffPaymentMethod): MethodOption {
    return OPTIONS[method];
  }

  protected isDisabled(method: StaffPaymentMethod): boolean {
    return method === 'ONLINE' && !this.onlineAvailable();
  }
}
