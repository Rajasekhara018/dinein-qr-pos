/** Privilege codes (exact names in privilege.json) behind subscribing a driver: the administrator's and the franchise owner's. */
export const DRIVER_SUBSCRIBE_PRIVILEGES = {
  adminPlans: 'DRIVER_SUBSCRIPTION_DRIVER_PLAN_LIST',
  adminSubscribe: 'DRIVER_SUBSCRIPTION_CREATE',
  ownerPlans: 'FRANCHISE_DRIVER_SUBSCRIPTION_OFFER_LIST',
  ownerSubscribe: 'FRANCHISE_DRIVER_SUBSCRIPTION_CREATE',
} as const;

/** Width of the "driver subscription" dialog, and the id of its title (for aria-labelledby). */
export const DRIVER_SUBSCRIPTION_DIALOG_WIDTH = '520px';
export const DRIVER_SUBSCRIPTION_DIALOG_TITLE_ID = 'driver-subscription-dialog-title';

/** Translation keys of the plan periods; a code with no key is shown as it is. */
export const DRIVER_SUBSCRIPTION_PERIOD_KEYS: Readonly<Record<string, string>> = {
  DAILY: 'accessManagement.driverSubscription.periods.DAILY',
  WEEKLY: 'accessManagement.driverSubscription.periods.WEEKLY',
  MONTHLY: 'accessManagement.driverSubscription.periods.MONTHLY',
};
