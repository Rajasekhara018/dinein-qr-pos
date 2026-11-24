import { HqAmount, hqAmountCode, hqAmountOrNull, hqAmountValue } from 'hq-shared-lib';
import { FLOAT_ERROR_LABEL_KEYS, REASON_MAX } from './wallet-float.constants';
import { FloatSummary } from './wallet-float.models';

/** The currency of a float (the one its balance is in), or '' when it names none (the amount field then uses the app default). */
export function floatCurrency(summary: FloatSummary | null): string {
  return hqAmountCode(summary?.balance);
}

/** An amount as a request carries it: `{ code, value }`, or null when nothing is entered (never `{ code, value: null }`). */
export function amountToSend(amount: HqAmount | null | undefined): HqAmount | null {
  return hqAmountOrNull(amount?.value, amount?.code);
}

/** True when an amount is entered and is not below zero. */
export function isNonNegativeAmount(amount: HqAmount | null | undefined): boolean {
  const value = hqAmountValue(amount);
  return value !== null && value >= 0;
}

/** True when an amount is entered and is above zero. */
export function isPositiveAmount(amount: HqAmount | null | undefined): boolean {
  const value = hqAmountValue(amount);
  return value !== null && value > 0;
}

/** A fresh Idempotency-Key for one form submit; the same key is reused on a retry. */
export function newIdempotencyKey(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** The wallet service's error code in an HTTP error, or ''. */
export function errorCodeOf(err: unknown): string {
  const body = (err as { error?: { errorCode?: unknown } } | null)?.error;
  return typeof body?.errorCode === 'string' ? body.errorCode : '';
}

/** Translation key for a known error code, or null when the service's own message should be shown. */
export function floatErrorKey(err: unknown): string | null {
  return FLOAT_ERROR_LABEL_KEYS[errorCodeOf(err)] ?? null;
}

/** Translation key of what is wrong with a reason, or null. */
export function reasonError(reason: string): string | null {
  const text = reason.trim();
  return text && text.length <= REASON_MAX ? null : 'walletFloat.errors.reason';
}

/** Page count for a page of alerts (at least one). */
export function alertPageCount(total: number, size: number): number {
  return Math.max(1, Math.ceil(total / Math.max(size, 1)));
}
