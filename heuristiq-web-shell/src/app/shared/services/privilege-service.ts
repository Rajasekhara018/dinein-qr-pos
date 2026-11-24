import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { HQ_PRIVILEGES_CHANGED_EVENT, HQ_PRIVILEGES_STORAGE_KEY } from 'hq-shared-lib';
import { MY_PRIVILEGES_PATH } from './privilege.constants';

/** What UAM returns for the signed-in caller (see UAM's MyPrivilegesResponse). */
export interface MyPrivileges {
  roles: string[];
  permissions: string[];
  privilegeIds: number[];
}

interface UamEnvelope<T> {
  success?: boolean;
  responseObject?: T;
}

/** 'idle': nothing known yet; 'loading': fetching with nothing cached; 'loaded': a snapshot is available; 'failed': UAM could not say. */
export type PrivilegeStatus = 'idle' | 'loading' | 'loaded' | 'failed';

/**
 * The signed-in user's privileges, used to show or hide screens. The snapshot is kept in memory and in
 * localStorage (so a reload shows the right menu at once, and Rider_AdminWeb, which runs inside this app, can
 * read it), then refreshed from UAM. If UAM cannot be reached the status is 'failed' and callers fall back to
 * the role-based hints. A UI hint only: the gateway checks every API call.
 */
@Injectable({ providedIn: 'root' })
export class PrivilegeService {
  private readonly http = inject(HttpClient);

  private granted: ReadonlySet<string> | null = null;
  private currentStatus: PrivilegeStatus = 'idle';

  get status(): PrivilegeStatus {
    return this.currentStatus;
  }

  /** True when a privilege snapshot is available to decide visibility. */
  get isLoaded(): boolean {
    return this.currentStatus === 'loaded';
  }

  /** True while there is no answer yet, so gated items should stay hidden rather than flash. */
  get isPending(): boolean {
    return this.currentStatus === 'idle' || this.currentStatus === 'loading';
  }

  /** Uses the snapshot saved by an earlier page load, if any, until the fresh answer arrives. */
  restore(): void {
    const stored = this.readStored();
    if (stored) {
      this.granted = new Set(stored.permissions);
      this.currentStatus = 'loaded';
    }
  }

  /** Fetches the caller's privileges from UAM. Never errors: on failure the status becomes 'failed'. */
  load(): Observable<void> {
    if (!this.isLoaded) {
      this.currentStatus = 'loading';
    }
    return this.http.get<UamEnvelope<MyPrivileges>>(`${environment.uamApiUrl}${MY_PRIVILEGES_PATH}`).pipe(
      map((response) => {
        if (!response.success || !response.responseObject) {
          throw new Error('Privileges response was not successful');
        }
        return response.responseObject;
      }),
      tap((privileges) => this.apply(privileges)),
      map(() => undefined),
      catchError(() => {
        // Keep an older snapshot if there is one; otherwise callers fall back to role hints.
        if (!this.isLoaded) {
          this.currentStatus = 'failed';
        }
        return of(undefined);
      }),
    );
  }

  /** Forgets everything (sign-out). */
  clear(): void {
    this.granted = null;
    this.currentStatus = 'idle';
    try {
      localStorage.removeItem(HQ_PRIVILEGES_STORAGE_KEY);
    } catch { /* storage unavailable */ }
    this.announceChange();
  }

  /** True when the user holds at least one of {@code codes}. Only meaningful once {@link isLoaded}. */
  hasAny(codes: readonly string[]): boolean {
    return !!this.granted && codes.some((code) => this.granted!.has(code));
  }

  private apply(privileges: MyPrivileges): void {
    this.granted = new Set(privileges.permissions ?? []);
    this.currentStatus = 'loaded';
    try {
      localStorage.setItem(HQ_PRIVILEGES_STORAGE_KEY, JSON.stringify(privileges));
    } catch { /* storage unavailable: the in-memory snapshot still works */ }
    this.announceChange();
  }

  /** Tells views (`*hasPrivilege`, in this app and in Rider_AdminWeb) to re-check what the user may do. */
  private announceChange(): void {
    window.dispatchEvent(new Event(HQ_PRIVILEGES_CHANGED_EVENT));
  }

  private readStored(): MyPrivileges | null {
    try {
      const raw = localStorage.getItem(HQ_PRIVILEGES_STORAGE_KEY);
      const parsed = raw ? (JSON.parse(raw) as Partial<MyPrivileges>) : null;
      return parsed && Array.isArray(parsed.permissions) ? (parsed as MyPrivileges) : null;
    } catch {
      return null;
    }
  }
}
