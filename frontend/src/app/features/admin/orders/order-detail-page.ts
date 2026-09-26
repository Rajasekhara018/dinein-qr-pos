import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { AdminOrdersApi } from '../../../core/api/admin.api';
import { AdminOrderView } from '../../../core/api/models';
import { formatInr } from '../../../core/util/money';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ToastService } from '../../../core/ui/toast.service';
import { orderRefreshSignals } from '../data/live-refresh';
import { ConfirmService } from '../shared/confirm.service';
import { errorMessage } from '../shared/form-errors';
import {
  canCancel,
  canRetryRefund,
  failedRefund,
  nextStatusActions,
  paymentStatusLabel,
  refundStatusLabel,
  StatusAction,
} from './order-actions';

/** Order detail: lines, bill, payments, flags; status actions; cancel & refund (with retry on refund failure). */
@Component({
  selector: 'app-admin-order-detail-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-detail-page.html',
})
export class OrderDetailPage implements OnInit {
  private readonly api = inject(AdminOrdersApi);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly realtime = inject(RealtimeService);
  private readonly destroyRef = inject(DestroyRef);

  /** Route param `:id`. */
  readonly id = input.required<string>();

  protected readonly order = signal<AdminOrderView | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<unknown>(null);
  protected readonly busy = signal<'status' | 'cancel' | null>(null);
  protected readonly refundError = signal('');

  protected readonly actions = computed<StatusAction[]>(() => {
    const order = this.order();
    return order ? nextStatusActions(order.status) : [];
  });
  protected readonly cancellable = computed(() => {
    const order = this.order();
    return !!order && canCancel(order.status);
  });
  protected readonly retryRefund = computed(() => {
    const order = this.order();
    return !!order && canRetryRefund(order);
  });
  protected readonly failedRefund = computed(() => {
    const order = this.order();
    return order ? failedRefund(order) : undefined;
  });
  protected readonly paymentStatusLabel = paymentStatusLabel;
  protected readonly refundStatusLabel = refundStatusLabel;

  ngOnInit(): void {
    void this.load();
    const orderId = Number(this.id());
    // Live: refetch on events for this order and on reconnect; unsubscribed with the component.
    const sub = orderRefreshSignals(this.realtime, (event) => event.orderId === orderId).subscribe(
      () => void this.load(true),
    );
    this.destroyRef.onDestroy(() => sub.unsubscribe());
  }

  protected async load(silent = false): Promise<void> {
    if (!silent) this.loading.set(true);
    try {
      this.order.set(await firstValueFrom(this.api.get(Number(this.id()))));
      this.error.set(null);
    } catch (error) {
      if (!silent || !this.order()) this.error.set(error);
    } finally {
      this.loading.set(false);
    }
  }

  protected async changeStatus(action: StatusAction): Promise<void> {
    const order = this.order();
    if (!order) return;
    this.busy.set('status');
    try {
      this.order.set(await firstValueFrom(this.api.changeStatus(order.id, action.to)));
      this.toasts.success(`Order #${order.displayToken}: ${action.label.toLowerCase()} done.`);
    } catch (error) {
      if (error instanceof ApiError && (error.code === 'ILLEGAL_TRANSITION' || error.status === 409)) {
        this.toasts.warning('The order changed meanwhile. Showing the latest status.');
        void this.load(true);
      } else {
        this.toasts.error(errorMessage(error, 'Could not update the order.'));
      }
    } finally {
      this.busy.set(null);
    }
  }

  protected async cancelAndRefund(): Promise<void> {
    const order = this.order();
    if (!order) return;
    const retry = order.status === 'CANCELLED';
    const confirmed = await this.confirmService.confirm(
      retry
        ? {
            title: 'Retry the refund?',
            message: `We'll ask the payment provider again to refund ${formatInr(order.bill.grandTotal)} for order #${order.displayToken}.`,
            confirmLabel: 'Retry refund',
          }
        : {
            title: `Cancel order #${order.displayToken}?`,
            message: 'The order is removed from the kitchen and the guest is refunded in full.',
            details: ['Refunds usually reach the guest in 5–7 working days.', 'This cannot be undone.'],
            confirmLabel: 'Cancel & refund',
            cancelLabel: 'Keep order',
            reason: { label: 'Reason', placeholder: 'e.g. Item not available', maxLength: 300 },
          },
    );
    if (!confirmed) return;
    await this.runCancel(order.id, confirmed.reason, retry);
  }

  private async runCancel(id: number, reason: string, retry: boolean): Promise<void> {
    this.busy.set('cancel');
    this.refundError.set('');
    try {
      const updated = await firstValueFrom(this.api.cancel(id, retry ? undefined : reason));
      this.order.set(updated);
      const refund = updated.payments.find((p) => p.refundStatus);
      this.toasts.success(
        refund?.refundStatus === 'PROCESSED'
          ? 'Order cancelled and refunded.'
          : refund
            ? 'Order cancelled. The refund has been requested.'
            : 'Order cancelled.',
      );
    } catch (error) {
      if (error instanceof ApiError && error.code === 'REFUND_FAILED') {
        this.refundError.set(error.message);
        this.toasts.error('The refund could not be started. You can retry it.', { key: `refund-${id}` });
      } else if (error instanceof ApiError && error.code === 'ILLEGAL_TRANSITION') {
        this.toasts.warning(error.message);
      } else {
        this.toasts.error(errorMessage(error, 'Could not cancel the order.'));
      }
      void this.load(true);
    } finally {
      this.busy.set(null);
    }
  }
}
