/** Icon of the "Total" card per screen; Active/Inactive share the same icons everywhere. */
export const TOTAL_ICON = {
  users: 'bi-people-fill',
  organizations: 'bi-building-fill',
  applications: 'bi-grid-3x3-gap-fill',
  clients: 'bi-plug-fill',
  securityPolicies: 'bi-shield-lock-fill',
  templates: 'bi-chat-square-text-fill',
} as const;

export const ACTIVE_ICON = 'bi-check-circle-fill';
export const INACTIVE_ICON = 'bi-slash-circle-fill';
