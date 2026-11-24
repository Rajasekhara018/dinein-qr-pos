import { HqAmount } from 'hq-shared-lib';

/** Float status of the holder's account. */
export type FloatStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';
export type SettlementDirection = 'DEBIT' | 'CREDIT';

/**
 * A partner's or agent's float. Every amount is `{ code, value }` (money stage 3) and is only shown (`| hqMoney`, which also
 * reads an older 2-decimal string), never computed on.
 */
export interface FloatSummary {
  userId: string;
  accountId: string | null;
  status: FloatStatus | string;
  balance: HqAmount | null;
  creditLimit: HqAmount | null;
  available: HqAmount | null;
  creditUsed: HqAmount | null;
  alertThreshold: HqAmount | null;
  belowAlert: boolean;
  creditLimitSetBy?: string | null;
  creditLimitSetAt?: string | null;
  creditLimitReason?: string | null;
  accountVersion?: number | null;
}

/** One page of floats that are below their alert threshold. */
export interface FloatAlertPage {
  content: FloatSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Body of the alert-threshold change; a null threshold clears it. */
export interface AlertThresholdRequest {
  threshold: HqAmount | null;
  reason: string;
}

export interface SettlementRequest {
  appCode: string;
  amount: HqAmount;
  direction: SettlementDirection;
  reason: string;
}

/** What a posting answers (only the fields the screen shows). */
export interface FloatJournal {
  journalId: string;
  journalType: string;
  totalAmount: HqAmount | null;
  replayed: boolean;
}
