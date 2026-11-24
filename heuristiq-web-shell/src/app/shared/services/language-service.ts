import { Injectable, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';

export interface SupportedLanguage {
  /** ISO 639-1 code; also the JSON file name under src/assets/i18n/. */
  code: string;
  /** Name in the language itself, as shown in the header switcher. */
  nativeName: string;
  dir: 'ltr' | 'rtl';
}

export const DEFAULT_LANGUAGE = 'en';
export const LANGUAGE_STORAGE_KEY = 'app_language';

export const SUPPORTED_LANGUAGES: readonly SupportedLanguage[] = [
  { code: 'en', nativeName: 'English', dir: 'ltr' },
  { code: 'hi', nativeName: 'हिन्दी', dir: 'ltr' },
  { code: 'ta', nativeName: 'தமிழ்', dir: 'ltr' },
  { code: 'te', nativeName: 'తెలుగు', dir: 'ltr' },
  { code: 'kn', nativeName: 'ಕನ್ನಡ', dir: 'ltr' },
  { code: 'ml', nativeName: 'മലയാളം', dir: 'ltr' },
  { code: 'ar', nativeName: 'العربية', dir: 'rtl' },
];

/**
 * Owns the active UI language: which languages exist, which one is active, persisting the
 * choice, and keeping <html lang/dir> in sync (Arabic is RTL).
 * `init()` runs once from provideAppInitializer in AppModule, so the stored language's JSON is
 * loaded before the first component renders (no flash of English).
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);

  readonly languages = SUPPORTED_LANGUAGES;

  get currentLanguage(): string {
    return this.translate.getCurrentLang() || DEFAULT_LANGUAGE;
  }

  get current(): SupportedLanguage {
    return this.find(this.currentLanguage) ?? SUPPORTED_LANGUAGES[0];
  }

  /** Called from the app initializer. Never rejects -- falls back to English on any failure. */
  async init(): Promise<void> {
    const lang = this.resolve(this.readStored());
    this.applyDocumentAttributes(lang);
    try {
      await firstValueFrom(this.translate.use(lang));
    } catch (err) {
      console.warn(`[LanguageService] Failed to load "${lang}" translations; falling back to English.`, err);
      if (lang !== DEFAULT_LANGUAGE) {
        this.applyDocumentAttributes(DEFAULT_LANGUAGE);
        try {
          await firstValueFrom(this.translate.use(DEFAULT_LANGUAGE));
        } catch {
          /* Keys render as-is; nothing more we can do. */
        }
      }
    }
  }

  setLanguage(code: string): void {
    const lang = this.resolve(code);
    this.translate.use(lang);
    this.applyDocumentAttributes(lang);
    try {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, lang);
    } catch {
      /* Storage unavailable (private mode etc.) -- choice just won't persist. */
    }
  }

  /**
   * Switches to the language the signed-in person saved on their account. Does nothing for a language the app has no text for
   * (or none), and when it is already showing. The switch is remembered in this browser like any other choice.
   */
  useSaved(code: string | null | undefined): void {
    if (!code || !this.find(code) || code === this.currentLanguage) return;
    this.setLanguage(code);
  }

  /**
   * For public pages a visitor opens from a shared link (no account, usually no saved choice): when nothing was chosen
   * in this browser, switches to the first browser language this app supports. A saved choice always wins, and nothing
   * is persisted, so the staff portal's own setting is never changed by it.
   */
  useBrowserLanguage(): void {
    if (this.find(this.readStored())) return;
    const preferred = this.browserLanguages()
      .map((tag) => tag.toLowerCase().split('-')[0])
      .find((code) => !!this.find(code));
    if (!preferred || preferred === this.currentLanguage) return;
    this.translate.use(preferred);
    this.applyDocumentAttributes(preferred);
  }

  private browserLanguages(): readonly string[] {
    const nav = this.document.defaultView?.navigator;
    if (!nav) return [];
    return nav.languages?.length ? nav.languages : nav.language ? [nav.language] : [];
  }

  private find(code: string | null | undefined): SupportedLanguage | undefined {
    return SUPPORTED_LANGUAGES.find(l => l.code === code);
  }

  private resolve(code: string | null | undefined): string {
    return this.find(code)?.code ?? DEFAULT_LANGUAGE;
  }

  private readStored(): string | null {
    try {
      return localStorage.getItem(LANGUAGE_STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private applyDocumentAttributes(code: string): void {
    const root = this.document.documentElement;
    root.setAttribute('lang', code);
    root.setAttribute('dir', this.find(code)?.dir ?? 'ltr');
  }
}
