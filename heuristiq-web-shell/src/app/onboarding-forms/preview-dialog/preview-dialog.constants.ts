/** Same built-in default patterns as the backend's OnboardingSubmissionService. PHONE assumes the
 *  client always prefixes a country dial code (the preview uses hq-phone-number-input, whose value is
 *  international E.164, like the customer/driver apps' PhoneCountryPicker), matching what the real backend enforces at submit time. */
export const EMAIL_PATTERN = /^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/;
export const PHONE_PATTERN = /^\+[1-9]\d{6,14}$/;
export const CURRENCY_PATTERN = /^\d+(\.\d{1,2})?$/;

export const CURRENCY_SYMBOL = '₹';
