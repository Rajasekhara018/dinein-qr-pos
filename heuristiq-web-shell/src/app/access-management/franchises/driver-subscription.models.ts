import type { HqAmount } from 'hq-shared-lib';

/**
 * A driver's subscription as an administrator or the driver's franchise owner sees it when subscribing them
 * (Rider_FranchiseService /driver-subscriptions and /{franchiseId}/drivers/{driverUserId}/subscription). Codes stay untranslated.
 */
export type DriverSubscriptionPeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY';

/** A plan open to the driver: their franchise's plan per period where it has one, otherwise the organization's. */
export interface DriverSubscriptionPlanOption {
  id: string;
  franchiseId: string | null;
  name: string;
  period: DriverSubscriptionPeriod;
  price: HqAmount | null;
  cashbackPercent: number;
  active: boolean;
  effectiveFrom: string | null;
  effectiveTo: string | null;
}

/** The driver's subscription after a subscribe. */
export interface DriverSubscriptionState {
  id: string;
  planId: string;
  nextPlanId: string | null;
  status: 'ACTIVE' | 'BLOCKED_DUES' | 'EXPIRED' | string;
  validUntil: string | null;
}

/** The answer to a subscribe. */
export interface DriverSubscriptionResult {
  active: boolean;
  blockedReason: string | null;
  subscription: DriverSubscriptionState | null;
}
