import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError, Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { FranchiseWalletAccountingApi } from './franchise-wallet-accounting-api';
import { OwnerFranchiseAccounting } from './franchise-wallet-accounting.models';
import { EarningsAccount, EarningsEntry, MyEarnings as MyEarningsView } from './franchise.models';
import { MyEarnings } from './my-earnings';

const entry = (id: string): EarningsEntry => ({
  entryId: id, journalId: `j-${id}`, journalType: 'SALE', direction: 'CREDIT', amount: { code: 'INR', value: 50 }, appCode: 'RIDE',
  postedAt: '2026-01-02T10:00:00Z', description: null, referenceType: 'SALE', referenceId: 'evt-1', journalStatus: 'POSTED',
});
const franchiseAccount: EarningsAccount = {
  ownerType: 'FRANCHISE', ownerId: 'f1', franchiseCode: 'RBM-1', settled: { code: 'INR', value: 300 }, pending: { code: 'INR', value: 50 },
  reserved: { code: 'INR', value: 60 }, available: { code: 'INR', value: 240 },
};
const agentAccount: EarningsAccount = { ...franchiseAccount, ownerType: 'AGENT', ownerId: 'u1', franchiseCode: null };
const earnings: MyEarningsView = { accounts: [agentAccount, franchiseAccount] };

const api = { myEntries: vi.fn(), myEarnings: vi.fn() };
const walletAccounting = { mine: vi.fn() };
const ACCOUNTING: OwnerFranchiseAccounting = {
  franchiseId: 'f1',
  totals: { count: 3, gross: { code: 'INR', value: 150.3 }, refunds: null, owner: { code: 'INR', value: 10.05 }, agent: { code: 'INR', value: 5 } },
  shares: [{ shareParty: 'OWNER', amount: { code: 'INR', value: 10.05 } }],
  payable: { available: { code: 'INR', value: 7 }, pending: null, paidOut: { code: 'INR', value: 2 } },
  payouts: [],
  recentTransactions: [],
};
const notify = { success: vi.fn(), error: vi.fn() };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

