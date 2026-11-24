import { inject, Injectable, signal } from '@angular/core';
import { SafeStorage } from '../../../core/util/storage';

export type AdminTheme = 'light' | 'dark';

const THEME_KEY = 'dinein.admin.theme.v1';

/** Per-device admin panel theme preference, persisted in localStorage (never throws). Light is the default. */
@Injectable({ providedIn: 'root' })
export class AdminPrefs {
  private readonly storage = inject(SafeStorage);

  private readonly _theme = signal<AdminTheme>(
    this.storage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light',
  );
  readonly theme = this._theme.asReadonly();

  setTheme(theme: AdminTheme): void {
    this._theme.set(theme);
    this.storage.setItem(THEME_KEY, theme);
  }

  toggleTheme(): void {
    this.setTheme(this._theme() === 'dark' ? 'light' : 'dark');
  }
}
