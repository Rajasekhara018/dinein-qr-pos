import { InjectionToken } from '@angular/core';
import { HqAppSetting } from '../hq-app-setting';

/** Decimal places of an amount whose currency the browser does not know (most currencies use two). */
export const HQ_AMOUNT_FALLBACK_DECIMALS = 2;

/** Most decimal places any currency uses (KWD, BHD, OMR have three); a caller-set `decimals` is capped here. */
export const HQ_AMOUNT_MAX_DECIMALS = 4;

/** The currency an amount field starts on until the app provides HQ_AMOUNT_DEFAULT_CURRENCY. */
export const HQ_AMOUNT_FALLBACK_CURRENCY = 'INR';

/**
 * The currency (ISO 4217, for example 'INR') an amount field starts on when its value names none. An app provides it once: a
 * fixed code, or a function returning the current organization's currency (read each time). Without it
 * HQ_AMOUNT_FALLBACK_CURRENCY is used.
 */
export const HQ_AMOUNT_DEFAULT_CURRENCY = new InjectionToken<HqAppSetting>('HQ_AMOUNT_DEFAULT_CURRENCY');
