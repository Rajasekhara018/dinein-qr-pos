/**
 * UAM sign-in refusals shown in the user's language, by the envelope's `errorCode`. Any other refusal shows the server's
 * text through `apiErrorMessage`. The codes are API values and are never translated.
 */
export const LOGIN_ERROR_CODE_KEYS: Readonly<Record<string, string>> = {
  /** The account's franchise role is its only access and none of its franchises is active (closed, suspended or pending). */
  FRANCHISE_INACTIVE: 'login.messages.franchiseInactive',
};

/** UAM's refusal when the organization's security policy needs a verified email and the account's is not: the page then emails a code. */
export const EMAIL_VERIFICATION_REQUIRED_CODE = 'EMAIL_VERIFICATION_REQUIRED';

/** OTP key namespace of the web portal's email codes (UAM looks the account up by email alone). */
export const LOGIN_OTP_USER_TYPE = 'OPS';

/** Shape of an email address the page can send a code to. */
export const LOGIN_EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Picture above the default (translated, cab/train) sign-in story: the rail-cab scene. A deployment with its own
 * `loginStory` shows only the picture it names in `loginStory.illustration`, or none.
 */
export const LOGIN_DEFAULT_STORY_ILLUSTRATION = 'assets/illustrations/rail-cab.svg';

/** CSS custom properties the pointer-tilt directive sets on its host: the pointer's offset from the centre, -1 to 1. */
export const POINTER_TILT_VARS = { x: '--tilt-x', y: '--tilt-y' } as const;

/** Media queries that switch the tilt off: no hover-capable pointer (touch) or the user asked for less motion. */
export const POINTER_TILT_FINE_POINTER_QUERY = '(pointer: fine)';
export const POINTER_TILT_REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)';
