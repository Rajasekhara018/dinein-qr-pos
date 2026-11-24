import { SettlementDirection } from './wallet-float.models';

/** Screen permission keys (mapped to privileges in hq-shared-lib's privilege map). */
export const MY_FLOAT_PERMISSION = 'view_my_float';
export const PARTNER_FLOATS_PERMISSION = 'view_partner_floats';

/** Stable audit keys (area.screen): queryable history, never renamed. */
export const MY_FLOAT_SCREEN_KEY = 'wallet.my-float';
export const PARTNER_FLOATS_SCREEN_KEY = 'wallet.partner-floats';

/** Exact privilege names (privilege.json) behind the float screens. */
export const FLOAT_PRIVILEGES = {
  selfRead: 'WALLET_PARTNER_FLOAT_SELF_READ',
  adminRead: 'WALLET_ADMIN_PARTNER_FLOAT_READ',
  alertList: 'WALLET_ADMIN_PARTNER_FLOAT_ALERT_LIST',
  alertUpdate: 'WALLET_ADMIN_PARTNER_FLOAT_ALERT_UPDATE',
  settlementCreate: 'WALLET_ADMIN_PARTNER_FLOAT_SETTLEMENT_CREATE',
  selfStatement: 'WALLET_SELF_STATEMENT_READ',
} as const;

export const FLOAT_ALERTS_PAGE_SIZE = 20;
export const SETTLEMENT_DIRECTIONS: readonly SettlementDirection[] = ['DEBIT', 'CREDIT'];
export const IDEMPOTENCY_KEY_HEADER = 'Idempotency-Key';
export const REASON_MAX = 500;

const NS = 'walletFloat';
export const FLOAT_STATUS_LABEL_KEYS: Readonly<Record<string, string>> = {
  ACTIVE: `${NS}.status.ACTIVE`, FROZEN: `${NS}.status.FROZEN`, CLOSED: `${NS}.status.CLOSED`,
};
export const DIRECTION_LABEL_KEYS: Readonly<Record<string, string>> = {
  DEBIT: `${NS}.directions.DEBIT`, CREDIT: `${NS}.directions.CREDIT`,
};
/** Error codes of the wallet service (kept untranslated) mapped to a label; any other code shows the service's own message. */
export const FLOAT_ERROR_LABEL_KEYS: Readonly<Record<string, string>> = {
  FLOAT_LIMIT_EXCEEDED: `${NS}.errors.FLOAT_LIMIT_EXCEEDED`,
  ACCOUNT_FROZEN: `${NS}.errors.ACCOUNT_FROZEN`,
  IDEMPOTENCY_CONFLICT: `${NS}.errors.IDEMPOTENCY_CONFLICT`,
  APP_NOT_ENABLED: `${NS}.errors.APP_NOT_ENABLED`,
  CUSTOMER_NOT_FOUND: `${NS}.errors.CUSTOMER_NOT_FOUND`,
};
