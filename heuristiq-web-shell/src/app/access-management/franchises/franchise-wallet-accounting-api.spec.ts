import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { FranchiseWalletAccountingApi, rowsOf, toAmount, toOwnerAccounting } from './franchise-wallet-accounting-api';
import { OwnerFranchiseAccounting } from './franchise-wallet-accounting.models';

const inr = (value: number) => ({ code: 'INR', value });

describe('FranchiseWalletAccountingApi', () => {
  let api: FranchiseWalletAccountingApi;
  let http: HttpTestingController;
  const url = `${environment.walletApiUrl}/me/franchises/f%201/accounting`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(FranchiseWalletAccountingApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('reads the owner accounting live from the wallet with the period as inclusive UTC bounds', () => {
    let result: OwnerFranchiseAccounting | undefined;
    api.mine('f 1', { app: 'RIDE', from: '2026-10-01', to: '2026-10-31' }).subscribe((value) => (result = value));
    const req = http.expectOne((r) => r.url === url);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('app')).toBe('RIDE');
    expect(req.request.params.get('from')).toBe('2026-10-01T00:00:00Z');
    expect(req.request.params.get('to')).toBe('2026-10-31T23:59:59Z');
    const shares = { platform: inr(10), owner: inr(6), agent: null, referrer: null, provider: inr(80), tax: inr(4), customerPool: null, memberCashback: null };
    req.flush({
      success: true,
      responseObject: {
        franchiseId: 'f 1', from: null, to: null,
        summary: { rows: [], totals: { appCode: null, eventType: null, channel: null, count: 3, gross: inr(100), refunds: null, shares } },
        shares,
        payable: { available: inr(7), pending: inr(1), paidOut: inr(4) },
        payouts: [{ payoutId: 'p1', amount: inr(4), status: 'PAID', createdAt: '2026-10-05T00:00:00Z', paidAt: '2026-10-06T00:00:00Z' }],
        recentTransactions: [{ journalId: 'j1', postedAt: '2026-10-02T00:00:00Z', appCode: 'RIDE', eventType: 'RIDE_COMPLETED', journalType: 'SALE', channel: 'CASH', amount: inr(100), status: 'POSTED' }],
      },
    });
    expect(result).toEqual({
      franchiseId: 'f 1',
      totals: { count: 3, gross: inr(100), refunds: null, owner: inr(6), agent: null },
      shares: [
        { shareParty: 'PLATFORM', amount: inr(10) }, { shareParty: 'OWNER', amount: inr(6) },
        { shareParty: 'PROVIDER', amount: inr(80) }, { shareParty: 'TAX', amount: inr(4) },
      ],
      payable: { available: inr(7), pending: inr(1), paidOut: inr(4) },
      payouts: [{ payoutId: 'p1', amount: inr(4), status: 'PAID', createdAt: '2026-10-05T00:00:00Z', paidAt: '2026-10-06T00:00:00Z' }],
      recentTransactions: [{ journalId: 'j1', postedAt: '2026-10-02T00:00:00Z', appCode: 'RIDE', eventType: 'RIDE_COMPLETED', journalType: 'SALE', channel: 'CASH', amount: inr(100), status: 'POSTED' }],
    });
  });

  it('sends no period or app when none is chosen, and fails on a refused envelope', () => {
    let failure: unknown;
    api.mine('f 1', { from: '', to: '' }).subscribe({ error: (e: unknown) => (failure = e) });
    const req = http.expectOne((r) => r.url === url);
    expect(req.request.params.keys()).toEqual([]);
    req.flush({ success: false, message: 'Not an owner', responseObject: null });
    expect(failure).toEqual({ error: { message: 'Not an owner', detail: 'Not an owner' } });
  });

  it('tolerates a missing or sparse body', () => {
    expect(toOwnerAccounting(null, 'f9')).toEqual({
      franchiseId: 'f9', totals: { count: 0, gross: null, refunds: null, owner: null, agent: null }, shares: [],
      payable: { available: null, pending: null, paidOut: null }, payouts: [], recentTransactions: [],
    });
    const sparse = toOwnerAccounting({ payouts: { content: [{}] }, recentTransactions: [{ journalId: 7 }], summary: { totals: { count: 'x' } } }, 'f9');
    expect(sparse.payouts[0]).toEqual({ payoutId: '', amount: null, status: '', createdAt: null, paidAt: null });
    expect(sparse.recentTransactions[0]).toMatchObject({ journalId: '7', journalType: '' });
    expect(sparse.totals.count).toBe(0);
    expect(rowsOf({ items: [] })).toEqual([]);
  });

  it('reads only {code, value} amounts', () => {
    expect(toAmount({ code: 'USD', value: '3' })).toEqual({ code: 'USD', value: 3 });
    expect(toAmount({ code: 'USD', value: null })).toBeNull();
    expect(toAmount({ code: 'USD', value: 'abc' })).toBeNull();
    expect(toAmount({ value: 3 })).toBeNull();
    expect(toAmount(5)).toBeNull();
  });
});
