import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { SessionResponse } from '../../../core/api/models';
import { PublicApi } from '../../../core/api/public.api';
import { formatOpeningHours } from '../../../core/util/time';

export type SessionStatus = 'idle' | 'loading' | 'ready' | 'invalid' | 'error';
export type SessionOutcome = 'ready' | 'invalid' | 'error';

/** Guest session: which table we are at and the restaurant's public info (branding, open/closed). */
@Injectable({ providedIn: 'root' })
export class GuestSessionStore {
  private readonly api = inject(PublicApi);

  private readonly _status = signal<SessionStatus>('idle');
  private readonly _session = signal<SessionResponse | null>(null);
  private readonly _error = signal<ApiError | null>(null);

  readonly status = this._status.asReadonly();
  readonly session = this._session.asReadonly();
  readonly error = this._error.asReadonly();

  readonly table = computed(() => this._session()?.table ?? null);
  readonly restaurant = computed(() => this._session()?.restaurant ?? null);

  /** Both switches must be on for checkout; the menu stays browsable either way. */
  readonly canOrder = computed(() => {
    const r = this.restaurant();
    return !!r && r.acceptingOrders && r.openNow;
  });

  readonly openingHours = computed(() => {
    const r = this.restaurant();
    return r ? formatOpeningHours(r.openingTime, r.closingTime) : '';
  });

  /** Why ordering is unavailable, or null. */
  readonly closedReason = computed<'not-accepting' | 'outside-hours' | null>(() => {
    const r = this.restaurant();
    if (!r) return null;
    if (!r.acceptingOrders) return 'not-accepting';
    if (!r.openNow) return 'outside-hours';
    return null;
  });

  /**
   * Starts (with a QR token) or resumes (cookie only) the session.
   * `invalid` = bad/inactive table token or no session cookie → ask the guest to scan the table QR.
   */
  async start(qrToken?: string | null, restaurantId?: string | null): Promise<SessionOutcome> {
    this._status.set('loading');
    this._error.set(null);
    try {
      const session = await firstValueFrom(this.api.session(qrToken, restaurantId));
      this._session.set(session);
      this._status.set('ready');
      return 'ready';
    } catch (e) {
      const error = ApiError.from(e);
      this._error.set(error);
      const invalid =
        error.code === 'INVALID_TABLE' ||
        error.code === 'GUEST_SESSION_REQUIRED' ||
        error.status === 401 ||
        error.status === 404;
      this._status.set(invalid ? 'invalid' : 'error');
      if (invalid) this._session.set(null);
      return invalid ? 'invalid' : 'error';
    }
  }

  /** Silently refreshes restaurant info (e.g. after ORDERING_CLOSED) without flipping to a loading state. */
  async refresh(): Promise<void> {
    try {
      const session = await firstValueFrom(this.api.session());
      this._session.set(session);
    } catch {
      // keep the previous info
    }
  }
}
