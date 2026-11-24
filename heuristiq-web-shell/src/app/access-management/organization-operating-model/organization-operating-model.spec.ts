import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationOperatingModel } from './organization-operating-model';
import { OrganizationOperatingModelService } from './organization-operating-model.service';
import { OperatingModelView } from './organization-operating-model.types';

describe('OrganizationOperatingModel', () => {
  const service = { load: vi.fn(), schedule: vi.fn(), cancel: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const dialogs = { confirm: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  const view: OperatingModelView = {
    organizationId: 'org1',
    today: '2026-11-08',
    currentModel: 'FRANCHISE',
    schedule: [
      { model: 'FRANCHISE', effectiveFrom: '2026-10-01', inForce: true },
      { model: 'DIRECT', effectiveFrom: '2027-01-01', inForce: false },
    ],
  };

  function create(): ComponentFixture<OrganizationOperatingModel> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrganizationOperatingModel],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: OrganizationOperatingModelService, useValue: service },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(OrganizationOperatingModel);
    fixture.componentRef.setInput('tenantId', 'org1');
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    service.load.mockReturnValue(of(view));
    service.schedule.mockReturnValue(of(view));
    service.cancel.mockReturnValue(of(view));
    dialogs.confirm.mockReturnValue(of(true));
  });

  it('loads the model of the organization and shows today\'s model and every change', () => {
    const fixture = create();
    const text: string = fixture.nativeElement.textContent;

    expect(service.load).toHaveBeenCalledWith('org1');
    expect(text).toContain('accessManagement.organizations.operatingModel.models.FRANCHISE');
    expect(text).toContain('2026-10-01');
    expect(text).toContain('2027-01-01');
    expect(text).toContain('accessManagement.organizations.operatingModel.inForce');
    expect(text).toContain('accessManagement.organizations.operatingModel.scheduled');
  });

  it('offers a switch to either model other than the last scheduled, from the day after it', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const options = Array.from(fixture.nativeElement.querySelectorAll('.om__select option') as NodeListOf<HTMLOptionElement>)
      .map((o) => o.textContent?.trim());

    expect(component.options).toEqual(['FRANCHISE', 'HYBRID']);
    expect(component.target).toBe('FRANCHISE');
    expect(component.minDate).toBe('2027-01-02');
    expect(options).toEqual([
      'accessManagement.organizations.operatingModel.models.FRANCHISE',
      'accessManagement.organizations.operatingModel.models.HYBRID',
    ]);
  });

  it('explains today\'s model and the model chosen for the switch', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    const select: HTMLSelectElement = fixture.nativeElement.querySelector('.om__select');
    select.value = select.options[1].value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;

    expect(component.selectedModel).toBe('HYBRID');

    expect(text).toContain('accessManagement.organizations.operatingModel.descriptions.FRANCHISE');
    expect(text).toContain('accessManagement.organizations.operatingModel.descriptions.HYBRID');
    expect(component.description(null)).toBeNull();
    expect(component.description('OTHER' as never)).toBeNull();
  });

  it('schedules a switch to hybrid when it is chosen', () => {
    const component = create().componentInstance;
    component.selectedModel = 'HYBRID';
    component.effectiveFrom = '2027-06-01';

    component.scheduleSwitch();

    expect(service.schedule).toHaveBeenCalledWith('org1', view, 'HYBRID', '2027-06-01');
    expect(component.selectedModel).toBeNull();
  });

  it('falls back to the first allowed model when the chosen one is no longer allowed', () => {
    const component = create().componentInstance;

    component.selectedModel = 'DIRECT';

    expect(component.target).toBe('FRANCHISE');
  });

  it('a hybrid organization is shown as hybrid and may switch to either other model', () => {
    service.load.mockReturnValue(of({
      ...view,
      currentModel: 'HYBRID',
      schedule: [{ model: 'HYBRID', effectiveFrom: '2026-10-01', inForce: true }],
    }));
    const fixture = create();

    expect(fixture.nativeElement.querySelector('.om__model--HYBRID')?.textContent)
      .toContain('accessManagement.organizations.operatingModel.models.HYBRID');
    expect(fixture.componentInstance.options).toEqual(['DIRECT', 'FRANCHISE']);
  });

  it('only a scheduled change can be cancelled', () => {
    const buttons = create().nativeElement.querySelectorAll('.om__actions button');

    expect(buttons.length).toBe(1);
  });

  it('schedules a switch after confirmation and shows the answer', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const answer: OperatingModelView = { ...view, schedule: [...view.schedule, { model: 'FRANCHISE', effectiveFrom: '2027-06-01', inForce: false }] };
    service.schedule.mockReturnValue(of(answer));
    component.effectiveFrom = '2027-06-01';

    component.scheduleSwitch();

    expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({
      message: 'accessManagement.organizations.operatingModel.switchDialog.message',
    }));
    expect(service.schedule).toHaveBeenCalledWith('org1', view, 'FRANCHISE', '2027-06-01');
    expect(notify.success).toHaveBeenCalledWith('accessManagement.organizations.operatingModel.messages.scheduled');
    expect(component.view).toBe(answer);
    expect(component.effectiveFrom).toBe('');
    expect(component.busy).toBe(false);
  });

  it('does nothing without a date, or when the switch is not confirmed', () => {
    const component = create().componentInstance;

    component.scheduleSwitch();
    expect(dialogs.confirm).not.toHaveBeenCalled();

    dialogs.confirm.mockReturnValue(of(false));
    component.effectiveFrom = '2027-06-01';
    component.scheduleSwitch();
    expect(service.schedule).not.toHaveBeenCalled();
  });

  it('cancels a scheduled switch after confirmation, never one in force', () => {
    const component = create().componentInstance;

    component.cancel(view.schedule[0]);
    expect(dialogs.confirm).not.toHaveBeenCalled();

    component.cancel(view.schedule[1]);
    expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger' }));
    expect(service.cancel).toHaveBeenCalledWith('org1', view, view.schedule[1]);
    expect(notify.success).toHaveBeenCalledWith('accessManagement.organizations.operatingModel.messages.cancelled');
  });

  it('shows the server\'s reason when a change is refused', () => {
    const component = create().componentInstance;
    service.schedule.mockReturnValue(throwError(() => ({ error: { message: 'Each change must start after the one before it' } })));
    component.effectiveFrom = '2027-06-01';

    component.scheduleSwitch();

    expect(notify.error).toHaveBeenCalled();
    expect(component.busy).toBe(false);
  });

  it('falls back to a translated message when loading fails without a reason', () => {
    service.load.mockReturnValue(throwError(() => ({})));

    const component = create().componentInstance;

    expect(notify.error).toHaveBeenCalledWith('accessManagement.organizations.operatingModel.messages.loadFailed');
    expect(component.isLoading).toBe(false);
  });

  it('a viewer without the update privilege sees the model but no way to change it', () => {
    granted = new Set(['ORGANIZATION_OPERATING_MODEL_READ']);
    const element: HTMLElement = create().nativeElement;

    expect(element.querySelector('.om')).not.toBeNull();
    expect(element.querySelector('.om__add')).toBeNull();
    expect(element.querySelectorAll('.om__actions button').length).toBe(0);
  });

  it('without the read privilege nothing is shown and nothing is asked of the server', () => {
    granted = new Set();
    const element: HTMLElement = create().nativeElement;

    expect(element.querySelector('.om')).toBeNull();
    expect(service.load).not.toHaveBeenCalled();
    expect(notify.error).not.toHaveBeenCalled();
  });

  it('shows an unknown model code as it is', () => {
    const component = create().componentInstance;

    expect(component.label('OTHER' as never)).toBe('OTHER');
  });
});
