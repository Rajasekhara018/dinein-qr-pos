import { CartProblemReason, FoodType, MenuItem } from '../../../core/api/models';

/** What the guest picked for one item (from the card's quick add or the item sheet). */
export interface ItemSelection {
  variantId: number | null;
  addonIds: number[];
  notes: string;
  quantity: number;
}

export interface CartLineIssue {
  reason: CartProblemReason;
  message: string;
}

/**
 * One cart line. Identity (`key`) = item + variant + sorted addons + notes. The display fields are a snapshot that
 * is refreshed from every new menu (see CartStore.reconcile).
 */
export interface CartLine {
  key: string;
  itemId: number;
  variantId: number | null;
  /** Sorted ascending. */
  addonIds: number[];
  notes: string;
  quantity: number;

  name: string;
  variantName: string | null;
  addonNames: string[];
  foodType: FoodType;
  /** Rupees: variant/base price + addons. */
  unitPrice: number;
  gstPercent: number;
  thumbUrl: string | null;
  addedAt: number;

  /** Set when the line can no longer be ordered (from the menu or a server ITEM_UNAVAILABLE response). */
  issue: CartLineIssue | null;
}

export const MAX_LINE_QUANTITY = 50;
export const MAX_CART_LINES = 50;
export const MAX_LINE_NOTES = 200;
export const MAX_ORDER_NOTES = 300;
export const MAX_CUSTOMER_NAME = 60;
export const INDIAN_MOBILE = /^[6-9]\d{9}$/;

export function normaliseNotes(notes: string | null | undefined): string {
  return (notes ?? '').trim().replace(/\s+/g, ' ').slice(0, MAX_LINE_NOTES);
}

export function lineKey(
  itemId: number,
  variantId: number | null,
  addonIds: readonly number[],
  notes: string,
): string {
  const addons = [...addonIds].sort((a, b) => a - b).join(',');
  return `${itemId}|${variantId ?? ''}|${addons}|${normaliseNotes(notes).toLowerCase()}`;
}

/** Items that need the sheet: more than one variant, or any addons. */
export function isCustomizable(item: MenuItem): boolean {
  return (item.variants?.length ?? 0) > 1 || (item.addons?.length ?? 0) > 0;
}

export function defaultVariantId(item: MenuItem): number | null {
  const variants = item.variants ?? [];
  if (!variants.length) return null;
  return (variants.find((v) => v.isDefault) ?? variants[0]).id;
}

/** Lowest orderable price ("from ₹X") — variant minimum or the base price. */
export function startingPrice(item: MenuItem): number {
  const variants = item.variants ?? [];
  if (variants.length) return Math.min(...variants.map((v) => v.price));
  return item.displayPrice ?? item.basePrice ?? 0;
}

export const ISSUE_MESSAGES: Record<string, string> = {
  ITEM_NOT_FOUND: 'No longer on the menu',
  CATEGORY_UNAVAILABLE: 'No longer available',
  ITEM_UNAVAILABLE: 'Out of stock',
  VARIANT_REQUIRED: 'Please choose a size',
  VARIANT_UNAVAILABLE: 'This size is no longer available',
  ADDON_UNAVAILABLE: 'An add-on is no longer available',
};

export function issueFor(reason: CartProblemReason): CartLineIssue {
  return { reason, message: ISSUE_MESSAGES[reason] ?? 'No longer available' };
}
