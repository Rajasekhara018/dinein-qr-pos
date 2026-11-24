import { describe, expect, it } from 'vitest';
import { AGREEMENT_RENEWALS, KYC_DOCUMENT_TYPES } from './franchise-onboarding.constants';
import { emptyAgreement, emptyBankAccount } from './franchise-onboarding.helpers';
import { AgreementRequest, BankAccountRequest } from './franchise-onboarding.models';
import {
  accountNumberRequired, agreementIssues, bankAccountIssues, currencyDigits, decimalPlaces, documentIssues, isIsoDate, serverFieldError,
  serverIssue, shownIssues,
} from './franchise-onboarding.validation';

const V = 'franchiseOnboarding.validation.';

function bank(overrides: Partial<BankAccountRequest> = {}): BankAccountRequest {
  return {
    ...emptyBankAccount('IN', 'INR'), holderName: 'Acme Pvt Ltd', accountNumber: '50100012345678', routingCode: 'HDFC0001234',
    proofDmsDocumentId: 'dms-1', proofFileName: 'cheque.jpg', ...overrides,
  };
}

const keys = (issues: object) => Object.fromEntries(Object.entries(issues).map(([field, issue]) => [field, (issue as { key: string }).key]));

describe('bank account checks (mirror OnboardingRules.bankAccount)', () => {
  it('accepts a complete Indian account; the bank name is optional', () => {
    expect(bankAccountIssues(bank())).toEqual({});
    expect(bankAccountIssues(bank({ bankName: null, routingCode: ' hdfc-0001234 ' }))).toEqual({});
  });

  it('requires a well-formed IFSC in India', () => {
    expect(keys(bankAccountIssues(bank({ routingCode: null })))).toEqual({ routingCode: `${V}routing.IFSC.required` });
    expect(keys(bankAccountIssues(bank({ routingCode: 'HDFC1001234' })))).toEqual({ routingCode: `${V}routing.IFSC.format` });
    expect(keys(bankAccountIssues(bank({ routingCode: 'HDF0001234' })))).toEqual({ routingCode: `${V}routing.IFSC.format` });
  });

  it('reports every missing or broken field at once', () => {
    const issues = bankAccountIssues(bank({ holderName: ' ', accountNumber: '12', proofDmsDocumentId: '', country: '', currency: '' }));
    expect(keys(issues)).toMatchObject({
      holderName: `${V}required`, country: `${V}required`, currency: `${V}required`, accountNumber: `${V}accountNumberFormat`,
      proofDmsDocumentId: `${V}proofRequired`,
    });
    expect(bankAccountIssues(bank({ holderName: 'x'.repeat(201) }))['holderName']).toEqual({ key: `${V}tooLong`, params: { max: 200 } });
    expect(bankAccountIssues(bank({ bankName: 'x'.repeat(101) }))['bankName']?.key).toBe(`${V}tooLong`);
  });

  it('requires an ABA routing number in the US and accepts an optional SWIFT/BIC elsewhere', () => {
    expect(keys(bankAccountIssues(bank({ country: 'US', currency: 'USD', routingCode: '12345678' })))).toEqual({ routingCode: `${V}routing.ABA.format` });
    expect(bankAccountIssues(bank({ country: 'US', currency: 'USD', routingCode: '021000021' }))).toEqual({});
    expect(bankAccountIssues(bank({ country: 'JP', currency: 'JPY', routingCode: null }))).toEqual({});
    expect(keys(bankAccountIssues(bank({ country: 'JP', currency: 'JPY', routingCode: 'XX' })))).toEqual({ routingCode: `${V}routing.SWIFT.format` });
  });

  it('needs the IBAN in the UAE and nothing else', () => {
    expect(keys(bankAccountIssues(bank({ country: 'AE', currency: 'AED', accountNumber: null, routingCode: null })))).toEqual({ iban: `${V}ibanRequired` });
    expect(bankAccountIssues(bank({ country: 'AE', currency: 'AED', iban: 'AE07 0331 2345 6789 0123 456', accountNumber: null, routingCode: null }))).toEqual({});
    expect(keys(bankAccountIssues(bank({ country: 'AE', currency: 'AED', iban: 'AE08 0331 2345 6789 0123 456' })))).toEqual({ iban: `${V}ibanInvalid` });
  });

  it('in the UK takes an IBAN instead of the account number and sort code, or both of those', () => {
    const uk = { country: 'GB', currency: 'GBP' };
    expect(bankAccountIssues(bank({ ...uk, iban: 'GB82WEST12345698765432', accountNumber: null, routingCode: null }))).toEqual({});
    expect(bankAccountIssues(bank({ ...uk, accountNumber: '31926819', routingCode: '60-16-13' }))).toEqual({});
    expect(keys(bankAccountIssues(bank({ ...uk, accountNumber: null, routingCode: null })))).toEqual({
      accountNumber: `${V}accountOrIbanRequired`, routingCode: `${V}routing.SORT_CODE.required`,
    });
    expect(accountNumberRequired({ country: 'GB', iban: 'GB82WEST12345698765432' })).toBe(false);
    expect(accountNumberRequired({ country: 'IN', iban: 'GB82WEST12345698765432' })).toBe(true);
  });

  it('ignores an IBAN typed for a country that uses none (it is not sent)', () => {
    expect(bankAccountIssues(bank({ iban: 'garbage' }))).toEqual({});
  });
});

