import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HQ_CONFIG, HQ_THEME_CATALOG, HqSharedLibModule } from 'hq-shared-lib';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { MyActivityEvent } from '../shared/models/my-activity.models';
import { AppDialogService } from '../shared/services/app-dialog-service';
import { BrandingService } from '../shared/services/branding-service';
import { MyActivityService } from '../shared/services/my-activity.service';
import { NotificationService } from '../shared/services/notification-service';
import { ProfilePhotoService } from '../shared/services/profile-photo-service';
import { ThemeService } from '../shared/services/theme-service';
import { AppearancePreferencesService } from '../shared/services/appearance-preferences.service';
import { UamService } from '../shared/services/uam-service';
import { Profile } from './profile';

describe('Profile', () => {
  const appearancePrefs = { apply: vi.fn(), applySaved: vi.fn() };
  const uam = {
    getMyProfile: vi.fn(), updateMyProfile: vi.fn(), getMyPreferences: vi.fn(), updateMyPreferences: vi.fn(),
    getMySessions: vi.fn(), revokeMySession: vi.fn(), revokeAllMySessions: vi.fn(),
    getMyDevices: vi.fn(), trustMyDevice: vi.fn(), blockMyDevice: vi.fn(), deleteMyDevice: vi.fn(),
    getMyMfa: vi.fn(), enrollMyTotp: vi.fn(), verifyMyMfaEnrollment: vi.fn(), deleteMyMfa: vi.fn(), disableMyMfa: vi.fn(),
    getMyLoginSecurity: vi.fn(),
  };
  const activityService = { list: vi.fn() };
  const dialogs = { confirm: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const photo = { refresh: vi.fn() };
  const theme = { currentTheme: HQ_THEME_CATALOG[0].id, setTheme: vi.fn() };

  const profile = {
    id: 'u1', userName: 'sita', email: 's@x.io', timezone: 'Asia/Kolkata', preferredLanguage: 'EN', gender: 'FEMALE',
    fullName: { firstName: 'Sita', middleName: 'K', lastName: 'Rao' }, phoneNumber: { countryCode: '+91', number: '9876543210' },
    roles: ['OPS', 'ADMIN'], audit: { createdAt: '2025-06-15T00:00:00Z' },
  };

  function create(): ComponentFixture<Profile> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [Profile],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: HQ_CONFIG, useValue: { gatewayUrl: 'http://gw', adminApiUrl: 'http://admin', currentUserId: () => 'u1' } },
        { provide: UamService, useValue: uam },
        { provide: MyActivityService, useValue: activityService },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
        { provide: ThemeService, useValue: theme },
        { provide: AppearancePreferencesService, useValue: appearancePrefs },
        { provide: ProfilePhotoService, useValue: photo },
        { provide: BrandingService, useValue: { branding: { appName: 'TestApp' } } },
      ],
    });
    const fixture = TestBed.createComponent(Profile);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    dialogs.confirm.mockReturnValue(of(true));
    uam.getMyProfile.mockReturnValue(of(profile));
    uam.updateMyProfile.mockReturnValue(of(profile));
    uam.getMyPreferences.mockReturnValue(of({
      ui: { theme: 'DARK', compactMode: true }, notification: { smsEnabled: false, whatsappEnabled: true },
      accessibility: { highContrast: true }, regional: { language: 'HI', currency: 'INR' },
    }));
    uam.updateMyPreferences.mockReturnValue(of({ ui: { theme: 'LIGHT' } }));
    uam.getMySessions.mockReturnValue(of([{ sessionId: 's1', deviceType: 'MOBILE' }]));
    uam.getMyDevices.mockReturnValue(of([{ id: 'd1', platform: 'ANDROID', trusted: false, blocked: false }]));
    uam.getMyMfa.mockReturnValue(of([{ id: 'm1', mfaType: 'TOTP', enabled: true, primary: true }]));
    uam.getMyLoginSecurity.mockReturnValue(of({ failedLoginAttempts: 2, lastLoginIp: '10.0.0.1', accountLocked: false }));
    activityService.list.mockReturnValue(of({ content: [] }));
    for (const name of ['revokeMySession', 'revokeAllMySessions', 'trustMyDevice', 'blockMyDevice', 'deleteMyDevice', 'deleteMyMfa', 'disableMyMfa', 'verifyMyMfaEnrollment']) {
      (uam as Record<string, ReturnType<typeof vi.fn>>)[name].mockReturnValue(of({}));
    }
  });

  describe('loading', () => {
    it('loads the profile and every section', () => {
      const component = create().componentInstance;

      expect(uam.getMyProfile).toHaveBeenCalled();
      expect(component.user?.id).toBe('u1');
      expect(component.form).toEqual({
        fullName: 'Sita K Rao', email: 's@x.io', phoneNumber: expect.stringContaining('9876543210'), timezone: 'Asia/Kolkata',
        preferredLanguage: 'EN', gender: 'FEMALE',
      });
      expect(component.sessions.length).toBe(1);
      expect(component.devices.length).toBe(1);
      expect(component.mfaMethods.length).toBe(1);
      expect(component.loginSecurity?.failedLoginAttempts).toBe(2);
      expect(activityService.list).toHaveBeenCalledWith(0, expect.any(Number));
      expect(component.isLoadingUser).toBe(false);
    });

    it('reports a profile that cannot be loaded', () => {
      uam.getMyProfile.mockReturnValue(throwError(() => new Error('x')));
      const component = create().componentInstance;

      expect(notify.error).toHaveBeenCalledWith('profile.messages.loadFailed');
      expect(component.isLoadingUser).toBe(false);
    });

    it('keeps the screen usable when the other sections fail to load', () => {
      for (const name of ['getMyPreferences', 'getMySessions', 'getMyDevices', 'getMyMfa', 'getMyLoginSecurity']) {
        (uam as Record<string, ReturnType<typeof vi.fn>>)[name].mockReturnValue(throwError(() => new Error('x')));
      }
      activityService.list.mockReturnValue(throwError(() => new Error('x')));
      const component = create().componentInstance;

      expect(component.sessions).toEqual([]);
      expect(component.loginStatusItems).toEqual([]);
      expect(component.activity).toEqual([]);
    });

    it('tolerates a profile with few fields and an activity page with no content', () => {
      uam.getMyProfile.mockReturnValue(of({ id: 'u2', userName: 'bare' }));
      activityService.list.mockReturnValue(of({}));
      const component = create().componentInstance;

      expect(component.form.fullName).toBe('');
      expect(component.userDisplayName).toBe('bare');
      expect(component.userInitial).toBe('B');
      expect(component.activity).toEqual([]);
      expect(component.memberSince).toBe('—');
      expect(component.profileDetails.every((d) => typeof d.value === 'string')).toBe(true);
    });
  });

  describe('derived display state', () => {
    it('names the user and gives the initial', () => {
      const component = create().componentInstance;

      expect(component.userDisplayName).toBe('Sita Rao');
      expect(component.userInitial).toBe('S');
    });

    it('has no name before the profile arrives', () => {
      uam.getMyProfile.mockReturnValue(throwError(() => new Error('x')));
      const component = create().componentInstance;

      expect(component.userDisplayName).toBe('');
      expect(component.userInitial).toBe('U');
      expect(component.profileDetails).toEqual([]);
    });

    it('summarizes sessions, devices and two-factor in the hero metrics', () => {
      const component = create().componentInstance;

      expect(component.mfaEnabled).toBe(true);
      expect(component.heroMetrics.map((m) => m.value)).toEqual(['1', '1', 'profile.security.enabled']);

      component.mfaMethods = [];
      expect(component.heroMetrics[2].value).toBe('profile.security.disabled');
    });

    it('lists the profile details and the member-since month', () => {
      const component = create().componentInstance;

      expect(component.profileDetails.map((d) => d.value)).toEqual(['Sita Rao', 's@x.io', '9876543210', 'Asia/Kolkata', 'EN', 'FEMALE']);
      expect(component.memberSince).toContain('2025');
    });

    it('describes login security and account facts', () => {
      const component = create().componentInstance;

      expect(component.loginStatusItems.map((i) => i.value)).toEqual(['2', '—', '—', '10.0.0.1']);
      expect(component.mfaMethodLabel).toBe('TOTP');
      expect(component.accountStatItems.map((i) => i.value)).toEqual(['u1', 'OPS, ADMIN', 'TOTP', 'shared.commonTable.no']);

      component.mfaMethods = [];
      component.loginSecurity = { accountLocked: true, lastSuccessfulLoginAt: '2026-01-01T00:00:00Z' } as never;
      expect(component.mfaMethodLabel).toBe('profile.security.loginDetails.notConfigured');
      expect(component.accountStatItems[3].value).toBe('shared.commonTable.yes');
      expect(component.loginStatusItems[1].value).not.toBe('—');
    });

    it('falls back to any enabled method when none is primary', () => {
      const component = create().componentInstance;
      component.mfaMethods = [{ id: 'a', mfaType: 'SMS', enabled: true, primary: false }, { id: 'b', mfaType: 'EMAIL', enabled: false }] as never;

      expect(component.mfaMethodLabel).toBe('SMS');
    });
  });

  describe('activity', () => {
    const event = (extra: Partial<MyActivityEvent>): MyActivityEvent => ({ eventType: 'LOGIN', outcome: 'SUCCESS', occurredAt: '2026-03-01T10:00:00Z', ...extra } as MyActivityEvent);

    it('titles events, hides background reads and colors by outcome', () => {
      const component = create().componentInstance;
      component.activity = [
        event({ eventType: 'SCREEN_VIEW', screenKey: 'payments.refunds' }),
        event({ eventType: 'ACCESS', method: 'POST', path: '/x', outcome: 'DENIED', statusCode: 403, ipAddress: '1.2.3.4' }),
        event({ eventType: 'ACCESS', method: 'GET', path: '/hidden' }),
        event({ eventType: 'UNKNOWN_KIND', outcome: 'FAILURE', occurredAt: '' }),
      ];

      const items = component.activityEvents;

      expect(items.length).toBe(3);
      expect(items[0].title).toBe('profile.activity.screenView');
      expect(items[1].title).toBe('profile.activity.apiCall');
      expect(items[1].desc).toBe('profile.activity.denied · 403 · profile.activity.fromIp');
      expect(items[1].color).toBe('icon-amber');
      expect(items[2].title).toBe('profile.activity.defaultLabel');
      expect(items[2].desc).toBe('profile.activity.failed');
      expect(items[2].color).toBe('icon-red');
      expect(items[2].time).toBe('');
      expect(items[0].color).toBe('icon-green');
    });
  });

  describe('profile editing', () => {
    it('opens a draft copy and cancels cleanly when nothing changed', () => {
      const component = create().componentInstance;

      component.toggleEdit();
      expect(component.isEditing).toBe(true);
      expect(component.hasProfileChanges).toBe(false);
      component.toggleEdit();
      expect(component.isEditing).toBe(false);
      expect(dialogs.confirm).not.toHaveBeenCalled();
    });

    it('asks before throwing away a changed draft', () => {
      const component = create().componentInstance;
      component.toggleEdit();
      component.editDraft.fullName = 'Changed';
      expect(component.hasChanges).toBe(true);
      expect(component.hasPendingChanges()).toBe(true);

      dialogs.confirm.mockReturnValueOnce(of(false));
      component.cancelEdit();
      expect(component.isEditing).toBe(true);

      component.cancelEdit();
      expect(component.isEditing).toBe(false);
      expect(dialogs.confirm).toHaveBeenLastCalledWith(expect.objectContaining({ variant: 'warning', title: 'profile.discardDialog.title' }));
    });

    it('warns the browser before unload only with unsaved changes', () => {
      const component = create().componentInstance;
      const event = { preventDefault: vi.fn(), returnValue: 'x' } as unknown as BeforeUnloadEvent;

      component.beforeUnload(event);
      expect(event.preventDefault).not.toHaveBeenCalled();

      component.toggleEdit();
      component.editDraft.email = 'new@x.io';
      component.beforeUnload(event);
      expect(event.preventDefault).toHaveBeenCalled();
    });

    it('saves the draft, leaves edit mode and shows the new values', () => {
      uam.updateMyProfile.mockReturnValue(of({ ...profile, timezone: 'Asia/Dubai' }));
      const component = create().componentInstance;
      component.toggleEdit();
      Object.assign(component.editDraft, { fullName: 'New Name', timezone: 'Asia/Dubai', gender: '' });

      component.saveProfile();

      expect(uam.updateMyProfile).toHaveBeenCalledWith(expect.objectContaining({ fullName: 'New Name', timezone: 'Asia/Dubai', gender: null }));
      expect(component.isEditing).toBe(false);
      expect(component.form.fullName).toBe('New Name');
      expect(notify.success).toHaveBeenCalledWith('profile.messages.updated');
      expect(component.savingProfile).toBe(false);
    });

    it('sends empty fields as null', () => {
      const component = create().componentInstance;
      component.toggleEdit();
      Object.assign(component.editDraft, { fullName: '', email: '', phoneNumber: '', timezone: '', preferredLanguage: '' });

      component.saveProfile();

      expect(uam.updateMyProfile).toHaveBeenCalledWith({ fullName: null, email: null, phoneNumber: null, timezone: null, preferredLanguage: null, gender: 'FEMALE' });
    });

    it('refuses an invalid timezone', () => {
      const component = create().componentInstance;
      component.toggleEdit();
      component.editDraft.timezone = 'Mars/Phobos';

      component.saveProfile();

      expect(uam.updateMyProfile).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('timezone.picker.invalid');
    });

    it('reports a failed save with the server detail or a default', () => {
      const component = create().componentInstance;
      component.toggleEdit();

      uam.updateMyProfile.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Email taken' } })));
      component.saveProfile();
      expect(notify.error).toHaveBeenLastCalledWith('Email taken');

      uam.updateMyProfile.mockReturnValueOnce(throwError(() => ({})));
      component.saveProfile();
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.updateFailed');
      expect(component.isEditing).toBe(true);
    });

    it('renders the edit form after clicking Edit', () => {
      const fixture = create();

      (fixture.nativeElement.querySelector('.pf-edit-btn') as HTMLElement).click();
      fixture.detectChanges();

      expect(fixture.componentInstance.isEditing).toBe(true);
      expect(fixture.nativeElement.querySelector('.pf-btn-save')).not.toBeNull();
    });
  });

  describe('preferences', () => {
    it('applies the loaded values with defaults for the missing ones', () => {
      const f = create().componentInstance.preferencesForm;

      // An old DARK-only preference selects the dark theme; no text size saved means normal.
      expect(f.ui).toEqual({ theme: 'DARK', themeId: 'dark', fontScale: 'normal', compactMode: true, animationsEnabled: true, showSidebar: true });
      expect(f.notification).toEqual(expect.objectContaining({ smsEnabled: false, whatsappEnabled: true, emailEnabled: true, doNotDisturb: false }));
      expect(f.accessibility.highContrast).toBe(true);
      expect(f.regional).toEqual({ language: 'HI', timezone: '', currency: 'INR', dateFormat: '' });
    });

    it('keeps a saved theme id and text size, and ignores ones the app does not know', () => {
      uam.getMyPreferences.mockReturnValue(of({ ui: { themeId: 'ocean', fontScale: 'xlarge' } }));
      expect(create().componentInstance.preferencesForm.ui).toMatchObject({ themeId: 'ocean', fontScale: 'xlarge' });

      uam.getMyPreferences.mockReturnValue(of({ ui: { themeId: 'neon', fontScale: 'giant' } }));
      expect(create().componentInstance.preferencesForm.ui).toMatchObject({ themeId: '', fontScale: 'normal' });
    });

    it('shows a chosen theme, text size or comfort option on the page at once, before it is saved', () => {
      const component = create().componentInstance;
      appearancePrefs.apply.mockClear();

      component.preferencesForm.ui.themeId = 'forest';
      component.preferencesForm.ui.fontScale = 'large';
      component.preferencesForm.accessibility.highContrast = true;
      component.previewAppearance();

      expect(appearancePrefs.apply).toHaveBeenCalledWith(expect.objectContaining({ themeId: 'forest', fontScale: 'large', highContrast: true }));
    });

    it('saves the theme id and text size, and the matching old LIGHT / DARK value next to them', () => {
      const component = create().componentInstance;
      component.preferencesForm.ui.themeId = 'obsidian';
      component.preferencesForm.ui.fontScale = 'small';

      component.savePreferences();

      expect(uam.updateMyPreferences).toHaveBeenCalledWith(expect.objectContaining({
        ui: expect.objectContaining({ themeId: 'obsidian', fontScale: 'small', theme: 'DARK' }),
      }));
    });

    it('tracks unsaved preference changes', () => {
      const component = create().componentInstance;
      expect(component.hasPreferenceChanges).toBe(false);

      component.preferencesForm.ui.compactMode = false;

      expect(component.hasPreferenceChanges).toBe(true);
      expect(component.hasChanges).toBe(true);
    });

    it('saves the form and takes the saved values back', () => {
      const component = create().componentInstance;
      component.preferencesForm.regional.language = '';

      component.savePreferences();

      expect(uam.updateMyPreferences).toHaveBeenCalledWith(expect.objectContaining({
        ui: expect.objectContaining({ theme: 'DARK' }),
        regional: { language: null, timezone: null, currency: 'INR', dateFormat: null },
      }));
      expect(component.preferencesForm.ui.theme).toBe('LIGHT');
      expect(component.hasPreferenceChanges).toBe(false);
      expect(notify.success).toHaveBeenCalledWith('profile.messages.preferencesSaved');
    });

    it('reports a failed save', () => {
      const component = create().componentInstance;

      uam.updateMyPreferences.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Bad' } })));
      component.savePreferences();
      expect(notify.error).toHaveBeenLastCalledWith('Bad');

      uam.updateMyPreferences.mockReturnValueOnce(throwError(() => ({})));
      component.savePreferences();
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.preferencesSaveFailed');
      expect(component.savingPreferences).toBe(false);
    });
  });

  describe('sessions', () => {
    it('shows a map link for a session with a stored position, and none otherwise', () => {
      const component = create().componentInstance;

      expect(component.sessionPosition({ metadata: { location: { latitude: 12.97123, longitude: 77.5946 } } } as never))
        .toEqual({ label: '12.9712, 77.5946', url: 'https://www.google.com/maps?q=12.97123,77.5946' });
      expect(component.sessionPosition({ metadata: { location: { latitude: 'x' } } } as never)).toBeNull();
      expect(component.sessionPosition({} as never)).toBeNull();
    });

    it('picks an icon by device type', () => {
      const component = create().componentInstance;

      expect(component.sessionIcon({ deviceType: 'mobile' } as never)).toBe('bi-phone');
      expect(component.sessionIcon({ deviceType: 'TABLET' } as never)).toBe('bi-tablet');
      expect(component.sessionIcon({} as never)).toBe('bi-laptop');
      expect(component.deviceIcon({ platform: 'ios' } as never)).toBe('bi-phone');
      expect(component.deviceIcon({ platform: 'WINDOWS' } as never)).toBe('bi-laptop');
    });

    it('revokes one session, reloads, and reports failure', () => {
      const component = create().componentInstance;
      uam.getMySessions.mockClear();

      component.revokeSession({ sessionId: 's1' } as never);
      expect(uam.revokeMySession).toHaveBeenCalledWith('s1');
      expect(uam.getMySessions).toHaveBeenCalledTimes(1);
      expect(component.revokingSessionId).toBeNull();

      uam.revokeMySession.mockReturnValue(throwError(() => new Error('x')));
      component.revokeSession({ sessionId: 's1' } as never);
      expect(notify.error).toHaveBeenCalledWith('profile.messages.sessionRevokeFailed');
      expect(component.revokingSessionId).toBeNull();
    });

    it('signs out everywhere only after confirmation', () => {
      const component = create().componentInstance;

      component.revokeAllSessions();
      expect(uam.revokeAllMySessions).toHaveBeenCalled();
      expect(notify.success).toHaveBeenCalledWith('profile.messages.signedOutEverywhere');

      uam.revokeAllMySessions.mockReturnValue(throwError(() => new Error('x')));
      component.revokeAllSessions();
      expect(notify.error).toHaveBeenCalledWith('profile.messages.revokeAllFailed');

      uam.revokeAllMySessions.mockClear();
      dialogs.confirm.mockReturnValue(of(false));
      component.revokeAllSessions();
      expect(uam.revokeAllMySessions).not.toHaveBeenCalled();
    });
  });

  describe('devices', () => {
    it('flips trust and block, removes, and reports failures', () => {
      const component = create().componentInstance;
      const device = { id: 'd1', trusted: false, blocked: true } as never;

      component.toggleTrust(device);
      expect(uam.trustMyDevice).toHaveBeenCalledWith('d1', true);
      component.toggleBlock(device);
      expect(uam.blockMyDevice).toHaveBeenCalledWith('d1', false);
      component.removeDevice(device);
      expect(uam.deleteMyDevice).toHaveBeenCalledWith('d1');
      expect(notify.success).toHaveBeenLastCalledWith('profile.messages.deviceRemoved');

      uam.trustMyDevice.mockReturnValue(throwError(() => new Error('x')));
      uam.blockMyDevice.mockReturnValue(throwError(() => new Error('x')));
      uam.deleteMyDevice.mockReturnValue(throwError(() => new Error('x')));
      component.toggleTrust(device);
      component.toggleBlock(device);
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.deviceUpdateFailed');
      component.removeDevice(device);
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.deviceRemoveFailed');
    });
  });

  describe('two-factor enrollment', () => {
    it('starts an enrollment and shows the secret', () => {
      uam.enrollMyTotp.mockReturnValue(of({ id: 'e1', secret: 'S' }));
      const component = create().componentInstance;
      component.mfaVerifyCode = 'old';

      component.startEnrollTotp();

      expect(component.mfaEnrollment).toEqual({ id: 'e1', secret: 'S' });
      expect(component.mfaVerifyCode).toBe('');
      expect(component.enrollingMfa).toBe(false);
    });

    it('reports a failed start', () => {
      const component = create().componentInstance;

      uam.enrollMyTotp.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Already enrolled' } })));
      component.startEnrollTotp();
      expect(notify.error).toHaveBeenLastCalledWith('Already enrolled');

      uam.enrollMyTotp.mockReturnValueOnce(throwError(() => ({})));
      component.startEnrollTotp();
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.mfaStartFailed');
    });

    it('verifies the code and switches two-factor on', () => {
      const component = create().componentInstance;
      component.mfaEnrollment = { id: 'e1' } as never;
      component.mfaVerifyCode = '123456';

      component.confirmEnrollTotp();

      expect(uam.verifyMyMfaEnrollment).toHaveBeenCalledWith('e1', '123456');
      expect(component.mfaEnrollment).toBeNull();
      expect(notify.success).toHaveBeenCalledWith('profile.messages.mfaEnabled');
    });

    it('does nothing without an enrollment or code, and reports a wrong code', () => {
      const component = create().componentInstance;

      component.confirmEnrollTotp();
      component.mfaEnrollment = { id: 'e1' } as never;
      component.confirmEnrollTotp();
      expect(uam.verifyMyMfaEnrollment).not.toHaveBeenCalled();

      component.mfaVerifyCode = '000000';
      uam.verifyMyMfaEnrollment.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Wrong code' } })));
      component.confirmEnrollTotp();
      expect(notify.error).toHaveBeenLastCalledWith('Wrong code');
      expect(component.mfaEnrollment).not.toBeNull();

      uam.verifyMyMfaEnrollment.mockReturnValueOnce(throwError(() => ({})));
      component.confirmEnrollTotp();
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.mfaInvalidCode');
      expect(component.verifyingMfa).toBe(false);
    });

    it('cancelling removes the half-made enrollment', () => {
      const component = create().componentInstance;
      component.mfaEnrollment = { id: 'e1' } as never;

      component.cancelEnrollTotp();

      expect(uam.deleteMyMfa).toHaveBeenCalledWith('e1');
      expect(component.mfaEnrollment).toBeNull();
    });

    it('cancelling with nothing started calls nothing', () => {
      create().componentInstance.cancelEnrollTotp();

      expect(uam.deleteMyMfa).not.toHaveBeenCalled();
    });

    it('disables and removes a method, reporting failures', () => {
      const component = create().componentInstance;

      component.disableMfa({ id: 'm1' } as never);
      expect(notify.success).toHaveBeenLastCalledWith('profile.messages.mfaDisabled');
      component.removeMfa({ id: 'm1' } as never);
      expect(notify.success).toHaveBeenLastCalledWith('profile.messages.mfaRemoved');

      uam.disableMyMfa.mockReturnValue(throwError(() => new Error('x')));
      uam.deleteMyMfa.mockReturnValue(throwError(() => new Error('x')));
      component.disableMfa({ id: 'm1' } as never);
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.mfaDisableFailed');
      component.removeMfa({ id: 'm1' } as never);
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.mfaRemoveFailed');
    });
  });

  describe('theme and photo', () => {
    it('marks the current theme active and switches theme', () => {
      const component = create().componentInstance;

      expect(component.themeOptions.filter((t) => t.active).map((t) => t.id)).toEqual([theme.currentTheme]);
      component.selectTheme(HQ_THEME_CATALOG[1].id);
      expect(theme.setTheme).toHaveBeenCalledWith(HQ_THEME_CATALOG[1].id);
    });

    it('refreshes the photo everywhere after an upload or removal and reports a rejected file', () => {
      const component = create().componentInstance;

      component.onPhotoUploaded();
      expect(notify.success).toHaveBeenCalledWith('profile.photo.uploaded');
      expect(photo.refresh).toHaveBeenCalledWith('u1');
      photo.refresh.mockClear();
      component.onPhotoRemoved();
      expect(photo.refresh).toHaveBeenCalledWith('u1');
      component.onPhotoRejected('Too large');
      expect(notify.error).toHaveBeenCalledWith('Too large');
    });
  });

  it('the sign-out-everywhere and refresh buttons work from the rendered page', () => {
    const fixture = create();
    const el: HTMLElement = fixture.nativeElement;
    uam.getMySessions.mockClear();

    (el.querySelector('.pf-btn-outline') as HTMLElement | null)?.click();
    (el.querySelector('.pf-btn-danger-sm') as HTMLElement).click();

    expect(uam.revokeMySession).toHaveBeenCalledWith('s1');
    expect(uam.getMySessions.mock.calls.length).toBeGreaterThan(0);
  });
});
