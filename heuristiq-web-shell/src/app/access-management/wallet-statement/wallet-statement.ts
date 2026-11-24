import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { Subject, takeUntil } from 'rxjs';
import { NotificationService } from '../../shared/services/notification-service';
import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { WalletStatementApi } from './wallet-statement-api';
import {
  ACCOUNT_LABEL_KEYS, CATEGORY_LABEL_KEYS, DEFAULT_OWN_ACCOUNT, JOURNAL_STATUS_REVERSED, OWN_ACCOUNTS, PARTY_LABEL_KEYS,
  STATEMENT_PAGE_SIZE, STATEMENT_PAGE_SIZES, STATEMENT_PRIVILEGES, YEAR_SUMMARY_COLUMNS,
} from './wallet-statement.constants';
import { defaultMonth, isDebit, isTooLarge, labelKey, pageCount, saveBlob, yearOf } from './wallet-statement.helpers';
import {
  StatementCategory, StatementFormat, StatementLine, StatementMonth, StatementQuery, StatementScope, StatementView, WalletStatement, YearSummary,
  YearSummaryMonth,
} from './wallet-statement.models';

/**
 * A bank-style wallet statement: one calendar month at a time (balances, lines, categories), a year overview and CSV/PDF
 * downloads. One component serves two routes: the signed-in person's own statement (scope "self") and an administrator's
 * view of an account picked by account id or user id (scope "admin"). Amounts are the API's strings and are never computed on.
 */
@Component({
  selector: 'app-wallet-statement',
  standalone: false,
  templateUrl: './wallet-statement.html',
  styleUrls: ['../franchises/franchises-list.scss', './wallet-statement.scss'],
})
export class WalletStatementScreen implements OnInit, OnDestroy {
  scope: StatementScope = 'self';
  readonly ownAccounts = OWN_ACCOUNTS;
  readonly pageSizes: number[] = [...STATEMENT_PAGE_SIZES];
  /** The ledger and category grids' columns (headings translated when the screen opens). */
  lineColumns: HqCommonTableColumn<StatementLine>[] = [];
  categoryColumns: HqCommonTableColumn<StatementCategory>[] = [];
  yearColumns: HqCommonTableColumn<YearSummaryMonth>[] = [];
  readonly isDebit = isDebit;

  // Admin search.
  searchBy: 'account' | 'user' = 'account';
  searchTerm = '';
  accountId = '';
  userId = '';

  // Filters.
  account: string = DEFAULT_OWN_ACCOUNT;
  app = '';
  month = '';
  year = 0;
  page = 0;
  size = STATEMENT_PAGE_SIZE;
  view: StatementView = 'month';

  months: StatementMonth[] = [];
  statement: WalletStatement | null = null;
  summary: YearSummary | null = null;

  isLoading = false;
  isSummaryLoading = false;
  hasError = false;
  hasSummaryError = false;
  exporting: StatementFormat | null = null;

  private options: StatementMonth[] = [];
  private optionsFor: { months: StatementMonth[] | null; month: string } = { months: null, month: '' };
  private readonly destroy$ = new Subject<void>();

