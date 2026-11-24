import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { UamService } from '../../shared/services/uam-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import {
  UamUser, UserSession, UserDevice, UserMfa, UserLoginSecurity,
  CredentialSummary, RefreshToken, PasswordHistory,
} from '../../shared/models/uam.models';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-user-detail',
  standalone: false,
  templateUrl: './user-detail.html',
  styleUrl: './user-detail.scss',
})
export class UserDetail implements OnInit {
  userId = '';
  user: UamUser | null = null;
  isLoadingUser = false;

  sessions: UserSession[] = [];
  devices: UserDevice[] = [];
  mfaMethods: UserMfa[] = [];
  loginSecurity: UserLoginSecurity | null = null;
  credentials: CredentialSummary[] = [];
  refreshTokens: RefreshToken[] = [];
  passwordHistory: PasswordHistory[] = [];

  sessionColumns: HqCommonTableColumn<UserSession>[] = [];
  deviceColumns: HqCommonTableColumn<UserDevice>[] = [];
  mfaColumns: HqCommonTableColumn<UserMfa>[] = [];
  credentialColumns: HqCommonTableColumn<CredentialSummary>[] = [];
  refreshTokenColumns: HqCommonTableColumn<RefreshToken>[] = [];
  passwordHistoryColumns: HqCommonTableColumn<PasswordHistory>[] = [];

  constructor(
    private route: ActivatedRoute,
    private uam: UamService,
    private dialogs: AppDialogService,
    private notify: NotificationService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    const t = (key: string) => this.translate.instant(key);
    this.sessionColumns = [
      { key: 'ipAddress', label: t('accessManagement.userDetail.fields.ipAddress') },
      { key: 'browser', label: t('accessManagement.userDetail.fields.browser') },
      { key: 'operatingSystem', label: t('accessManagement.userDetail.fields.os') },
      { key: 'loginAt', label: t('accessManagement.userDetail.fields.loginAt') },
      { key: 'lastActivityAt', label: t('accessManagement.userDetail.fields.lastActivity') },
      { key: 'active', label: t('accessManagement.fields.active') },
    ];
    this.deviceColumns = [
      { key: 'deviceName', label: t('accessManagement.userDetail.fields.device') },
      { key: 'platform', label: t('accessManagement.userDetail.fields.platform') },
      { key: 'trusted', label: t('accessManagement.userDetail.fields.trusted') },
      { key: 'blocked', label: t('accessManagement.userDetail.fields.blocked') },
      { key: 'lastActiveAt', label: t('accessManagement.userDetail.fields.lastActive') },
    ];
    this.mfaColumns = [
      { key: 'mfaType', label: t('accessManagement.fields.type') },
      { key: 'enabled', label: t('accessManagement.userDetail.fields.enabled') },
      { key: 'primary', label: t('accessManagement.userDetail.fields.primary') },
      { key: 'verified', label: t('accessManagement.userDetail.fields.verified') },
    ];
    this.credentialColumns = [
      { key: 'provider', label: t('accessManagement.userDetail.fields.provider') },
      { key: 'verified', label: t('accessManagement.userDetail.fields.verified') },
      { key: 'primaryCredential', label: t('accessManagement.userDetail.fields.primary') },
      { key: 'active', label: t('accessManagement.fields.active') },
      { key: 'lastAuthenticatedAt', label: t('accessManagement.userDetail.fields.lastAuthenticated') },
    ];
    this.refreshTokenColumns = [
      { key: 'tokenId', label: t('accessManagement.userDetail.fields.tokenId') },
      { key: 'deviceId', label: t('accessManagement.userDetail.fields.deviceId') },
      { key: 'issuedAt', label: t('accessManagement.userDetail.fields.issuedAt') },
      { key: 'expiresAt', label: t('accessManagement.userDetail.fields.expiresAt') },
      { key: 'revoked', label: t('accessManagement.userDetail.fields.revoked') },
    ];
    this.passwordHistoryColumns = [
      { key: 'changeReason', label: t('accessManagement.userDetail.fields.reason') },
      { key: 'changedBy', label: t('accessManagement.userDetail.fields.changedBy') },
      { key: 'algorithm', label: t('accessManagement.userDetail.fields.algorithm') },
      { key: 'passwordVersion', label: t('accessManagement.userDetail.fields.version') },
    ];
    this.userId = this.route.snapshot.paramMap.get('id') || '';
    if (!this.userId) return;
    this.loadUser();
    this.loadSessions();
    this.loadDevices();
    this.loadMfa();
    this.loadLoginSecurity();
    this.loadCredentials();
    this.loadRefreshTokens();
    this.loadPasswordHistory();
  }

  get userDisplayName(): string {
    if (!this.user) return '';
    const name = [this.user.fullName?.firstName, this.user.fullName?.lastName].filter(Boolean).join(' ');
    return name || this.user.userName;
  }

