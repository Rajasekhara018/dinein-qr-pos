import { describe, expect, it } from 'vitest';
import {
  compactCode, documentError, emptyAgreement, emptyBankAccount, ibanAccepted, ibanRequired, ibanValid,
  normalizedAgreementRequest, normalizedBankRequest, routingRuleFor, todayIso,
} from './franchise-onboarding.helpers';
import { BankAccountRequest } from './franchise-onboarding.models';

function bank(overrides: Partial<BankAccountRequest> = {}): BankAccountRequest {
  return {
    ...emptyBankAccount('IN', 'INR'), holderName: 'Acme Pvt Ltd', accountNumber: '50100012345678', routingCode: 'HDFC0001234',
    proofDmsDocumentId: 'dms-1', proofFileName: 'cheque.jpg', ...overrides,
  };
}

describe('franchise onboarding helpers', () => {
  it('checks IBANs with the mod-97 rule', () => {
    expect(ibanValid('GB82WEST12345698765432')).toBe(true);
    expect(ibanValid('AE070331234567890123456')).toBe(true);
    expect(ibanValid('GB82WEST12345698765431')).toBe(false);
    expect(ibanValid('GB82')).toBe(false);
    expect(compactCode(' gb82 west-1234 ')).toBe('GB82WEST1234');
  });

  it('picks the routing code of the bank country', () => {
    expect(routingRuleFor('in').type).toBe('IFSC');
    expect(routingRuleFor('US').type).toBe('ABA');
    expect(routingRuleFor('GB').type).toBe('SORT_CODE');
    expect(routingRuleFor('DE')).toMatchObject({ type: 'SWIFT', required: 'never' });
    expect(ibanRequired('ae')).toBe(true);
    expect(ibanRequired('IN')).toBe(false);
    expect(ibanAccepted('de')).toBe(true);
    expect(ibanAccepted('IN')).toBe(false);
  });

  it('sends bank codes compact and blank text as null', () => {
    expect(normalizedBankRequest(bank({ holderName: ' Acme ', bankName: ' ', routingCode: 'hdfc 0001234', iban: '' })))
      .toMatchObject({ holderName: 'Acme', bankName: null, routingCode: 'HDFC0001234', iban: null, accountNumber: '50100012345678' });
    // Hidden fields are not sent: no IBAN for India, only the IBAN in the UAE.
    expect(normalizedBankRequest(bank({ iban: 'GB82WEST12345698765432' })).iban).toBeNull();
    expect(normalizedBankRequest(bank({ country: 'AE', iban: 'ae07 0331 2345 6789 0123 456' })))
      .toMatchObject({ iban: 'AE070331234567890123456', accountNumber: null, routingCode: null });
  });

  it('leaves out an empty end date or deposit', () => {
    const base = { ...emptyAgreement('INR'), agreementNumber: 'FA-1', signedOn: '2026-11-01', startsOn: '2026-11-01', signedCopyDmsDocumentId: 'dms-2', signedCopyFileName: 'a.pdf' };
    expect(normalizedAgreementRequest({ ...base, endsOn: '' }).securityDeposit).toBeNull();
    expect(normalizedAgreementRequest({ ...base, securityDeposit: { code: 'INR', value: 500 } }).securityDeposit).toEqual({ code: 'INR', value: 500 });
  });

  it('checks a KYC document before it is sent', () => {
    expect(documentError('IDENTITY_PROOF', '', 'dms-1')).toBeNull();
    expect(documentError('IDENTITY_PROOF', 'ABCDE1234F', '')).toBe('required');
    expect(documentError('IDENTITY_PROOF', '#bad', 'dms-1')).toBe('invalidDocumentNumber');
    expect(documentError('IDENTITY_PROOF', '1'.repeat(51), 'dms-1')).toBe('tooLong');
  });

  it("gives today's date in the browser's day", () => {
    expect(todayIso(new Date(2026, 10, 8, 23, 30))).toBe('2026-11-08');
  });
});
