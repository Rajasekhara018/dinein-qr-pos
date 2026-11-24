import {
  OFFLINE_PAYMENT_METHODS,
  OFFLINE_PROVIDER,
  OfflinePaymentMethod,
  OrderType,
  StaffPaymentMethod,
} from '../api/models';

/** Human labels for order types, staff payment methods and payment channels (shared by all apps). */

export function orderTypeLabel(type: OrderType | null | undefined): string {
  return type === 'TAKEAWAY' ? 'Takeaway' : 'Dine-in';
}

const STAFF_METHOD_LABELS: Record<StaffPaymentMethod, string> = {
  CASH: 'Cash',
  UPI_AT_COUNTER: 'UPI at counter',
  CARD_AT_COUNTER: 'Card at counter',
  ONLINE: 'Online',
};

/** `CASH` → "Cash", `UPI_AT_COUNTER` → "UPI at counter", `ONLINE` → "Online". */
export function staffPaymentMethodLabel(method: string | null | undefined): string {
  if (!method) return '—';
  return STAFF_METHOD_LABELS[method as StaffPaymentMethod] ?? method;
}

export function isOfflineMethod(method: string | null | undefined): method is OfflinePaymentMethod {
  return !!method && (OFFLINE_PAYMENT_METHODS as readonly string[]).includes(method);
}

/**
 * How an order was paid, for lists: "Cash at counter" / "UPI at counter" / "Card at counter" for OFFLINE payments,
 * otherwise the gateway and its method (e.g. "Razorpay · UPI").
 */
export function paymentChannelLabel(
  provider: string | null | undefined,
  method: string | null | undefined,
): string {
  if (provider === OFFLINE_PROVIDER || (!provider && isOfflineMethod(method))) {
    if (method === 'CASH') return 'Cash at counter';
    return isOfflineMethod(method) ? staffPaymentMethodLabel(method) : 'At counter';
  }
  const gateway = provider ? providerLabel(provider) : '';
  const how = method ? method.toUpperCase() : '';
  return [gateway, how].filter(Boolean).join(' · ') || '—';
}

export function providerLabel(provider: string): string {
  switch (provider) {
    case 'RAZORPAY':
      return 'Razorpay';
    case 'PAYU':
      return 'PayU';
    case 'PINELABS':
      return 'Pine Labs';
    case OFFLINE_PROVIDER:
      return 'At counter';
    default:
      return provider;
  }
}
