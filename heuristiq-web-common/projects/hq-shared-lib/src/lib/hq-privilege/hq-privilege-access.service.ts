import { Injectable } from '@angular/core';
import { Observable, fromEvent, map } from 'rxjs';
import { HQ_PRIVILEGES_CHANGED_EVENT, HQ_PRIVILEGES_STORAGE_KEY } from './hq-privilege.constants';

/**
 * Answers "may the signed-in user perform this action?" from the privileges saved at sign-in, so a screen can
 * show or hide an individual button, row action or column, not just the whole screen.
 *
 * A UI hint only: the gateway checks every API call. When no privileges are saved yet (an older session, or UAM
 * could not be reached) the answer is "yes", so the screen behaves as it did before privileges existed and the
 * gateway stays the real gate.
 */
@Injectable({ providedIn: 'root' })
export class HqPrivilegeAccessService {
  /** Emits whenever the saved privileges change; the `*hqHasPrivilege` directive re-checks on it. */
  readonly changes$: Observable<void> = fromEvent(window, HQ_PRIVILEGES_CHANGED_EVENT).pipe(map(() => undefined));

  /** True when the user holds at least one of {@code codes} (a privilege code from privilege.json, or several). */
  can(codes: string | readonly string[] | null | undefined): boolean {
    const wanted = typeof codes === 'string' ? [codes] : [...(codes ?? [])];
    if (wanted.length === 0) {
      return true;
    }
    const granted = this.granted();
    return granted === null || wanted.some((code) => granted.has(code));
  }

  /**
   * Whether the saved privileges open the screen with this permission key: true or false when they decide it,
   * null when they cannot (no privileges saved, or the key has no mapping), so the caller can fall back.
   */
  canOpen(permission: string, mapping: Readonly<Record<string, readonly string[]>>): boolean | null {
    const required = mapping[permission];
    const granted = required ? this.granted() : null;
    return required && granted ? required.some((code) => granted.has(code)) : null;
  }

  /** The saved permission codes, or null when none are saved. */
  granted(): ReadonlySet<string> | null {
    try {
      const raw = localStorage.getItem(HQ_PRIVILEGES_STORAGE_KEY);
      const parsed = raw ? (JSON.parse(raw) as { permissions?: unknown }) : null;
      return parsed && Array.isArray(parsed.permissions) ? new Set(parsed.permissions as string[]) : null;
    } catch {
      return null;
    }
  }
}