describe('KYC document checks (mirror OnboardingRules.document)', () => {
  it('needs a listed type and a file; the number is optional but well-formed', () => {
    expect(documentIssues('IDENTITY_PROOF', '', 'dms-1', KYC_DOCUMENT_TYPES)).toEqual({});
    expect(documentIssues('IDENTITY_PROOF', 'ABCDE1234F', 'dms-1', KYC_DOCUMENT_TYPES)).toEqual({});
    expect(keys(documentIssues('PASSPORT', '#bad', '', KYC_DOCUMENT_TYPES))).toEqual({
      type: `${V}documentTypeRequired`, number: `${V}numberFormat`, file: `${V}fileRequired`,
    });
    expect(documentIssues('IDENTITY_PROOF', '1'.repeat(51), 'dms-1', KYC_DOCUMENT_TYPES)['number']?.key).toBe(`${V}tooLong`);
  });
});

describe('agreement checks (mirror OnboardingRules.agreement)', () => {
  const today = '2026-11-08';
  const base: AgreementRequest = {
    ...emptyAgreement('INR'), agreementNumber: 'FA-1', signedOn: '2026-11-01', startsOn: '2026-11-01', signedCopyDmsDocumentId: 'dms-2',
    signedCopyFileName: 'a.pdf',
  };
  const check = (overrides: Partial<AgreementRequest>) => keys(agreementIssues({ ...base, ...overrides }, today, AGREEMENT_RENEWALS));

  it('accepts a complete, open-ended agreement and one signed today', () => {
    expect(check({})).toEqual({});
    expect(check({ signedOn: today, endsOn: '2026-11-01' })).toEqual({});
  });

  it('requires the number, dates, renewal and signed copy', () => {
    expect(check({ agreementNumber: ' ', signedOn: '', startsOn: '', renewal: '', signedCopyDmsDocumentId: '' })).toEqual({
      agreementNumber: `${V}required`, signedOn: `${V}required`, startsOn: `${V}required`, renewal: `${V}required`,
      signedCopyDmsDocumentId: `${V}signedCopyRequired`,
    });
    expect(check({ agreementNumber: '#1' })).toEqual({ agreementNumber: `${V}numberFormat` });
    expect(check({ renewal: 'MONTHLY' })).toEqual({ renewal: `${V}required` });
  });

  it('checks the dates: real dates, not signed after today, not ending before the start', () => {
    expect(check({ signedOn: '2026-11-09' })).toEqual({ signedOn: `${V}signedInFuture` });
    expect(check({ endsOn: '2026-10-31' })).toEqual({ endsOn: `${V}endBeforeStart` });
    expect(check({ startsOn: '2026-02-30' })).toEqual({ startsOn: `${V}dateInvalid` });
    expect(isIsoDate('2028-02-29')).toBe(true);
    expect(isIsoDate('2027-02-29')).toBe(false);
  });

  it('checks the deposit: zero or more, no more decimals than its currency', () => {
    expect(check({ securityDeposit: { code: 'INR', value: 0 } })).toEqual({});
    expect(check({ securityDeposit: { code: 'INR', value: -1 } })).toEqual({ securityDeposit: `${V}amountInvalid` });
    expect(agreementIssues({ ...base, securityDeposit: { code: 'JPY', value: 10.5 } }, today, AGREEMENT_RENEWALS)['securityDeposit'])
      .toEqual({ key: `${V}amountDecimals`, params: { digits: 0, currency: 'JPY' } });
    expect(check({ securityDeposit: { code: 'INR', value: 10.25 } })).toEqual({});
    expect(check({ securityDeposit: { code: 'XYZ1', value: 10 } })).toEqual({ securityDeposit: `${V}depositCurrencyRequired` });
    expect(currencyDigits('KWD')).toBe(3);
    expect(decimalPlaces(12.5)).toBe(1);
    expect(decimalPlaces(100)).toBe(0);
  });
});

