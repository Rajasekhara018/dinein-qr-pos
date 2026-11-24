import { LOGIN_ERROR_CODE_KEYS } from './login.constants';

interface ErrorEnvelopeShape {
  error?: { errorCode?: unknown } | null;
}

/** The `errorCode` UAM put on a refusal, or '' when it named none. */
export function loginErrorCode(error: unknown): string {
  const body = (error as ErrorEnvelopeShape | null)?.error;
  return body && typeof body === 'object' && typeof body.errorCode === 'string' ? body.errorCode : '';
}

/** The translation key for a sign-in refusal UAM marks with a known `errorCode`, or null for any other failure. */
export function loginErrorKey(error: unknown): string | null {
  const code = loginErrorCode(error);
  return Object.prototype.hasOwnProperty.call(LOGIN_ERROR_CODE_KEYS, code) ? LOGIN_ERROR_CODE_KEYS[code] : null;
}

/** Where a pointer sits in an area, as offsets from its centre between -1 (left/top) and 1 (right/bottom). */
export interface PointerOffset {
  x: number;
  y: number;
}

/** The pointer's offset from the centre of a `width` x `height` area, clamped to -1..1 (0 for an empty area). */
export function pointerOffset(clientX: number, clientY: number, width: number, height: number): PointerOffset {
  const axis = (position: number, size: number) => (size > 0 ? Math.max(-1, Math.min(1, (position / size) * 2 - 1)) : 0);
  return { x: axis(clientX, width), y: axis(clientY, height) };
}
