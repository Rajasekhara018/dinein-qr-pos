/** Longest server text shown to the user; anything longer is cut with an ellipsis. */
export const API_ERROR_MAX_LENGTH = 300;

/** Texts that say nothing useful; when the server sends only one of these, the screen's own fallback is shown instead. */
export const API_ERROR_GENERIC_MESSAGES: readonly string[] = [
  'error',
  'failure',
  'failed',
  'bad request',
  'forbidden',
  'not found',
  'internal server error',
  'validation failed',
  'an unexpected error occurred. please try again later.',
];

/** Lowest HTTP status treated as a server fault, whose internals are never shown. */
export const API_ERROR_SERVER_STATUS_FROM = 500;
