import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  AdminOrderView,
  OFFLINE_PAYMENT_METHODS,
  OfflinePaymentMethod,
  StaffPaymentMethod,
} from '../../../core/api/models';
import { staffPaymentMethodLabel } from '../../../core/util/order-labels';

export interface MarkPaidDialogData {
  order: AdminOrderView;
}

/**
 * "Mark paid (offline)": pick how the money was taken at the counter and confirm the amount. Closes with the method
 * only when confirmed.
 */
@Component({
  selector: 'app-mark-paid-dialog',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mark-paid-dialog.html',
})
export class MarkPaidDialog {
  protected readonly ref = inject<DialogRef<OfflinePaymentMethod, MarkPaidDialog>>(DialogRef);
  protected readonly data = inject<MarkPaidDialogData>(DIALOG_DATA);

  protected readonly methods = OFFLINE_PAYMENT_METHODS;
  protected readonly method = signal<OfflinePaymentMethod>('CASH');
  protected readonly methodLabel = computed(() => staffPaymentMethodLabel(this.method()));

  protected onMethod(method: StaffPaymentMethod): void {
    if (method !== 'ONLINE') this.method.set(method);
  }

  protected confirm(): void {
    this.ref.close(this.method());
  }
}
