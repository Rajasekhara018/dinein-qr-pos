import { TestBed } from '@angular/core/testing';
import { HQ_THEME_CATALOG } from './hq-theme-catalog';
import { HQ_DARK_THEME_IDS, HQ_DEFAULT_THEMES, HQ_THEME_IDS, HQ_THEME_STORAGE_KEY, HqThemeService } from './hq-theme.service';

const DEFAULT_KEY = 'bestride_theme';
const html = () => document.documentElement;

function createService(storageKey?: string): HqThemeService {
  TestBed.resetTestingModule();
  if (storageKey !== undefined) {
    TestBed.configureTestingModule({ providers: [{ provide: HQ_THEME_STORAGE_KEY, useValue: storageKey }] });
  }
  return TestBed.inject(HqThemeService);
}

function stubMatchMedia(dark: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn((query: string) => ({ matches: dark && query === '(prefers-color-scheme: dark)' })),
  });
}

describe('HqThemeService with an app default pair', () => {
  const originalMatchMedia = window.matchMedia;

  function createWithPair(): HqThemeService {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: HQ_DEFAULT_THEMES, useValue: { light: 'pasupu', dark: 'pasupu-dark' } }] });
    return TestBed.inject(HqThemeService);
  }

  beforeEach(() => {
    localStorage.clear();
    html().removeAttribute('data-theme');
  });

  afterEach(() => {
    Object.defineProperty(window, 'matchMedia', { configurable: true, writable: true, value: originalMatchMedia });
    localStorage.clear();
  });

  it('starts a first visit on the app light theme, or its dark one when the OS prefers dark', () => {
    stubMatchMedia(false);
    expect(createWithPair().currentTheme).toBe('pasupu');
    expect(html().getAttribute('data-theme')).toBe('pasupu');
    stubMatchMedia(true);
    const dark = createWithPair();
    expect(dark.currentTheme).toBe('pasupu-dark');
    expect(dark.isDark).toBe(true);
  });

  it('keeps a saved choice over the app default', () => {
    localStorage.setItem(DEFAULT_KEY, 'ocean');
    stubMatchMedia(false);
    expect(createWithPair().currentTheme).toBe('ocean');
  });

  it('toggles between the pair of the app', () => {
    stubMatchMedia(false);
    const service = createWithPair();
    service.toggle();
    expect(service.currentTheme).toBe('pasupu-dark');
    service.toggle();
    expect(service.currentTheme).toBe('pasupu');
  });
});

describe('HqThemeService', () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    localStorage.clear();
    html().removeAttribute('data-theme');
    html().removeAttribute('data-theme-mode');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    Object.defineProperty(window, 'matchMedia', { configurable: true, writable: true, value: originalMatchMedia });
    localStorage.clear();
  });

  describe('first visit', () => {
    it('follows an OS dark preference', () => {
      stubMatchMedia(true);
      const service = createService();
      expect(service.currentTheme).toBe('dark');
      expect(service.isDark).toBe(true);
      expect(html().getAttribute('data-theme')).toBe('dark');
      expect(html().getAttribute('data-theme-mode')).toBe('dark');
    });

    it('uses light when the OS prefers light', () => {
      stubMatchMedia(false);
      const service = createService();
      expect(service.currentTheme).toBe('light');
      expect(html().getAttribute('data-theme-mode')).toBe('light');
    });

    it('uses light when the browser has no matchMedia', () => {
      Object.defineProperty(window, 'matchMedia', { configurable: true, writable: true, value: undefined });
      expect(createService().currentTheme).toBe('light');
    });

    it('ignores a saved value that is not a known theme', () => {
      stubMatchMedia(true);
      localStorage.setItem(DEFAULT_KEY, 'neon');
      expect(createService().currentTheme).toBe('dark');
    });

    it('falls back to the OS preference when storage cannot be read', () => {
      stubMatchMedia(false);
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('blocked');
      });
      expect(createService().currentTheme).toBe('light');
    });
  });

  it('restores the saved theme and applies it to <html>', () => {
    stubMatchMedia(true);
    localStorage.setItem(DEFAULT_KEY, 'ocean');
    const service = createService();
    expect(service.currentTheme).toBe('ocean');
    expect(service.isDark).toBe(false);
    expect(html().getAttribute('data-theme')).toBe('ocean');
    expect(html().getAttribute('data-theme-mode')).toBe('light');
  });

  it('uses the app-provided storage key', () => {
    stubMatchMedia(false);
    localStorage.setItem('admin_theme', 'graphite');
    localStorage.setItem(DEFAULT_KEY, 'rose');
    const service = createService('admin_theme');
    expect(service.currentTheme).toBe('graphite');
    service.setTheme('forest');
    expect(localStorage.getItem('admin_theme')).toBe('forest');
    expect(localStorage.getItem(DEFAULT_KEY)).toBe('rose');
  });

  it('falls back to the default key when the provided key is empty', () => {
    stubMatchMedia(false);
    localStorage.setItem(DEFAULT_KEY, 'royal');
    expect(createService('').currentTheme).toBe('royal');
  });

  it('setTheme saves and applies the theme, dark variants set dark mode', () => {
    stubMatchMedia(false);
    const service = createService();
    service.setTheme('pasupu-dark');
    expect(service.currentTheme).toBe('pasupu-dark');
    expect(service.isDark).toBe(true);
    expect(localStorage.getItem(DEFAULT_KEY)).toBe('pasupu-dark');
    expect(html().getAttribute('data-theme')).toBe('pasupu-dark');
    expect(html().getAttribute('data-theme-mode')).toBe('dark');
  });

  it('setTheme still applies the theme when storage cannot be written', () => {
    stubMatchMedia(false);
    const service = createService();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota');
    });
    service.setTheme('sunset');
    expect(service.currentTheme).toBe('sunset');
    expect(html().getAttribute('data-theme')).toBe('sunset');
  });

  it('toggle flips any light variant to dark and any dark variant to light', () => {
    stubMatchMedia(false);
    const service = createService();
    service.setTheme('arctic');
    service.toggle();
    expect(service.currentTheme).toBe('dark');
    service.setTheme('obsidian');
    service.toggle();
    expect(service.currentTheme).toBe('light');
    expect(html().getAttribute('data-theme-mode')).toBe('light');
  });

  it('the picker catalog has one entry per theme, with the matching light/dark mode', () => {
    expect(HQ_THEME_CATALOG.map((t) => t.id)).toEqual([...HQ_THEME_IDS]);
    for (const entry of HQ_THEME_CATALOG) {
      expect(entry.mode, entry.id).toBe(HQ_DARK_THEME_IDS.includes(entry.id) ? 'Dark' : 'Light');
    }
  });
});
