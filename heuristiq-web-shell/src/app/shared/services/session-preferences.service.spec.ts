import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UserPreference } from '../models/uam.models';
import { AppearancePreferencesService } from './appearance-preferences.service';
import { AuthService } from './auth-service';
import { LanguageService } from './language-service';
import { SessionPreferencesService } from './session-preferences.service';
import { UamService } from './uam-service';

const saved = {
  userId: 'u1', applicationId: null, ui: { themeId: 'light', fontScale: 'large' }, notification: { email: true },
  accessibility: { highContrast: true }, regional: { language: 'EN', timezone: 'Asia/Kolkata' },
} as unknown as UserPreference;

describe('SessionPreferencesService', () => {
  const uam = { getMyPreferences: vi.fn(), getMyProfile: vi.fn(), updateMyPreferences: vi.fn() };
  const appearance = { applySaved: vi.fn() };
  const language = { useSaved: vi.fn() };
  const auth = { currentUser: { id: 'u1' } as unknown };
  let service: SessionPreferencesService;

  beforeEach(() => {
    vi.clearAllMocks();
    auth.currentUser = { id: 'u1' };
    uam.getMyPreferences.mockReturnValue(of(saved));
    uam.getMyProfile.mockReturnValue(of({ preferredLanguage: 'HI' }));
    uam.updateMyPreferences.mockReturnValue(of(saved));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: UamService, useValue: uam }, { provide: AppearancePreferencesService, useValue: appearance },
        { provide: LanguageService, useValue: language }, { provide: AuthService, useValue: auth },
      ],
    });
    service = TestBed.inject(SessionPreferencesService);
  });

  describe('after sign-in', () => {
    it('applies the saved look and the language chosen under Preferences > Regional', () => {
      service.applyAfterSignIn().subscribe();

      expect(appearance.applySaved).toHaveBeenCalledWith(saved);
      expect(language.useSaved).toHaveBeenCalledWith('en');
      expect(uam.getMyProfile).not.toHaveBeenCalled();
    });

    it('falls back to the preferred language of the profile when no regional language was saved', () => {
      uam.getMyPreferences.mockReturnValue(of({ ...saved, regional: { timezone: 'Asia/Kolkata' } }));

      service.applyAfterSignIn().subscribe();

      expect(language.useSaved).toHaveBeenCalledWith('hi');
    });

    it('leaves the browser language alone when neither the preference nor the profile has one the app can show', () => {
      uam.getMyPreferences.mockReturnValue(of({ ...saved, regional: null }));
      uam.getMyProfile.mockReturnValue(of({ preferredLanguage: 'OD' }));

      service.applyAfterSignIn().subscribe();

      expect(language.useSaved).toHaveBeenCalledWith(null);
    });

    it('reports a failed read to the caller so nothing half-applies', () => {
      uam.getMyPreferences.mockReturnValue(throwError(() => new Error('down')));
      const failed = vi.fn();

      service.applyAfterSignIn().subscribe({ error: failed });

      expect(failed).toHaveBeenCalled();
      expect(appearance.applySaved).not.toHaveBeenCalled();
    });
  });

  describe('saving a header choice', () => {
    it('saves the language and keeps every other saved setting', () => {
      service.saveLanguage('kn');

      expect(uam.updateMyPreferences).toHaveBeenCalledWith({
        applicationId: null, ui: saved.ui, notification: saved.notification, dashboard: null, accessibility: saved.accessibility,
        regional: { language: 'KA', timezone: 'Asia/Kolkata' },
      });
    });

    it('saves the theme and keeps every other saved setting', () => {
      service.saveTheme('ocean');

      expect(uam.updateMyPreferences).toHaveBeenCalledWith(expect.objectContaining({
        ui: { themeId: 'ocean', fontScale: 'large' }, regional: saved.regional, accessibility: saved.accessibility,
      }));
    });

    it('does not save a language the account cannot hold', () => {
      service.saveLanguage('xx');

      expect(uam.getMyPreferences).not.toHaveBeenCalled();
      expect(uam.updateMyPreferences).not.toHaveBeenCalled();
    });

    it('saves nothing when nobody is signed in', () => {
      auth.currentUser = null;

      service.saveLanguage('ta');
      service.saveTheme('ocean');

      expect(uam.updateMyPreferences).not.toHaveBeenCalled();
    });

    it('writes nothing when the saved preferences cannot be read, so other sections are never blanked', () => {
      uam.getMyPreferences.mockReturnValue(throwError(() => new Error('down')));

      expect(() => service.saveTheme('ocean')).not.toThrow();
      expect(uam.updateMyPreferences).not.toHaveBeenCalled();
    });

    it('swallows a failed save', () => {
      uam.updateMyPreferences.mockReturnValue(throwError(() => new Error('down')));

      expect(() => service.saveLanguage('ta')).not.toThrow();
    });
  });
});
