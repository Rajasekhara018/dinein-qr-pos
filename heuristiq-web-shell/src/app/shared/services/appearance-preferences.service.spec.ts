import { TestBed } from '@angular/core/testing';
import { HqAppearanceService } from 'hq-shared-lib';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UserPreference } from '../models/uam.models';
import { AppearancePreferencesService } from './appearance-preferences.service';
import { ThemeService } from './theme-service';

describe('AppearancePreferencesService', () => {
  const theme = { currentTheme: 'light', setTheme: vi.fn() };
  const appearance = { apply: vi.fn() };
  let service: AppearancePreferencesService;

  beforeEach(() => {
    vi.clearAllMocks();
    theme.currentTheme = 'light';
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: ThemeService, useValue: theme }, { provide: HqAppearanceService, useValue: appearance }],
    });
    service = TestBed.inject(AppearancePreferencesService);
  });

  it('puts a saved preference on the page: its theme, text size and comfort options', () => {
    service.applySaved({
      userId: 'u1', ui: { themeId: 'ocean', fontScale: 'large', compactMode: true, animationsEnabled: true },
      accessibility: { highContrast: true, screenReader: false, reducedMotion: false },
    } as UserPreference);

    expect(theme.setTheme).toHaveBeenCalledWith('ocean');
    expect(appearance.apply).toHaveBeenCalledWith({
      fontScale: 'large', compact: true, animations: true, highContrast: true, reducedMotion: false, screenReader: false,
    });
  });

  it('does nothing for a user who has no saved preference', () => {
    service.applySaved(null);

    expect(theme.setTheme).not.toHaveBeenCalled();
    expect(appearance.apply).not.toHaveBeenCalled();
  });

  it('leaves the theme alone when none is chosen or it is already the current one', () => {
    service.apply({ themeId: '', fontScale: 'normal', compact: false, animations: true, highContrast: false, reducedMotion: false, screenReader: false });
    theme.currentTheme = 'forest';
    service.apply({ themeId: 'forest', fontScale: 'small', compact: false, animations: true, highContrast: false, reducedMotion: false, screenReader: false });

    expect(theme.setTheme).not.toHaveBeenCalled();
    expect(appearance.apply).toHaveBeenLastCalledWith(expect.objectContaining({ fontScale: 'small' }));
  });

  it('maps an old LIGHT / DARK-only preference to the app\'s own light or dark theme', () => {
    service.applySaved({ userId: 'u1', ui: { theme: 'DARK' } } as UserPreference);
    expect(theme.setTheme).toHaveBeenCalledWith('dark');
  });
});
