/** Steps the go-live list never shows: the summary step UAM adds at the end. */
export const GO_LIVE_STEP_EXCLUDED_IDS: readonly string[] = ['GO_LIVE_REVIEW'];

/** Services whose steps are left out (reports are switched off for now). */
export const GO_LIVE_STEP_EXCLUDED_SERVICES: readonly string[] = ['reports'];

/** Step ids left out by prefix: reports checks, and anything about owner teams, roles or users. */
export const GO_LIVE_STEP_EXCLUDED_PREFIXES: readonly string[] = ['REPORTS_', 'UAM_OWNER_', 'UAM_ROLE_', 'UAM_USER_'];
