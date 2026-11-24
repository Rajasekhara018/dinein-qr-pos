import { ACCESS_STATUS, ACCESS_TEXT_KEYS } from './application-access.constants';

/** The translation key of the sentence for a failed call, chosen by HTTP status (an error or the status itself). */
export function accessFailureKey(err: unknown): string {
  switch (typeof err === 'number' ? err : httpStatusOf(err)) {
    case ACCESS_STATUS.forbidden:
      return ACCESS_TEXT_KEYS.errorForbidden;
    case ACCESS_STATUS.conflict:
      return ACCESS_TEXT_KEYS.errorDuplicate;
    case ACCESS_STATUS.notFound:
      return ACCESS_TEXT_KEYS.errorNotFound;
    case ACCESS_STATUS.badRequest:
      return ACCESS_TEXT_KEYS.errorInvalid;
    default:
      return ACCESS_TEXT_KEYS.errorFailed;
  }
}

/** The HTTP status of a failed call (0 when the error carries none). */
export function httpStatusOf(err: unknown): number {
  const status = typeof err === 'object' && err !== null ? (err as { status?: unknown }).status : undefined;
  return typeof status === 'number' ? status : 0;
}
