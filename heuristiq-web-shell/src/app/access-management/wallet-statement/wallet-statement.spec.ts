import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { WalletStatementApi } from './wallet-statement-api';
import { WalletStatementScreen } from './wallet-statement';
import { StatementLine, WalletStatement, YearSummary } from './wallet-statement.models';

const inr = (value: number) => ({ code: 'INR', value });

const credit: StatementLine = {
  entryId: 'e1', postedAt: '2026-03-02T10:00:00Z', description: 'Top-up', category: 'TOP_UP', appCode: null, journalType: 'TOP_UP',
  reference: { journalId: 'j1', referenceType: 'PAYMENT', referenceId: 'PAY-1', reversalOfJournalId: null, journalStatus: 'POSTED', correlationId: null },
  credit: inr(100), debit: null, balanceAfter: inr(100), franchise: null,
};
const debit: StatementLine = {
  ...credit, entryId: 'e2', description: 'Ride', category: 'RIDE', credit: null, debit: inr(30), balanceAfter: inr(70),
  reference: { journalId: 'j2', referenceType: 'FRANCHISE_EVENT', referenceId: 'FE-9', reversalOfJournalId: 'j0', journalStatus: 'REVERSED', correlationId: null },
  franchise: { eventId: 'FE-9', franchiseId: 'f1', shares: [{ party: 'OWNER', partyRef: 'u1', amount: inr(3), planId: null, planVersion: null }] },
};

function statement(overrides: Partial<WalletStatement> = {}): WalletStatement {
  return {
    account: { accountId: 'acc1', accountNo: 'W-1', accountType: 'WALLET', ownerId: 'u1', appCode: null, currency: 'INR', normalSide: 'CREDIT' },
    period: { month: '2026-03', from: '', to: '', timezone: 'Asia/Kolkata' },
    openingBalance: inr(0), closingBalance: inr(70), totals: { credits: inr(100), debits: inr(30), count: 2 },
    categories: [{ category: 'TOP_UP', credits: inr(100), debits: inr(0), count: 1 }, { category: 'RIDE', credits: inr(0), debits: inr(30), count: 1 }],
    reconciled: true, lines: [credit, debit], page: 0, size: 50, totalLines: 2, totalPages: 1, generatedAt: '', ...overrides,
  };
}

const summary: YearSummary = {
  account: null, year: 2026, timezone: 'Asia/Kolkata', openingBalance: inr(0), closingBalance: inr(70), reconciled: true,
  months: [{ month: '2026-03', openingBalance: inr(0), credits: inr(100), debits: inr(30), closingBalance: inr(70), count: 2 }],
};

const api = { statement: vi.fn(), months: vi.fn(), summary: vi.fn(), export: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};
let scope = 'self';

