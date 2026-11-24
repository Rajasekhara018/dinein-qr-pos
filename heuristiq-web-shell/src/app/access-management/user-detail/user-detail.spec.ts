import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { ApplicationAccessService } from '../../application-access/application-access.service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { UserApplicationAccess } from './user-application-access';
import { UserDetail } from './user-detail';

describe('UserDetail', () => {
  const uam = {
    getUser: vi.fn(), getUserSessions: vi.fn(), revokeSession: vi.fn(), revokeAllSessions: vi.fn(),
    getUserDevices: vi.fn(), trustDevice: vi.fn(), blockDevice: vi.fn(), deleteDevice: vi.fn(),
    getUserMfa: vi.fn(), disableMfa: vi.fn(), deleteMfa: vi.fn(),
    getLoginSecurity: vi.fn(), unlockAccount: vi.fn(),
    getUserCredentials: vi.fn(), deleteCredential: vi.fn(),
    getRefreshTokens: vi.fn(), revokeRefreshToken: vi.fn(), revokeAllRefreshTokens: vi.fn(),
    getPasswordHistory: vi.fn(),
  };
  const notify = { success: vi.fn(), error: vi.fn() };
  const dialogs = { confirm: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(id: string | null = 'u1'): ComponentFixture<UserDetail> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [UserDetail, UserApplicationAccess],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule, MatTabsModule],
      providers: [
        { provide: UamService, useValue: uam },
        { provide: ApplicationAccessService, useValue: { listForUser: () => of({ state: 'ready', grants: [] }), grant: vi.fn(), revoke: vi.fn() } },
        { provide: OrganizationContextService, useValue: { currentTenantId: () => null } },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } },
      ],
    });
    const fixture = TestBed.createComponent(UserDetail);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    uam.getUser.mockReturnValue(of({ id: 'u1', userName: 'sita', email: 's@x.io', status: 'ACTIVE', fullName: { firstName: 'Sita', lastName: 'Rao' } }));
    uam.getUserSessions.mockReturnValue(of([{ sessionId: 's1', ipAddress: '10.0.0.1', browser: 'Chrome' }]));
    uam.getUserDevices.mockReturnValue(of([{ id: 'd1', deviceName: 'Phone', trusted: false, blocked: true }]));
    uam.getUserMfa.mockReturnValue(of([{ id: 'm1', mfaType: 'TOTP' }]));
    uam.getLoginSecurity.mockReturnValue(of({ failedLoginAttempts: 3, accountLocked: true, lockReason: 'Too many' }));
    uam.getUserCredentials.mockReturnValue(of([{ id: 'c1', provider: 'LOCAL' }]));
    uam.getRefreshTokens.mockReturnValue(of([{ id: 't1', tokenId: 'tok' }]));
    uam.getPasswordHistory.mockReturnValue(of({ content: [{ changeReason: 'RESET' }] }));
    for (const name of ['revokeSession', 'revokeAllSessions', 'trustDevice', 'blockDevice', 'deleteDevice', 'disableMfa', 'deleteMfa', 'deleteCredential', 'revokeRefreshToken', 'revokeAllRefreshTokens']) {
      (uam as Record<string, ReturnType<typeof vi.fn>>)[name].mockReturnValue(of({}));
    }
    uam.unlockAccount.mockReturnValue(of({ accountLocked: false }));
    dialogs.confirm.mockReturnValue(of(true));
  });

  it('does nothing without a user id in the route', () => {
    const component = create(null).componentInstance;

    expect(uam.getUser).not.toHaveBeenCalled();
    expect(component.userDisplayName).toBe('');
  });

  it('loads the user and each section for the route id', () => {
    const component = create().componentInstance;

    expect(uam.getUser).toHaveBeenCalledWith('u1');
    expect(uam.getUserSessions).toHaveBeenCalledWith('u1');
    expect(uam.getPasswordHistory).toHaveBeenCalledWith('u1', 0, 20);
    expect(component.userDisplayName).toBe('Sita Rao');
    expect(component.sessions.length).toBe(1);
    expect(component.devices.length).toBe(1);
    expect(component.mfaMethods.length).toBe(1);
    expect(component.loginSecurity?.failedLoginAttempts).toBe(3);
    expect(component.credentials.length).toBe(1);
    expect(component.refreshTokens.length).toBe(1);
    expect(component.passwordHistory).toEqual([{ changeReason: 'RESET' }]);
    expect(component.sessionColumns.length).toBe(6);
  });

  it('falls back to the user name when there is no full name', () => {
    uam.getUser.mockReturnValue(of({ id: 'u1', userName: 'bare', status: 'ACTIVE' }));

    expect(create().componentInstance.userDisplayName).toBe('bare');
  });

  it('reports a user that cannot be loaded and keeps the other sections independent', () => {
    uam.getUser.mockReturnValue(throwError(() => new Error('x')));
    uam.getUserSessions.mockReturnValue(throwError(() => new Error('x')));
    uam.getUserDevices.mockReturnValue(throwError(() => new Error('x')));
    uam.getUserMfa.mockReturnValue(throwError(() => new Error('x')));
    uam.getLoginSecurity.mockReturnValue(throwError(() => new Error('x')));
    uam.getUserCredentials.mockReturnValue(throwError(() => new Error('x')));
    uam.getRefreshTokens.mockReturnValue(throwError(() => new Error('x')));
    uam.getPasswordHistory.mockReturnValue(throwError(() => new Error('x')));

    const component = create().componentInstance;

    expect(notify.error).toHaveBeenCalledWith('accessManagement.userDetail.messages.loadFailed');
    expect(component.isLoadingUser).toBe(false);
    expect(component.sessions).toEqual([]);
    expect(component.loginSecurity).toBeNull();
  });

  it('treats a password history page without content as empty', () => {
    uam.getPasswordHistory.mockReturnValue(of({}));

    expect(create().componentInstance.passwordHistory).toEqual([]);
  });

  describe('sessions', () => {
    it('revokes one session and reloads the list', () => {
      const component = create().componentInstance;
      uam.getUserSessions.mockClear();

      component.revokeSession({ sessionId: 's1' } as never);

      expect(uam.revokeSession).toHaveBeenCalledWith('s1');
      expect(notify.success).toHaveBeenCalledWith('profile.messages.sessionRevoked');
      expect(uam.getUserSessions).toHaveBeenCalledTimes(1);
    });

    it('reports a failed revoke', () => {
      uam.revokeSession.mockReturnValue(throwError(() => new Error('x')));
      create().componentInstance.revokeSession({ sessionId: 's1' } as never);

      expect(notify.error).toHaveBeenCalledWith('profile.messages.sessionRevokeFailed');
    });

    it('revokes all sessions only after the confirmation', () => {
      const component = create().componentInstance;

      component.revokeAllSessions();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger' }));
      expect(uam.revokeAllSessions).toHaveBeenCalledWith('u1');
      expect(notify.success).toHaveBeenCalledWith('accessManagement.userDetail.messages.allSessionsRevoked');

      uam.revokeAllSessions.mockClear();
      dialogs.confirm.mockReturnValue(of(false));
      component.revokeAllSessions();
      expect(uam.revokeAllSessions).not.toHaveBeenCalled();
    });

    it('reports a failure to revoke all sessions', () => {
      uam.revokeAllSessions.mockReturnValue(throwError(() => new Error('x')));
      create().componentInstance.revokeAllSessions();

      expect(notify.error).toHaveBeenCalledWith('accessManagement.userDetail.messages.revokeSessionsFailed');
    });
  });

  describe('devices', () => {
    it('flips trust and block against the current state, then reloads', () => {
      const component = create().componentInstance;
      const device = { id: 'd1', trusted: false, blocked: true } as never;

      component.toggleTrust(device);
      expect(uam.trustDevice).toHaveBeenCalledWith('d1', true);
      component.toggleBlock(device);
      expect(uam.blockDevice).toHaveBeenCalledWith('d1', false);
      expect(notify.success).toHaveBeenCalledWith('profile.messages.deviceUpdated');
    });

    it('removes a device', () => {
      create().componentInstance.deleteDevice({ id: 'd1' } as never);

      expect(uam.deleteDevice).toHaveBeenCalledWith('d1');
      expect(notify.success).toHaveBeenCalledWith('profile.messages.deviceRemoved');
    });

    it('reports failures of each device action', () => {
      const component = create().componentInstance;
      uam.trustDevice.mockReturnValue(throwError(() => new Error('x')));
      uam.blockDevice.mockReturnValue(throwError(() => new Error('x')));
      uam.deleteDevice.mockReturnValue(throwError(() => new Error('x')));

      component.toggleTrust({ id: 'd1' } as never);
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.deviceUpdateFailed');
      component.toggleBlock({ id: 'd1' } as never);
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.deviceUpdateFailed');
      component.deleteDevice({ id: 'd1' } as never);
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.deviceRemoveFailed');
    });
  });

  describe('two-factor methods', () => {
    it('disables and removes a method', () => {
      const component = create().componentInstance;

      component.disableMfa({ id: 'm1' } as never);
      expect(uam.disableMfa).toHaveBeenCalledWith('m1');
      expect(notify.success).toHaveBeenLastCalledWith('profile.messages.mfaDisabled');
      component.deleteMfa({ id: 'm1' } as never);
      expect(uam.deleteMfa).toHaveBeenCalledWith('m1');
      expect(notify.success).toHaveBeenLastCalledWith('profile.messages.mfaRemoved');
    });

    it('reports failures', () => {
      const component = create().componentInstance;
      uam.disableMfa.mockReturnValue(throwError(() => new Error('x')));
      uam.deleteMfa.mockReturnValue(throwError(() => new Error('x')));

      component.disableMfa({ id: 'm1' } as never);
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.mfaDisableFailed');
      component.deleteMfa({ id: 'm1' } as never);
      expect(notify.error).toHaveBeenLastCalledWith('profile.messages.mfaRemoveFailed');
    });
  });

  describe('login security and credentials', () => {
    it('unlocks the account and shows the new state', () => {
      const component = create().componentInstance;

      component.unlockAccount();

      expect(uam.unlockAccount).toHaveBeenCalledWith('u1');
      expect(component.loginSecurity?.accountLocked).toBe(false);
      expect(notify.success).toHaveBeenCalledWith('accessManagement.userDetail.messages.accountUnlocked');
    });

    it('reports a failed unlock', () => {
      uam.unlockAccount.mockReturnValue(throwError(() => new Error('x')));
      create().componentInstance.unlockAccount();

      expect(notify.error).toHaveBeenCalledWith('accessManagement.userDetail.messages.unlockFailed');
    });

    it('deactivates a credential or reports why not', () => {
      const component = create().componentInstance;

      component.deactivateCredential({ id: 'c1' } as never);
      expect(uam.deleteCredential).toHaveBeenCalledWith('c1');
      expect(notify.success).toHaveBeenCalledWith('accessManagement.userDetail.messages.credentialDeactivated');

      uam.deleteCredential.mockReturnValue(throwError(() => new Error('x')));
      component.deactivateCredential({ id: 'c1' } as never);
      expect(notify.error).toHaveBeenCalledWith('accessManagement.userDetail.messages.deactivateCredentialFailed');
    });
  });

  describe('refresh tokens', () => {
    it('revokes one or all tokens and reloads', () => {
      const component = create().componentInstance;
      uam.getRefreshTokens.mockClear();

      component.revokeRefreshToken({ id: 't1' } as never);
      expect(uam.revokeRefreshToken).toHaveBeenCalledWith('t1');
      component.revokeAllRefreshTokens();
      expect(uam.revokeAllRefreshTokens).toHaveBeenCalledWith('u1');
      expect(uam.getRefreshTokens).toHaveBeenCalledTimes(2);
    });

    it('reports failures', () => {
      const component = create().componentInstance;
      uam.revokeRefreshToken.mockReturnValue(throwError(() => new Error('x')));
      uam.revokeAllRefreshTokens.mockReturnValue(throwError(() => new Error('x')));

      component.revokeRefreshToken({ id: 't1' } as never);
      expect(notify.error).toHaveBeenLastCalledWith('accessManagement.userDetail.messages.revokeRefreshTokenFailed');
      component.revokeAllRefreshTokens();
      expect(notify.error).toHaveBeenLastCalledWith('accessManagement.userDetail.messages.revokeRefreshTokensFailed');
    });
  });

  describe('privilege-gated tabs and actions', () => {
    const tabs = (fixture: ComponentFixture<UserDetail>) =>
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('[role="tab"]')).map((t) => t.textContent?.trim());

    it('shows every tab to the users manager', () => {
      granted = new Set(['UAM_USERS_MANAGE', 'REFRESH_TOKEN_LIST']);

      expect(tabs(create()).length).toBe(7);
    });

    it('shows no tab to someone with no privilege', () => {
      granted = new Set();

      expect(tabs(create()).length).toBe(0);
    });

    it('shows only the tabs whose list privilege is held', () => {
      granted = new Set(['USER_SESSION_LIST', 'PASSWORD_HISTORY_LIST']);

      expect(tabs(create())).toEqual([
        'accessManagement.userDetail.tabs.sessions',
        'accessManagement.userDetail.tabs.passwordHistory',
      ]);
    });

    it('a sessions viewer sees the list but no revoke buttons', () => {
      granted = new Set(['USER_SESSION_LIST']);
      const el: HTMLElement = create().nativeElement;

      expect(el.textContent).toContain('10.0.0.1');
      expect(el.querySelector('.row-action-btn')).toBeNull();
      expect(el.querySelector('.table-create-btn')).toBeNull();
    });

    it('revoking is offered with the delete privilege and revoke-all with the manage privilege', () => {
      granted = new Set(['USER_SESSION_LIST', 'USER_SESSION_DELETE']);
      let fixture = create();
      (fixture.nativeElement.querySelector('.row-action-btn.danger') as HTMLElement).click();
      expect(uam.revokeSession).toHaveBeenCalledWith('s1');
      expect(fixture.nativeElement.querySelector('.table-create-btn')).toBeNull();

      granted = new Set(['UAM_USERS_MANAGE']);
      fixture = create();
      (fixture.nativeElement.querySelector('.table-create-btn') as HTMLElement).click();
      expect(dialogs.confirm).toHaveBeenCalled();
    });
  });
});
