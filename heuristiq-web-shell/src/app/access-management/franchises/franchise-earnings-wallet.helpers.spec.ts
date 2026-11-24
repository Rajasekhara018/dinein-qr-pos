import { describe, expect, it } from 'vitest';
import { accountFromKey, accountKey, earningsQuery, toPayoutBody } from './franchise-earnings.helpers';

describe('earningsQuery', () => {
  it('always names the account and leaves empty values out', () => {
    expect(earningsQuery({ account: 'AGENT' })).toBe('?account=AGENT');
    expect(earningsQuery({ account: 'AGENT', franchiseId: 'f1', from: '', to: '' }, 0, 20)).toBe('?account=AGENT&page=0&size=20');
  });

  it('sends the franchise only for a franchise account, with the view and the period', () => {
    expect(earningsQuery({ account: 'FRANCHISE', franchiseId: 'f1', state: 'PENDING', from: '2026-01-01', to: '2026-01-31' }, 1, 50))
      .toBe('?account=FRANCHISE&franchiseId=f1&state=PENDING&from=2026-01-01&to=2026-01-31&page=1&size=50');
  });
});

describe('account keys', () => {
  it('round-trips an agent and a franchise account', () => {
    expect(accountKey({ ownerType: 'AGENT', ownerId: 'u1' })).toBe('AGENT');
    expect(accountKey({ ownerType: 'FRANCHISE', ownerId: 'f1' })).toBe('FRANCHISE:f1');
    expect(accountFromKey('FRANCHISE:f1')).toEqual({ account: 'FRANCHISE', franchiseId: 'f1' });
    expect(accountFromKey('AGENT')).toEqual({ account: 'AGENT' });
    expect(accountFromKey('anything')).toEqual({ account: 'AGENT' });
  });
});

describe('toPayoutBody with the wallet fields', () => {
  it('sends the application in upper case and the note trimmed, and leaves blank ones out', () => {
    expect(toPayoutBody({ franchiseId: 'f1', party: 'AGENT', amount: 5, currency: 'INR', appCode: ' ride ', note: ' hi ' }))
      .toEqual({ franchiseId: 'f1', party: 'AGENT', amount: { code: 'INR', value: 5 }, appCode: 'RIDE', note: 'hi' });
    expect(toPayoutBody({ franchiseId: 'f1', party: 'AGENT', amount: 5, currency: 'INR', appCode: ' ', note: '' }))
      .toEqual({ franchiseId: 'f1', party: 'AGENT', amount: { code: 'INR', value: 5 } });
  });
});
