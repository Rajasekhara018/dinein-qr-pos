import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { Subscription } from 'rxjs';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { watchColumns } from '../../shared/table-columns.helpers';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { EarningsAccount, EarningsEntry, EarningsState } from './franchise.models';
import { FranchiseWalletAccountingApi } from './franchise-wallet-accounting-api';
import {
  OwnerAccountingPayout, OwnerAccountingShare, OwnerAccountingTransaction, OwnerFranchiseAccounting,
} from './franchise-wallet-accounting.models';
import {
  AGENT_ACCOUNT_KEY, EARNINGS_PAGE_SIZE, EARNINGS_PRIVILEGES, EARNINGS_STATES, EARNINGS_STATE_LABEL_KEYS, ENTRY_DIRECTION_LABEL_KEYS,
  FRANCHISE_ACCOUNT_KEY, OWNER_CHANNEL_LABEL_KEYS, OWNER_EVENT_LABEL_KEYS, OWNER_PAYOUT_COLUMNS, OWNER_SHARE_COLUMNS, OWNER_TRANSACTION_COLUMNS,
  PARTY_LABEL_KEYS, WALLET_PAYOUT_STATUS_LABEL_KEYS,
} from './franchise-earnings.constants';
import { accountFromKey, accountKey, isLastPage, labelKey } from './franchise-earnings.helpers';

/**
 * The person's earnings as the wallet holds them: per account (their agent earnings, each franchise they own) what is settled,
 * pending (money not arrived yet), waiting for payout and available, and the wallet entries of one account. For a franchise the
 * person owns it also shows the franchise's accounting from the wallet: totals, share breakdown, payable, payouts and recent
 * transactions (WALLET_FRANCHISE_ACCOUNTING_MINE_READ).
 */
@Component({
  selector: 'app-my-earnings',
  standalone: false,
  templateUrl: './my-earnings.html',
  styleUrl: './franchises-list.scss',
})
export class MyEarnings implements OnInit, OnDestroy {
  readonly privileges = EARNINGS_PRIVILEGES;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  readonly states = EARNINGS_STATES;
  readonly accountKey = accountKey;
  /** The key maps behind {@link codeLabel}. */
  private readonly codeLabelKeys = {
    share: PARTY_LABEL_KEYS,
    channel: OWNER_CHANNEL_LABEL_KEYS,
    event: OWNER_EVENT_LABEL_KEYS,
    payout: WALLET_PAYOUT_STATUS_LABEL_KEYS,
  } as const;
  pageSize = EARNINGS_PAGE_SIZE;
  /** The grids' columns (headings translated once the screen opens). */
  accountColumns: HqCommonTableColumn<EarningsAccount>[] = [];
  entryColumns: HqCommonTableColumn<EarningsEntry>[] = [];
  shareColumns: HqCommonTableColumn<OwnerAccountingShare>[] = [];
  payoutColumns: HqCommonTableColumn<OwnerAccountingPayout>[] = [];
  transactionColumns: HqCommonTableColumn<OwnerAccountingTransaction>[] = [];
  private readonly columnSubs: Subscription[] = [];

  accounts: EarningsAccount[] = [];
  /** The account whose entries are shown: "AGENT" or "FRANCHISE:<id>". */
  selectedAccount = 'AGENT';
  state: EarningsState = 'AVAILABLE';
  from = '';
  to = '';
  entries: EarningsEntry[] = [];
  page = 0;
  total = 0;
  isLoading = false;
  /** The balances could not be read (kept apart so a later entries load does not hide it). */
  accountsError = false;
  entriesError = false;
  /** The franchise's accounting from the wallet, once read. */
  accounting: OwnerFranchiseAccounting | null = null;
  accountingLoading = false;
  accountingError = false;
  private accountingRequest = 0;

  /** Reads the selected franchise's accounting for the period (only for a franchise account, with the privilege). */
  loadAccounting(): void {
    const ticket = ++this.accountingRequest;
    this.accounting = null;
    this.accountingError = false;
    const { franchiseId } = accountFromKey(this.selectedAccount);
    if (!franchiseId || !this.access.can(this.privileges.readAccounting)) return;
    const selected = this.selectedAccount;
    this.accountingLoading = true;
    this.walletAccounting.mine(franchiseId, { from: this.from, to: this.to }).subscribe({
      next: result => {
        if (ticket !== this.accountingRequest || selected !== this.selectedAccount) return;
        this.accounting = result;
        this.accountingLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        if (ticket !== this.accountingRequest || selected !== this.selectedAccount) return;
        this.accountingLoading = false;
        this.accountingError = true;
        this.cdr.detectChanges();
      },
    });
  }

