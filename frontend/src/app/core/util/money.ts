/**
 * Money helpers. The backend sends rupees as JSON numbers with ≤ 2 decimals; all arithmetic is done in integer
 * paise so we never accumulate floating-point error, and rounding mirrors Java's `RoundingMode.HALF_UP`.
 */

export type Paise = number;

const inrFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const inrWholeFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

/** Exact conversion of a rupee amount (≤ 2 dp, as sent by the API) to integer paise. */
export function toPaise(rupees: number | null | undefined): Paise {
  if (rupees == null || !Number.isFinite(rupees)) return 0;
  const negative = rupees < 0;
  const [whole, fraction = ''] = Math.abs(rupees).toFixed(2).split('.');
  const paise = Number(whole) * 100 + Number(fraction.padEnd(2, '0').slice(0, 2));
  return negative ? -paise : paise;
}

export function fromPaise(paise: Paise): number {
  return paise / 100;
}

/**
 * `round(numerator / denominator)` with HALF_UP semantics (ties away from zero), using BigInt so large
 * intermediate products stay exact. Mirrors `BigDecimal.divide(…, RoundingMode.HALF_UP)`.
 */
export function divideHalfUp(numerator: number, denominator: number): number {
  if (denominator === 0) throw new RangeError('Division by zero');
  let n = BigInt(Math.round(numerator));
  let d = BigInt(Math.round(denominator));
  if (d < 0n) {
    n = -n;
    d = -d;
  }
  const negative = n < 0n;
  const abs = negative ? -n : n;
  const q = (abs * 2n + d) / (2n * d);
  return Number(negative ? -q : q);
}

/** Formats a rupee amount, e.g. `₹1,250.00`. */
export function formatInr(
  rupees: number | null | undefined,
  options?: { whole?: boolean },
): string {
  const value = rupees ?? 0;
  if (options?.whole) return inrWholeFormatter.format(value);
  return inrFormatter.format(value);
}

/** Formats paise, e.g. `formatPaise(54000)` → `₹540.00`. */
export function formatPaise(paise: Paise, options?: { whole?: boolean }): string {
  return formatInr(fromPaise(paise), options);
}
