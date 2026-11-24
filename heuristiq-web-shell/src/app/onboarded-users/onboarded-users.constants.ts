import { OnboardingSubmissionStatus } from './onboarded-users.models';

export const DEFAULT_PAGE_SIZE = 10;
export const PAGE_SIZE_OPTIONS: readonly number[] = [10, 20, 50];

/** Onboarding type codes the filter offers (the service seeds exactly these two). */
export const TYPE_OPTIONS: readonly string[] = ['CUSTOMER', 'DRIVER'];

export const STATUS_OPTIONS: readonly OnboardingSubmissionStatus[] = [
  'IN_PROGRESS',
  'SUBMITTED',
  'UNDER_REVIEW',
  'APPROVED',
  'REJECTED',
  'ACCOUNT_CREATION_FAILED',
  'NEEDS_CORRECTION',
];

/** Answer keys the default CUSTOMER/DRIVER schemas use for the headline columns. */
export const ANSWER_KEY_NAME = 'fullName';
export const ANSWER_KEY_MOBILE = 'mobile';
export const ANSWER_KEY_EMAIL = 'email';

export const EMPTY_VALUE = '—';

/** Onboarding type whose approved submissions are linked to a franchise (backend code, not translated). */
export const FRANCHISE_LINKED_TYPE = 'DRIVER';

/** Status in which a driver's franchise link can be repaired with "Re-link franchise". */
export const RELINK_STATUS = 'APPROVED';

/** Privilege of the onboarding admin endpoints (wildcard MANAGE entry in privilege.json). */
export const ONBOARDING_ADMIN_PRIVILEGE = 'RIDER_ONBOARDING_ADMIN_ACCESS';
