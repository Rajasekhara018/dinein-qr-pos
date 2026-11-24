import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { EarningsAccount, Franchise, WalletPayout } from './franchise.models';
import {
  AGENT_ACCOUNT_KEY, DEFAULT_PAYOUT_CURRENCY, EARNINGS_PAGE_SIZE, EARNINGS_PRIVILEGES, FRANCHISE_ACCOUNT_KEY, PARTY_LABEL_KEYS,
  PAYABLE_PARTIES, WALLET_PAYOUT_STATUS_LABEL_KEYS,
} from './franchise-earnings.constants';
import { PayoutForm, accountFromKey, accountKey, isLastPage, labelKey, payoutFormError, toPayoutBody } from './franchise-earnings.helpers';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

/**
 * A person's payouts and the form to ask for one. The wallet pays out settled earnings only (pending ones once their money has
 * arrived) and sends each payout for a second person's approval; Franchise checks the franchise and the payout rule first.
 */
@Component({
  selector: 'app-my-payouts',
  standalone: false,
  templateUrl: './my-payouts.html',
  styleUrl: './franchises-list.scss',
})
export class MyPayouts implements OnInit {
  readonly privileges = EARNINGS_PRIVILEGES;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  readonly accountKey = accountKey;
  pageSize = EARNINGS_PAGE_SIZE;
  /** The grid's columns (headings translated once the screen opens). */
  columns: HqCommonTableColumn<WalletPayout>[] = [];
  balanceColumns: HqCommonTableColumn<EarningsAccount>[] = [];
  /** Parties a payout can be asked as: a franchise's earnings (owner) or the person's own agent earnings. */
  parties: string[] = [...PAYABLE_PARTIES];

  franchises: Franchise[] = [];
  accounts: EarningsAccount[] = [];
  /** The account whose payouts are listed: "AGENT" or "FRANCHISE:<id>". */
  selectedAccount = 'AGENT';
  payouts: WalletPayout[] = [];
  form: PayoutForm = this.emptyForm();
  page = 0;
  total = 0;
  isLoading = false;
  hasError = false;
  saving = false;
  /** Kept until the payout is accepted, so a retry of the same submission is the same payout. */
  private idempotencyKey: string | null = null;

  constructor(
    private api: FranchiseApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
  ) {}

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`accessManagement.myPayouts.${key}`);
    const earnings = (key: string): string => this.translate.instant(`accessManagement.myEarnings.${key}`);
    this.columns = [
      { key: 'createdAt', label: heading('requestedOn') },
      { key: 'amount', label: heading('amount') },
      { key: 'status', label: heading('status') },
      { key: 'note', label: heading('note') },
    ];
    this.balanceColumns = [
      { key: 'ownerType', label: earnings('account') },
      { key: 'pending', label: earnings('pending') },
      { key: 'available', label: earnings('available') },
    ];
    if (this.access.can(this.privileges.requestPayout) && this.access.can(this.privileges.listOwnFranchises)) {
      this.api.mine().subscribe({
        next: (rows) => {
          this.franchises = rows ?? [];
          if (this.franchises.length === 1) {
            this.form.franchiseId = this.franchises[0].unitId;
          }
          this.cdr.detectChanges();
        },
        error: () => { this.franchises = []; },
      });
    }
    this.loadAccounts();
    this.load();
  }

  /** What each account may pay out (from the wallet), and which accounts the list can show. */
  private loadAccounts(): void {
    if (!this.access.can(this.privileges.readStatement)) { return; }
    this.api.myEarnings().subscribe({
      next: (earnings) => {
        this.accounts = earnings?.accounts ?? [];
        this.cdr.detectChanges();
      },
      error: () => { this.accounts = []; },
    });
  }

  load(page = 0): void {
    this.page = Math.max(page, 0);
    if (!this.access.can(this.privileges.listPayouts)) {
      this.payouts = [];
      this.total = 0;
      return;
    }
    this.isLoading = true;
    this.hasError = false;
    this.api.myPayouts(accountFromKey(this.selectedAccount), this.page, this.pageSize).subscribe({
      next: (result) => {
        this.payouts = result.items ?? [];
        this.total = result.totalElements ?? 0;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.myPayouts.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  goToPage(page: number): void {
    this.load(page);
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.load(0);
  }

  get isLast(): boolean {
    return isLastPage(this.page, this.pageSize, this.total);
  }

  request(): void {
    const error = payoutFormError(this.form);
    if (error) {
      this.notify.error(this.translate.instant(error));
      return;
    }
    this.saving = true;
    this.idempotencyKey = this.idempotencyKey ?? crypto.randomUUID();
    this.api.requestPayout(toPayoutBody(this.form), this.idempotencyKey).subscribe({
      next: () => {
        this.saving = false;
        this.idempotencyKey = null;
        this.form = { ...this.emptyForm(), franchiseId: this.form.franchiseId };
        this.notify.success(this.translate.instant('accessManagement.myPayouts.messages.requested'));
        this.loadAccounts();
        this.load();
      },
      error: (err: { error?: { detail?: string; message?: string } }) => {
        this.saving = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.myPayouts.messages.requestFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  /** Changing what is asked makes it a new payout, with a new key. */
  formChanged(): void {
    this.idempotencyKey = null;
  }

  accountLabel(account: Pick<EarningsAccount, 'ownerType' | 'ownerId' | 'franchiseCode'>): string {
    return account.ownerType === 'FRANCHISE'
      ? this.translate.instant(FRANCHISE_ACCOUNT_KEY, { code: account.franchiseCode || account.ownerId })
      : this.translate.instant(AGENT_ACCOUNT_KEY);
  }

  partyLabel(code: string): string {
    const key = labelKey(PARTY_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }

  statusLabel(code: string): string {
    const key = labelKey(WALLET_PAYOUT_STATUS_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }

  private emptyForm(): PayoutForm {
    return { franchiseId: '', party: '', amount: null, currency: DEFAULT_PAYOUT_CURRENCY, appCode: '', note: '' };
  }
}
