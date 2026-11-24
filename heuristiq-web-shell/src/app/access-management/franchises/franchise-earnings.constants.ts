import { TableColumnSpec } from '../../shared/table-columns.helpers';
import type { OwnerAccountingPayout, OwnerAccountingShare, OwnerAccountingTransaction } from './franchise-wallet-accounting.models';

/** Screen permission keys (mapped to privileges in hq-shared-lib's privilege map) and audit screen keys of the earnings screens. */
export const MY_EARNINGS_PERMISSION = 'view_my_earnings';
export const MY_PAYOUTS_PERMISSION = 'view_my_payouts';

/** Stable audit keys (area.screen): queryable history, never renamed. */
export const MY_EARNINGS_SCREEN_KEY = 'access_management.my_earnings';
export const MY_PAYOUTS_SCREEN_KEY = 'access_management.my_payouts';

/** Privilege codes (exact names in privilege.json) behind the actions on the earnings screens. */
export const EARNINGS_PRIVILEGES = {
  /** The owner's accounting of a franchise, read live from the wallet. */
  readAccounting: 'WALLET_FRANCHISE_ACCOUNTING_MINE_READ',
  listOwnFranchises: 'FRANCHISE_MINE_LIST',
  listEntries: 'LEDGER_ENTRY_MINE_LIST',
  readStatement: 'COMMISSION_STATEMENT_MINE_READ',
  listPayouts: 'PAYOUT_REQUEST_MINE_LIST',
  requestPayout: 'PAYOUT_REQUEST_CREATE',
} as const;

/** Rows per page of the ledger and payout lists. */
export const EARNINGS_PAGE_SIZE = 20;

/** Currency a payout is asked in until the person picks another. */
export const DEFAULT_PAYOUT_CURRENCY = 'INR';

/** Translation keys of the parties; a code with no key is shown as it is. */
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

/** Translation keys of the payout statuses; a code with no key is shown as it is. */
export const PAYOUT_STATUS_LABEL_KEYS: Readonly<Record<string, string>> = {
  REQUESTED: 'accessManagement.myPayouts.statuses.REQUESTED',
  APPROVED: 'accessManagement.myPayouts.statuses.APPROVED',
  REJECTED: 'accessManagement.myPayouts.statuses.REJECTED',
};

/**
 * The parties the wallet pays out: a franchise's earnings (asked by its owner) and the person's own agent earnings. A referrer's
 * or member's cashback share is credited to their own wallet and is not paid out here.
 */
export const PAYABLE_PARTIES: readonly string[] = ['OWNER', 'AGENT'];

/** Header that makes a repeated payout request answer the same payout. */
export const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key';

/** Translation keys of the entry views (payable account or its pending twin). */
export const EARNINGS_STATE_LABEL_KEYS: Readonly<Record<string, string>> = {
  AVAILABLE: 'accessManagement.myEarnings.states.AVAILABLE',
  PENDING: 'accessManagement.myEarnings.states.PENDING',
};

/** The entry views in the order they are offered. */
export const EARNINGS_STATES: readonly ('AVAILABLE' | 'PENDING')[] = ['AVAILABLE', 'PENDING'];

/** Translation keys of an entry's direction; a code with no key is shown as it is. */
export const ENTRY_DIRECTION_LABEL_KEYS: Readonly<Record<string, string>> = {
  CREDIT: 'accessManagement.myEarnings.directions.CREDIT',
  DEBIT: 'accessManagement.myEarnings.directions.DEBIT',
};

/** Translation keys of the wallet payout statuses (older Franchise statuses kept for their history). */
export const WALLET_PAYOUT_STATUS_LABEL_KEYS: Readonly<Record<string, string>> = {
  ...PAYOUT_STATUS_LABEL_KEYS,
  PENDING_APPROVAL: 'accessManagement.myPayouts.statuses.PENDING_APPROVAL',
  PAID: 'accessManagement.myPayouts.statuses.PAID',
  RETURNED: 'accessManagement.myPayouts.statuses.RETURNED',
};

