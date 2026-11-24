import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { GoLiveBlockedInfo } from '../go-live-governance.models';
import { GoLiveGovernanceService } from '../go-live-governance.service';
import { ReadinessNavigationService } from '../readiness-navigation.service';
import { GoLiveBlockedDialog } from './go-live-blocked-dialog';

const INFO: GoLiveBlockedInfo = {
  applicationCode: 'CENTRALISED_WALLET_APP',
  messageKey: 'readiness.governance.goLiveRefused',
  blockers: [
    { stepId: 'WALLET_APP_REGISTERED', status: 'FAIL', messageKey: 'readiness.wallet.appRegistered.fail', params: null, fixPath: '/wallet/admin/apps?application=CENTRALISED_WALLET_APP' },
    { stepId: 'NMS_TEMPLATES_EXIST', status: 'UNKNOWN', messageKey: 'readiness.nms.templatesExist.unknown', params: { reason: 'TIMEOUT' }, fixPath: null },
  ],
};

describe('GoLiveBlockedDialog', () => {
  let fixture: ComponentFixture<GoLiveBlockedDialog>;
  let granted: Set<string> | null;
  const ref = { close: vi.fn() };
  const governance = { override: vi.fn() };
  const navigation = { canOpen: vi.fn(), open: vi.fn().mockResolvedValue(true) };
  const dialogs = { confirm: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)) };

  function create(): GoLiveBlockedDialog {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [GoLiveBlockedDialog],
      imports: [FormsModule, HqSharedLibModule, TranslateModule.forRoot()],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { info: INFO } },
        { provide: MatDialogRef, useValue: ref },
        { provide: GoLiveGovernanceService, useValue: governance },
        { provide: ReadinessNavigationService, useValue: navigation },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    fixture = TestBed.createComponent(GoLiveBlockedDialog);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement;
  /** The component is zoneless: a change made outside an event handler needs marking before the view is drawn again. */
  const redraw = (): void => {
    fixture.componentRef.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  };
  const buttons = (): string[] => Array.from(el().querySelectorAll('.glb__footer button')).map((b) => b.textContent?.trim() ?? '');

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    navigation.canOpen.mockReturnValue(true);
    navigation.open.mockResolvedValue(true);
    dialogs.confirm.mockReturnValue(of(true));
  });

  it('lists every blocker with its status, and a Fix button only where a safe, openable path exists', () => {
    create();
    const items = el().querySelectorAll('.glb__item');
    expect(items).toHaveLength(2);
    expect(items[0].querySelector('.glb__fix')).not.toBeNull();
    expect(items[1].querySelector('.glb__fix')).toBeNull();
    expect(items[1].querySelector('.glb__nofix')).not.toBeNull();
    expect(items[0].querySelector('.glb__status')?.textContent).toContain('readiness.stepStatus.fail');
  });

  it('hides Fix when the user may not open the screen', () => {
    navigation.canOpen.mockReturnValue(false);
    create();
    expect(el().querySelector('.glb__fix')).toBeNull();
  });

  it('Fix closes the dialog without a change and opens the screen', () => {
    create();
    (el().querySelector('.glb__fix') as HTMLButtonElement).click();
    expect(ref.close).toHaveBeenCalledWith('closed');
    expect(navigation.open).toHaveBeenCalledWith({ path: '/wallet/admin/apps', queryParams: { application: 'CENTRALISED_WALLET_APP' } });
  });

  it('Close answers closed', () => {
    create();
    (el().querySelector('.glb__footer .btn-accent') as HTMLButtonElement).click();
    expect(ref.close).toHaveBeenCalledWith('closed');
  });

  describe('override privilege', () => {
    it('offers Override only to a holder of APPLICATION_GO_LIVE_OVERRIDE_CREATE', () => {
      granted = new Set(['APPLICATION_READINESS_READ']);
      create();
      expect(buttons()).toEqual(['readiness.governance.blocked.close']);

      granted = new Set(['APPLICATION_GO_LIVE_OVERRIDE_CREATE']);
      create();
      expect(buttons()).toEqual(['readiness.governance.override.action', 'readiness.governance.blocked.close']);
    });
  });

  describe('override form', () => {
    const open = (): GoLiveBlockedDialog => {
      const dialog = create();
      dialog.startOverride();
      redraw();
      return dialog;
    };

    it('needs a reason of 10 to 500 characters before it can be sent', () => {
      const dialog = open();
      const confirm = (): HTMLButtonElement => el().querySelector('.glb__footer .btn-accent') as HTMLButtonElement;
      expect(confirm().disabled).toBe(true);

      dialog.reason = 'too short';
      redraw();
      expect(dialog.reasonValid).toBe(false);
      expect(confirm().disabled).toBe(true);

      dialog.reason = '   because of the launch   ';
      redraw();
      expect(dialog.reasonValid).toBe(true);
      expect(confirm().disabled).toBe(false);

      dialog.reason = 'x'.repeat(501);
      expect(dialog.reasonValid).toBe(false);
    });

    it('does not call UAM when the confirmation is declined or the reason is invalid', () => {
      const dialog = open();
      dialog.confirmOverride();
      expect(dialogs.confirm).not.toHaveBeenCalled();

      dialog.reason = 'a valid reason here';
      dialogs.confirm.mockReturnValue(of(false));
      dialog.confirmOverride();
      expect(governance.override).not.toHaveBeenCalled();
    });

    it('confirms, sends the override, toasts and closes as overridden', () => {
      governance.override.mockReturnValue(of({}));
      const dialog = open();
      dialog.reason = 'a valid reason here';

      dialog.confirmOverride();

      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger' }));
      expect(governance.override).toHaveBeenCalledWith('CENTRALISED_WALLET_APP', 'a valid reason here');
      expect(notify.success).toHaveBeenCalled();
      expect(ref.close).toHaveBeenCalledWith('overridden');
    });

    it('shows the translated reason for a 403 and keeps the dialog open', () => {
      governance.override.mockReturnValue(throwError(() => ({ status: 403, error: { message: 'Only a platform administrator' } })));
      const dialog = open();
      dialog.reason = 'a valid reason here';

      dialog.confirmOverride();
      redraw();

      expect(dialog.errorText).toBe('readiness.governance.overrideForbidden');
      expect(el().querySelector('.glb__error')?.textContent).toContain('readiness.governance.overrideForbidden');
      expect(ref.close).not.toHaveBeenCalled();
      expect(dialog.busy).toBe(false);
    });

    it('shows nothingToOverride for a 400 when nothing blocks', () => {
      governance.override.mockReturnValue(throwError(() => ({ status: 400, error: { message: 'No required step is blocking this application; switch it on normally' } })));
      const dialog = open();
      dialog.reason = 'a valid reason here';
      dialog.confirmOverride();
      expect(dialog.errorText).toBe('readiness.governance.nothingToOverride');
    });

    it('goes back to the list and clears the error', () => {
      const dialog = open();
      dialog.errorText = 'x';
      dialog.backToList();
      redraw();
      expect(dialog.overriding).toBe(false);
      expect(dialog.errorText).toBeNull();
      expect(el().querySelectorAll('.glb__item')).toHaveLength(2);
    });

    it('tracks blockers by step id', () => {
      const dialog = create();
      expect(dialog.trackBlocker(0, dialog.blockers[1])).toBe('NMS_TEMPLATES_EXIST');
    });
  });
});
