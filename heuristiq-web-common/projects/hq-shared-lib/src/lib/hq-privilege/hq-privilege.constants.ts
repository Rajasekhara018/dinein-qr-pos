/** localStorage key holding the signed-in user's privileges (saved by Rider_Web, read by Rider_AdminWeb too). */
export const HQ_PRIVILEGES_STORAGE_KEY = 'user_privileges';

/** Fired on `window` whenever the saved privileges change (sign-in, refresh, sign-out), so views can re-check. */
export const HQ_PRIVILEGES_CHANGED_EVENT = 'rider:privileges-changed';