function create() {
  const fixture = TestBed.createComponent(WalletStatementScreen);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('WalletStatementScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    scope = 'self';
    api.months.mockReturnValue(of({ account: 'WALLET', timezone: 'Asia/Kolkata', months: [{ month: '2026-03', count: 2 }, { month: '2026-02', count: 1 }] }));
    api.statement.mockReturnValue(of(statement()));
    api.summary.mockReturnValue(of(summary));
    api.export.mockReturnValue(of({ blob: new Blob(['x']), filename: 'statement-2026-03.csv' }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [WalletStatementScreen],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: WalletStatementApi, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: ActivatedRoute, useValue: { get snapshot() { return { data: { scope } }; } } },
      ],
    });
  });

  it('opens on the latest month with activity and shows the cards and lines', () => {
    const { component, el } = create();
    expect(component.month).toBe('2026-03');
    expect(api.statement).toHaveBeenCalledWith(expect.objectContaining({ scope: 'self', month: '2026-03', page: 0, size: 50, account: 'WALLET' }));
    expect(el.querySelectorAll('.statement-cards .statement-card').length).toBe(5);
    expect(el.querySelectorAll('tbody tr').length).toBeGreaterThanOrEqual(2);
    expect(el.textContent).toContain('100.00');
    expect(el.querySelector('.statement-chip--bad')).toBeNull();
  });

  it('shows the loading state while the statement is on its way', () => {
    api.statement.mockReturnValue(EMPTY);
    const { component, el } = create();
    expect(component.isLoading).toBe(true);
    expect(el.querySelector('[role="status"]')).not.toBeNull();
  });

  it('shows an empty-month message', () => {
    api.statement.mockReturnValue(of(statement({ lines: [], totalLines: 0, categories: [], totals: { credits: inr(0), debits: inr(0), count: 0 } })));
    const { el } = create();
    expect(el.querySelectorAll('tbody tr').length).toBe(0);
    expect(el.textContent).toContain('walletStatement.noLines');
  });

  it('shows a red warning chip when the statement is not reconciled', () => {
    api.statement.mockReturnValue(of(statement({ reconciled: false })));
    const { el } = create();
    const warning = el.querySelector('.statement-chip--bad[role="alert"]');
    expect(warning?.textContent).toContain('walletStatement.unreconciled');
    expect(el.textContent).toContain('walletStatement.notReconciled');
  });

  it('shows the error state and a toast when the statement fails', () => {
    api.statement.mockReturnValue(throwError(() => ({ status: 500 })));
    const { component, el } = create();
    expect(component.hasError).toBe(true);
    expect(notify.error).toHaveBeenCalled();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('walletStatement.errorMessage');
  });

  it('marks credits and debits in their own columns, reversals, and the franchise share', () => {
    const { component, el } = create();
    expect(component.isReversed(debit)).toBe(true);
    expect(component.isReversed(credit)).toBe(false);
    expect(component.referenceOf(debit)).toBe('FE-9');
    expect(component.referenceOf({ ...credit, reference: null })).toBe('');
    const text = el.textContent ?? '';
    expect(text).toContain('walletStatement.franchise.event');
    expect(text).toContain('walletStatement.franchise.share');
    expect(el.querySelectorAll('.statement-num.amount--credit').length).toBeGreaterThan(0);
    expect(el.querySelectorAll('.statement-num.amount--debit').length).toBeGreaterThan(0);
  });

  it('pages through long statements', () => {
    api.statement.mockReturnValue(of(statement({ totalPages: 3, totalLines: 120 })));
    const { component } = create();
    expect(component.isFirstPage).toBe(true);
    component.goToPage(1);
    expect(api.statement).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
    component.goToPage(99);
    expect(component.page).toBe(2);
    expect(component.isLastPage).toBe(true);
    component.goToPage(-4);
    expect(component.page).toBe(0);
  });

  it('starts again from the first page when the footer page size changes', () => {
    const { component } = create();
    component.page = 2;

    component.onPageSizeChange(100);

    expect(component.size).toBe(100);
    expect(api.statement).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 100 }));
  });

  it('reloads from the first page when the month, filters or page size change', () => {
    const { component } = create();
    component.page = 2;
    component.month = '2026-02';
    component.onMonthChange();
    expect(api.statement).toHaveBeenLastCalledWith(expect.objectContaining({ month: '2026-02', page: 0 }));
    component.app = 'RIDE';
    component.onFilterChange();
    expect(api.statement).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 }));
    component.account = 'EARNINGS';
    component.onAccountChange();
    expect(api.months).toHaveBeenLastCalledWith(expect.objectContaining({ account: 'EARNINGS' }));
  });

  it('lists the category summary with translated labels falling back to the code', () => {
    const { component, el } = create();
    expect(component.categoryLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
    expect(component.categoryLabel('RIDE')).toBe('walletStatement.categories.RIDE');
    expect(component.partyLabel('OWNER')).toBe('accessManagement.myEarnings.parties.OWNER');
    expect(component.accountLabel(null)).toBe('');
    expect(el.querySelector('.statement-categories table')).not.toBeNull();
    expect(el.querySelectorAll('.statement-categories tbody tr').length).toBe(2);
  });

  it('shows the year overview and opens a month from it', () => {
    const { component, el, fixture } = create();
    component.setView('year');
    fixture.detectChanges();
    expect(api.summary).toHaveBeenCalledWith(expect.objectContaining({ scope: 'self' }), 2026);
    expect(el.querySelectorAll('#statement-panel tbody tr').length).toBe(1);
    component.shiftYear(-1);
    expect(api.summary).toHaveBeenLastCalledWith(expect.anything(), 2025);
    component.openMonth('2026-03');
    expect(component.view).toBe('month');
    expect(api.statement).toHaveBeenLastCalledWith(expect.objectContaining({ month: '2026-03' }));
  });

  it('reports a failed year overview', () => {
    api.summary.mockReturnValue(throwError(() => ({ status: 500 })));
    const { component } = create();
    component.setView('year');
    expect(component.hasSummaryError).toBe(true);
    expect(component.summary).toBeNull();
  });

  it('downloads CSV and PDF files and tells the user', () => {
    const create$ = vi.fn().mockReturnValue('blob:1');
    const revoke = vi.fn();
    Object.assign(URL, { createObjectURL: create$, revokeObjectURL: revoke });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const { component } = create();
    component.download('csv');
    expect(api.export).toHaveBeenLastCalledWith(expect.objectContaining({ month: '2026-03' }), 'csv');
    expect(create$).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    expect(revoke).toHaveBeenCalledWith('blob:1');
    expect(notify.success).toHaveBeenCalled();
    expect(component.exporting).toBeNull();
    component.download('pdf');
    expect(api.export).toHaveBeenLastCalledWith(expect.anything(), 'pdf');
    click.mockRestore();
  });

  it('explains a statement that is too large to export', () => {
    api.export.mockReturnValue(throwError(() => ({ status: 413, error: { errorCode: 'STATEMENT_TOO_LARGE' } })));
    const { component } = create();
    component.download('pdf');
    expect(notify.error).toHaveBeenCalledWith('walletStatement.export.tooLarge');
    expect(component.exporting).toBeNull();
  });

  it('shows the server text, else a generic message, when an export fails', () => {
    api.export.mockReturnValue(throwError(() => ({ status: 400, error: { message: 'Month is invalid' } })));
    const { component } = create();
    component.download('csv');
    expect(notify.error).toHaveBeenLastCalledWith('Month is invalid');
    api.export.mockReturnValue(throwError(() => ({ status: 0 })));
    component.download('csv');
    expect(notify.error).toHaveBeenLastCalledWith('walletStatement.export.failed');
  });

  it('copies a reference and reports when it cannot', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    const { component } = create();
    component.copyReference('PAY-1');
    await Promise.resolve();
    expect(writeText).toHaveBeenCalledWith('PAY-1');
    expect(notify.success).toHaveBeenCalledWith('walletStatement.copied');
    writeText.mockRejectedValue(new Error('denied'));
    component.copyReference('PAY-2');
    await Promise.resolve();
    await Promise.resolve();
    expect(notify.error).toHaveBeenCalledWith('walletStatement.copyFailed');
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    component.copyReference('PAY-3');
    expect(notify.error).toHaveBeenCalledTimes(2);
    component.copyReference('');
    expect(notify.error).toHaveBeenCalledTimes(2);
  });

  describe('privileges', () => {
    it('hides downloads and the year tab without the export and summary privileges', () => {
      granted = new Set(['WALLET_SELF_STATEMENT_READ', 'WALLET_SELF_STATEMENT_MONTH_LIST']);
      const { el } = create();
      expect(el.querySelector('.statement-downloads')).toBeNull();
      expect(el.querySelector('#statement-tab-year')).toBeNull();
      expect(el.querySelector('#statement-tab-month')).not.toBeNull();
    });

    it('shows downloads and the year tab with the privileges', () => {
      granted = new Set(['WALLET_SELF_STATEMENT_READ', 'WALLET_SELF_STATEMENT_EXPORT_READ', 'WALLET_SELF_STATEMENT_SUMMARY_READ']);
      const { el } = create();
      expect(el.querySelectorAll('.statement-downloads button').length).toBe(2);
      expect(el.querySelector('#statement-tab-year')).not.toBeNull();
    });

    it('falls back to the current month when the months list is not allowed', () => {
      granted = new Set(['WALLET_SELF_STATEMENT_READ']);
      const { component } = create();
      expect(api.months).not.toHaveBeenCalled();
      expect(component.month).toMatch(/^\d{4}-\d{2}$/);
      expect(api.statement).toHaveBeenCalled();
    });

    it('does not read a statement without the read privilege', () => {
      granted = new Set();
      create();
      expect(api.statement).not.toHaveBeenCalled();
    });

    it('still opens when the months call fails', () => {
      api.months.mockReturnValue(throwError(() => ({ status: 500 })));
      const { component } = create();
      expect(component.months).toEqual([]);
      expect(api.statement).toHaveBeenCalled();
    });
  });

  describe('administrator', () => {
    beforeEach(() => { scope = 'admin'; });

    it('waits for a search and shows the hint', () => {
      const { component, el } = create();
      expect(component.hasTarget).toBe(false);
      expect(api.statement).not.toHaveBeenCalled();
      expect(el.textContent).toContain('walletStatement.search.hint');
      component.searchTerm = '   ';
      component.search();
      expect(api.statement).not.toHaveBeenCalled();
    });

    it('reads an account by id with the admin privileges and without the own-account picker', () => {
      const { component } = create();
      component.searchBy = 'account';
      component.searchTerm = ' acc1 ';
      component.search();
      expect(api.months).toHaveBeenCalledWith(expect.objectContaining({ scope: 'admin', accountId: 'acc1' }));
      expect(api.statement).toHaveBeenCalledWith(expect.objectContaining({ scope: 'admin', accountId: 'acc1', month: '2026-03' }));
      expect(component.showAccountPicker).toBe(false);
      expect(component.showAppFilter).toBe(true);
    });

    it('looks a person up by user id, then uses the account id for months, summary and export', () => {
      const { component } = create();
      component.searchBy = 'user';
      component.searchTerm = 'u1';
      component.search();
      expect(api.statement).toHaveBeenCalledWith(expect.objectContaining({ userId: 'u1' }));
      expect(component.accountId).toBe('acc1');
      expect(api.months).toHaveBeenCalledWith(expect.objectContaining({ accountId: 'acc1' }));
      expect(component.showAccountPicker).toBe(true);
      component.download('csv');
      expect(api.export).toHaveBeenLastCalledWith(expect.objectContaining({ accountId: 'acc1' }), 'csv');
      expect(api.export.mock.calls.at(-1)?.[0]).not.toHaveProperty('userId');
      component.account = 'HELD';
      component.onAccountChange();
      expect(api.statement).toHaveBeenLastCalledWith(expect.objectContaining({ userId: 'u1', account: 'HELD' }));
    });

    it('offers the user search only with the user privilege', () => {
      granted = new Set(['WALLET_ADMIN_STATEMENT_READ']);
      const { el } = create();
      expect(el.querySelectorAll('select[name="searchBy"] option').length).toBe(1);
    });

    it('cannot read a person without the user-statement privilege', () => {
      granted = new Set(['WALLET_ADMIN_STATEMENT_READ']);
      const { component } = create();
      component.searchBy = 'user';
      component.searchTerm = 'u1';
      component.search();
      expect(api.statement).not.toHaveBeenCalled();
    });
  });
});