/** Translation keys of the account names: the person's agent earnings, and a franchise (its code as {{code}}). */
export const AGENT_ACCOUNT_KEY = 'accessManagement.myEarnings.agentAccount';
export const FRANCHISE_ACCOUNT_KEY = 'accessManagement.myEarnings.franchiseAccount';

/** The owner's wallet accounting on My earnings: columns of its tables (translation keys of the headings; cells are drawn by the screen). */
export const OWNER_SHARE_COLUMNS: readonly TableColumnSpec<OwnerAccountingShare>[] = [
  { key: 'shareParty', labelKey: 'accessManagement.myEarnings.party' },
  { key: 'amount', labelKey: 'accessManagement.myEarnings.amount' },
];
export const OWNER_PAYOUT_COLUMNS: readonly TableColumnSpec<OwnerAccountingPayout>[] = [
  { key: 'createdAt', labelKey: 'accessManagement.myEarnings.walletAccounting.requestedAt' },
  { key: 'amount', labelKey: 'accessManagement.myEarnings.amount' },
  { key: 'status', labelKey: 'accessManagement.myEarnings.walletAccounting.status' },
  { key: 'paidAt', labelKey: 'accessManagement.myEarnings.walletAccounting.paidAt' },
];
export const OWNER_TRANSACTION_COLUMNS: readonly TableColumnSpec<OwnerAccountingTransaction>[] = [
  { key: 'postedAt', labelKey: 'accessManagement.myEarnings.postedAt' },
  { key: 'appCode', labelKey: 'accessManagement.myEarnings.walletAccounting.app' },
  { key: 'eventType', labelKey: 'accessManagement.myEarnings.event' },
  { key: 'channel', labelKey: 'accessManagement.myEarnings.walletAccounting.channel' },
  { key: 'amount', labelKey: 'accessManagement.myEarnings.amount' },
];

/** Time appended to a yyyy-MM-dd period bound to make an inclusive UTC range. */
export const OWNER_ACCOUNTING_DAY_START = 'T00:00:00Z';
export const OWNER_ACCOUNTING_DAY_END = 'T23:59:59Z';
/** The share keys of the wallet's shares object and the share role each one is (order of the breakdown table). */
export const OWNER_SHARE_KEYS: readonly (readonly [string, string])[] = [
  ['platform', 'PLATFORM'], ['owner', 'OWNER'], ['agent', 'AGENT'], ['referrer', 'REFERRER'], ['provider', 'PROVIDER'], ['tax', 'TAX'],
  ['customerPool', 'CUSTOMER_POOL'], ['memberCashback', 'MEMBER_CASHBACK'],
];

/** Translation keys of the accounting events; a code with no key is shown as it is. */
export const OWNER_EVENT_LABEL_KEYS: Readonly<Record<string, string>> = Object.fromEntries(
  [
    'RIDE_COMPLETED', 'DRIVER_SUBSCRIPTION', 'DRIVER_SUBSCRIPTION_CASHBACK', 'MATRIMONY_SUBSCRIPTION', 'CREDIT_PACK', 'ONBOARDING_FEE',
    'MARRIAGE_CONFIRMED', 'CREDIT_SPEND', 'WALLET_TOPUP', 'PAYMENT_REFUND', 'WELCOME_CREDIT', 'ADMIN_GRANT', 'PAYOUT',
  ].map((e) => [e, `accessManagement.myEarnings.events.${e}`]),
);

/** Translation keys of the wallet channels; a code with no key is shown as it is. */
export const OWNER_CHANNEL_LABEL_KEYS: Readonly<Record<string, string>> = Object.fromEntries(
  ['WALLET', 'GATEWAY', 'CASH', 'UPI', 'OFFLINE', 'EARNINGS', 'PAYOUT', 'INTERNAL'].map((c) => [c, `accessManagement.myEarnings.channels.${c}`]),
);
