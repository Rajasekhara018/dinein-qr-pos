import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { OwnAccount, StatementScope, YearSummaryMonth } from './wallet-statement.models';

/** Screen permission keys (mapped to privileges in hq-shared-lib's privilege map). */
export const MY_STATEMENT_PERMISSION = 'view_my_statement';
export const ACCOUNT_STATEMENT_PERMISSION = 'view_account_statement';

/** Stable audit keys (area.screen): queryable history, never renamed. */
export const MY_STATEMENT_SCREEN_KEY = 'wallet.my-statement';
export const ACCOUNT_STATEMENT_SCREEN_KEY = 'wallet.account-statement';

/** Exact privilege names (privilege.json) behind each action, per scope. */
export const STATEMENT_PRIVILEGES: Readonly<Record<StatementScope, {
  read: string; months: string; summary: string; exportFile: string; userRead?: string;
}>> = {
  self: {
    read: 'WALLET_SELF_STATEMENT_READ',
    months: 'WALLET_SELF_STATEMENT_MONTH_LIST',
    summary: 'WALLET_SELF_STATEMENT_SUMMARY_READ',
    exportFile: 'WALLET_SELF_STATEMENT_EXPORT_READ',
  },
  admin: {
    read: 'WALLET_ADMIN_STATEMENT_READ',
    months: 'WALLET_ADMIN_STATEMENT_MONTH_LIST',
    summary: 'WALLET_ADMIN_STATEMENT_SUMMARY_READ',
    exportFile: 'WALLET_ADMIN_STATEMENT_EXPORT_READ',
    userRead: 'WALLET_ADMIN_USER_STATEMENT_READ',
  },
};

/** Lines per page and the choices offered. The API caps a page at 200. */
export const STATEMENT_PAGE_SIZE = 50;
export const STATEMENT_PAGE_SIZES: readonly number[] = [25, 50, 100, 200];

/** The person's own accounts a statement can be read for; WALLET is what customers, franchise owners and agents use. */
export const OWN_ACCOUNTS: readonly OwnAccount[] = ['WALLET', 'HELD', 'FLOAT', 'EARNINGS'];
export const DEFAULT_OWN_ACCOUNT: OwnAccount = 'WALLET';

/** Translation keys of the account types; a code with no key is shown as it is. */
export const ACCOUNT_LABEL_KEYS: Readonly<Record<string, string>> = {
  WALLET: 'walletStatement.accounts.WALLET',
  HELD: 'walletStatement.accounts.HELD',
  FLOAT: 'walletStatement.accounts.FLOAT',
  EARNINGS: 'walletStatement.accounts.EARNINGS',
};

/** Translation keys of the line categories (the codes themselves are kept); an unknown code is shown as it is. */
export const CATEGORY_LABEL_KEYS: Readonly<Record<string, string>> = {
  TOP_UP: 'walletStatement.categories.TOP_UP',
  ADMIN_GRANT: 'walletStatement.categories.ADMIN_GRANT',
  SPEND: 'walletStatement.categories.SPEND',
  HOLD: 'walletStatement.categories.HOLD',
  REFUND: 'walletStatement.categories.REFUND',
  COMMISSION: 'walletStatement.categories.COMMISSION',
  FRANCHISE_COMMISSION: 'walletStatement.categories.FRANCHISE_COMMISSION',
  TRANSFER: 'walletStatement.categories.TRANSFER',
  RIDE: 'walletStatement.categories.RIDE',
  OFFLINE_CASH: 'walletStatement.categories.OFFLINE_CASH',
  ADJUSTMENT: 'walletStatement.categories.ADJUSTMENT',
  REVERSAL: 'walletStatement.categories.REVERSAL',
  OPENING_BALANCE: 'walletStatement.categories.OPENING_BALANCE',
  OTHER: 'walletStatement.categories.OTHER',
};

/** Translation keys of the franchise parties shown in a line's share. */
export const PARTY_LABEL_KEYS: Readonly<Record<string, string>> = {
  PLATFORM: 'accessManagement.myEarnings.parties.PLATFORM',
  OWNER: 'accessManagement.myEarnings.parties.OWNER',
  AGENT: 'accessManagement.myEarnings.parties.AGENT',
  REFERRER: 'accessManagement.myEarnings.parties.REFERRER',
  CUSTOMER_POOL: 'accessManagement.myEarnings.parties.CUSTOMER_POOL',
  MEMBER_CASHBACK: 'accessManagement.myEarnings.parties.MEMBER_CASHBACK',
  PROVIDER: 'accessManagement.myEarnings.parties.PROVIDER',
  TAX: 'accessManagement.myEarnings.parties.TAX',
};

/** Journal status of a line that a later reversal cancelled. */
export const JOURNAL_STATUS_REVERSED = 'REVERSED';

/** Error code and HTTP status of an export that is over the server's line cap. */
export const STATEMENT_TOO_LARGE_CODE = 'STATEMENT_TOO_LARGE';
export const STATEMENT_TOO_LARGE_STATUS = 413;


/** MIME type of each export format, used when the server sends none. */
export const EXPORT_MIME: Readonly<Record<string, string>> = { csv: 'text/csv', pdf: 'application/pdf' };

/** Shape of a month (YYYY-MM). */
export const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

/** Columns of the year overview: translation keys of the headings; cells are drawn by the screen. */
export const YEAR_SUMMARY_COLUMNS: readonly TableColumnSpec<YearSummaryMonth>[] = [
  { key: 'month', labelKey: 'walletStatement.filters.month' },
  { key: 'openingBalance', labelKey: 'walletStatement.cards.opening' },
  { key: 'credits', labelKey: 'walletStatement.table.credit' },
  { key: 'debits', labelKey: 'walletStatement.table.debit' },
  { key: 'closingBalance', labelKey: 'walletStatement.cards.closing' },
  { key: 'count', labelKey: 'walletStatement.table.count' },
];
