import {
  ACCOUNT_NUMBER_PATTERN, DOCUMENT_NUMBER_PATTERN, ISO_DATE_PATTERN, ONBOARDING_ERROR_PREFIX, ONBOARDING_LIMITS,
  ONBOARDING_SERVER_FIELDS, ONBOARDING_VALIDATION_PREFIX, RoutingRule,
} from './franchise-onboarding.constants';
import { compactCode, ibanAccepted, ibanRequired, ibanValid, routingRuleFor } from './franchise-onboarding.helpers';
import { AgreementRequest, BankAccountRequest } from './franchise-onboarding.models';

/** One inline message: a translation key and its parameters. */
export interface FieldIssue {
  key: string;
  params?: Record<string, unknown>;
}

/** The inline messages of a form, by form field name. */
export type FieldIssues = Partial<Record<string, FieldIssue>>;

/** A validation error the service reported for one field (already mapped to the form field). */
export interface ServerFieldError {
  field: string;
  /** The service's message key without its prefix, e.g. `invalidRoutingCode`. */
  code: string;
}

const issue = (name: string, params?: Record<string, unknown>): FieldIssue =>
  params ? { key: ONBOARDING_VALIDATION_PREFIX + name, params } : { key: ONBOARDING_VALIDATION_PREFIX + name };

const tooLong = (value: string, max: number): boolean => [...value].length > max;

/** True when the routing code must be filled in, given whether an IBAN is. */
export function routingRequired(rule: RoutingRule, hasIban: boolean): boolean {
  return rule.required === 'always' || (rule.required === 'unlessIban' && !hasIban);
}

/** True when the account number must be filled in: always, unless the country accepts an IBAN and one is given. */
export function accountNumberRequired(form: Pick<BankAccountRequest, 'country' | 'iban'>): boolean {
  return !(ibanAccepted(form.country) && compactCode(form.iban));
}

/**
 * Every problem of a bank account form, by field. Mirrors OnboardingRules.bankAccount of the franchise service: holder name
 * required (≤200), country and currency required, bank name optional (≤100), IBAN only where the country uses one (required in
 * AE and SA, mod-97 checked; not sent elsewhere), account number required unless an IBAN is given (4-34 letters/digits), routing code per country
 * (IFSC and ABA required, sort code required unless an IBAN is given, SWIFT/BIC optional), proof of account required.
 */
export function bankAccountIssues(form: BankAccountRequest): FieldIssues {
  const issues: FieldIssues = {};
  const holder = form.holderName.trim();
  if (!holder) issues['holderName'] = issue('required');
  else if (tooLong(holder, ONBOARDING_LIMITS.holderName)) issues['holderName'] = issue('tooLong', { max: ONBOARDING_LIMITS.holderName });
  if (!form.country) issues['country'] = issue('required');
  if (!form.currency) issues['currency'] = issue('required');
  if (tooLong((form.bankName ?? '').trim(), ONBOARDING_LIMITS.bankName)) issues['bankName'] = issue('tooLong', { max: ONBOARDING_LIMITS.bankName });

  // The IBAN field is offered only where the country uses one; elsewhere it is not sent (normalizedBankRequest).
  const iban = ibanAccepted(form.country) ? compactCode(form.iban) : '';
  if (!iban) {
    if (ibanRequired(form.country)) issues['iban'] = issue('ibanRequired');
  } else if (!ibanValid(iban)) {
    issues['iban'] = issue('ibanInvalid');
  }
  // An IBAN typed in a country that uses one stands in for the account number and sort code (its own error is shown on it).
  const hasIban = !!iban;

  // Where only an IBAN is used (AE, SA) the page asks for nothing else and sends no account number or routing code.
  if (!ibanRequired(form.country)) {
    const account = compactCode(form.accountNumber);
    if (!account) {
      if (accountNumberRequired(form)) issues['accountNumber'] = issue(ibanAccepted(form.country) ? 'accountOrIbanRequired' : 'required');
    } else if (!ACCOUNT_NUMBER_PATTERN.test(account)) {
      issues['accountNumber'] = issue('accountNumberFormat');
    }

    const rule = routingRuleFor(form.country);
    const routing = compactCode(form.routingCode);
    if (!routing) {
      if (routingRequired(rule, hasIban)) issues['routingCode'] = issue(`routing.${rule.type}.required`);
    } else if (!rule.pattern.test(routing)) {
      issues['routingCode'] = issue(`routing.${rule.type}.format`);
    }
  }

  if (!form.proofDmsDocumentId) issues['proofDmsDocumentId'] = issue('proofRequired');
  return issues;
}

/** Every problem of a KYC document being added, by field (type, number, file). Mirrors OnboardingRules.document. */
export function documentIssues(type: string, number: string, dmsDocumentId: string, types: readonly string[]): FieldIssues {
  const issues: FieldIssues = {};
  if (!type || !types.includes(type)) issues['type'] = issue('documentTypeRequired');
  const value = number.trim();
  if (tooLong(value, ONBOARDING_LIMITS.documentNumber)) issues['number'] = issue('tooLong', { max: ONBOARDING_LIMITS.documentNumber });
  else if (value && !DOCUMENT_NUMBER_PATTERN.test(value)) issues['number'] = issue('numberFormat');
  if (!dmsDocumentId) issues['file'] = issue('fileRequired');
  return issues;
}

