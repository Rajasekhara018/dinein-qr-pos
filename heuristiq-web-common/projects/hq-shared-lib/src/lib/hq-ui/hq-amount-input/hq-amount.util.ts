import { HQ_AMOUNT_FALLBACK_DECIMALS, HQ_AMOUNT_MAX_DECIMALS } from './hq-amount-input.constants';

/** An amount of money: the ISO 4217 currency code and the value (null while nothing is entered). */
export interface HqAmount {
  code: string;
  value: number | null;
}

/** A currency code in the form the browser and the backend expect: three upper-case letters, or '' when it is not one. */
export function hqNormalizeCurrency(code: string | null | undefined): string {
  const trimmed = (code ?? '').trim().toUpperCase();
  return /^[A-Z]{3}$/.test(trimmed) ? trimmed : '';
}

/** How many decimal places a currency uses (INR 2, JPY 0, KWD 3); two when the currency is unknown to the browser. */
export function hqCurrencyDecimals(code: string | null | undefined): number {
  const currency = hqNormalizeCurrency(code);
  if (!currency) {
    return HQ_AMOUNT_FALLBACK_DECIMALS;
  }
  try {
    const digits = new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits;
    return typeof digits === 'number' ? Math.min(digits, HQ_AMOUNT_MAX_DECIMALS) : HQ_AMOUNT_FALLBACK_DECIMALS;
  } catch {
    return HQ_AMOUNT_FALLBACK_DECIMALS;
  }
}

/** The currency's symbol in a locale (₹, $, ¥), or its code when the browser has no symbol for it. */
export function hqCurrencySymbol(code: string | null | undefined, locale: string): string {
  const currency = hqNormalizeCurrency(code);
  if (!currency) {
    return '';
  }
  try {
    const part = new Intl.NumberFormat(locale, { style: 'currency', currency, currencyDisplay: 'narrowSymbol' })
      .formatToParts(0).find((p) => p.type === 'currency');
    return part?.value ?? currency;
  } catch {
    return currency;
  }
}

/** A value rounded half up to `decimals` places, done in whole minor units so 1.005 becomes 1.01, not 1.00. */
export function hqRoundAmount(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  const sign = value < 0 ? -1 : 1;
  return (sign * Math.round(Math.abs(value) * factor + Number.EPSILON * factor)) / factor;
}

/**
 * What a person typed, read as a number: grouping separators and spaces are ignored, a comma or a point is the decimal mark
 * when it is the only one, and anything else makes it unreadable (NaN). '' and only spaces are null.
 */
export function hqParseAmount(text: string | null | undefined): number | null {
  const raw = (text ?? '').replace(/[\s  ']/g, '');
  if (!raw) {
    return null;
  }
  let normalized = raw;
  const lastComma = raw.lastIndexOf(',');
  const lastPoint = raw.lastIndexOf('.');
  if (lastComma >= 0 && lastPoint >= 0) {
    // Both appear: the later one is the decimal mark, the other groups thousands.
    const decimalMark = lastComma > lastPoint ? ',' : '.';
    const group = decimalMark === ',' ? '.' : ',';
    normalized = raw.split(group).join('').replace(decimalMark, '.');
  } else if (lastComma >= 0) {
    // Only commas: one comma followed by 1-3 digits at the end is a decimal mark, otherwise they group thousands.
    normalized = /^-?\d*,\d{1,3}$/.test(raw) && raw.indexOf(',') === lastComma ? raw.replace(',', '.') : raw.split(',').join('');
  }
  return /^-?(\d+\.?\d*|\.\d+)$/.test(normalized) ? Number(normalized) : Number.NaN;
}

/**
 * Keeps only what may be typed into an amount: digits, one decimal point (only when the currency has decimals) and at most
 * `decimals` digits after it; a leading minus only when `allowNegative`.
 */
export function hqSanitizeAmountTyping(text: string, decimals: number, allowNegative = false): string {
  const negative = allowNegative && text.trim().startsWith('-');
  const cleaned = text.replace(/,/g, '.').replace(/[^\d.]/g, '');
  const [whole, ...rest] = cleaned.split('.');
  let out = whole;
  if (decimals > 0 && rest.length > 0) {
    out += '.' + rest.join('').slice(0, decimals);
  }
  return (negative ? '-' : '') + out;
}

/** The value written with grouping and exactly the currency's decimal places (1,00,000.00 in en-IN), without the symbol. */
export function hqFormatAmount(value: number | null | undefined, code: string | null | undefined, locale: string, decimals?: number): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '';
  }
  const places = decimals ?? hqCurrencyDecimals(code);
  try {
    return new Intl.NumberFormat(locale, { minimumFractionDigits: places, maximumFractionDigits: places }).format(hqRoundAmount(value, places));
  } catch {
    return hqRoundAmount(value, places).toFixed(places);
  }
}

