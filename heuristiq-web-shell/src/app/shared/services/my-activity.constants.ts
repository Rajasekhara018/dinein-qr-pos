/** Gateway path (below environment.gatewayUrl) of the caller's own audit events. */
export const MY_ACTIVITY_PATH = '/v1/audit/me/events';

/** Events requested for the profile Activity tab (more than shown, because read-only calls are filtered out). */
export const MY_ACTIVITY_PAGE_SIZE = 50;

/** Read-only API calls are background noise (every page load makes several), so the Activity tab hides them. */
export const ACTIVITY_HIDDEN_METHODS: ReadonlySet<string> = new Set(['GET', 'HEAD', 'OPTIONS']);

/** Event types the audit service records (its `AuditEventType`). */
export const ACTIVITY_EVENT_SCREEN_VIEW = 'SCREEN_VIEW';
export const ACTIVITY_EVENT_ACCESS = 'ACCESS';
export const ACTIVITY_EVENT_COMMUNICATION = 'COMMUNICATION';
export const ACTIVITY_EVENT_PAYMENT = 'PAYMENT_GATEWAY';
export const ACTIVITY_EVENT_DOMAIN = 'DOMAIN';

/** Outcomes the audit service records (its `AuditOutcome`). */
export const ACTIVITY_OUTCOME_FAILURE = 'FAILURE';
export const ACTIVITY_OUTCOME_DENIED = 'DENIED';

/** Bootstrap icon per event type. */
export const ACTIVITY_EVENT_ICONS: Readonly<Record<string, string>> = {
  [ACTIVITY_EVENT_SCREEN_VIEW]: 'bi-window',
  [ACTIVITY_EVENT_ACCESS]: 'bi-arrow-left-right',
  [ACTIVITY_EVENT_COMMUNICATION]: 'bi-bell-fill',
  [ACTIVITY_EVENT_PAYMENT]: 'bi-credit-card-fill',
  [ACTIVITY_EVENT_DOMAIN]: 'bi-pencil-fill',
};

/** Fallback icon for an event type without one. */
export const ACTIVITY_DEFAULT_ICON = 'bi-clock-history';

/** Translation key per event type for the activity title (screen views and requests build their own text). */
export const ACTIVITY_EVENT_TITLE_KEYS: Readonly<Record<string, string>> = {
  [ACTIVITY_EVENT_COMMUNICATION]: 'profile.activity.communication',
  [ACTIVITY_EVENT_PAYMENT]: 'profile.activity.payment',
  [ACTIVITY_EVENT_DOMAIN]: 'profile.activity.domain',
};
