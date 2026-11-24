import { inject, Injectable, signal } from '@angular/core';
import { SafeStorage } from '../../../core/util/storage';

export type KitchenTheme = 'dark' | 'light';
/** `null` = never asked (show the "Enable sound" prompt). */
export type SoundPreference = 'on' | 'off' | null;

export const KITCHEN_PREF_KEYS = {
  theme: 'dinein.kitchen.theme.v1',
  sound: 'dinein.kitchen.sound.v1',
} as const;

/** Per-device kitchen display preferences, persisted in localStorage (never throws). Dark is the default. */
@Injectable({ providedIn: 'root' })
export class KitchenPrefs {
  private readonly storage = inject(SafeStorage);

  private readonly _theme = signal<KitchenTheme>(
    this.storage.getItem(KITCHEN_PREF_KEYS.theme) === 'light' ? 'light' : 'dark',
  );
  readonly theme = this._theme.asReadonly();

  private readonly _sound = signal<SoundPreference>(readSound(this.storage));
  readonly sound = this._sound.asReadonly();

  setTheme(theme: KitchenTheme): void {
    this._theme.set(theme);
    this.storage.setItem(KITCHEN_PREF_KEYS.theme, theme);
  }

  toggleTheme(): void {
    this.setTheme(this._theme() === 'dark' ? 'light' : 'dark');
  }

  setSound(pref: 'on' | 'off'): void {
    this._sound.set(pref);
    this.storage.setItem(KITCHEN_PREF_KEYS.sound, pref);
  }
}

function readSound(storage: SafeStorage): SoundPreference {
  const value = storage.getItem(KITCHEN_PREF_KEYS.sound);
  return value === 'on' || value === 'off' ? value : null;
}
