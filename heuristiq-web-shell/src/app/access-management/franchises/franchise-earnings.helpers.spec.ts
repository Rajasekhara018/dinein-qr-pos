import { describe, expect, it } from 'vitest';
import { PARTY_LABEL_KEYS } from './franchise-earnings.constants';
import { isLastPage, labelKey, ledgerQuery, payableParties, payoutFormError, summaryCurrency, toPayoutBody } from './franchise-earnings.helpers';
import { StatementSummary } from './franchise.models';

describe('franchise earnings helpers', () => {
  it('builds a query string from the filter and leaves empty values out', () => {
    expect(ledgerQuery({})).toBe('');
    expect(ledgerQuery({ franchiseId: '', party: '', from: '', to: '' })).toBe('');
    expect(ledgerQuery({ franchiseId: 'f1', party: 'AGENT', from: '2026-01-01', to: '2026-01-31' }, 0, 20))
      .toBe('?franchiseId=f1&party=AGENT&from=2026-01-01&to=2026-01-31&page=0&size=20');
  });

  it('finds the translation key of a code, or null so the raw code is shown', () => {
    expect(labelKey(PARTY_LABEL_KEYS, 'OWNER')).toBe('accessManagement.myEarnings.parties.OWNER');
    expect(labelKey(PARTY_LABEL_KEYS, 'UNKNOWN')).toBeNull();
    expect(labelKey(PARTY_LABEL_KEYS, null)).toBeNull();
  });

  it('names what is wrong with a payout form, in the order the fields appear', () => {
    const ok = { franchiseId: 'f1', party: 'OWNER', amount: 10, currency: 'INR' };

    expect(payoutFormError({ ...ok, franchiseId: '' })).toBe('accessManagement.myPayouts.errors.franchise');
    expect(payoutFormError({ ...ok, party: '' })).toBe('accessManagement.myPayouts.errors.party');
    expect(payoutFormError({ ...ok, amount: null })).toBe('accessManagement.myPayouts.errors.amount');
    expect(payoutFormError({ ...ok, amount: 0 })).toBe('accessManagement.myPayouts.errors.amount');
    expect(payoutFormError({ ...ok, amount: Number.NaN })).toBe('accessManagement.myPayouts.errors.amount');
    expect(payoutFormError({ ...ok, currency: 'RUPEE' })).toBe('accessManagement.myPayouts.errors.currency');
    expect(payoutFormError(ok)).toBeNull();
  });

  it('sends the payout amount as { code, value } with the currency upper-cased inside it', () => {
    expect(toPayoutBody({ franchiseId: 'f1', party: 'AGENT', amount: 12.5, currency: 'inr' }))
      .toEqual({ franchiseId: 'f1', party: 'AGENT', amount: { code: 'INR', value: 12.5 } });
  });

  it('reads the currency of a summary row from its amounts', () => {
    expect(summaryCurrency({ net: { code: 'AED', value: 1 }, gst: null, gross: null })).toBe('AED');
    expect(summaryCurrency({ net: null, gst: null, gross: { code: 'usd', value: 2 } })).toBe('USD');
    expect(summaryCurrency({ net: 5, gst: 1, gross: 6 } as unknown as StatementSummary)).toBe('');
  });

  it('knows the last page', () => {
    expect(isLastPage(0, 20, 20)).toBe(true);
    expect(isLastPage(0, 20, 21)).toBe(false);
    expect(isLastPage(1, 20, 21)).toBe(true);
    expect(isLastPage(0, 20, 0)).toBe(true);
  });
});

describe('payableParties', () => {
  const payable = ['OWNER', 'AGENT', 'REFERRER', 'MEMBER_CASHBACK'];

  it('offers every payable party when there is no statement to look at', () => {
    expect(payableParties(null, payable)).toEqual(payable);
  });

  it('offers only the owner to someone who has earned nothing else', () => {
    expect(payableParties([], payable)).toEqual(['OWNER']);
  });

  it('adds the other parties the person has earned as, and ignores unpayable ones', () => {
    expect(payableParties(['REFERRER', 'PLATFORM'], payable)).toEqual(['OWNER', 'REFERRER']);
  });
});
