import { Component, HostListener, OnInit, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UamService } from '../shared/services/uam-service';
import { MyActivityService } from '../shared/services/my-activity.service';
import { MyActivityEvent } from '../shared/models/my-activity.models';
import {
  ACTIVITY_DEFAULT_ICON,
  ACTIVITY_EVENT_ACCESS,
  ACTIVITY_EVENT_ICONS,
  ACTIVITY_EVENT_SCREEN_VIEW,
  ACTIVITY_HIDDEN_METHODS,
  ACTIVITY_EVENT_TITLE_KEYS,
  ACTIVITY_OUTCOME_DENIED,
  ACTIVITY_OUTCOME_FAILURE,
  MY_ACTIVITY_PAGE_SIZE,
} from '../shared/services/my-activity.constants';
import { AppDialogService } from '../shared/services/app-dialog-service';
import { NotificationService } from '../shared/services/notification-service';
import { ThemeService, ThemeId } from '../shared/services/theme-service';
import { HQ_FONT_SCALES, HQ_THEME_CATALOG as THEME_CATALOG, HqFontScale, HqTimezoneService } from 'hq-shared-lib';
import { AppearancePreferencesService } from '../shared/services/appearance-preferences.service';
import { knownFontScale, knownThemeId, legacyThemeFor } from '../shared/services/appearance-preferences.helpers';
import { PendingChangesAware } from '../shared/guards/pending-changes.guard';
import {
  UamUser, UserSession, UserDevice, UserMfa, UserLoginSecurity,
  UserPreference, LanguageEnum, Gender, MfaEnrollTotpResponse,
} from '../shared/models/uam.models';

import { BrandingService } from '../shared/services/branding-service';
import { ProfilePhotoService } from '../shared/services/profile-photo-service';
import { TranslateService } from '@ngx-translate/core';
import { phoneToControlValue } from '../shared/phone.helpers';
import { TIMEZONE_PICKER_EXAMPLE } from './timezone-picker/timezone-picker.constants';
import { apiErrorMessage } from '../shared/utils/api-error.helpers';
import { languageLabel } from '../shared/utils/regional-names.helpers';
import { PROFILE_DEFAULT_TAB, ProfileTab } from './profile.constants';
interface ProfileForm {
  fullName: string;
  email: string;
  phoneNumber: string;
  timezone: string;
  preferredLanguage: LanguageEnum | '';
  gender: Gender | '';
}

interface PreferencesForm {
  ui: { theme: 'LIGHT' | 'DARK' | 'DEFAULT' | ''; themeId: string; fontScale: HqFontScale; compactMode: boolean; animationsEnabled: boolean; showSidebar: boolean };
  notification: { emailEnabled: boolean; smsEnabled: boolean; pushEnabled: boolean; whatsappEnabled: boolean; inAppEnabled: boolean; doNotDisturb: boolean };
  accessibility: { highContrast: boolean; screenReader: boolean; reducedMotion: boolean };
  regional: { language: LanguageEnum | ''; timezone: string; currency: string; dateFormat: string };
}

interface HeroMetric { label: string; value: string; note: string; }
interface InfoItem { label: string; value: string; }
interface ActivityItem { title: string; desc: string; time: string; icon: string; color: string; }
interface ThemeOption {
  id: ThemeId; name: string; mode: string; active: boolean;
  sidebar: string; surface: string; bar: string; cardBg: string; cardBorder: string; accent: string;
}

