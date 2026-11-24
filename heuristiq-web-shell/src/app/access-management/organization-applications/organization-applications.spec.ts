import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { GoLiveGovernanceService } from '../../application-readiness/go-live-governance.service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { OrganizationApplications } from './organization-applications';

describe('OrganizationApplications', () => {
  const uam = { get: vi.fn(), list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const dialogs = { confirm: vi.fn() };
  const goLive = { handleRefusal: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  const subscriptions = [
    { applicationName: 'Rides', subscription: { applicationCode: 'RIDES', status: 'ACTIVE', maxUsers: 10, validUntil: '2030-01-01' } },
    { applicationName: '', subscription: { applicationCode: 'BILLING', status: 'SUSPENDED' } },
    { applicationName: 'Old', subscription: { applicationCode: 'OLD', status: 'CANCELLED' } },
  ];
  const catalogue = [
    { code: 'RIDES', name: 'Rides' },
    { code: 'BILLING', name: 'Billing' },
    { code: 'OLD', name: 'Old app' },
    { code: 'PAYMENTS', name: 'Payments' },
    { code: 'RETIRED', name: 'Retired', active: false },
    { code: 'maps', name: '' },
  ];

  function create(): ComponentFixture<OrganizationApplications> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrganizationApplications],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: UamService, useValue: uam },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: GoLiveGovernanceService, useValue: goLive },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(OrganizationApplications);
    fixture.componentRef.setInput('tenantId', 'org1');
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    uam.get.mockReturnValue(of(subscriptions));
    uam.list.mockReturnValue(of({ content: catalogue }));
    uam.create.mockReturnValue(of({}));
    uam.update.mockReturnValue(of({}));
    uam.remove.mockReturnValue(of({}));
    dialogs.confirm.mockReturnValue(of(true));
    goLive.handleRefusal.mockReturnValue(of(null));
  });

  it('loads the subscriptions of the organization sorted by application code', () => {
    const component = create().componentInstance;

    expect(uam.get).toHaveBeenCalledWith('/organizations/org1/applications');
    expect(uam.list).toHaveBeenCalledWith('/applications', 0, 200);
    expect(component.rows.map((r) => r.subscription.applicationCode)).toEqual(['BILLING', 'OLD', 'RIDES']);
    expect(component.isLoading).toBe(false);
  });

  it('offers only active applications not already in use (a cancelled one can be taken again)', () => {
    const component = create().componentInstance;

    expect(component.choices.map((a) => a.code)).toEqual(['OLD', 'PAYMENTS', 'maps']);
  });

  it('renders each subscription with its status, limit and end date', () => {
    const text: string = create().nativeElement.textContent;

    expect(text).toContain('Rides');
    expect(text).toContain('BILLING');
    expect(text).toContain('accessManagement.organizations.applications.statuses.SUSPENDED');
    expect(text).toContain('2030-01-01');
    expect(text).toContain('accessManagement.organizations.applications.noLimit');
    expect(text).toContain('accessManagement.organizations.applications.noEnd');
  });

  it('shows the empty message when nothing is subscribed', () => {
    uam.get.mockReturnValue(of([]));

    expect(create().nativeElement.textContent).toContain('accessManagement.organizations.applications.empty');
  });

  it('reports a failed load with the server detail or a default', () => {
    uam.get.mockReturnValue(throwError(() => ({ error: { detail: 'Forbidden org' } })));
    expect(create().componentInstance.isLoading).toBe(false);
    expect(notify.error).toHaveBeenLastCalledWith('Forbidden org');

    uam.get.mockReturnValue(throwError(() => new Error('x')));
    create();
    expect(notify.error).toHaveBeenLastCalledWith('accessManagement.organizations.applications.messages.loadFailed');
  });

  it('does nothing when subscribing with no application chosen', () => {
    create().componentInstance.subscribe();

    expect(uam.create).not.toHaveBeenCalled();
  });

  it('subscribes the chosen application with its limit and end date, then reloads and clears the form', () => {
    const component = create().componentInstance;
    component.selectedCode = 'PAYMENTS';
    component.maxUsers = 5;
    component.validUntil = '2031-05-05';
    uam.get.mockClear();

    component.subscribe();

    expect(uam.create).toHaveBeenCalledWith('/organizations/org1/applications', { applicationCode: 'PAYMENTS', maxUsers: 5, validUntil: '2031-05-05' });
    expect(notify.success).toHaveBeenCalledWith('accessManagement.organizations.applications.messages.subscribed');
    expect([component.selectedCode, component.maxUsers, component.validUntil]).toEqual(['', null, '']);
    expect(uam.get).toHaveBeenCalledTimes(1);
  });

  it('sends no end date when none was typed', () => {
    const component = create().componentInstance;
    component.selectedCode = 'PAYMENTS';

    component.subscribe();

    expect(uam.create).toHaveBeenCalledWith(expect.any(String), { applicationCode: 'PAYMENTS', maxUsers: null, validUntil: null });
  });

  it('keeps the form and reports the error when subscribing fails', () => {
    uam.create.mockReturnValue(throwError(() => ({ error: { detail: 'Limit reached' } })));
    const component = create().componentInstance;
    component.selectedCode = 'PAYMENTS';

    component.subscribe();

    expect(notify.error).toHaveBeenCalledWith('Limit reached');
    expect(component.selectedCode).toBe('PAYMENTS');
    expect(component.busy).toBe(false);
  });

  it('pauses and resumes a subscription', () => {
    const component = create().componentInstance;

    component.setStatus(component.rows[2], 'SUSPENDED');
    expect(uam.update).toHaveBeenCalledWith('/organizations/org1/applications', 'RIDES', { status: 'SUSPENDED' });
    expect(notify.success).toHaveBeenCalledWith('accessManagement.organizations.applications.messages.updated');
  });

  it('cancels only after confirmation', () => {
    const component = create().componentInstance;

    component.cancel(component.rows[2]);
    expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger' }));
    expect(uam.remove).toHaveBeenCalledWith('/organizations/org1/applications', 'RIDES');
    expect(notify.success).toHaveBeenCalledWith('accessManagement.organizations.applications.messages.cancelled');

    uam.remove.mockClear();
    dialogs.confirm.mockReturnValue(of(false));
    component.cancel(component.rows[2]);
    expect(uam.remove).not.toHaveBeenCalled();
  });

  it('uses the application code in the cancel prompt when there is no name', () => {
    const component = create().componentInstance;

    component.cancel(component.rows[0]);

    expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ message: 'accessManagement.organizations.applications.cancelDialog.message' }));
  });

  it('reports a failed action with a default message', () => {
    uam.update.mockReturnValue(throwError(() => new Error('x')));
    const component = create().componentInstance;

    component.setStatus(component.rows[0], 'ACTIVE');

    expect(notify.error).toHaveBeenCalledWith('accessManagement.organizations.applications.messages.actionFailed');
  });

  it('opens the go-live blocked dialog when switching on is refused, with no error toast and no reload', () => {
    const refusal = { status: 409, error: { errorCode: 'GO_LIVE_BLOCKED' } };
    const component = create().componentInstance;
    uam.update.mockReturnValueOnce(throwError(() => refusal));
    goLive.handleRefusal.mockReturnValueOnce(of('closed'));
    uam.get.mockClear();

    component.setStatus(component.rows[0], 'ACTIVE');

    expect(goLive.handleRefusal).toHaveBeenCalledWith(refusal);
    expect(notify.error).not.toHaveBeenCalled();
    expect(uam.get).not.toHaveBeenCalled();
    expect(component.busy).toBe(false);
  });

  it('reloads the list when the refusal was overridden in the dialog', () => {
    const component = create().componentInstance;
    uam.update.mockReturnValueOnce(throwError(() => ({ status: 409 })));
    goLive.handleRefusal.mockReturnValueOnce(of('overridden'));
    uam.get.mockClear();

    component.setStatus(component.rows[0], 'ACTIVE');

    expect(uam.get).toHaveBeenCalledWith('/organizations/org1/applications');
    expect(notify.error).not.toHaveBeenCalled();
  });

  it('tracks rows by application code', () => {
    const component = create().componentInstance;

    expect(component.trackRow(0, component.rows[0])).toBe('BILLING');
  });

  describe('privileges', () => {
    it('renders nothing without the list privilege', () => {
      granted = new Set();

      expect(create().nativeElement.querySelector('section')).toBeNull();
    });

    it('a viewer sees the rows but no subscribe form and no row actions', () => {
      granted = new Set(['ORGANIZATION_APPLICATION_LIST']);
      const el: HTMLElement = create().nativeElement;

      expect(el.querySelector('tbody tr')).not.toBeNull();
      expect(el.querySelector('form')).toBeNull();
      expect(el.querySelectorAll('tbody button').length).toBe(0);
    });

    it('shows pause / resume / reactivate with the update privilege and cancel with the cancel privilege', () => {
      granted = new Set(['ORGANIZATION_APPLICATION_LIST', 'ORGANIZATION_APPLICATION_UPDATE']);
      let el: HTMLElement = create().nativeElement;
      const labels = Array.from(el.querySelectorAll('tbody button')).map((b) => b.textContent?.trim());
      expect(labels).toEqual([
        'accessManagement.organizations.applications.resume',
        'accessManagement.organizations.applications.reactivate',
        'accessManagement.organizations.applications.suspend',
      ]);

      granted = new Set(['ORGANIZATION_APPLICATION_LIST', 'ORGANIZATION_APPLICATION_CANCEL', 'ORGANIZATION_APPLICATION_SUBSCRIBE']);
      el = create().nativeElement;
      // Cancelled subscriptions cannot be cancelled again.
      expect(el.querySelectorAll('tbody button.danger').length).toBe(2);
      expect(el.querySelector('form')).not.toBeNull();
    });

    it('clicking a row action calls UAM', () => {
      granted = new Set(['ORGANIZATION_APPLICATION_LIST', 'ORGANIZATION_APPLICATION_UPDATE']);
      const el: HTMLElement = create().nativeElement;

      (el.querySelectorAll('tbody button')[2] as HTMLElement).click();

      expect(uam.update).toHaveBeenCalledWith(expect.any(String), 'RIDES', { status: 'SUSPENDED' });
    });
  });
});
