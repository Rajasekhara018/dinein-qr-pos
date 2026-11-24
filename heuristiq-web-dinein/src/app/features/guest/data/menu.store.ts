import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { MenuItem, MenuResponse } from '../../../core/api/models';
import { PublicApi } from '../../../core/api/public.api';

export type LoadStatus = 'idle' | 'loading' | 'ready' | 'error';

/**
 * The guest menu. `load()` is deduplicated and keeps showing the previous menu while revalidating (the browser
 * handles the ETag / 304 round-trip).
 */
@Injectable({ providedIn: 'root' })
export class MenuStore {
  private readonly api = inject(PublicApi);

  private readonly _menu = signal<MenuResponse | null>(null);
  private readonly _status = signal<LoadStatus>('idle');
  private readonly _error = signal<ApiError | null>(null);
  private inFlight: Promise<void> | null = null;

  readonly menu = this._menu.asReadonly();
  readonly status = this._status.asReadonly();
  readonly error = this._error.asReadonly();

  readonly categories = computed(() => this._menu()?.categories ?? []);
  readonly itemsById = computed(() => {
    const map = new Map<number, MenuItem>();
    for (const category of this.categories())
      for (const item of category.items) map.set(item.id, item);
    return map;
  });

  load(): Promise<void> {
    this.inFlight ??= (async () => {
      if (!this._menu()) this._status.set('loading');
      try {
        const menu = await firstValueFrom(this.api.menu());
        this._menu.set(menu);
        this._error.set(null);
        this._status.set('ready');
      } catch (e) {
        this._error.set(ApiError.from(e));
        // Keep a previously loaded menu visible; only show the error state when we have nothing.
        this._status.set(this._menu() ? 'ready' : 'error');
      } finally {
        this.inFlight = null;
      }
    })();
    return this.inFlight;
  }
}