/**
 * An amount with its currency's symbol, grouping and decimal places in a locale (₹1,00,000.00 in en-IN, $1,234.50 in en-US);
 * '' when there is no value.
 */
export function hqMoneyText(value: number | null | undefined, code: string | null | undefined, locale: string): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '';
  }
  const currency = hqNormalizeCurrency(code);
  const places = hqCurrencyDecimals(currency);
  if (!currency) {
    return hqFormatAmount(value, currency, locale, places);
  }
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency', currency, currencyDisplay: 'narrowSymbol', minimumFractionDigits: places, maximumFractionDigits: places,
    }).format(hqRoundAmount(value, places));
  } catch {
    return `${currency} ${hqFormatAmount(value, currency, locale, places)}`;
  }
}

/** A `{ code, value }` amount from a number and a currency code (the shape every API and screen uses for money). */
export function hqAmount(value: number | null | undefined, code: string | null | undefined): HqAmount {
  return { code: hqNormalizeCurrency(code), value: value === undefined || value === null || !Number.isFinite(value) ? null : value };
}

/** The value as it is edited: no grouping, the currency's decimal places (1500 becomes '1500.00'); '' when there is none. */
export function hqEditableAmount(value: number | null | undefined, decimals: number): string {
  return value === null || value === undefined || !Number.isFinite(value) ? '' : hqRoundAmount(value, decimals).toFixed(decimals);
}

/** An amount as an API may send it during the money rollout: the `{ code, value }` object, or a legacy bare number / numeric text. */
export type HqAmountLike = HqAmount | number | string | null | undefined;

/**
 * The numeric value of an amount, whichever shape it arrives in: `{ code, value }`, a bare number or numeric text ("10.00");
 * null when there is none or it is not a number. Use it wherever a screen adds, compares or sorts amounts.
 */
export function hqAmountValue(amount: HqAmountLike): number | null {
  if (amount === null || amount === undefined) {
    return null;
  }
  const raw: unknown = typeof amount === 'object' ? (amount as HqAmount).value : amount;
  if (raw === null || raw === undefined || (typeof raw === 'string' && raw.trim() === '')) {
    return null;
  }
  const value = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(value) ? value : null;
}

/** The currency of an amount: its own code when it is a `{ code, value }` object naming one, else `fallback` normalised. */
export function hqAmountCode(amount: HqAmountLike, fallback?: string | null): string {
  const own = amount !== null && typeof amount === 'object' ? hqNormalizeCurrency((amount as HqAmount).code) : '';
  return own || hqNormalizeCurrency(fallback);
}

/**
 * An amount read defensively into `{ code, value }`: an object keeps its code (or takes `fallbackCode` when it has none), a
 * legacy number or numeric text takes `fallbackCode`. Null when there is no readable value, never `{ code, value: null }`.
 */
export function hqToAmount(amount: HqAmountLike, fallbackCode?: string | null): HqAmount | null {
  const value = hqAmountValue(amount);
  return value === null ? null : { code: hqAmountCode(amount, fallbackCode), value };
}

/** The amount for a request body: `{ code, value }`, or null when there is no value (an API never gets `{ code, value: null }`). */
export function hqAmountOrNull(value: number | null | undefined, code: string | null | undefined): HqAmount | null {
  return value === null || value === undefined || !Number.isFinite(value) ? null : hqAmount(value, code);
}
