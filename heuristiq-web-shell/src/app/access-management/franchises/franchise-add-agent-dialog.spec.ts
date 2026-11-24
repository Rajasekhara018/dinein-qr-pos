import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { HqApplicationsService, HqSharedLibModule } from 'hq-shared-lib';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseAddAgentDialog, FranchiseAddAgentDialogData } from './franchise-add-agent-dialog';
import { FranchiseApi } from './franchise-api';
import { FranchiseApplication } from './franchise.models';

const full: FranchiseApplication = { applicationCode: 'RIDE', agentLimit: 2, agentCount: 2, fee: { code: 'INR', value: 10 } };
const open: FranchiseApplication = { applicationCode: 'MATRIMONY', agentLimit: 5, agentCount: 1, fee: { code: 'INR', value: 10 } };

const api = { adminAddAgent: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
const ref = { close: vi.fn() };
const applications = { nameOf: (code: string) => (code === 'MATRIMONY' ? 'Matrimony' : code) };

function create(data: FranchiseAddAgentDialogData) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    declarations: [FranchiseAddAgentDialog],
    imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
    providers: [
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatDialogRef, useValue: ref },
      { provide: FranchiseApi, useValue: api },
      { provide: NotificationService, useValue: notify },
      { provide: HqApplicationsService, useValue: applications },
    ],
  });
  const fixture = TestBed.createComponent(FranchiseAddAgentDialog);
  fixture.detectChanges();
  return { fixture, dialog: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('FranchiseAddAgentDialog', () => {
  it('keeps BestRide drivers out of the agent form', () => {
    const { dialog } = create({ franchiseId: 'f9', franchiseCode: '', applications: [open, { ...open, applicationCode: 'BESTRIDE_RIDER_APP' }] });
    expect(dialog.openApplications).toEqual([open]);
    dialog.agent.applicationCode = 'BESTRIDE_RIDER_APP';
    dialog.add();
    expect(api.adminAddAgent).not.toHaveBeenCalled();
  });
  beforeEach(() => {
    vi.clearAllMocks();
    api.adminAddAgent.mockReturnValue(of({ employeeId: 'm2' }));
  });

  it('offers only the applications with a place free, and picks the only one', () => {
    const { dialog, el } = create({ franchiseId: 'f9', franchiseCode: 'RBM-9', applications: [full, open] });

    expect(dialog.openApplications).toEqual([open]);
    expect(dialog.agent.applicationCode).toBe('MATRIMONY');
    expect(dialog.appName('MATRIMONY')).toBe('Matrimony');
    expect(el.querySelector('h2')?.textContent).toContain('accessManagement.franchiseMembers.addAgentDialog.title');
    expect(el.querySelector('[data-testid="add-agent-full"]')).toBeNull();
  });

  it('says every place is used and cannot be sent when no application has room', () => {
    const { dialog, el } = create({ franchiseId: 'f9', franchiseCode: '', applications: [full] });

    expect(dialog.openApplications).toEqual([]);
    expect(el.querySelector('[data-testid="add-agent-full"]')).not.toBeNull();
    expect((el.querySelector('[data-testid="add-agent-submit"]') as HTMLButtonElement).disabled).toBe(true);
    expect(el.querySelector('h2')?.textContent).toContain('accessManagement.franchises.addAgentTitle');
  });

  it('lets the administrator choose when several applications have room', () => {
    const other = { ...open, applicationCode: 'TRAIN' };
    const { dialog } = create({ franchiseId: 'f9', franchiseCode: 'RBM-9', applications: [open, other] });

    expect(dialog.agent.applicationCode).toBe('');
    dialog.add();
    expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.agentApplication');
    expect(api.adminAddAgent).not.toHaveBeenCalled();
  });

  it('refuses a missing name or a bad email and a phone the field marks invalid, without calling the server', () => {
    const { dialog } = create({ franchiseId: 'f9', franchiseCode: 'RBM-9', applications: [open] });

    dialog.agent = { applicationCode: 'MATRIMONY', fullName: 'Asha', email: 'not-an-email', phone: '' };
    dialog.add();
    expect(notify.error).toHaveBeenLastCalledWith('accessManagement.franchises.errors.agent');

    dialog.add(true);
    expect(notify.error).toHaveBeenLastCalledWith('accessManagement.franchises.errors.phone');
    expect(api.adminAddAgent).not.toHaveBeenCalled();
  });

  it('adds the agent through the administrator\'s call, then says so and closes with true', () => {
    const { dialog } = create({ franchiseId: 'f9', franchiseCode: 'RBM-9', applications: [open] });
    dialog.agent = { applicationCode: 'MATRIMONY', fullName: ' Asha ', email: ' asha@x.co ', phone: '' };

    dialog.submit();

    expect(api.adminAddAgent).toHaveBeenCalledWith('f9', { applicationCode: 'MATRIMONY', fullName: 'Asha', email: 'asha@x.co' });
    expect(notify.success).toHaveBeenCalledWith('accessManagement.franchises.messages.agentAdded');
    expect(ref.close).toHaveBeenCalledWith(true);
  });

  it('shows the server\'s reason and stays open with the form filled in when the add is refused', () => {
    api.adminAddAgent.mockReturnValue(throwError(() => ({ error: { message: 'This franchise already has its limit of 5 agents' } })));
    const { dialog } = create({ franchiseId: 'f9', franchiseCode: 'RBM-9', applications: [open] });
    dialog.agent = { applicationCode: 'MATRIMONY', fullName: 'Asha', email: 'asha@x.co', phone: '' };

    dialog.add();

    expect(notify.error).toHaveBeenCalledWith('This franchise already has its limit of 5 agents');
    expect(ref.close).not.toHaveBeenCalled();
    expect(dialog.saving).toBe(false);
    expect(dialog.agent.fullName).toBe('Asha');
  });

  it('ignores a second add while saving and closes with false on cancel, but not while saving', () => {
    const { dialog } = create({ franchiseId: 'f9', franchiseCode: 'RBM-9', applications: [open] });
    dialog.saving = true;
    dialog.add();
    dialog.cancel();
    expect(api.adminAddAgent).not.toHaveBeenCalled();
    expect(ref.close).not.toHaveBeenCalled();

    dialog.saving = false;
    dialog.cancel();
    expect(ref.close).toHaveBeenCalledWith(false);
  });
});