function create() {
  const fixture = TestBed.createComponent(MyEarnings);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

describe('MyEarnings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    api.myEntries.mockReturnValue(of({ items: [entry('1')], page: 0, size: 20, totalElements: 1 }));
    api.myEarnings.mockReturnValue(of(earnings));
    walletAccounting.mine.mockReturnValue(of(ACCOUNTING));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [MyEarnings],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: FranchiseApi, useValue: api },
        { provide: FranchiseWalletAccountingApi, useValue: walletAccounting },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
  });

  it('shows each account\'s pending apart from available, from the wallet, and the agent account\'s settled entries', () => {
    const { component, fixture } = create();

    expect(component.accounts).toEqual([agentAccount, franchiseAccount]);
    expect(component.entries).toHaveLength(1);
    expect(api.myEntries).toHaveBeenCalledWith({ account: 'AGENT', state: 'AVAILABLE', from: '', to: '' }, 0, 20);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('accessManagement.myEarnings.walletNotice');
    expect(text).toContain('accessManagement.myEarnings.accountsTitle');
  });

  it('reads the pending entries of a franchise the person owns for a period', () => {
    const { component } = create();
    component.selectedAccount = 'FRANCHISE:f1';
    component.state = 'PENDING';
    component.from = '2026-01-01';
    component.to = '2026-01-31';

    component.load();

    expect(api.myEntries).toHaveBeenLastCalledWith(
      { account: 'FRANCHISE', franchiseId: 'f1', state: 'PENDING', from: '2026-01-01', to: '2026-01-31' }, 0, 20);
  });

  it('opens on the first account the person has when they have no agent account', () => {
    api.myEarnings.mockReturnValue(of({ accounts: [franchiseAccount] }));

    const { component } = create();

    expect(component.selectedAccount).toBe('FRANCHISE:f1');
    expect(api.myEntries).toHaveBeenLastCalledWith(expect.objectContaining({ account: 'FRANCHISE', franchiseId: 'f1' }), 0, 20);
  });

  it('reports the wallet being unreachable instead of showing zero', () => {
    api.myEarnings.mockReturnValue(throwError(() => ({ status: 503 })));

    const { component } = create();

    expect(component.hasError).toBe(true);
    expect(component.accounts).toEqual([]);
    expect(notify.error).toHaveBeenCalledWith('accessManagement.myEarnings.errorMessage');
  });

  it('reads a franchise accounting from the wallet owner endpoint, only for franchise accounts and with its privilege', () => {
    const { component } = create();
    component.loadAccounting();
    expect(walletAccounting.mine).not.toHaveBeenCalled();
    component.selectedAccount = 'FRANCHISE:f1';
    component.from = '2026-10-01';
    component.loadAccounting();
    expect(walletAccounting.mine).toHaveBeenCalledWith('f1', { from: '2026-10-01', to: '' });
    expect(component.accounting).toEqual(ACCOUNTING);
    walletAccounting.mine.mockClear();
    granted = new Set(['COMMISSION_STATEMENT_MINE_READ']);
    component.loadAccounting();
    expect(walletAccounting.mine).not.toHaveBeenCalled();
    expect(component.accounting).toBeNull();
  });

  it('labels share roles, channels, events and payout statuses and shows unknown codes as they are', () => {
    const { component } = create();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { accessManagement: { myEarnings: { parties: { OWNER: 'Owner' }, channels: { CASH: 'Cash' },
      events: { RIDE_COMPLETED: 'Ride' } }, myPayouts: { statuses: { PAID: 'Paid' } } } });
    translate.use('en');
    expect(component.codeLabel('share', 'OWNER')).toBe('Owner');
    expect(component.codeLabel('channel', 'CASH')).toBe('Cash');
    expect(component.codeLabel('event', 'RIDE_COMPLETED')).toBe('Ride');
    expect(component.codeLabel('payout', 'PAID')).toBe('Paid');
    expect(component.codeLabel('channel', 'CARRIER_PIGEON')).toBe('CARRIER_PIGEON');
    expect(component.codeLabel('event', null)).toBe('');
  });

  it('keeps an accounting outage separate and drops stale responses after account selection changes', () => {
    const { component } = create();
    component.selectedAccount = 'FRANCHISE:f1';
    walletAccounting.mine.mockReturnValue(throwError(() => new Error('unavailable')));
    component.loadAccounting();
    expect(component.accountingError).toBe(true);
    expect(component.accountingLoading).toBe(false);
    const pending = new Subject<OwnerFranchiseAccounting>();
    walletAccounting.mine.mockReturnValue(pending);
    component.loadAccounting();
    component.selectedAccount = 'FRANCHISE:f2';
    component.resetAccounting();
    pending.next(ACCOUNTING);
    expect(component.accounting).toBeNull();
    expect(component.accountingLoading).toBe(false);
  });

  it('reports a failure to load the entries', () => {
    api.myEntries.mockReturnValue(throwError(() => new Error('down')));

    const { component } = create();

    expect(component.hasError).toBe(true);
    expect(component.isLoading).toBe(false);
  });

  it('reads nothing it has no privilege for', () => {
    granted = new Set<string>();

    const { component } = create();

    expect(api.myEarnings).not.toHaveBeenCalled();
    expect(api.myEntries).not.toHaveBeenCalled();
    expect(component.entries).toEqual([]);
  });

  it('pages, changes the page size and never goes before the first page', () => {
    api.myEntries.mockReturnValue(of({ items: [entry('1')], page: 0, size: 20, totalElements: 45 }));
    const { component } = create();

    expect(component.isFirstPage).toBe(true);
    component.goToPage(-3);
    expect(component.page).toBe(0);
    component.goToPage(2);
    expect(component.isLast).toBe(true);
    component.onPageSizeChange(50);
    expect(component.page).toBe(0);
    expect(api.myEntries).toHaveBeenLastCalledWith(expect.anything(), 0, 50);
  });

  it('names accounts, views and directions in the user\'s language and shows unknown codes as they are', () => {
    const { component } = create();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      accessManagement: { myEarnings: { agentAccount: 'My agent earnings', franchiseAccount: 'Franchise {{code}}', states: { PENDING: 'Pending' },
        directions: { CREDIT: 'Credit' } } },
    });
    translate.use('en');

    expect(component.accountLabel(agentAccount)).toBe('My agent earnings');
    expect(component.accountLabel(franchiseAccount)).toBe('Franchise RBM-1');
    expect(component.accountLabel({ ...franchiseAccount, franchiseCode: null })).toBe('Franchise f1');
    expect(component.stateLabel('PENDING')).toBe('Pending');
    expect(component.stateLabel('ODD')).toBe('ODD');
    expect(component.directionLabel('CREDIT')).toBe('Credit');
    expect(component.directionLabel('SIDEWAYS')).toBe('SIDEWAYS');
    expect(component.directionLabel(null)).toBe('');
  });
});
