/** Screen permission that makes the pending-reviews badge relevant (the change-requests list privilege). */
export const PENDING_REVIEWS_PERMISSION = 'review_changes';

/** How often the sidebar badge is refreshed while the app is open. */
export const PENDING_REVIEWS_POLL_MS = 60_000;

/** Counts above this are shown as "99+". */
export const PENDING_REVIEWS_BADGE_MAX = 99;

/** Path under the workflow API base; `pendingForMe` limits the list to requests this user may decide. */
export const PENDING_REVIEWS_PATH = '/change-requests';

export const TOTAL_COUNT_HEADER = 'X-Total-Count';

/** NavItem.badge value that shows the pending-reviews count. */
export const PENDING_REVIEWS_BADGE = 'pendingReviews';
