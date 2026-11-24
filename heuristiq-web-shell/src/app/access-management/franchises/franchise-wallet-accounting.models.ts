import { HqAmount } from 'hq-shared-lib';

/**
 * The franchise owner's accounting as Rider_WalletService answers it (`GET /api/v1/wallet/me/franchises/{id}/accounting`,
 * docs/ACCOUNTING_REBUILD_PLAN.md §2): the same body as the admin franchise view. Amounts are `{ code, value }`; null is a
 * value the wallet did not record. Codes travel untranslated.
 */

/** The franchise's totals over the period (the wallet's summary totals line). */
export interface OwnerAccountingTotals {
  count: number;
  gross: HqAmount | null;
  refunds: HqAmount | null;
  owner: HqAmount | null;
  agent: HqAmount | null;
}

/** What one share role received. */
export interface OwnerAccountingShare {
  shareParty: string;
  amount: HqAmount | null;
}

/** The franchise's payable account. */
export interface OwnerAccountingPayable {
  available: HqAmount | null;
  pending: HqAmount | null;
  paidOut: HqAmount | null;
}

/** A payout from the franchise's earnings. */
export interface OwnerAccountingPayout {
  payoutId: string;
  amount: HqAmount | null;
  status: string;
  createdAt: string | null;
  paidAt: string | null;
}

/** A recent sale or other journal of the franchise. */
export interface OwnerAccountingTransaction {
  journalId: string;
  postedAt: string | null;
  appCode: string | null;
  eventType: string | null;
  journalType: string;
  channel: string | null;
  amount: HqAmount | null;
  status: string | null;
}

export interface OwnerFranchiseAccounting {
  franchiseId: string;
  totals: OwnerAccountingTotals;
  shares: OwnerAccountingShare[];
  payable: OwnerAccountingPayable;
  payouts: OwnerAccountingPayout[];
  recentTransactions: OwnerAccountingTransaction[];
}

/** Period (yyyy-MM-dd as typed; blank = open) and app of the view. */
export interface OwnerAccountingFilter {
  app?: string;
  from: string;
  to: string;
}
