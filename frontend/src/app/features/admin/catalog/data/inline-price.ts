import { ItemResponse, PriceRequest, VariantPrice } from '../../../../core/api/models';
import { toPaise } from '../../../../core/util/money';

/**
 * Quick inline rate edit (`PATCH /api/admin/items/{id}/price`): either the base price, or prices of specific sizes.
 * All comparisons are done in paise to avoid floating-point noise.
 */
export interface PriceChange {
  basePrice?: number;
  variants?: VariantPrice[];
}

const same = (a: number, b: number) => toPaise(a) === toPaise(b);

/** Only the values that actually differ from the item; null when nothing changed. */
export function diffPriceChange(item: ItemResponse, change: PriceChange): PriceChange | null {
  const diff: PriceChange = {};
  if (change.basePrice !== undefined && !item.hasVariants) {
    if (item.basePrice === undefined || !same(item.basePrice, change.basePrice)) {
      diff.basePrice = change.basePrice;
    }
  }
  if (change.variants?.length) {
    const changed = change.variants.filter((vp) => {
      const current = item.variants.find((v) => v.id === vp.id);
      return current !== undefined && !same(current.price, vp.price);
    });
    if (changed.length) diff.variants = changed;
  }
  return diff.basePrice !== undefined || diff.variants ? diff : null;
}

/** The change that restores `previous` for the fields touched by `change` (used by Undo). */
export function undoChangeFor(previous: ItemResponse, change: PriceChange): PriceChange {
  const undo: PriceChange = {};
  if (change.basePrice !== undefined && previous.basePrice !== undefined) {
    undo.basePrice = previous.basePrice;
  }
  if (change.variants?.length) {
    undo.variants = change.variants
      .map((vp) => previous.variants.find((v) => v.id === vp.id))
      .filter((v): v is NonNullable<typeof v> => !!v)
      .map((v) => ({ id: v.id, price: v.price }));
  }
  return undo;
}

/** Optimistic local copy of the item with the change applied (displayPrice recomputed). */
export function applyPriceChange(item: ItemResponse, change: PriceChange): ItemResponse {
  const variants = item.variants.map((v) => {
    const updated = change.variants?.find((vp) => vp.id === v.id);
    return updated ? { ...v, price: updated.price } : v;
  });
  const basePrice = change.basePrice ?? item.basePrice;
  return { ...item, basePrice, variants, displayPrice: displayPriceOf(basePrice, variants) };
}

/** Mirrors the backend's `displayPrice`: base price, else the default (or cheapest) size price. */
export function displayPriceOf(
  basePrice: number | undefined,
  variants: readonly { price: number; isDefault: boolean }[],
): number | undefined {
  if (!variants.length) return basePrice;
  const def = variants.find((v) => v.isDefault);
  if (def) return def.price;
  return Math.min(...variants.map((v) => v.price));
}

export function toPriceRequest(change: PriceChange, version: number): PriceRequest {
  return {
    ...(change.basePrice !== undefined ? { basePrice: change.basePrice } : {}),
    ...(change.variants?.length ? { variants: change.variants } : {}),
    version,
  };
}

/** Parses a price typed by the user: > 0, ≤ 2 decimals, ≤ 99,999,999.99. Returns null if invalid. */
export function parsePrice(raw: string | number | null | undefined): number | null {
  if (raw === null || raw === undefined) return null;
  const text = String(raw).trim().replace(/,/g, '').replace(/^₹\s*/, '');
  if (!/^\d{1,8}(\.\d{1,2})?$/.test(text)) return null;
  const value = Number(text);
  return value > 0 ? value : null;
}
