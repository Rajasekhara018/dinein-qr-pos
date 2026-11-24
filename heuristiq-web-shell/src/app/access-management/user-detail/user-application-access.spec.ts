import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApplicationAccessService } from '../../application-access/application-access.service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { UamService } from '../../shared/services/uam-service';
import { UserApplicationAccess } from './user-application-access';

const grantRow = { id: 'g1', applicationCode: 'RIDES', subjectType: 'USER', subjectId: 'u1', active: true, note: null, grantedBy: null, grantedAt: null };

const access = { listForUser: vi.fn(), grant: vi.fn(), revoke: vi.fn() };
const uam = { get: vi.fn() };
const orgs = { currentTenantId: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
const dialogs = { confirm: vi.fn() };
const privileges = { changes$: EMPTY, can: () => true };

function create(): { fixture: ComponentFixture<UserApplicationAccess>; component: UserApplicationAccess } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    declarations: [UserApplicationAccess],
    imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
    providers: [
      { provide: ApplicationAccessService, useValue: access },
      { provide: UamService, useValue: uam },
      { provide: OrganizationContextService, useValue: orgs },
      { provide: AppDialogService, useValue: dialogs },
      { provide: NotificationService, useValue: notify },
      { provide: HqPrivilegeAccessService, useValue: privileges },
    ],
  });
  const fixture = TestBed.createComponent(UserApplicationAccess);
  fixture.componentRef.setInput('userId', 'u1');
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

describe('UserApplicationAccess', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    orgs.currentTenantId.mockReturnValue('t1');
    access.listForUser.mockReturnValue(of({ state: 'ready', grants: [grantRow, { ...grantRow, id: 'g2', applicationCode: 'OLD', active: false }] }));
    uam.get.mockReturnValue(of([
      { applicationName: 'Rides', subscription: { applicationCode: 'RIDES', status: 'ACTIVE' } },
      { applicationName: 'Matrimony', subscription: { applicationCode: 'MATRI', status: 'ACTIVE' } },
      { subscription: { applicationCode: 'GONE', status: 'SUSPENDED' } },
    ]));
  });

  it('lists the active direct grants and offers only active organization applications the person lacks', () => {
    const { component, fixture } = create();

    expect(access.listForUser).toHaveBeenCalledWith('u1');
    expect(component.grants.map((g) => g.applicationCode)).toEqual(['RIDES']);
    expect(component.choices).toEqual([{ code: 'MATRI', name: 'Matrimony' }]);
    expect(fixture.nativeElement.textContent).toContain('Rides');
  });

  it('shows that the grants are unavailable when they cannot be read', () => {
    access.listForUser.mockReturnValue(of({ state: 'forbidden', grants: [] }));
    expect(create().component.unavailable).toBe(true);
  });

  it('works without an organization in the token', () => {
    orgs.currentTenantId.mockReturnValue(null);
    expect(create().component.applications).toEqual([]);
    expect(uam.get).not.toHaveBeenCalled();
  });

  it('grants the chosen application directly and reloads', () => {
    access.grant.mockReturnValue(of([{ subjectId: 'u1', ok: true, status: 201 }]));
    const { component } = create();
    component.grant();
    expect(access.grant).not.toHaveBeenCalled();

    component.chosen = 'MATRI';
    component.grant();

    expect(access.grant).toHaveBeenCalledWith('MATRI', 'USER', ['u1'], '');
    expect(notify.success).toHaveBeenCalledWith('accessManagement.userDetail.applications.granted');
    expect(component.chosen).toBe('');
    expect(access.listForUser).toHaveBeenCalledTimes(2);
  });

  it('says why a grant was refused and keeps the choice', () => {
    access.grant.mockReturnValue(of([{ subjectId: 'u1', ok: false, status: 409 }]));
    const { component } = create();
    component.chosen = 'MATRI';

    component.grant();

    expect(notify.error).toHaveBeenCalledWith('applicationAccess.errors.duplicate');
    expect(component.chosen).toBe('MATRI');
    expect(component.saving).toBe(false);
  });

  it('withdraws a grant only after confirmation and reports a failure', () => {
    access.revoke.mockReturnValueOnce(of(undefined));
    access.revoke.mockReturnValueOnce(throwError(() => ({ status: 403 })));
    const { component } = create();

    dialogs.confirm.mockReturnValue(of(false));
    component.confirmRevoke(grantRow as never);
    expect(access.revoke).not.toHaveBeenCalled();

    dialogs.confirm.mockReturnValue(of(true));
    component.confirmRevoke(grantRow as never);
    expect(access.revoke).toHaveBeenCalledWith('g1');
    expect(notify.success).toHaveBeenCalledWith('applicationAccess.revoke.done');
    component.confirmRevoke(grantRow as never);
    expect(notify.error).toHaveBeenCalledWith('applicationAccess.errors.forbidden');
  });

  it('names an application by its code when it is not one of the organization ones', () => {
    expect(create().component.nameOf('UNKNOWN')).toBe('UNKNOWN');
  });
});
