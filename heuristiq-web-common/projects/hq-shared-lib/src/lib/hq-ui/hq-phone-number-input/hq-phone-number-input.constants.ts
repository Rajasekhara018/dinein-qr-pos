import { InjectionToken } from '@angular/core';
import { HqAppSetting } from '../hq-app-setting';

/**
 * @deprecated Indian-only. Phone numbers of any country are accepted now (see hq-phone.util.ts); use `hqIsValidPhone`
 * or `hqPhoneValidator`. Kept so existing imports still compile.
 */
export const HQ_INDIAN_MOBILE_PATTERN = /^[6-9]\d{9}$/;

/** Longest phone number E.164 allows, calling code included. */
export const HQ_PHONE_MAX_E164_DIGITS = 15;

/** The country a number typed without a country code is read in, until the app provides HQ_PHONE_DEFAULT_COUNTRY. */
export const HQ_PHONE_FALLBACK_COUNTRY = 'IN';

/**
 * The country (ISO 3166 alpha-2, for example 'IN') the phone field starts on and reads a number written without a
 * country code in. An app provides it once, from its organization's home country; without it the platform default
 * (HQ_PHONE_FALLBACK_COUNTRY) is used.
 */
export const HQ_PHONE_DEFAULT_COUNTRY = new InjectionToken<HqAppSetting>('HQ_PHONE_DEFAULT_COUNTRY');