  constructor(
    private api: WalletStatementApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`walletStatement.table.${key}`);
    this.lineColumns = [
      { key: 'postedAt', label: heading('date') },
      { key: 'description', label: heading('description') },
      { key: 'reference', label: heading('reference') },
      { key: 'category', label: heading('category') },
      { key: 'credit', label: heading('credit') },
      { key: 'debit', label: heading('debit') },
      { key: 'balanceAfter', label: heading('balance') },
    ];
    this.categoryColumns = [
      { key: 'category', label: heading('category') },
      { key: 'count', label: heading('count') },
      { key: 'credits', label: heading('credit') },
      { key: 'debits', label: heading('debit') },
    ];
    watchColumnsUntil(this.translate, YEAR_SUMMARY_COLUMNS, (columns) => (this.yearColumns = columns), this.destroy$);
    this.scope = this.route.snapshot.data['scope'] === 'admin' ? 'admin' : 'self';
    if (this.scope === 'self') {
      const requested = this.route.snapshot.queryParamMap?.get('account') ?? '';
      if ((OWN_ACCOUNTS as readonly string[]).includes(requested)) {
        this.account = requested;
      }
      this.loadMonths(true);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get privileges() {
    return STATEMENT_PRIVILEGES[this.scope];
  }

  get isAdmin(): boolean {
    return this.scope === 'admin';
  }

  /** The account selector is for the person's own accounts, or the account type of a user an administrator looked up. */
  get showAccountPicker(): boolean {
    return this.scope === 'self' || (this.isAdmin && !!this.userId);
  }

  /** The app filter does not exist on the user route. */
  get showAppFilter(): boolean {
    return this.scope === 'self' || !!this.accountId;
  }

  /** Whether a statement can be read yet: always for the person's own, after a search for an administrator. */
  get hasTarget(): boolean {
    return this.scope === 'self' || !!this.accountId || !!this.userId;
  }

  /** The months in the picker: those with activity, plus the chosen one when it has none. The array is reused while its inputs are unchanged, so the select is not rebuilt on every check. */
  get monthOptions(): StatementMonth[] {
    if (this.optionsFor.months !== this.months || this.optionsFor.month !== this.month) {
      this.optionsFor = { months: this.months, month: this.month };
      const missing = !!this.month && !this.months.some((m) => m.month === this.month);
      this.options = missing ? [{ month: this.month, count: 0 }, ...this.months] : this.months;
    }
    return this.options;
  }

  get currency(): string {
    return this.statement?.account.currency ?? '';
  }

  get totalPages(): number {
    return pageCount(this.statement?.totalPages);
  }

  get isFirstPage(): boolean {
    return this.page === 0;
  }

  get isLastPage(): boolean {
    return this.page + 1 >= this.totalPages;
  }

  get canExport(): boolean {
    return !!this.statement && !!this.month && !this.exporting;
  }

  /** Administrator: looks up an account (by account id) or a person (by user id) and shows the latest month. */
  search(): void {
    const term = this.searchTerm.trim();
    if (!term) {
      return;
    }
    this.accountId = this.searchBy === 'account' ? term : '';
    this.userId = this.searchBy === 'user' ? term : '';
    this.months = [];
    this.statement = null;
    this.summary = null;
    this.month = '';
    this.view = 'month';
    if (this.accountId) {
      this.loadMonths(true);
    } else {
      this.loadStatement(true);
    }
  }

  onAccountChange(): void {
    if (this.isAdmin && this.userId) {
      this.accountId = '';
    }
    this.month = '';
    this.months = [];
    this.summary = null;
    if (this.isAdmin && this.userId) {
      this.loadStatement(true);
    } else {
      this.loadMonths(true);
    }
  }

  onFilterChange(): void {
    this.page = 0;
    this.loadStatement(false);
    if (this.view === 'year') {
      this.loadSummary();
    }
  }

  onMonthChange(): void {
    this.page = 0;
    this.year = this.month ? yearOf(this.month) : this.year;
    this.loadStatement(false);
  }

  /** The footer's page size: the statement starts again from its first page. */
  onPageSizeChange(size: number): void {
    this.size = size;
    this.onFilterChange();
  }

  goToPage(page: number): void {
    this.page = Math.min(Math.max(page, 0), this.totalPages - 1);
    this.loadStatement(false);
  }

  setView(view: StatementView): void {
    this.view = view;
    if (view === 'year') {
      this.year = this.year || (this.month ? yearOf(this.month) : new Date().getFullYear());
      this.loadSummary();
    }
  }

  shiftYear(delta: number): void {
    this.year += delta;
    this.loadSummary();
  }

  /** From the year overview: open one month's lines. */
  openMonth(month: string): void {
    this.month = month;
    this.view = 'month';
    this.onMonthChange();
  }

  categoryLabel(code: string): string {
    return this.label(CATEGORY_LABEL_KEYS, code);
  }

  partyLabel(code: string): string {
    return this.label(PARTY_LABEL_KEYS, code);
  }

  accountLabel(code: string | null): string {
    return code ? this.label(ACCOUNT_LABEL_KEYS, code) : '';
  }

  isReversed(line: StatementLine): boolean {
    return line.reference?.journalStatus === JOURNAL_STATUS_REVERSED;
  }

  /** What to copy and show as the line's reference: the business reference, else the journal. */
  referenceOf(line: StatementLine): string {
    return line.reference?.referenceId || line.reference?.journalId || '';
  }

  trackLine(_index: number, line: StatementLine): string {
    return line.entryId;
  }

  copyReference(text: string): void {
    if (!text) {
      return;
    }
    const clipboard = typeof navigator === 'undefined' ? undefined : navigator.clipboard;
    if (!clipboard) {
      this.notify.error(this.translate.instant('walletStatement.copyFailed'));
      return;
    }
    clipboard.writeText(text).then(
      () => this.notify.success(this.translate.instant('walletStatement.copied')),
      () => this.notify.error(this.translate.instant('walletStatement.copyFailed')),
    );
  }

  /** Downloads the chosen month as CSV or PDF. */
  download(format: StatementFormat): void {
    if (!this.canExport) {
      return;
    }
    this.exporting = format;
    this.api.export(this.targetQuery(), format).pipe(takeUntil(this.destroy$)).subscribe({
      next: (file) => {
        saveBlob(file);
        this.exporting = null;
        this.notify.success(this.translate.instant('walletStatement.export.done', { file: file.filename }));
        this.cdr.markForCheck();
      },
      error: (error: unknown) => {
        this.exporting = null;
        const message = isTooLarge(error)
          ? this.translate.instant('walletStatement.export.tooLarge')
          : apiErrorMessage(error, this.translate.instant('walletStatement.export.failed'));
        this.notify.error(message);
        this.cdr.markForCheck();
      },
    });
  }

  /** Reads the months with activity, picks the newest unless one is already chosen, then the statement. */
  private loadMonths(thenStatement: boolean): void {
    if (!this.access.can(this.privileges.months)) {
      this.months = [];
      this.finishMonths(thenStatement);
      return;
    }
    this.api.months(this.targetQuery()).pipe(takeUntil(this.destroy$)).subscribe({
      next: (result) => {
        this.months = result.months ?? [];
        this.finishMonths(thenStatement);
      },
      error: () => {
        this.months = [];
        this.finishMonths(thenStatement);
      },
    });
  }

  private finishMonths(thenStatement: boolean): void {
    if (!this.month) {
      this.month = defaultMonth(this.months);
    }
    this.year = yearOf(this.month);
    if (thenStatement) {
      this.loadStatement(false);
    }
    this.cdr.markForCheck();
  }

  private loadStatement(fromUserLookup: boolean): void {
    if (!this.hasTarget || !this.canReadStatement()) {
      return;
    }
    this.isLoading = true;
    this.hasError = false;
    this.api.statement(this.statementQuery()).pipe(takeUntil(this.destroy$)).subscribe({
      next: (statement) => {
        this.statement = statement;
        this.isLoading = false;
        if (fromUserLookup) {
          this.afterUserLookup(statement);
        }
        this.cdr.markForCheck();
      },
      error: (error: unknown) => {
        this.statement = null;
        this.isLoading = false;
        this.hasError = true;
        this.notify.error(apiErrorMessage(error, this.translate.instant('walletStatement.errorMessage')));
        this.cdr.markForCheck();
      },
    });
  }

  /** A user lookup tells us the account id: remember it for the months, year overview and downloads. */
  private afterUserLookup(statement: WalletStatement): void {
    this.accountId = statement.account.accountId ?? '';
    this.month = statement.period.month;
    this.year = yearOf(this.month);
    if (this.accountId) {
      this.loadMonths(false);
    }
  }

  private loadSummary(): void {
    if (!this.access.can(this.privileges.summary) || !this.hasTarget) {
      return;
    }
    this.isSummaryLoading = true;
    this.hasSummaryError = false;
    this.api.summary(this.targetQuery(), this.year).pipe(takeUntil(this.destroy$)).subscribe({
      next: (summary) => {
        this.summary = summary;
        this.isSummaryLoading = false;
        this.cdr.markForCheck();
      },
      error: (error: unknown) => {
        this.summary = null;
        this.isSummaryLoading = false;
        this.hasSummaryError = true;
        this.notify.error(apiErrorMessage(error, this.translate.instant('walletStatement.errorMessage')));
        this.cdr.markForCheck();
      },
    });
  }

  private canReadStatement(): boolean {
    const byUser = this.isAdmin && !!this.userId;
    return this.access.can(byUser ? (this.privileges.userRead ?? this.privileges.read) : this.privileges.read);
  }

  /** The query of the statement itself: by user while the administrator looks a person up, else by account. */
  private statementQuery(): StatementQuery {
    return { ...this.targetQuery(), month: this.month || undefined, page: this.page, size: this.size, ...this.userRoute() };
  }

  private userRoute(): Partial<StatementQuery> {
    return this.isAdmin && this.userId ? { userId: this.userId, accountId: undefined, account: this.account } : {};
  }

  /** The query that names the account: own account, or the administrator's account id. */
  private targetQuery(): StatementQuery {
    return {
      scope: this.scope,
      accountId: this.accountId || undefined,
      account: this.scope === 'self' ? this.account : undefined,
      app: this.showAppFilter ? this.app : undefined,
      month: this.month || undefined,
    };
  }

  private label(map: Readonly<Record<string, string>>, code: string): string {
    const key = labelKey(map, code);
    return key ? this.translate.instant(key) : code;
  }
}
