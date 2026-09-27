import { OrderStatus } from '../core/api/models';

export type StatusAudience = 'guest' | 'staff';

export type StatusTone = 'warning' | 'info' | 'brand' | 'success' | 'neutral' | 'danger';

export interface OrderStatusMeta {
  /** Wording shown to the given audience. */
  label: string;
  /** Semantic tone — maps to the `--{tone}-bg` / `--{tone}-fg` token pair (or brand/neutral, which have their own scale). */
  tone: StatusTone;
  /** Lucide icon name (see `shared/icons.ts`), for anywhere that shows an icon next to the status. */
  icon: string;
}

/**
 * Single source of truth for order-status wording, colour and icon, by audience:
 * - `guest`: reassuring, plain language, written for someone tracking their own order.
 * - `staff` (kitchen/waiter/admin): short, operational, scannable at a glance on a ticket or list row.
 *
 * `OrderStatusBadge` and any future status text (ticket headers, notifications, order timelines) should read from
 * this map rather than hard-coding labels/colours, so the two audiences stay consistent with themselves and with
 * each other's underlying meaning.
 *
 * Tone → token pairing: warning = amber, info = blue, brand = the restaurant's brand colour, success = green,
 * neutral = stone, danger = red. See the `-bg`/`-fg`/`-border` triples in `src/styles.css`.
 */
const STATUS_META: Record<OrderStatus, Record<StatusAudience, OrderStatusMeta>> = {
  PENDING_PAYMENT: {
    guest: { label: 'Waiting for payment', tone: 'warning', icon: 'lucideClock' },
    staff: { label: 'Awaiting payment', tone: 'warning', icon: 'lucideClock' },
  },
  CONFIRMED: {
    guest: { label: 'Order placed', tone: 'info', icon: 'lucideCircleCheck' },
    staff: { label: 'Paid', tone: 'info', icon: 'lucideCircleCheck' },
  },
  PREPARING: {
    guest: { label: 'Being prepared', tone: 'brand', icon: 'lucideChefHat' },
    staff: { label: 'Preparing', tone: 'brand', icon: 'lucideChefHat' },
  },
  READY: {
    guest: { label: 'Ready', tone: 'success', icon: 'lucideBell' },
    staff: { label: 'Ready', tone: 'success', icon: 'lucideBell' },
  },
  COMPLETED: {
    guest: { label: 'Served', tone: 'neutral', icon: 'lucideCheckCheck' },
    staff: { label: 'Served', tone: 'neutral', icon: 'lucideCheckCheck' },
  },
  EXPIRED: {
    guest: { label: 'Payment window expired', tone: 'neutral', icon: 'lucideTimerOff' },
    staff: { label: 'Expired', tone: 'neutral', icon: 'lucideTimerOff' },
  },
  PAYMENT_FAILED: {
    guest: { label: "Payment didn't go through", tone: 'danger', icon: 'lucideCircleX' },
    staff: { label: 'Payment failed', tone: 'danger', icon: 'lucideCircleX' },
  },
  CANCELLED: {
    guest: { label: 'Cancelled', tone: 'danger', icon: 'lucideBan' },
    staff: { label: 'Cancelled', tone: 'danger', icon: 'lucideBan' },
  },
};

const TONE_CLASSES: Record<StatusTone, string> = {
  warning: 'bg-warning-bg text-warning-fg',
  info: 'bg-info-bg text-info-fg',
  success: 'bg-success-bg text-success-fg',
  danger: 'bg-danger-bg text-danger-fg',
  neutral: 'bg-surface-sunken text-ink-muted',
  brand: 'bg-brand-soft text-brand-ink',
};

export function orderStatusMeta(status: OrderStatus, audience: StatusAudience = 'staff'): OrderStatusMeta {
  return STATUS_META[status]?.[audience] ?? STATUS_META.CONFIRMED[audience];
}

export function orderStatusLabel(status: OrderStatus, audience: StatusAudience = 'staff'): string {
  return orderStatusMeta(status, audience).label;
}

export function orderStatusToneClasses(status: OrderStatus, audience: StatusAudience = 'staff'): string {
  return TONE_CLASSES[orderStatusMeta(status, audience).tone];
}

export const ALL_ORDER_STATUSES: readonly OrderStatus[] = [
  'PENDING_PAYMENT',
  'CONFIRMED',
  'PREPARING',
  'READY',
  'COMPLETED',
  'EXPIRED',
  'PAYMENT_FAILED',
  'CANCELLED',
];