  private loadUser(): void {
    this.isLoadingUser = true;
    this.uam.getUser(this.userId).subscribe({
      next: (u) => { this.user = u; this.isLoadingUser = false; },
      error: () => { this.isLoadingUser = false; this.notify.error(this.translate.instant('accessManagement.userDetail.messages.loadFailed')); },
    });
  }

  loadSessions(): void {
    this.uam.getUserSessions(this.userId).subscribe({ next: (s) => (this.sessions = s), error: () => {} });
  }

  revokeSession(row: UserSession): void {
    this.uam.revokeSession(row.sessionId).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.sessionRevoked')); this.loadSessions(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.sessionRevokeFailed')),
    });
  }

  revokeAllSessions(): void {
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.userDetail.revokeAllSessionsDialog.title'),
      message: this.translate.instant('accessManagement.userDetail.revokeAllSessionsDialog.message'),
      variant: 'danger',
      confirmText: this.translate.instant('accessManagement.userDetail.revokeAll'),
    })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.uam.revokeAllSessions(this.userId).subscribe({
          next: () => { this.notify.success(this.translate.instant('accessManagement.userDetail.messages.allSessionsRevoked')); this.loadSessions(); },
          error: () => this.notify.error(this.translate.instant('accessManagement.userDetail.messages.revokeSessionsFailed')),
        });
      });
  }

  loadDevices(): void {
    this.uam.getUserDevices(this.userId).subscribe({ next: (d) => (this.devices = d), error: () => {} });
  }

  toggleTrust(row: UserDevice): void {
    this.uam.trustDevice(row.id, !row.trusted).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.deviceUpdated')); this.loadDevices(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.deviceUpdateFailed')),
    });
  }

  toggleBlock(row: UserDevice): void {
    this.uam.blockDevice(row.id, !row.blocked).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.deviceUpdated')); this.loadDevices(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.deviceUpdateFailed')),
    });
  }

  deleteDevice(row: UserDevice): void {
    this.uam.deleteDevice(row.id).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.deviceRemoved')); this.loadDevices(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.deviceRemoveFailed')),
    });
  }

  loadMfa(): void {
    this.uam.getUserMfa(this.userId).subscribe({ next: (m) => (this.mfaMethods = m), error: () => {} });
  }

  disableMfa(row: UserMfa): void {
    this.uam.disableMfa(row.id).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.mfaDisabled')); this.loadMfa(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.mfaDisableFailed')),
    });
  }

  deleteMfa(row: UserMfa): void {
    this.uam.deleteMfa(row.id).subscribe({
      next: () => { this.notify.success(this.translate.instant('profile.messages.mfaRemoved')); this.loadMfa(); },
      error: () => this.notify.error(this.translate.instant('profile.messages.mfaRemoveFailed')),
    });
  }

  loadLoginSecurity(): void {
    this.uam.getLoginSecurity(this.userId).subscribe({ next: (s) => (this.loginSecurity = s), error: () => {} });
  }

  unlockAccount(): void {
    this.uam.unlockAccount(this.userId).subscribe({
      next: (s) => { this.loginSecurity = s; this.notify.success(this.translate.instant('accessManagement.userDetail.messages.accountUnlocked')); },
      error: () => this.notify.error(this.translate.instant('accessManagement.userDetail.messages.unlockFailed')),
    });
  }

  loadCredentials(): void {
    this.uam.getUserCredentials(this.userId).subscribe({ next: (c) => (this.credentials = c), error: () => {} });
  }

  deactivateCredential(row: CredentialSummary): void {
    this.uam.deleteCredential(row.id).subscribe({
      next: () => { this.notify.success(this.translate.instant('accessManagement.userDetail.messages.credentialDeactivated')); this.loadCredentials(); },
      error: () => this.notify.error(this.translate.instant('accessManagement.userDetail.messages.deactivateCredentialFailed')),
    });
  }

  loadRefreshTokens(): void {
    this.uam.getRefreshTokens(this.userId).subscribe({ next: (t) => (this.refreshTokens = t), error: () => {} });
  }

  revokeRefreshToken(row: RefreshToken): void {
    this.uam.revokeRefreshToken(row.id).subscribe({
      next: () => { this.notify.success(this.translate.instant('accessManagement.userDetail.messages.refreshTokenRevoked')); this.loadRefreshTokens(); },
      error: () => this.notify.error(this.translate.instant('accessManagement.userDetail.messages.revokeRefreshTokenFailed')),
    });
  }

  revokeAllRefreshTokens(): void {
    this.uam.revokeAllRefreshTokens(this.userId).subscribe({
      next: () => { this.notify.success(this.translate.instant('accessManagement.userDetail.messages.allRefreshTokensRevoked')); this.loadRefreshTokens(); },
      error: () => this.notify.error(this.translate.instant('accessManagement.userDetail.messages.revokeRefreshTokensFailed')),
    });
  }

  loadPasswordHistory(): void {
    this.uam.getPasswordHistory(this.userId, 0, 20).subscribe({
      next: (page) => (this.passwordHistory = page.content ?? []),
      error: () => {},
    });
  }
}
