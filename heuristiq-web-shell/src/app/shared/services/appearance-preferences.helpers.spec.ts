import { describe, expect, it } from 'vitest';
import { UserPreference } from '../models/uam.models';
import { choiceFromPreference, knownFontScale, knownThemeId, legacyThemeFor } from './appearance-preferences.helpers';

const preference = (over: Partial<UserPreference>): UserPreference => ({ userId: 'u1', ...over }) as UserPreference;

describe('knownThemeId and knownFontScale', () => {
  it('accept only the themes and text sizes the apps offer', () => {
    expect(knownThemeId('ocean')).toBe('ocean');
    expect(knownThemeId('pasupu-dark')).toBe('pasupu-dark');
    expect(knownThemeId('neon')).toBe('');
    expect(knownThemeId(null)).toBe('');
    expect(knownFontScale('large')).toBe('large');
    expect(knownFontScale('giant')).toBe('normal');
    expect(knownFontScale(undefined)).toBe('normal');
  });
});

describe('choiceFromPreference', () => {
  it('reads the theme, text size and comfort options of a saved preference', () => {
    const choice = choiceFromPreference(preference({
      ui: { themeId: 'forest', fontScale: 'xlarge', compactMode: true, animationsEnabled: false },
      accessibility: { highContrast: true, screenReader: true, reducedMotion: true },
    }), 'light', 'dark');

    expect(choice).toEqual({
      themeId: 'forest', fontScale: 'xlarge', compact: true, animations: false, highContrast: true, reducedMotion: true, screenReader: true,
    });
  });

  it('asks for nothing special when the user has saved no preference', () => {
    expect(choiceFromPreference(null, 'light', 'dark')).toEqual({
      themeId: '', fontScale: 'normal', compact: false, animations: true, highContrast: false, reducedMotion: false, screenReader: false,
    });
  });

  it('keeps an old LIGHT or DARK choice working when no theme was picked, and lets a picked theme win', () => {
    expect(choiceFromPreference(preference({ ui: { theme: 'DARK' } }), 'light', 'dark').themeId).toBe('dark');
    expect(choiceFromPreference(preference({ ui: { theme: 'LIGHT' } }), 'light', 'dark').themeId).toBe('light');
    expect(choiceFromPreference(preference({ ui: { theme: 'DARK', themeId: 'royal' } }), 'light', 'dark').themeId).toBe('royal');
    expect(choiceFromPreference(preference({ ui: { theme: 'DEFAULT' } }), 'light', 'dark').themeId).toBe('');
  });

  it('ignores a theme or text size this app does not know', () => {
    const choice = choiceFromPreference(preference({ ui: { themeId: 'neon', fontScale: 'giant' } }), 'light', 'dark');
    expect(choice.themeId).toBe('');
    expect(choice.fontScale).toBe('normal');
  });
});

describe('legacyThemeFor', () => {
  it('stores DARK for a dark theme, LIGHT for any other, and nothing when no theme is chosen', () => {
    expect(legacyThemeFor('obsidian')).toBe('DARK');
    expect(legacyThemeFor('pasupu-dark')).toBe('DARK');
    expect(legacyThemeFor('pasupu')).toBe('LIGHT');
    expect(legacyThemeFor('')).toBeNull();
  });
});
