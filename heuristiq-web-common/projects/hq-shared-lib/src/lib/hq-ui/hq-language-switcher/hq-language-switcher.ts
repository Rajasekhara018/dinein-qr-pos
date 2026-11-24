import { Component, ElementRef, HostListener, inject } from '@angular/core';
import { HQ_LANGUAGE_PROVIDER, HqLanguageProvider } from './hq-language-provider';
import { HQ_LANGUAGE_SWITCHER_DEFAULT_TEXTS, HQ_LANGUAGE_SWITCHER_TEXTS, HqLanguageSwitcherTexts } from './hq-language-switcher.texts';

/**
 * Self-contained language dropdown for screens outside an authenticated shell (login, register,
 * forgot-password): owns its own open/close state and outside-click handling. The languages, the
 * active one and the switch action come from the app through HQ_LANGUAGE_PROVIDER.
 */
@Component({
  selector: 'hq-language-switcher',
  standalone: false,
  templateUrl: './hq-language-switcher.html',
  styleUrl: './hq-language-switcher.scss',
})
export class HqLanguageSwitcher {
  readonly language: HqLanguageProvider = inject(HQ_LANGUAGE_PROVIDER);
  readonly texts: HqLanguageSwitcherTexts =
    inject(HQ_LANGUAGE_SWITCHER_TEXTS, { optional: true }) ?? HQ_LANGUAGE_SWITCHER_DEFAULT_TEXTS;
  private readonly elRef = inject<ElementRef<HTMLElement>>(ElementRef);

  menuOpen = false;

  toggleMenu(): void {
    this.menuOpen = !this.menuOpen;
  }

  selectLanguage(code: string): void {
    this.menuOpen = false;
    this.language.setLanguage(code);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (!this.elRef.nativeElement.contains(event.target as Node)) {
      this.menuOpen = false;
    }
  }
}
