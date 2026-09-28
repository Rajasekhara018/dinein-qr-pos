import { Injectable, signal } from '@angular/core';

/**
 * Holds the platform admin key for the current tab only — deliberately never persisted (not `localStorage`,
 * not `sessionStorage`): it can onboard a restaurant on any tenant, so it shouldn't outlive the page. Root-provided
 * so it survives SPA navigation within one session; a full reload clears it and the operator re-enters it.
 */
@Injectable({ providedIn: 'root' })
export class PlatformPrefs {
  private readonly _key = signal<string>('');
  readonly key = this._key.asReadonly();

  setKey(key: string): void {
    this._key.set(key.trim());
  }

  clear(): void {
    this._key.set('');
  }
}
