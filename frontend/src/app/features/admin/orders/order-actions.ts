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
    case 'MANUAL':
      return 'Manual refund: hand back';
    default:
      return status ?? '—';
  }
}

/** Unpaid orders an admin can settle at the counter (`POST /admin/orders/{id}/mark-paid-offline`). */
export const MARK_PAID_STATUSES: readonly OrderStatus[] = ['PENDING_PAYMENT', 'EXPIRED', 'PAYMENT_FAILED'];

export function canMarkPaidOffline(order: AdminOrderView): boolean {
  return MARK_PAID_STATUSES.includes(order.status) && !order.paymentFlagged;
}

/** The offline payment that has to be handed back (refund status MANUAL), if any. */
export function manualRefundPayment(order: AdminOrderView): PaymentView | undefined {
  return order.payments.find((p) => p.refundStatus === 'MANUAL');
}

/** Rupees to hand back for a manual refund: the offline payment amount, else the bill total. */
export function manualRefundAmount(order: AdminOrderView): number {
  const payment = manualRefundPayment(order);
  return payment?.amountPaise != null ? payment.amountPaise / 100 : order.bill.grandTotal;
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
