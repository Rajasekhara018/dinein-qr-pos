import { AdminOrderView, OrderStatus, PaymentView } from '../../../core/api/models';

export interface StatusAction {
  to: OrderStatus;
  label: string;
}

/**
 * Forward transitions an admin may trigger (backend `ADMIN_TARGETS` ∩ `OrderStateMachine`):
 * CONFIRMED → PREPARING → READY → COMPLETED.
 */
export function nextStatusActions(status: OrderStatus): StatusAction[] {
  switch (status) {
    case 'CONFIRMED':
      return [{ to: 'PREPARING', label: 'Start preparing' }];
    case 'PREPARING':
      return [{ to: 'READY', label: 'Mark ready' }];
    case 'READY':
      return [{ to: 'COMPLETED', label: 'Mark completed' }];
    default:
      return [];
  }
}

/** Paid orders that are not ready yet can be cancelled with a full refund. */
export function canCancel(status: OrderStatus): boolean {
  return status === 'CONFIRMED' || status === 'PREPARING';
}

/** A captured payment whose refund failed (cancel again to retry). */
export function failedRefund(order: AdminOrderView): PaymentView | undefined {
  return order.payments.find((p) => p.refundStatus === 'FAILED');
}

/** Cancelled but the refund has not been started or failed → offer "Retry refund". */
export function canRetryRefund(order: AdminOrderView): boolean {
  return order.status === 'CANCELLED' && !!failedRefund(order);
}

export function refundStatusLabel(status: string | undefined): string {
  switch (status) {
    case 'PENDING':
      return 'Refund pending';
    case 'PROCESSED':
      return 'Refunded';
    case 'FAILED':
      return 'Refund failed';
    default:
      return status ?? '—';
  }
}

export function paymentStatusLabel(status: string | undefined): string {
  switch (status) {
    case 'CREATED':
      return 'Started';
    case 'AUTHORIZED':
      return 'Authorized';
    case 'CAPTURED':
      return 'Paid';
    case 'FAILED':
      return 'Failed';
    case 'REFUNDED':
      return 'Refunded';
    default:
      return status ?? '—';
  }
}

export const FILTERABLE_STATUSES: readonly OrderStatus[] = [
  'CONFIRMED',
  'PREPARING',
  'READY',
  'COMPLETED',
  'CANCELLED',
  'PENDING_PAYMENT',
  'PAYMENT_FAILED',
  'EXPIRED',
];