/** True when the value is a real yyyy-mm-dd calendar date. */
export function isIsoDate(value: string): boolean {
  if (!ISO_DATE_PATTERN.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

/** Decimal places a currency has (2 for INR, 0 for JPY, 3 for KWD), or null for an unknown code. */
export function currencyDigits(code: string): number | null {
  try {
    return new Intl.NumberFormat('en', { style: 'currency', currency: code }).resolvedOptions().maximumFractionDigits ?? null;
  } catch {
    return null;
  }
}

/** Decimal places of a number as typed (12.50 → 1, 12.345 → 3). */
export function decimalPlaces(value: number): number {
  const text = Math.abs(value).toFixed(20).replace(/0+$/, '');
  const dot = text.indexOf('.');
  return dot < 0 ? 0 : text.length - dot - 1;
}

function dateIssue(value: string | null | undefined, required: boolean): FieldIssue | null {
  const text = (value ?? '').trim();
  if (!text) return required ? issue('required') : null;
  return isIsoDate(text) ? null : issue('dateInvalid');
}

/**
 * Every problem of an agreement form (`today` as yyyy-mm-dd), by field. Mirrors OnboardingRules.agreement: number required
 * (≤50, letters/digits/space . / -), signed on required and not after today, starts on required, ends on optional and not before
 * the start, renewal required, deposit optional (zero or more, no more decimals than its currency has), signed copy required.
 */
export function agreementIssues(form: AgreementRequest, today: string, renewals: readonly string[]): FieldIssues {
  const issues: FieldIssues = {};
  const number = form.agreementNumber.trim();
  if (!number) issues['agreementNumber'] = issue('required');
  else if (tooLong(number, ONBOARDING_LIMITS.agreementNumber)) issues['agreementNumber'] = issue('tooLong', { max: ONBOARDING_LIMITS.agreementNumber });
  else if (!DOCUMENT_NUMBER_PATTERN.test(number)) issues['agreementNumber'] = issue('numberFormat');

  const signed = dateIssue(form.signedOn, true);
  if (signed) issues['signedOn'] = signed;
  else if (form.signedOn > today) issues['signedOn'] = issue('signedInFuture');
  const starts = dateIssue(form.startsOn, true);
  if (starts) issues['startsOn'] = starts;
  const ends = dateIssue(form.endsOn, false);
  if (ends) issues['endsOn'] = ends;
  else if (form.endsOn && !starts && form.endsOn < form.startsOn) issues['endsOn'] = issue('endBeforeStart');

  if (!form.renewal || !renewals.includes(form.renewal)) issues['renewal'] = issue('required');

  const deposit = form.securityDeposit;
  const value = deposit?.value;
  if (deposit && value !== null && value !== undefined) {
    const digits = deposit.code ? currencyDigits(deposit.code) : null;
    if (!Number.isFinite(value) || value < 0) issues['securityDeposit'] = issue('amountInvalid');
    else if (digits === null) issues['securityDeposit'] = issue('depositCurrencyRequired');
    else if (decimalPlaces(value) > digits) issues['securityDeposit'] = issue('amountDecimals', { digits, currency: deposit.code });
  }

  if (!form.signedCopyDmsDocumentId) issues['signedCopyDmsDocumentId'] = issue('signedCopyRequired');
  return issues;
}

/** True when the form has at least one issue. */
export function hasIssues(issues: FieldIssues): boolean {
  return Object.keys(issues).length > 0;
}

/**
 * The field error of a failed save, when the service named a field: it answers 400 with the message key and a
 * `[{ field, message }]` list (as `responseObject`, or `errors`). Null for anything else (stale version, network, 5xx).
 */
export function serverFieldError(err: unknown): ServerFieldError | null {
  const shape = (err ?? {}) as { status?: unknown; error?: unknown };
  if (shape.status !== 400 || typeof shape.error !== 'object' || shape.error === null) return null;
  const body = shape.error as { responseObject?: unknown; errors?: unknown };
  const list = Array.isArray(body.responseObject) ? body.responseObject : Array.isArray(body.errors) ? body.errors : [];
  for (const entry of list as unknown[]) {
    const { field, message } = (entry ?? {}) as { field?: unknown; message?: unknown };
    if (typeof field !== 'string' || !field || typeof message !== 'string' || !message.startsWith(ONBOARDING_ERROR_PREFIX)) continue;
    return { field: ONBOARDING_SERVER_FIELDS[field] ?? field, code: message.slice(ONBOARDING_ERROR_PREFIX.length) };
  }
  return null;
}

/**
 * The inline message for a server field error: the page's own, more specific message for that field when its checks see the
 * problem too, otherwise the translated service message (franchiseOnboarding.errors.*).
 */
export function serverIssue(error: ServerFieldError, clientIssues: FieldIssues): FieldIssue {
  return clientIssues[error.field] ?? { key: `${ONBOARDING_ERROR_PREFIX}errors.${error.code}` };
}

/** A server field error together with the value its field had when the error came back. */
export interface HeldServerError {
  error: ServerFieldError;
  snapshot: string;
}

/** The value of one form field, comparable across changes. */
export function fieldSnapshot(form: object, field: string): string {
  return JSON.stringify((form as Record<string, unknown>)[field] ?? null);
}

/**
 * The messages a form shows: its own checks once a save was tried, plus the service's error for a field until that field
 * changes.
 */
export function shownIssues(client: FieldIssues, tried: boolean, server: HeldServerError | null, form: object): FieldIssues {
  const shown: FieldIssues = tried ? { ...client } : {};
  const field = server?.error.field;
  if (server && field && !shown[field] && fieldSnapshot(form, field) === server.snapshot) shown[field] = serverIssue(server.error, client);
  return shown;
}
