import { HqAmount } from 'hq-shared-lib';

/**
 * Shapes of Rider_WalletService's statement API (docs/WALLET_STATEMENTS_API.md). Money is `{ code, value }` (money stage 3):
 * display only, through `| hqMoney` (which also reads an older 2-decimal string).
 */

export interface StatementAccount {
  accountId: string | null;
  accountNo: string | null;
  accountType: string | null;
  ownerId: string | null;
  appCode: string | null;
  currency: string;
  normalSide: string | null;
}

export interface StatementPeriod {
  month: string;
  from: string;
  to: string;
  timezone: string;
}

export interface StatementTotals {
  credits: HqAmount | null;
  debits: HqAmount | null;
  count: number;
}

export interface StatementCategory {
  category: string;
  credits: HqAmount | null;
  debits: HqAmount | null;
  count: number;
}

export interface StatementReference {
  journalId: string | null;
  referenceType: string | null;
  referenceId: string | null;
  reversalOfJournalId: string | null;
  journalStatus: string | null;
  correlationId: string | null;
}

export interface FranchiseShare {
  party: string;
  partyRef: string | null;
  amount: HqAmount | null;
  planId: string | null;
  planVersion: number | null;
}

export interface StatementFranchise {
  eventId: string;
  franchiseId: string | null;
  shares: FranchiseShare[];
}

export interface StatementLine {
  entryId: string;
  postedAt: string;
  description: string | null;
  reference: StatementReference | null;
  journalType: string | null;
  category: string;
  appCode: string | null;
  credit: HqAmount | null;
  debit: HqAmount | null;
  balanceAfter: HqAmount | null;
  franchise: StatementFranchise | null;
}

export interface WalletStatement {
  account: StatementAccount;
  period: StatementPeriod;
  openingBalance: HqAmount | null;
  closingBalance: HqAmount | null;
  totals: StatementTotals;
  categories: StatementCategory[];
  reconciled: boolean;
  lines: StatementLine[];
  page: number;
  size: number;
  totalLines: number;
  totalPages: number;
  generatedAt: string;
}

export interface StatementMonth {
  month: string;
  count: number;
}

export interface StatementMonths {
  account: StatementAccount | string | null;
  timezone: string;
  months: StatementMonth[];
}

export interface YearSummaryMonth {
  month: string;
  openingBalance: HqAmount | null;
  credits: HqAmount | null;
  debits: HqAmount | null;
  closingBalance: HqAmount | null;
  count: number;
}

export interface YearSummary {
  account: StatementAccount | string | null;
  year: number;
  timezone: string;
  openingBalance: HqAmount | null;
  closingBalance: HqAmount | null;
  months: YearSummaryMonth[];
  reconciled: boolean;
}

/** Who the statement is read for: the signed-in person, or an account/user an administrator picked. */
export type StatementScope = 'self' | 'admin';

export type StatementFormat = 'csv' | 'pdf';

/** Account type of the person's own statement. */
export type OwnAccount = 'WALLET' | 'HELD' | 'FLOAT' | 'EARNINGS';

/** What one request reads. {@code accountId} is the admin account route; {@code userId} the admin user route. */
export interface StatementQuery {
  scope: StatementScope;
  accountId?: string;
  userId?: string;
  account?: string;
  app?: string;
  month?: string;
  page?: number;
  size?: number;
}

/** A downloaded statement file and the name to save it under. */
export interface StatementFile {
  blob: Blob;
  filename: string;
}

export type StatementView = 'month' | 'year';
