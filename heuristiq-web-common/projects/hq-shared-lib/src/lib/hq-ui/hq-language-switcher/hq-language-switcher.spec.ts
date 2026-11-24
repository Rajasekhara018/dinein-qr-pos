import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { HQ_LANGUAGE_PROVIDER, HqLanguage, HqLanguageProvider } from './hq-language-provider';
import { HqLanguageSwitcher } from './hq-language-switcher';
import { HQ_LANGUAGE_SWITCHER_TEXTS, HqLanguageSwitcherTexts } from './hq-language-switcher.texts';

const LANGUAGES: HqLanguage[] = [
  { code: 'en', nativeName: 'English', dir: 'ltr' },
  { code: 'hi', nativeName: 'हिन्दी', dir: 'ltr' },
  { code: 'ar', nativeName: 'العربية', dir: 'rtl' },
];

class FakeLanguageService implements HqLanguageProvider {
  readonly languages = LANGUAGES;
  currentLanguage = 'en';
  get current(): HqLanguage {
    return LANGUAGES.find((l) => l.code === this.currentLanguage) ?? LANGUAGES[0];
  }
  setLanguage = vi.fn((code: string) => {
    this.currentLanguage = code;
  });
}

interface Setup {
  fixture: ComponentFixture<HqLanguageSwitcher>;
  el: HTMLElement;
  provider: FakeLanguageService;
}

function setup(texts?: HqLanguageSwitcherTexts): Setup {
  const provider = new FakeLanguageService();
  TestBed.configureTestingModule({
    imports: [CommonModule],
    declarations: [HqLanguageSwitcher],
    providers: [
      { provide: HQ_LANGUAGE_PROVIDER, useValue: provider },
      ...(texts ? [{ provide: HQ_LANGUAGE_SWITCHER_TEXTS, useValue: texts }] : []),
    ],
  });
  const fixture = TestBed.createComponent(HqLanguageSwitcher);
  fixture.detectChanges();
  // TestBed attaches the host to the document, so document-level click handling is real.
  expect(document.body.contains(fixture.nativeElement)).toBe(true);
  return { fixture, el: fixture.nativeElement as HTMLElement, provider };
}

function trigger(el: HTMLElement): HTMLButtonElement {
  return el.querySelector('.lang-switcher-btn') as HTMLButtonElement;
}

function open(s: Setup): void {
  trigger(s.el).click();
  s.fixture.detectChanges();
}

describe('HqLanguageSwitcher', () => {
  it('shows the active language on a closed trigger with default English labels', () => {
    const { el } = setup();
    const btn = trigger(el);
    expect(btn.getAttribute('aria-expanded')).toBe('false');
    expect(btn.getAttribute('aria-label')).toBe('Change language');
    expect(btn.title).toBe('Change language');
    const label = el.querySelector('.lang-switcher-label') as HTMLElement;
    expect(label.textContent).toBe('English');
    expect(label.getAttribute('lang')).toBe('en');
    expect(el.querySelector('.lang-switcher-dropdown')).toBeNull();
  });

  it('opens the list with every language, marking the active one, and closes on a second click', () => {
    const s = setup();
    open(s);
    const dropdown = s.el.querySelector('.lang-switcher-dropdown') as HTMLElement;
    expect(dropdown.getAttribute('aria-label')).toBe('Language');
    expect(s.el.querySelector('.lang-switcher-title')?.textContent).toBe('Language');
    expect(trigger(s.el).getAttribute('aria-expanded')).toBe('true');
    expect(s.el.querySelector('.lang-switcher-chevron')?.classList.contains('rotated')).toBe(true);

    const items = Array.from(s.el.querySelectorAll('.lang-switcher-item')) as HTMLButtonElement[];
    expect(items.map((i) => i.textContent?.trim())).toEqual(['English', 'हिन्दी', 'العربية']);
    expect(items.map((i) => i.getAttribute('aria-selected'))).toEqual(['true', 'false', 'false']);
    expect(items[0].classList.contains('active')).toBe(true);
    expect(items[0].querySelector('.bi-check2')).not.toBeNull();
    expect(items[1].querySelector('.bi-check2')).toBeNull();
    expect(items[2].getAttribute('dir')).toBe('rtl');
    expect(items[2].getAttribute('lang')).toBe('ar');

    open(s);
    expect(s.el.querySelector('.lang-switcher-dropdown')).toBeNull();
  });

  it('switches the language through the provider and closes the menu', () => {
    const s = setup();
    open(s);
    (s.el.querySelectorAll('.lang-switcher-item')[2] as HTMLButtonElement).click();
    s.fixture.detectChanges();
    expect(s.provider.setLanguage).toHaveBeenCalledWith('ar');
    expect(s.fixture.componentInstance.menuOpen).toBe(false);
    expect(s.el.querySelector('.lang-switcher-dropdown')).toBeNull();
    expect(s.el.querySelector('.lang-switcher-label')?.textContent).toBe('العربية');
  });

  it('closes on a click outside, but stays open for a click inside', () => {
    const s = setup();
    open(s);
    (s.el.querySelector('.lang-switcher-title') as HTMLElement).click();
    s.fixture.detectChanges();
    expect(s.fixture.componentInstance.menuOpen).toBe(true);

    document.body.click();
    s.fixture.detectChanges();
    expect(s.fixture.componentInstance.menuOpen).toBe(false);
    expect(s.el.querySelector('.lang-switcher-dropdown')).toBeNull();
  });

  it('the trigger click does not reach the document listener (stopPropagation)', () => {
    const s = setup();
    const spy = vi.spyOn(s.fixture.componentInstance, 'onDocumentClick');
    open(s);
    expect(spy).not.toHaveBeenCalled();
    expect(s.fixture.componentInstance.menuOpen).toBe(true);
  });

  it('uses HQ_LANGUAGE_SWITCHER_TEXTS when provided', () => {
    const s = setup({ change: () => 'Bhasha badlein', label: () => 'Bhasha' });
    expect(trigger(s.el).getAttribute('aria-label')).toBe('Bhasha badlein');
    open(s);
    expect(s.el.querySelector('.lang-switcher-title')?.textContent).toBe('Bhasha');
  });
});
