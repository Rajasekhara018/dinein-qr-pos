import { divideHalfUp, Paise, toPaise } from '../../../core/util/money';

/**
 * Client-side bill ESTIMATE mirroring the backend's `order.PricingService` exactly (the server stays authoritative):
 *
 * - `lineTotal = unitPrice × qty`.
 * - Exclusive GST (default): `tax = round(lineTotal × gst / 100)`, `grand = subtotal + tax`.
 * - Inclusive GST:           `tax = round(lineTotal × gst / (100 + gst))`, `subtotal = grand − tax`.
 * - Tax is rounded HALF_UP to 2 dp PER LINE; totals are sums of rounded lines.
 * - CGST = half of the tax total rounded HALF_UP; SGST = the remainder (they always add up exactly).
 *
 * All maths is in integer paise; GST percentages are handled in basis points (5.00% → 500).
 */

export interface BillLineInput {
  /** Rupees (variant/base price + addons), ≤ 2 dp. */
  unitPrice: number;
  quantity: number;
  /** e.g. 5, 12, 18. */
  gstPercent: number;
}

export interface BillLineResult {
  lineTotal: Paise;
  tax: Paise;
}

export interface BillAmounts {
  subtotal: Paise;
  taxTotal: Paise;
  cgst: Paise;
  sgst: Paise;
  grandTotal: Paise;
}

export interface BillEstimate extends BillAmounts {
  lines: BillLineResult[];
}

export function lineTotalPaise(unitPrice: number, quantity: number): Paise {
  return toPaise(unitPrice) * quantity;
}

export function lineTaxPaise(
  lineTotal: Paise,
  gstPercent: number,
  pricesIncludeGst: boolean,
): Paise {
  const gstBp = toPaise(gstPercent); // hundredths of a percent
  if (gstBp === 0) return 0;
  return pricesIncludeGst
    ? divideHalfUp(lineTotal * gstBp, 10_000 + gstBp)
    : divideHalfUp(lineTotal * gstBp, 10_000);
}

/** Splits a tax total into [CGST, SGST]. */
export function splitGst(taxTotal: Paise): [Paise, Paise] {
  const cgst = divideHalfUp(taxTotal, 2);
  return [cgst, taxTotal - cgst];
}

export function estimateBill(
  lines: readonly BillLineInput[],
  pricesIncludeGst: boolean,
): BillEstimate {
  const results: BillLineResult[] = [];
  let gross = 0;
  let taxTotal = 0;
  for (const line of lines) {
    const lineTotal = lineTotalPaise(line.unitPrice, line.quantity);
    const tax = lineTaxPaise(lineTotal, line.gstPercent, pricesIncludeGst);
    results.push({ lineTotal, tax });
    gross += lineTotal;
    taxTotal += tax;
  }
  const [cgst, sgst] = splitGst(taxTotal);
  return {
    lines: results,
    subtotal: pricesIncludeGst ? gross - taxTotal : gross,
    taxTotal,
    cgst,
    sgst,
    grandTotal: pricesIncludeGst ? gross : gross + taxTotal,
  };
}

/** Converts a server `BillView` (rupees) to paise amounts for display components. */
export function billViewToAmounts(bill: {
  subtotal: number;
  taxTotal: number;
  cgst: number;
  sgst: number;
  grandTotal: number;
}): BillAmounts {
  return {
    subtotal: toPaise(bill.subtotal),
    taxTotal: toPaise(bill.taxTotal),
    cgst: toPaise(bill.cgst),
    sgst: toPaise(bill.sgst),
    grandTotal: toPaise(bill.grandTotal),
  };
}
