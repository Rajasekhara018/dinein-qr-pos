import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { EarningsAccount, WalletPayout } from './franchise.models';
import { MyPayouts } from './my-payouts';

const payout: WalletPayout = {
  payoutId: 'wp-1', ownerType: 'FRANCHISE', ownerId: 'f1', appCode: 'RIDE', amount: { code: 'INR', value: 100 }, status: 'PENDING_APPROVAL',
  note: 'monthly', statusReason: null, bankReference: null, createdAt: '2026-01-02T10:00:00Z', replayed: false,
};
const franchiseAccount: EarningsAccount = {
  ownerType: 'FRANCHISE', ownerId: 'f1', franchiseCode: 'RBM-1', settled: { code: 'INR', value: 300 }, pending: { code: 'INR', value: 50 },
  reserved: { code: 'INR', value: 100 }, available: { code: 'INR', value: 200 },
};
const agentAccount: EarningsAccount = { ...franchiseAccount, ownerType: 'AGENT', ownerId: 'u1', franchiseCode: null };

const api = { mine: vi.fn(), myPayouts: vi.fn(), requestPayout: vi.fn(), myEarnings: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

function create() {
  const fixture = TestBed.createComponent(MyPayouts);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

function fill(component: MyPayouts): void {
  component.form = { franchiseId: 'f1', party: 'OWNER', amount: 100, currency: 'INR', appCode: ' ride ', note: ' monthly ' };
}

describe('MyPayouts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    api.mine.mockReturnValue(of([{ unitId: 'f1', code: 'RBM-1' }]));
    api.myPayouts.mockReturnValue(of({ items: [payout], page: 0, size: 20, totalElements: 1 }));
    api.requestPayout.mockReturnValue(of(payout));
    api.myEarnings.mockReturnValue(of({ accounts: [agentAccount, franchiseAccount] }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [MyPayouts],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: FranchiseApi, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
  });

  it('lists the agent account\'s wallet payouts, shows what each account may pay out and picks the only franchise', () => {
    const { component } = create();

    expect(component.payouts).toEqual([payout]);
    expect(component.accounts).toEqual([agentAccount, franchiseAccount]);
    expect(component.form.franchiseId).toBe('f1');
    expect(component.parties).toEqual(['OWNER', 'AGENT']);
    expect(api.myPayouts).toHaveBeenCalledWith({ account: 'AGENT' }, 0, 20);
  });

  it('lists the payouts of a franchise the person owns', () => {
    const { component } = create();
    component.selectedAccount = 'FRANCHISE:f1';

    component.load();

    expect(api.myPayouts).toHaveBeenLastCalledWith({ account: 'FRANCHISE', franchiseId: 'f1' }, 0, 20);
  });

  it('refuses an incomplete form without calling the service', () => {
    const { component } = create();

    component.request();

    expect(api.requestPayout).not.toHaveBeenCalled();
    expect(notify.error).toHaveBeenCalledWith('accessManagement.myPayouts.errors.party');
  });

  it('sends the request with a key, then clears the amount and reloads the balances and the list', () => {
    const { component } = create();
    fill(component);

    component.request();

    const [body, key] = api.requestPayout.mock.calls[0];
    expect(body).toEqual({ franchiseId: 'f1', party: 'OWNER', amount: { code: 'INR', value: 100 }, appCode: 'RIDE', note: 'monthly' });
    expect(typeof key).toBe('string');
    expect(component.form.amount).toBeNull();
    expect(component.form.franchiseId).toBe('f1');
    expect(notify.success).toHaveBeenCalledWith('accessManagement.myPayouts.messages.requested');
    expect(api.myEarnings).toHaveBeenCalledTimes(2);
  });

  it('retries a refused submission with the same key and makes a new key once the form changes', () => {
    api.requestPayout.mockReturnValue(throwError(() => ({ status: 503 })));
    const { component } = create();
    fill(component);

    component.request();
    component.request();
    expect(api.requestPayout.mock.calls[1][1]).toBe(api.requestPayout.mock.calls[0][1]);

    component.formChanged();
    component.request();
    expect(api.requestPayout.mock.calls[2][1]).not.toBe(api.requestPayout.mock.calls[0][1]);
  });

  it('shows the reason the wallet gave when the request is refused, or a general message', () => {
    const { component } = create();
    fill(component);
    api.requestPayout.mockReturnValueOnce(throwError(() => ({ error: { detail: 'INSUFFICIENT_BALANCE: above the available balance' } })));
    component.request();
    expect(notify.error).toHaveBeenLastCalledWith('INSUFFICIENT_BALANCE: above the available balance');
    expect(component.saving).toBe(false);

    api.requestPayout.mockReturnValueOnce(throwError(() => ({})));
    component.request();
    expect(notify.error).toHaveBeenLastCalledWith('accessManagement.myPayouts.messages.requestFailed');
  });

  it('reports a failure to load the list', () => {
    api.myPayouts.mockReturnValue(throwError(() => ({ status: 503 })));

    const { component } = create();

    expect(component.hasError).toBe(true);
    expect(notify.error).toHaveBeenCalledWith('accessManagement.myPayouts.errorMessage');
  });

  it('keeps the balances empty when the wallet cannot be read', () => {
    api.myEarnings.mockReturnValue(throwError(() => ({ status: 503 })));

    const { component } = create();

    expect(component.accounts).toEqual([]);
  });

  it('pages, changes the page size and never goes before the first page', () => {
    api.myPayouts.mockReturnValue(of({ items: [payout], page: 0, size: 20, totalElements: 45 }));
    const { component } = create();

    component.goToPage(-1);
    expect(component.page).toBe(0);
    component.goToPage(2);
    expect(component.isLast).toBe(true);
    component.onPageSizeChange(50);
    expect(api.myPayouts).toHaveBeenLastCalledWith({ account: 'AGENT' }, 0, 50);
  });

  it('shows labels for known codes and the raw code otherwise', () => {
    const { component } = create();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      accessManagement: {
        myEarnings: { agentAccount: 'My agent earnings', franchiseAccount: 'Franchise {{code}}', parties: { OWNER: 'Owner' } },
        myPayouts: { statuses: { PENDING_APPROVAL: 'Waiting for approval', REQUESTED: 'Requested' } },
      },
    });
    translate.use('en');

    expect(component.statusLabel('PENDING_APPROVAL')).toBe('Waiting for approval');
    expect(component.statusLabel('REQUESTED')).toBe('Requested');
    expect(component.statusLabel('ODD')).toBe('ODD');
    expect(component.partyLabel('OWNER')).toBe('Owner');
    expect(component.partyLabel('ODD')).toBe('ODD');
    expect(component.accountLabel(franchiseAccount)).toBe('Franchise RBM-1');
    expect(component.accountLabel(agentAccount)).toBe('My agent earnings');
  });

  it('neither lists, reads balances nor loads franchises without the privileges', () => {
    granted = new Set<string>();

    const { component } = create();

    expect(api.myPayouts).not.toHaveBeenCalled();
    expect(api.mine).not.toHaveBeenCalled();
    expect(api.myEarnings).not.toHaveBeenCalled();
    expect(component.payouts).toEqual([]);
  });
});