describe('server field errors', () => {
  const refusal = (field: string, key: string) => ({ status: 400, error: { message: key, responseObject: [{ field, message: key }] } });

  it('reads the field and key of a 400 and maps file fields to their upload', () => {
    expect(serverFieldError(refusal('routingCode', 'franchiseOnboarding.invalidRoutingCode'))).toEqual({ field: 'routingCode', code: 'invalidRoutingCode' });
    expect(serverFieldError(refusal('proofFileName', 'franchiseOnboarding.required'))).toEqual({ field: 'proofDmsDocumentId', code: 'required' });
    expect(serverFieldError(refusal('securityDeposit.value', 'franchiseOnboarding.invalidAmount'))?.field).toBe('securityDeposit');
    expect(serverFieldError({ status: 400, error: { errors: [{ field: 'iban', message: 'franchiseOnboarding.invalidIban' }] } })?.field).toBe('iban');
  });

  it('ignores anything that is not a field refusal', () => {
    expect(serverFieldError({ status: 409, error: { message: 'franchiseOnboarding.staleVersion' } })).toBeNull();
    expect(serverFieldError({ status: 400, error: { responseObject: [{ field: 'x', message: 'Some text' }] } })).toBeNull();
    expect(serverFieldError({ status: 0 })).toBeNull();
    expect(serverFieldError(null)).toBeNull();
  });

  it("prefers the page's specific message and shows the service's until the field changes", () => {
    const form = bank({ routingCode: 'BAD' });
    const error = { field: 'routingCode', code: 'invalidRoutingCode' };
    expect(serverIssue(error, bankAccountIssues(form))).toEqual({ key: `${V}routing.IFSC.format` });
    expect(serverIssue(error, {})).toEqual({ key: 'franchiseOnboarding.errors.invalidRoutingCode' });
    const held = { error, snapshot: JSON.stringify('BAD') };
    expect(Object.keys(shownIssues({}, false, held, form))).toEqual(['routingCode']);
    expect(shownIssues({}, false, held, { ...form, routingCode: 'HDFC0001234' })).toEqual({});
    expect(shownIssues({ holderName: { key: 'k' } }, false, null, form)).toEqual({});
    expect(shownIssues({ holderName: { key: 'k' } }, true, null, form)).toEqual({ holderName: { key: 'k' } });
  });
});
