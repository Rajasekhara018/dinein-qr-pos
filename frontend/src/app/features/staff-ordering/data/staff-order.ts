import {
  CartLineRequest,
  CheckoutResponse,
  OFFLINE_PROVIDER,
  OrderType,
  StaffPaymentMethod,
  StaffPlaceOrderRequest,
} from '../../../core/api/models';
import { ApiError } from '../../../core/api/api-error';

/**
 * Pure helpers of the staff-assisted ordering flow (waiter screen and admin counter orders), kept free of Angular so
 * they are easy to unit-test.
 */

/** Where the staff flow was opened from: decides the place endpoint and the order page. */
export type StaffOrderingMode = 'waiter' | 'admin';

export interface StaffOrderDraft {
  tableId: number | null;
  orderType: OrderType;
  items: CartLineRequest[];
  note: string;
  customerName: string;
  customerPhone: string;
  paymentMethod: StaffPaymentMethod;
}

export const MAX_STAFF_NOTE = 300;

/**
 * The exact request body. Blank texts become `null`, the phone keeps digits only. A takeaway order keeps its table
 * only when one was picked (the backend accepts both); dine-in always needs one (the backend answers
 * `400 TABLE_REQUIRED` otherwise, and the UI blocks it first).
 */
export function buildStaffOrderRequest(
  draft: StaffOrderDraft,
  idempotencyKey: string,
): StaffPlaceOrderRequest {
  const phone = draft.customerPhone.replace(/\D/g, '');
  return {
    tableId: draft.tableId,
    orderType: draft.orderType,
    items: draft.items.map((line) => ({ ...line })),
    note: draft.note.trim().slice(0, MAX_STAFF_NOTE) || null,
    customerName: draft.customerName.trim() || null,
    customerPhone: phone || null,
    paymentMethod: draft.paymentMethod,
    idempotencyKey,
  };
}

/** A stable fingerprint of everything but the key (same payload → same idempotency key). */
export function draftFingerprint(draft: StaffOrderDraft): string {
  return JSON.stringify(buildStaffOrderRequest(draft, ''));
}

/**
 * What to do with the place-order response:
 * - `confirmed`: an offline payment (cash / UPI / card at the counter) was recorded, the order is in the kitchen →
 *   success screen with the token;
 * - `checkout`: ONLINE and still unpaid → hand the response to `CheckoutService`;
 * - `already-paid`: an idempotent replay of an order that is already paid → open its page.
 */
export type CheckoutBranch = 'confirmed' | 'checkout' | 'already-paid';

export function checkoutBranch(response: CheckoutResponse): CheckoutBranch {
  if (response.provider === OFFLINE_PROVIDER) return 'confirmed';
  if (response.status === 'PENDING_PAYMENT') return 'checkout';
  return 'already-paid';
}

/** Can the draft be placed? Returns the reason it cannot, or null. */
export function draftProblem(draft: {
  tableId: number | null;
  orderType: OrderType;
  lineCount: number;
  hasIssues: boolean;
}): string | null {
  if (draft.orderType === 'DINE_IN' && draft.tableId == null) return 'Choose a table first.';
  if (draft.lineCount === 0) return 'Add at least one item.';
  if (draft.hasIssues) return 'Remove the items that are no longer available.';
  return null;
}

/** Staff-facing message for a failed place-order call (the flow handles the side effects). */
export function placeErrorMessage(error: ApiError): string {
  switch (error.code) {
    case 'TABLE_REQUIRED':
      return 'Choose a table for a dine-in order.';
    case 'INVALID_TABLE':
      return 'That table is no longer active. Pick another one.';
    case 'ITEM_UNAVAILABLE':
      return 'Some items are no longer available. Remove them to continue.';
    case 'TAKEAWAY_DISABLED':
      return 'Takeaway is switched off right now. Choose a table instead.';
    case 'ORDERING_CLOSED':
    case 'NOT_ACCEPTING_ORDERS':
    case 'OUTSIDE_OPENING_HOURS':
      return error.message || 'The restaurant is not accepting orders right now.';
    case 'PAYMENTS_NOT_CONFIGURED':
    case 'PAYMENT_PROVIDER_ERROR':
      return `${error.message || 'Online payment is not available.'} Take cash, UPI or card instead.`;
    case 'IDEMPOTENCY_KEY_REUSED':
      return 'Please tap Place order again.';
    case 'RATE_LIMITED':
      return 'Too many attempts. Please wait a moment and try again.';
    case 'VALIDATION_FAILED': {
      const fields = error.fieldErrors.map((f) => f.message).filter(Boolean);
      return fields.length ? fields.join('. ') : error.message;
    }
    case 'NETWORK_ERROR':
      return 'Cannot reach the server. Check the Wi-Fi and try again.';
    default:
      return error.message || 'The order could not be placed.';
  }
}
