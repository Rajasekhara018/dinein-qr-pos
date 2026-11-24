import { Inject, Injectable, InjectionToken, Optional } from '@angular/core';

/** The text sizes a user can choose; "normal" is the stylesheet's own size. */
export type HqFontScale = 'small' | 'normal' | 'large' | 'xlarge';

export const HQ_FONT_SCALES: readonly HqFontScale[] = ['small', 'normal', 'large', 'xlarge'];

/** The comfort choices of one user. Each one only sets an attribute on `<html>`; theme/styles.scss does the rest. */
export interface HqAppearance {
  fontScale: HqFontScale;
  /** Tighter spacing in tables, cards and buttons. */
  compact: boolean;
  /** Stronger borders, text and focus outlines. */
  highContrast: boolean;
  /** Turns off transitions and animations (the same as the operating system's reduced-motion setting). */
  reducedMotion: boolean;
  /** Off turns animations and transitions off too; kept apart from `reducedMotion` because users set them in different places. */
  animations: boolean;
  /** Always-visible, thick focus outlines, for people who work with a keyboard or a screen reader. */
  screenReader: boolean;
}

export const HQ_APPEARANCE_DEFAULTS: Readonly<HqAppearance> = {
  fontScale: 'normal', compact: false, highContrast: false, reducedMotion: false, animations: true, screenReader: false,
};

/** localStorage key the choices are remembered under, so the page is right before sign-in and before the server answers. */
export const HQ_APPEARANCE_STORAGE_KEY = new InjectionToken<string>('HQ_APPEARANCE_STORAGE_KEY');

/**
 * Applies the user's comfort choices (text size, compact spacing, high contrast, reduced motion, screen reader emphasis) to the
 * page by setting `data-*` attributes on `<html>`, which theme/styles.scss keys off. The choices are remembered in localStorage and
 * applied the moment the service is created; an app copies the user's saved server preference into it after sign-in.
 */
@Injectable({ providedIn: 'root' })
export class HqAppearanceService {
  private readonly storageKey: string;
  private state: HqAppearance = { ...HQ_APPEARANCE_DEFAULTS };

  constructor(@Optional() @Inject(HQ_APPEARANCE_STORAGE_KEY) storageKey: string | null) {
    this.storageKey = storageKey || 'rou_appearance';
    this.state = this.read();
    this.paint();
  }

  get current(): Readonly<HqAppearance> {
    return this.state;
  }

  /** Changes some choices, remembers them and applies them at once. Unknown text sizes fall back to normal. */
  apply(change: Partial<HqAppearance>): void {
    const next = { ...this.state, ...change };
    this.state = { ...next, fontScale: HQ_FONT_SCALES.includes(next.fontScale) ? next.fontScale : 'normal' };
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(this.state));
    } catch {
      /* storage unavailable: the choices still apply for this session */
    }
    this.paint();
  }

  /** Back to the stylesheet's own look, and forgets the saved choices (used when someone signs out of a shared computer). */
  reset(): void {
    this.state = { ...HQ_APPEARANCE_DEFAULTS };
    try {
      localStorage.removeItem(this.storageKey);
    } catch {
      /* nothing to forget */
    }
    this.paint();
  }

  private read(): HqAppearance {
    try {
      const saved = JSON.parse(localStorage.getItem(this.storageKey) ?? 'null') as Partial<HqAppearance> | null;
      if (saved && typeof saved === 'object') {
        const scale = HQ_FONT_SCALES.includes(saved.fontScale as HqFontScale) ? (saved.fontScale as HqFontScale) : 'normal';
        return {
          fontScale: scale, compact: saved.compact === true, highContrast: saved.highContrast === true,
          reducedMotion: saved.reducedMotion === true, animations: saved.animations !== false, screenReader: saved.screenReader === true,
        };
      }
    } catch {
      /* unreadable or unavailable storage: start from the defaults */
    }
    return { ...HQ_APPEARANCE_DEFAULTS };
  }

  private paint(): void {
    if (typeof document === 'undefined') {
      return;
    }
    const html = document.documentElement;
    html.setAttribute('data-font-scale', this.state.fontScale);
    this.flag(html, 'data-compact', this.state.compact);
    this.flag(html, 'data-contrast', this.state.highContrast, 'high');
    this.flag(html, 'data-reduced-motion', this.state.reducedMotion || !this.state.animations);
    this.flag(html, 'data-screen-reader', this.state.screenReader);
  }

  private flag(html: HTMLElement, name: string, on: boolean, value = 'true'): void {
    if (on) {
      html.setAttribute(name, value);
    } else {
      html.removeAttribute(name);
    }
  }
}
