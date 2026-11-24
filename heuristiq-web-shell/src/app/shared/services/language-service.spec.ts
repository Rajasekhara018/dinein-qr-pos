import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { LANGUAGE_STORAGE_KEY, LanguageService } from './language-service';

describe('LanguageService.useBrowserLanguage', () => {
  let current: string;
  const translate = {
    use: vi.fn((lang: string) => { current = lang; return of({}); }),
    getCurrentLang: vi.fn(() => current),
  };
  let languages: string[];

  function setup(): LanguageService {
    const doc = document.implementation.createHTMLDocument('t');
    Object.defineProperty(doc, 'defaultView', {
      value: { navigator: { languages, language: languages[0] } },
    });
    TestBed.configureTestingModule({
      providers: [
        LanguageService,
        { provide: TranslateService, useValue: translate },
        { provide: DOCUMENT, useValue: doc },
      ],
    });
    return TestBed.inject(LanguageService);
  }

  beforeEach(() => {
    current = 'en';
    translate.use.mockClear();
    localStorage.removeItem(LANGUAGE_STORAGE_KEY);
  });

  afterEach(() => localStorage.removeItem(LANGUAGE_STORAGE_KEY));

  it('uses the first supported browser language without saving it', () => {
    languages = ['fr-FR', 'ta-IN', 'hi'];
    const service = setup();
    service.useBrowserLanguage();
    expect(translate.use).toHaveBeenCalledWith('ta');
    expect(TestBed.inject(DOCUMENT).documentElement.getAttribute('lang')).toBe('ta');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBeNull();
  });

  it('sets right-to-left for Arabic', () => {
    languages = ['ar-AE'];
    setup().useBrowserLanguage();
    expect(TestBed.inject(DOCUMENT).documentElement.getAttribute('dir')).toBe('rtl');
  });

  it('keeps a saved choice and does nothing when no browser language is supported or it is already active', () => {
    languages = ['ta'];
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'hi');
    setup().useBrowserLanguage();
    expect(translate.use).not.toHaveBeenCalled();
    TestBed.resetTestingModule();
    localStorage.removeItem(LANGUAGE_STORAGE_KEY);
    languages = ['fr', 'de'];
    setup().useBrowserLanguage();
    TestBed.resetTestingModule();
    languages = ['en-GB'];
    setup().useBrowserLanguage();
    expect(translate.use).not.toHaveBeenCalled();
  });
});
