import { inject, Injectable, signal } from '@angular/core';
import { SafeStorage } from '../../../core/util/storage';

export type WaiterTheme = 'light' | 'dark';

const THEME_KEY = 'dinein.waiter.theme.v1';

/** Per-device waiter app theme preference, persisted in localStorage (never throws). Light is the default. */
@Injectable({ providedIn: 'root' })
export class WaiterPrefs {
  private readonly storage = inject(SafeStorage);

  private readonly _theme = signal<WaiterTheme>(
    this.storage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light',
  );
  readonly theme = this._theme.asReadonly();

  setTheme(theme: WaiterTheme): void {
    this._theme.set(theme);
    this.storage.setItem(THEME_KEY, theme);
  }

  toggleTheme(): void {
    this.setTheme(this._theme() === 'dark' ? 'light' : 'dark');
  }
}
