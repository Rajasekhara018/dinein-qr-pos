import { Inject, Injectable, InjectionToken, Optional } from '@angular/core';

export type HqThemeId =
  | 'light' | 'dark' | 'ocean' | 'forest'
  | 'royal' | 'rose' | 'sunset' | 'arctic'
  | 'graphite' | 'obsidian' | 'indigo'
  | 'pasupu' | 'pasupu-dark';

export const HQ_THEME_IDS: readonly HqThemeId[] = [
  'light', 'dark', 'ocean', 'forest',
  'royal', 'rose', 'sunset', 'arctic',
  'graphite', 'obsidian', 'indigo',
  'pasupu', 'pasupu-dark',
];

/** Themes with a dark surface. They share the dark-mode rules in theme/styles.scss, keyed off
 *  <html data-theme-mode="dark">. */
export const HQ_DARK_THEME_IDS: readonly HqThemeId[] = ['dark', 'graphite', 'obsidian', 'indigo', 'pasupu-dark'];

/** localStorage key the selected theme is saved under -- provide per app to keep an app's
 *  existing saved choice (defaults to 'bestride_theme'). */
export const HQ_THEME_STORAGE_KEY = new InjectionToken<string>('HQ_THEME_STORAGE_KEY');

/** The pair of themes an app starts with and its header toggle flips between: one light, one dark. */
export interface HqDefaultThemes {
  light: HqThemeId;
  dark: HqThemeId;
}

/** Themes used when an app provides no HQ_DEFAULT_THEMES: the classic pair. */
export const HQ_CLASSIC_THEMES: HqDefaultThemes = { light: 'light', dark: 'dark' };

/** Optional per app: the light and dark theme a visitor with no saved choice gets (the OS dark-mode preference picks which),
 *  and the two the header toggle flips between. */
export const HQ_DEFAULT_THEMES = new InjectionToken<HqDefaultThemes>('HQ_DEFAULT_THEMES');

/**
 * Runtime theme switcher for hq-shared-lib's theme/styles.scss: sets
 * <html data-theme="light|dark|ocean|forest|royal|rose|sunset|arctic|graphite|obsidian|indigo|pasupu|pasupu-dark">, which the stylesheet's [data-theme] blocks key
 * off. Remembers the choice in localStorage; first visit follows the OS dark-mode preference, using the app's own default pair
 * (HQ_DEFAULT_THEMES) when it provides one.
 */
@Injectable({ providedIn: 'root' })
export class HqThemeService {
  private _theme: HqThemeId = 'light';
  private readonly storageKey: string;
  private readonly defaults: HqDefaultThemes;

  constructor(
    @Optional() @Inject(HQ_THEME_STORAGE_KEY) storageKey: string | null,
    @Optional() @Inject(HQ_DEFAULT_THEMES) defaults: HqDefaultThemes | null = null,
  ) {
    this.storageKey = storageKey || 'bestride_theme';
    this.defaults = defaults ?? HQ_CLASSIC_THEMES;
    const saved = this.read();
    if (saved && HQ_THEME_IDS.includes(saved)) {
      this._theme = saved;
    } else {
      const osDark = typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-color-scheme: dark)').matches;
      this._theme = osDark ? this.defaults.dark : this.defaults.light;
    }
    this.apply();
  }

  get currentTheme(): HqThemeId {
    return this._theme;
  }

  get isDark(): boolean {
    return HQ_DARK_THEME_IDS.includes(this._theme);
  }

  /** Header toggle: flips dark <-> light (any light variant counts as light), to the app's default pair. */
  toggle(): void {
    this.setTheme(this.isDark ? this.defaults.light : this.defaults.dark);
  }

  setTheme(id: HqThemeId): void {
    this._theme = id;
    try {
      localStorage.setItem(this.storageKey, id);
    } catch {
      /* storage unavailable -- theme still applies for this session */
    }
    this.apply();
  }

  private read(): HqThemeId | null {
    try {
      return localStorage.getItem(this.storageKey) as HqThemeId | null;
    } catch {
      return null;
    }
  }

  private apply(): void {
    if (typeof document !== 'undefined') {
      document.documentElement.setAttribute('data-theme', this._theme);
      document.documentElement.setAttribute('data-theme-mode', this.isDark ? 'dark' : 'light');
    }
  }
}
