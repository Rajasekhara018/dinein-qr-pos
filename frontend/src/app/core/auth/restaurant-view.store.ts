import { inject, Injectable, signal } from '@angular/core';
import { SafeStorage } from '../util/storage';
import { AuthStore } from './auth.store';

const STORAGE_KEY = 'dinein.admin.viewingRestaurantId.v1';

/**
 * A `platformAdmin` account (see `StaffUserEntity`) can switch which restaurant its admin session acts on, via
 * the `X-Restaurant-Id` header (see `TokenAuthenticationFilter.withRestaurantOverride` on the backend, and
 * `authInterceptor`, which attaches it). `null` means "my own restaurant" (the JWT's own `rid` claim, no header
 * sent) — the ordinary case for every non-platform-admin account, which this header would be ignored for anyway.
 *
 * Persisted per-browser (not per-tab) so reloading, or opening a new admin tab, keeps the platform admin looking
 * at the restaurant they were last viewing rather than silently snapping back to their own.
 */
@Injectable({ providedIn: 'root' })
export class RestaurantViewStore {
  private readonly storage = inject(SafeStorage);
  private readonly auth = inject(AuthStore);

  private readonly _restaurantId = signal<number | null>(this.readStored());
  readonly restaurantId = this._restaurantId.asReadonly();

  view(restaurantId: number | null): void {
    this._restaurantId.set(restaurantId);
    if (restaurantId === null) this.storage.removeItem(STORAGE_KEY);
    else this.storage.setItem(STORAGE_KEY, String(restaurantId));
  }

  /** The header value for the next admin request, or `null` to send none. */
  headerValue(): string | null {
    if (!this.auth.isPlatformAdmin()) return null;
    const id = this._restaurantId();
    return id === null ? null : String(id);
  }

  private readStored(): number | null {
    const raw = this.storage.getItem(STORAGE_KEY);
    const n = raw ? Number(raw) : NaN;
    return Number.isFinite(n) ? n : null;
  }
}