@Component({
  selector: 'app-profile',
  standalone: false,
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile implements OnInit, PendingChangesAware {
  private readonly hqTimezone = inject(HqTimezoneService);
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  private readonly profilePhoto = inject(ProfilePhotoService);

  onPhotoUploaded(): void {
    this.notify.success(this.translate.instant('profile.photo.uploaded'));
    this.profilePhoto.refresh(this.user?.id); // header, dropdown and sidebar pick it up too
  }

  onPhotoRemoved(): void {
    this.profilePhoto.refresh(this.user?.id);
  }

  onPhotoRejected(message: string): void {
    this.notify.error(message);
  }

  user: UamUser | null = null;
  isLoadingUser = false;
  savingProfile = false;
  savingPreferences = false;
  isEditing = false;

  languages: LanguageEnum[] = ['EN', 'HI', 'TE', 'TA', 'ML', 'OD', 'KA', 'AR'];
  /** A language by its own and its English name ("తెలుగు (Telugu)"), not its code. */
  readonly languageLabel = languageLabel;
  /** The profile section the tab strip shows. */
  activeTab: ProfileTab = PROFILE_DEFAULT_TAB;
  genders: Gender[] = ['MALE', 'FEMALE'];

  form: ProfileForm = { fullName: '', email: '', phoneNumber: '', timezone: '', preferredLanguage: '', gender: '' };
  editDraft: ProfileForm = { ...this.form };

  preferencesForm: PreferencesForm = {
    ui: { theme: '', themeId: '', fontScale: 'normal', compactMode: false, animationsEnabled: true, showSidebar: true },
    notification: { emailEnabled: true, smsEnabled: true, pushEnabled: true, whatsappEnabled: false, inAppEnabled: true, doNotDisturb: false },
    accessibility: { highContrast: false, screenReader: false, reducedMotion: false },
    regional: { language: '', timezone: '', currency: '', dateFormat: '' },
  };

  /** Snapshot of `editDraft` taken when edit mode opens -- drives the profile dirty-check. */
  private profileSnapshot = '';
  /** Snapshot of `preferencesForm` as last loaded/saved -- drives the preferences dirty-check. */
  private preferencesSnapshot = JSON.stringify(this.preferencesForm);

  sessions: UserSession[] = [];
  devices: UserDevice[] = [];
  mfaMethods: UserMfa[] = [];
  loginSecurity: UserLoginSecurity | null = null;
  activity: MyActivityEvent[] = [];

  revokingSessionId: string | null = null;

  mfaEnrollment: MfaEnrollTotpResponse | null = null;
  mfaVerifyCode = '';
  enrollingMfa = false;
  verifyingMfa = false;

  private readonly translate = inject(TranslateService);
  private readonly myActivity = inject(MyActivityService);

  constructor(
    private uam: UamService,
    private dialogs: AppDialogService,
    private notify: NotificationService,
    public theme: ThemeService,
    private appearancePrefs: AppearancePreferencesService,
  ) {}

  ngOnInit(): void {
    this.loadProfile();
    this.loadPreferences();
    this.loadSessions();
    this.loadDevices();
    this.loadMfa();
    this.loadLoginSecurity();
    this.loadActivity();
  }

  // ── Derived display state ──────────────────────────────────────────────────
  get userDisplayName(): string {
    if (!this.user) return '';
    const name = [this.user.fullName?.firstName, this.user.fullName?.lastName].filter(Boolean).join(' ');
    return name || this.user.userName;
  }

  get userInitial(): string {
    const name = this.userDisplayName;
    return name ? name[0].toUpperCase() : 'U';
  }

  get mfaEnabled(): boolean {
    return this.mfaMethods.some((m) => m.enabled);
  }

  get memberSince(): string {
    const created = this.user?.audit?.createdAt;
    if (!created) return '—';
    return this.hqTimezone.formatLocale(created, 'date', { month: 'short', year: 'numeric' });
  }

  get heroMetrics(): HeroMetric[] {
    const t = (key: string) => this.translate.instant(key);
    return [
      { label: t('profile.metrics.activeSessions.label'), value: String(this.sessions.length), note: t('profile.metrics.activeSessions.note') },
      { label: t('profile.metrics.registeredDevices.label'), value: String(this.devices.length), note: t('profile.metrics.registeredDevices.note') },
      { label: t('profile.metrics.twoFaStatus.label'), value: t(this.mfaEnabled ? 'profile.security.enabled' : 'profile.security.disabled'), note: t(this.mfaEnabled ? 'profile.metrics.twoFaStatus.protectedNote' : 'profile.metrics.twoFaStatus.considerNote') },
    ];
  }

  get profileDetails(): InfoItem[] {
    if (!this.user) return [];
    const t = (key: string) => this.translate.instant(key);
    return [
      { label: t('profile.form.fullName.label'), value: this.userDisplayName || '—' },
      { label: t('profile.form.email.label'), value: this.user.email || '—' },
      { label: t('profile.form.phoneNumber.label'), value: this.user.phoneNumber?.number || '—' },
      { label: t('profile.form.timezone.label'), value: this.user.timezone || '—' },
      { label: t('profile.form.preferredLanguage.label'), value: this.user.preferredLanguage || '—' },
      { label: t('profile.form.gender.label'), value: this.user.gender || '—' },
    ];
  }

  get activityEvents(): ActivityItem[] {
    return this.activity.filter((event) => !this.isBackgroundCall(event)).map((event) => ({
      title: this.activityTitle(event),
      desc: this.activityDescription(event),
      time: event.occurredAt ? this.hqTimezone.formatLocale(event.occurredAt, 'datetime') : '',
      icon: ACTIVITY_EVENT_ICONS[event.eventType] ?? ACTIVITY_DEFAULT_ICON,
      color: this.activityColor(event.outcome),
    }));
  }

  /** A read-only API call: kept out of the list so sign-ins, screens and changes stay visible. */
  private isBackgroundCall(event: MyActivityEvent): boolean {
    return event.eventType === ACTIVITY_EVENT_ACCESS && ACTIVITY_HIDDEN_METHODS.has((event.method ?? '').toUpperCase());
  }

  private activityTitle(event: MyActivityEvent): string {
    if (event.eventType === ACTIVITY_EVENT_SCREEN_VIEW) {
      return this.translate.instant('profile.activity.screenView', { screen: event.screenKey ?? '' });
    }
    if (event.eventType === ACTIVITY_EVENT_ACCESS) {
      return this.translate.instant('profile.activity.apiCall', { method: event.method ?? '', path: event.path ?? '' });
    }
    const key = ACTIVITY_EVENT_TITLE_KEYS[event.eventType];
    return key ? this.translate.instant(key) : this.translate.instant('profile.activity.defaultLabel');
  }

  /** Second line: the outcome when it was not a success, then where it came from. */
  private activityDescription(event: MyActivityEvent): string {
    const parts: string[] = [];
    if (event.outcome === ACTIVITY_OUTCOME_DENIED) parts.push(this.translate.instant('profile.activity.denied'));
    if (event.outcome === ACTIVITY_OUTCOME_FAILURE) parts.push(this.translate.instant('profile.activity.failed'));
    if (event.statusCode) parts.push(String(event.statusCode));
    if (event.ipAddress) parts.push(this.translate.instant('profile.activity.fromIp', { ip: event.ipAddress }));
    return parts.join(' · ');
  }

  private activityColor(outcome: string | null | undefined): string {
    if (outcome === ACTIVITY_OUTCOME_FAILURE) return 'icon-red';
    if (outcome === ACTIVITY_OUTCOME_DENIED) return 'icon-amber';
    return 'icon-green';
  }

  // ── Unsaved changes ───────────────────────────────────────────────────────
  get hasProfileChanges(): boolean {
    return this.isEditing && JSON.stringify(this.editDraft) !== this.profileSnapshot;
  }

  get hasPreferenceChanges(): boolean {
    return JSON.stringify(this.preferencesForm) !== this.preferencesSnapshot;
  }

  get hasChanges(): boolean {
    return this.hasProfileChanges || this.hasPreferenceChanges;
  }

  @HostListener('window:beforeunload', ['$event'])
  beforeUnload(event: BeforeUnloadEvent): void {
    if (!this.hasChanges) return;
    event.preventDefault();
    event.returnValue = '';
  }

  hasPendingChanges(): boolean {
    return this.hasChanges;
  }

  confirmDiscardChanges(): Observable<boolean> {
    return this.dialogs.confirm({
      title: this.translate.instant('profile.discardDialog.title'),
      message: this.translate.instant('profile.discardDialog.message'),
      icon: 'bi-exclamation-triangle',
      variant: 'warning',
      confirmText: this.translate.instant('profile.discardDialog.confirm'),
      cancelText: this.translate.instant('profile.discardDialog.cancel'),
    });
  }

  // ── Profile edit ──────────────────────────────────────────────────────────
  toggleEdit(): void {
    if (this.isEditing) {
      this.cancelEdit();
      return;
    }
    this.editDraft = { ...this.form };
    this.profileSnapshot = JSON.stringify(this.editDraft);
    this.isEditing = true;
  }

  /** Leaves edit mode -- immediately when clean, after a discard prompt when the draft was changed. */
  cancelEdit(): void {
    if (!this.hasProfileChanges) {
      this.isEditing = false;
      return;
    }
    this.confirmDiscardChanges().subscribe((confirmed) => {
      if (confirmed) this.isEditing = false;
    });
  }

  private loadProfile(): void {
    this.isLoadingUser = true;
    this.uam.getMyProfile().subscribe({
      next: (u) => {
        this.user = u;
        this.form = {
          fullName: this.userNameOf(u),
          email: u.email ?? '',
          phoneNumber: phoneToControlValue(u.phoneNumber),
          timezone: u.timezone ?? '',
          preferredLanguage: u.preferredLanguage ?? '',
          gender: u.gender ?? '',
        };
        this.hqTimezone.setUserTimezone(u.timezone);
        this.isLoadingUser = false;
      },
      error: () => { this.isLoadingUser = false; this.notify.error(this.translate.instant('profile.messages.loadFailed')); },
    });
  }

  private userNameOf(u: UamUser): string {
    return [u.fullName?.firstName, u.fullName?.middleName, u.fullName?.lastName].filter(Boolean).join(' ');
  }

  saveProfile(): void {
    const d = this.editDraft;
    if (d.timezone && !this.hqTimezone.isValidTimezone(d.timezone)) {
      this.notify.error(this.translate.instant('timezone.picker.invalid', { example: TIMEZONE_PICKER_EXAMPLE }));
      return;
    }
    this.savingProfile = true;
    this.uam.updateMyProfile({
      fullName: d.fullName || null,
      email: d.email || null,
      phoneNumber: d.phoneNumber || null,
      timezone: d.timezone || null,
      preferredLanguage: d.preferredLanguage || null,
      gender: d.gender || null,
    }).subscribe({
      next: (u) => {
        this.user = u;
        this.form = { ...d };
        this.hqTimezone.setUserTimezone(u.timezone);
        this.savingProfile = false;
        this.isEditing = false;
        this.notify.success(this.translate.instant('profile.messages.updated'));
      },
      error: (err: any) => { this.savingProfile = false; this.notify.error(apiErrorMessage(err, this.translate.instant('profile.messages.updateFailed'))); },
    });
  }

  // ── Preferences ───────────────────────────────────────────────────────────
  private loadPreferences(): void {
    this.uam.getMyPreferences().subscribe({
      next: (p: UserPreference) => this.applyPreferences(p),
      error: () => {},
    });
  }

  private applyPreferences(p: UserPreference): void {
    this.preferencesForm = {
      ui: {
        theme: p.ui?.theme ?? '',
        themeId: knownThemeId(p.ui?.themeId) || (p.ui?.theme === 'DARK' ? 'dark' : p.ui?.theme === 'LIGHT' ? 'light' : ''),
        fontScale: knownFontScale(p.ui?.fontScale),
        compactMode: !!p.ui?.compactMode,
        animationsEnabled: p.ui?.animationsEnabled ?? true,
        showSidebar: p.ui?.showSidebar ?? true,
      },
      notification: {
        emailEnabled: p.notification?.emailEnabled ?? true,
        smsEnabled: p.notification?.smsEnabled ?? true,
        pushEnabled: p.notification?.pushEnabled ?? true,
        whatsappEnabled: !!p.notification?.whatsappEnabled,
        inAppEnabled: p.notification?.inAppEnabled ?? true,
        doNotDisturb: !!p.notification?.doNotDisturb,
      },
      accessibility: {
        highContrast: !!p.accessibility?.highContrast,
        screenReader: !!p.accessibility?.screenReader,
        reducedMotion: !!p.accessibility?.reducedMotion,
      },
      regional: {
        language: p.regional?.language ?? '',
        timezone: p.regional?.timezone ?? '',
        currency: p.regional?.currency ?? '',
        dateFormat: p.regional?.dateFormat ?? '',
      },
    };
    this.preferencesSnapshot = JSON.stringify(this.preferencesForm);
  }

  savePreferences(): void {
    this.savingPreferences = true;
    const f = this.preferencesForm;
    this.uam.updateMyPreferences({
      ui: { theme: legacyThemeFor(f.ui.themeId) ?? (f.ui.theme || null), themeId: f.ui.themeId || null, fontScale: f.ui.fontScale, compactMode: f.ui.compactMode, animationsEnabled: f.ui.animationsEnabled, showSidebar: f.ui.showSidebar },
      notification: {
        emailEnabled: f.notification.emailEnabled, smsEnabled: f.notification.smsEnabled, pushEnabled: f.notification.pushEnabled,
        whatsappEnabled: f.notification.whatsappEnabled, inAppEnabled: f.notification.inAppEnabled, doNotDisturb: f.notification.doNotDisturb,
      },
      accessibility: { highContrast: f.accessibility.highContrast, screenReader: f.accessibility.screenReader, reducedMotion: f.accessibility.reducedMotion },
      regional: { language: f.regional.language || null, timezone: f.regional.timezone || null, currency: f.regional.currency || null, dateFormat: f.regional.dateFormat || null },
    }).subscribe({
      next: (p) => { this.applyPreferences(p); this.savingPreferences = false; this.notify.success(this.translate.instant('profile.messages.preferencesSaved')); },
      error: (err: any) => { this.savingPreferences = false; this.notify.error(apiErrorMessage(err, this.translate.instant('profile.messages.preferencesSaveFailed'))); },
    });
  }

  // ── Sessions ──────────────────────────────────────────────────────────────
  loadSessions(): void {
    this.uam.getMySessions().subscribe({ next: (s) => (this.sessions = s), error: () => {} });
  }

  /** The browser-reported position stored on the session, with a link to see it on a map. */
  sessionPosition(session: UserSession): { label: string; url: string } | null {
    const loc = session.metadata?.['location'] as { latitude?: number; longitude?: number; accuracyMeters?: number } | undefined;
    if (!loc || typeof loc.latitude !== 'number' || typeof loc.longitude !== 'number') return null;
    const label = `${loc.latitude.toFixed(4)}, ${loc.longitude.toFixed(4)}`;
    return { label, url: `https://www.google.com/maps?q=${loc.latitude},${loc.longitude}` };
  }

  sessionIcon(session: UserSession): string {
    const type = (session.deviceType || '').toUpperCase();
    if (type === 'MOBILE') return 'bi-phone';
    if (type === 'TABLET') return 'bi-tablet';
    return 'bi-laptop';
  }

  revokeSession(row: UserSession): void {
    this.revokingSessionId = row.sessionId;
    this.uam.revokeMySession(row.sessionId).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.sessionRevoked')); this.revokingSessionId = null; this.loadSessions(); },
      error: () => { this.notify.error(this.translate.instant('profile.messages.sessionRevokeFailed')); this.revokingSessionId = null; },
    });
  }

  revokeAllSessions(): void {
    this.dialogs.confirm({
      title: this.translate.instant('profile.signOutEverywhereDialog.title'),
      message: this.translate.instant('profile.signOutEverywhereDialog.message'),
      variant: 'danger',
      confirmText: this.translate.instant('profile.signOutEverywhereDialog.confirm'),
    })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.uam.revokeAllMySessions().subscribe({
          next: () => { this.notify.success(this.translate.instant('profile.messages.signedOutEverywhere')); this.loadSessions(); },
          error: () => this.notify.error(this.translate.instant('profile.messages.revokeAllFailed')),
        });
      });
  }

  // ── Devices ───────────────────────────────────────────────────────────────
  loadDevices(): void {
    this.uam.getMyDevices().subscribe({ next: (d) => (this.devices = d), error: () => {} });
  }

  deviceIcon(device: UserDevice): string {
    const platform = (device.platform || '').toUpperCase();
    if (platform === 'ANDROID' || platform === 'IOS') return 'bi-phone';
    return 'bi-laptop';
  }

  toggleTrust(row: UserDevice): void {
    this.uam.trustMyDevice(row.id, !row.trusted).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.deviceUpdated')); this.loadDevices(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.deviceUpdateFailed')),
    });
  }

  toggleBlock(row: UserDevice): void {
    this.uam.blockMyDevice(row.id, !row.blocked).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.deviceUpdated')); this.loadDevices(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.deviceUpdateFailed')),
    });
  }

  removeDevice(row: UserDevice): void {
    this.uam.deleteMyDevice(row.id).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.deviceRemoved')); this.loadDevices(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.deviceRemoveFailed')),
    });
  }

  // ── MFA ───────────────────────────────────────────────────────────────────
  loadMfa(): void {
    this.uam.getMyMfa().subscribe({ next: (m) => (this.mfaMethods = m), error: () => {} });
  }

  startEnrollTotp(): void {
    this.enrollingMfa = true;
    this.uam.enrollMyTotp().subscribe({
      next: (res) => { this.mfaEnrollment = res; this.mfaVerifyCode = ''; this.enrollingMfa = false; },
      error: (err: any) => { this.enrollingMfa = false; this.notify.error(apiErrorMessage(err, this.translate.instant('profile.messages.mfaStartFailed'))); },
    });
  }

  confirmEnrollTotp(): void {
    if (!this.mfaEnrollment || !this.mfaVerifyCode) return;
    this.verifyingMfa = true;
    this.uam.verifyMyMfaEnrollment(this.mfaEnrollment.id, this.mfaVerifyCode).subscribe({
      next: () => {
        this.verifyingMfa = false;
        this.mfaEnrollment = null;
        this.mfaVerifyCode = '';
        this.notify.success(this.translate.instant('profile.messages.mfaEnabled'));
        this.loadMfa();
      },
      error: (err: any) => { this.verifyingMfa = false; this.notify.error(apiErrorMessage(err, this.translate.instant('profile.messages.mfaInvalidCode'))); },
    });
  }

  cancelEnrollTotp(): void {
    const enrollment = this.mfaEnrollment;
    this.mfaEnrollment = null;
    this.mfaVerifyCode = '';
    if (enrollment) this.uam.deleteMyMfa(enrollment.id).subscribe({ error: () => {} });
  }

  disableMfa(row: UserMfa): void {
    this.uam.disableMyMfa(row.id).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.mfaDisabled')); this.loadMfa(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.mfaDisableFailed')),
    });
  }

  removeMfa(row: UserMfa): void {
    this.uam.deleteMyMfa(row.id).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.mfaRemoved')); this.loadMfa(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.mfaRemoveFailed')),
    });
  }

  // ── Login security ────────────────────────────────────────────────────────
  loadLoginSecurity(): void {
    this.uam.getMyLoginSecurity().subscribe({ next: (s) => (this.loginSecurity = s), error: () => {} });
  }

  get loginStatusItems(): InfoItem[] {
    const ls = this.loginSecurity;
    if (!ls) return [];
    const t = (key: string) => this.translate.instant(key);
    return [
      { label: t('profile.security.loginStatus.failedAttempts'), value: String(ls.failedLoginAttempts ?? 0) },
      { label: t('profile.security.loginStatus.lastSuccessfulLogin'), value: ls.lastSuccessfulLoginAt ? this.hqTimezone.formatLocale(ls.lastSuccessfulLoginAt, 'datetime') : '—' },
      { label: t('profile.security.loginStatus.lastFailedLogin'), value: ls.lastFailedLoginAt ? this.hqTimezone.formatLocale(ls.lastFailedLoginAt, 'datetime') : '—' },
      { label: t('profile.security.loginStatus.lastLoginIp'), value: ls.lastLoginIp || '—' },
    ];
  }

  get mfaMethodLabel(): string {
    const primary = this.mfaMethods.find((m) => m.enabled && m.primary) ?? this.mfaMethods.find((m) => m.enabled);
    return primary ? primary.mfaType : this.translate.instant('profile.security.loginDetails.notConfigured');
  }

  get accountStatItems(): InfoItem[] {
    const t = (key: string) => this.translate.instant(key);
    return [
      { label: t('profile.security.loginDetails.accountId'), value: this.user?.id || '—' },
      { label: t('profile.security.loginDetails.role'), value: (this.user?.roles || []).join(', ') || '—' },
      { label: t('profile.security.loginDetails.twoFaMethod'), value: this.mfaMethodLabel },
      { label: t('profile.security.loginDetails.accountLockedLabel'), value: t(this.loginSecurity?.accountLocked ? 'shared.commonTable.yes' : 'shared.commonTable.no') },
    ];
  }

  // ── Look and comfort: every change shows on the page at once; saving keeps it for the next sign-in ─────────────────────────────
  readonly themeChoices = THEME_CATALOG.map((t) => ({ value: t.id as string, label: `${t.name} (${t.mode})` }));
  readonly fontScales = HQ_FONT_SCALES;

  /** The label of a theme in the dropdown, for the id the form holds. */
  fontScaleKey(scale: string): string {
    return `profile.preferences.interface.fontSize.${scale}`;
  }

  /** Shows the form's current theme, text size and comfort options on the page without saving them. */
  previewAppearance(): void {
    const f = this.preferencesForm;
    this.appearancePrefs.apply({
      themeId: knownThemeId(f.ui.themeId), fontScale: f.ui.fontScale, compact: f.ui.compactMode, animations: f.ui.animationsEnabled,
      highContrast: f.accessibility.highContrast, reducedMotion: f.accessibility.reducedMotion, screenReader: f.accessibility.screenReader,
    });
  }

  // ── Theme ─────────────────────────────────────────────────────────────────
  get themeOptions(): ThemeOption[] {
    return THEME_CATALOG.map((t) => ({ ...t, active: t.id === this.theme.currentTheme }));
  }

  selectTheme(id: ThemeId): void {
    this.theme.setTheme(id);
  }

  // ── Activity ──────────────────────────────────────────────────────────────
  /** The signed-in user's own events from the audit service (the user comes from the token). */
  loadActivity(): void {
    this.myActivity.list(0, MY_ACTIVITY_PAGE_SIZE).subscribe({
      next: (page) => (this.activity = page.content ?? []),
      error: () => {},
    });
  }
}