  /** True when either the balances or the entries could not be read from the wallet. */
  get hasError(): boolean {
    return this.accountsError || this.entriesError;
  }

  constructor(
    private api: FranchiseApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
    private walletAccounting: FranchiseWalletAccountingApi,
  ) {}

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`accessManagement.myEarnings.${key}`);
    this.accountColumns = [
      { key: 'ownerType', label: heading('account') },
      { key: 'settled', label: heading('settled') },
      { key: 'pending', label: heading('pending') },
      { key: 'reserved', label: heading('reserved') },
      { key: 'available', label: heading('available') },
    ];
    this.entryColumns = [
      { key: 'postedAt', label: heading('postedAt') },
      { key: 'journalType', label: heading('type') },
      { key: 'direction', label: heading('direction') },
      { key: 'amount', label: heading('amount') },
    ];
    this.columnSubs.push(
      watchColumns(this.translate, OWNER_SHARE_COLUMNS, (columns) => (this.shareColumns = columns)),
      watchColumns(this.translate, OWNER_PAYOUT_COLUMNS, (columns) => (this.payoutColumns = columns)),
      watchColumns(this.translate, OWNER_TRANSACTION_COLUMNS, (columns) => (this.transactionColumns = columns)),
    );
    this.loadAccounts();
    this.load();
  }

  ngOnDestroy(): void {
    this.columnSubs.forEach((sub) => sub.unsubscribe());
  }

  /** Reads the balances of every account the person has. */
  loadAccounts(): void {
    if (!this.access.can(this.privileges.readStatement)) {
      this.accounts = [];
      return;
    }
    this.accountsError = false;
    this.api.myEarnings().subscribe({
      next: (earnings) => {
        this.accounts = earnings?.accounts ?? [];
        if (!this.accounts.some((a) => accountKey(a) === this.selectedAccount) && this.accounts.length > 0) {
          this.selectedAccount = accountKey(this.accounts[0]);
          this.load();
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.accounts = [];
        this.accountsError = true;
        this.fail();
      },
    });
  }

  /** Reads the first page of entries for the chosen account, view and period. */
  load(): void {
    this.resetAccounting();
    this.page = 0;
    this.loadEntries();
  }

  resetAccounting(): void {
    ++this.accountingRequest;
    this.accounting = null;
    this.accountingLoading = false;
    this.accountingError = false;
  }

  get isFirstPage(): boolean {
    return this.page === 0;
  }

  get isLast(): boolean {
    return isLastPage(this.page, this.pageSize, this.total);
  }

  goToPage(page: number): void {
    this.page = Math.max(page, 0);
    this.loadEntries();
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.goToPage(0);
  }

  /** The account's name in the user's language: "My agent earnings" or "Franchise <code>". */
  accountLabel(account: Pick<EarningsAccount, 'ownerType' | 'ownerId' | 'franchiseCode'>): string {
    return account.ownerType === 'FRANCHISE'
      ? this.translate.instant(FRANCHISE_ACCOUNT_KEY, { code: account.franchiseCode || account.ownerId })
      : this.translate.instant(AGENT_ACCOUNT_KEY);
  }

  stateLabel(code: string): string {
    const key = labelKey(EARNINGS_STATE_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }

  /** A share role, wallet channel or payout status in the user's language; an unknown code is shown as it is. */
  codeLabel(kind: 'share' | 'channel' | 'event' | 'payout', code: string | null): string {
    const key = labelKey(this.codeLabelKeys[kind], code);
    return key ? this.translate.instant(key) : (code ?? '');
  }

  directionLabel(code: string | null): string {
    const key = labelKey(ENTRY_DIRECTION_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : (code ?? '');
  }

  private loadEntries(): void {
    if (!this.access.can(this.privileges.listEntries)) {
      this.entries = [];
      this.total = 0;
      this.isLoading = false;
      return;
    }
    this.isLoading = true;
    this.entriesError = false;
    const filter = { ...accountFromKey(this.selectedAccount), state: this.state, from: this.from, to: this.to };
    this.api.myEntries(filter, this.page, this.pageSize).subscribe({
      next: (result) => {
        this.entries = result.items ?? [];
        this.total = result.totalElements ?? 0;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.entriesError = true;
        this.fail();
      },
    });
  }

  private fail(): void {
    this.isLoading = false;
    this.notify.error(this.translate.instant('accessManagement.myEarnings.errorMessage'));
    this.cdr.detectChanges();
  }
}
