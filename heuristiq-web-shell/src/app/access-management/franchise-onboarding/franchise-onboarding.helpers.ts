import { TranslateService } from '@ngx-translate/core';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import {
  DOCUMENT_NUMBER_PATTERN, IBAN_COUNTRIES, IBAN_PATTERN, IBAN_REQUIRED_COUNTRIES, ONBOARDING_ERROR_PREFIX, ONBOARDING_LIMITS, ROUTING_RULES,
  RoutingRule, SWIFT_RULE,
} from './franchise-onboarding.constants';
import { Agreement, AgreementRequest, BankAccount, BankAccountRequest } from './franchise-onboarding.models';

/** Upper case without spaces or hyphens, as the service stores bank codes and numbers. */
export function compactCode(value: string | null | undefined): string {
  return (value ?? '').replace(/[\s-]+/g, '').toUpperCase();
}

/** The routing code a country's bank accounts use (IFSC, ABA, sort code), or an optional SWIFT/BIC elsewhere. */
export function routingRuleFor(country: string | null | undefined): RoutingRule {
  return ROUTING_RULES[(country ?? '').toUpperCase()] ?? SWIFT_RULE;
}

export function ibanRequired(country: string | null | undefined): boolean {
  return IBAN_REQUIRED_COUNTRIES.includes((country ?? '').toUpperCase());
}

/** True when the bank's country accepts an IBAN at all (the service refuses one elsewhere). */
export function ibanAccepted(country: string | null | undefined): boolean {
  return IBAN_COUNTRIES.includes((country ?? '').toUpperCase());
}

/** ISO 7064 mod-97 check of an IBAN already in compact form. */
export function ibanValid(iban: string): boolean {
  if (!IBAN_PATTERN.test(iban)) return false;
  const rearranged = iban.slice(4) + iban.slice(0, 4);
  let remainder = 0;
  for (const ch of rearranged) {
    const digits = /[A-Z]/.test(ch) ? String(ch.charCodeAt(0) - 55) : ch;
    for (const d of digits) remainder = (remainder * 10 + Number(d)) % 97;
  }
  return remainder === 1;
}

/** A bank account form with nothing filled in, in the given country and currency. */
export function emptyBankAccount(country: string, currency: string): BankAccountRequest {
  return {
    holderName: '', country, currency, bankName: null, accountNumber: null, iban: null, routingCode: null,
    proofDmsDocumentId: '', proofFileName: '', version: null,
  };
}

export function bankRequestOf(account: BankAccount): BankAccountRequest {
  return {
    holderName: account.holderName, country: account.country, currency: account.currency, bankName: account.bankName,
    accountNumber: account.accountNumber, iban: account.iban, routingCode: account.routingCode,
    proofDmsDocumentId: account.proofDmsDocumentId, proofFileName: account.proofFileName, version: account.version,
  };
}

/**
 * The bank account as sent: trimmed, codes compact, empty text as null. Fields the page hides for the country are left out: the
 * IBAN where the country uses none, the account number and routing code where only an IBAN is used (AE, SA).
 */
export function normalizedBankRequest(form: BankAccountRequest): BankAccountRequest {
  const blankToNull = (v: string | null | undefined) => (v && v.trim() ? v.trim() : null);
  const ibanOnly = ibanRequired(form.country);
  return {
    ...form,
    holderName: form.holderName.trim(),
    bankName: blankToNull(form.bankName),
    accountNumber: ibanOnly ? null : compactCode(form.accountNumber) || null,
    iban: ibanAccepted(form.country) ? compactCode(form.iban) || null : null,
    routingCode: ibanOnly ? null : compactCode(form.routingCode) || null,
  };
}

export function emptyAgreement(currency: string): AgreementRequest {
  return {
    agreementNumber: '', signedOn: '', startsOn: '', endsOn: null, renewal: 'BY_NOTICE', exclusiveTerritory: false,
    securityDeposit: { code: currency, value: null as unknown as number }, signedCopyDmsDocumentId: '', signedCopyFileName: '', version: null,
  };
}

export function agreementRequestOf(agreement: Agreement, currency: string): AgreementRequest {
  return {
    agreementNumber: agreement.agreementNumber, signedOn: agreement.signedOn, startsOn: agreement.startsOn, endsOn: agreement.endsOn,
    renewal: agreement.renewal, exclusiveTerritory: agreement.exclusiveTerritory,
    securityDeposit: agreement.securityDeposit ?? { code: currency, value: null as unknown as number },
    signedCopyDmsDocumentId: agreement.signedCopyDmsDocumentId, signedCopyFileName: agreement.signedCopyFileName, version: agreement.version,
  };
}

/** The agreement as sent: trimmed, an empty end date or deposit left out. */
export function normalizedAgreementRequest(form: AgreementRequest): AgreementRequest {
  const deposit = form.securityDeposit;
  const hasDeposit = deposit && deposit.value !== null && deposit.value !== undefined && Number.isFinite(deposit.value);
  return { ...form, agreementNumber: form.agreementNumber.trim(), endsOn: form.endsOn || null, securityDeposit: hasDeposit ? deposit : null };
}

/** The first problem with a KYC document being added, or null. */
export function documentError(type: string, number: string, dmsDocumentId: string): string | null {
  if (!type || !dmsDocumentId) return 'required';
  const value = number.trim();
  if (value.length > ONBOARDING_LIMITS.documentNumber) return 'tooLong';
  return value && !DOCUMENT_NUMBER_PATTERN.test(value) ? 'invalidDocumentNumber' : null;
}

/** Today in the browser's time zone as yyyy-mm-dd (an agreement cannot be signed later than that). */
export function todayIso(now: Date = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

/** The translated reason a call failed: a franchiseOnboarding.* key from the service, its own text, or the fallback. */
export function onboardingError(err: unknown, translate: TranslateService, fallbackKey: string): string {
  const message = apiErrorMessage(err, translate.instant(fallbackKey));
  if (!message.startsWith(ONBOARDING_ERROR_PREFIX)) return message;
  return translate.instant(`${ONBOARDING_ERROR_PREFIX}errors.${message.slice(ONBOARDING_ERROR_PREFIX.length)}`);
}
