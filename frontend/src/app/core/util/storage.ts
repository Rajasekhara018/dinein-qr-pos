import { Injectable } from '@angular/core';

/**
 * `localStorage` that never throws (private mode, blocked storage, quota exceeded, corrupt JSON).
 * Every read falls back to `null`; every write returns whether it succeeded.
 */
@Injectable({ providedIn: 'root' })
export class SafeStorage {
  private get store(): Storage | null {
    try {
      return typeof localStorage === 'undefined' ? null : localStorage;
    } catch {
      return null;
    }
  }

  getItem(key: string): string | null {
    try {
      return this.store?.getItem(key) ?? null;
    } catch {
      return null;
    }
  }

  setItem(key: string, value: string): boolean {
    try {
      const store = this.store;
      if (!store) return false;
      store.setItem(key, value);
      return true;
    } catch {
      return false;
    }
  }

  removeItem(key: string): void {
    try {
      this.store?.removeItem(key);
    } catch {
      // ignore
    }
  }

  getJson<T>(key: string): T | null {
    const raw = this.getItem(key);
    if (raw == null) return null;
    try {
      return JSON.parse(raw) as T;
    } catch {
      this.removeItem(key);
      return null;
    }
  }

  setJson(key: string, value: unknown): boolean {
    try {
      return this.setItem(key, JSON.stringify(value));
    } catch {
      return false;
    }
  }
}

/** Namespaced storage keys used across the app. */
export const STORAGE_KEYS = {
  cart: (tableId: number) => `dinein.cart.v1.${tableId}`,
  kitchenDevice: 'dinein.kitchen.device.v1',
} as const;
