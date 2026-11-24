import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { HQ_APPEARANCE_DEFAULTS, HQ_APPEARANCE_STORAGE_KEY, HqAppearanceService } from './hq-appearance.service';

const KEY = 'test_appearance';
const html = () => document.documentElement;

function service(): HqAppearanceService {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [{ provide: HQ_APPEARANCE_STORAGE_KEY, useValue: KEY }] });
  return TestBed.inject(HqAppearanceService);
}

describe('HqAppearanceService', () => {
  beforeEach(() => {
    localStorage.clear();
    ['data-font-scale', 'data-compact', 'data-contrast', 'data-reduced-motion', 'data-screen-reader'].forEach((a) => html().removeAttribute(a));
  });

  it('starts from the stylesheet\'s own look when nothing is saved', () => {
    const appearance = service();

    expect(appearance.current).toEqual(HQ_APPEARANCE_DEFAULTS);
    expect(html().getAttribute('data-font-scale')).toBe('normal');
    expect(html().hasAttribute('data-compact')).toBe(false);
    expect(html().hasAttribute('data-contrast')).toBe(false);
  });

  it('sets one attribute per choice on <html> and removes it when the choice is turned off', () => {
    const appearance = service();

    appearance.apply({ fontScale: 'large', compact: true, highContrast: true, reducedMotion: true, screenReader: true });

    expect(html().getAttribute('data-font-scale')).toBe('large');
    expect(html().getAttribute('data-compact')).toBe('true');
    expect(html().getAttribute('data-contrast')).toBe('high');
    expect(html().getAttribute('data-reduced-motion')).toBe('true');
    expect(html().getAttribute('data-screen-reader')).toBe('true');

    appearance.apply({ compact: false, highContrast: false, reducedMotion: false, screenReader: false });

    expect(['data-compact', 'data-contrast', 'data-reduced-motion', 'data-screen-reader'].some((a) => html().hasAttribute(a))).toBe(false);
    expect(html().getAttribute('data-font-scale')).toBe('large');
  });

  it('treats turned-off animations like reduced motion', () => {
    const appearance = service();

    appearance.apply({ animations: false });
    expect(html().getAttribute('data-reduced-motion')).toBe('true');

    appearance.apply({ animations: true });
    expect(html().hasAttribute('data-reduced-motion')).toBe(false);
  });

  it('remembers the choices and applies them again the next time the page opens', () => {
    service().apply({ fontScale: 'xlarge', highContrast: true });

    const next = service();

    expect(next.current).toMatchObject({ fontScale: 'xlarge', highContrast: true, compact: false });
    expect(html().getAttribute('data-font-scale')).toBe('xlarge');
    expect(html().getAttribute('data-contrast')).toBe('high');
  });

  it('falls back to normal for an unknown text size, saved or given', () => {
    localStorage.setItem(KEY, JSON.stringify({ fontScale: 'giant', compact: true }));
    const appearance = service();
    expect(appearance.current.fontScale).toBe('normal');
    expect(appearance.current.compact).toBe(true);

    appearance.apply({ fontScale: 'tiny' as never });
    expect(appearance.current.fontScale).toBe('normal');
  });

  it('ignores unreadable saved text and keeps working when storage is unavailable', () => {
    localStorage.setItem(KEY, '{not json');
    expect(service().current).toEqual(HQ_APPEARANCE_DEFAULTS);

    const appearance = service();
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = () => { throw new Error('quota'); };
    try {
      appearance.apply({ compact: true });
    } finally {
      Storage.prototype.setItem = original;
    }
    expect(html().getAttribute('data-compact')).toBe('true');
  });

  it('forgets everything on reset', () => {
    const appearance = service();
    appearance.apply({ fontScale: 'large', compact: true });

    appearance.reset();

    expect(appearance.current).toEqual(HQ_APPEARANCE_DEFAULTS);
    expect(localStorage.getItem(KEY)).toBeNull();
    expect(html().hasAttribute('data-compact')).toBe(false);
    expect(html().getAttribute('data-font-scale')).toBe('normal');
  });
});
