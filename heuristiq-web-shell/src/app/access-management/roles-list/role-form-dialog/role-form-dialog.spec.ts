import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../../shared/services/app-dialog-service';
import { NotificationService } from '../../../shared/services/notification-service';
import { UamService } from '../../../shared/services/uam-service';
import { OrganizationContextService } from '../../../shared/services/organization-context-service';
import { Permission, UamRole } from '../../../shared/models/uam.models';
import { forgetPermissionCatalogue } from './permission-catalogue.cache';
import { RoleFormDialog, RoleFormDialogData } from './role-form-dialog';

const perm = (code: string, module: string | null = null, resource: string | null = null, displayName: string | null = null): Permission => ({
  id: code, code, applicationCode: null, module: module as never, resource: resource as never, action: null, displayName, description: null,
  active: true, systemPermission: true, attributes: null, audit: null,
});

const PERMISSIONS = [
  perm('USER_LIST', 'ADMINISTRATION', 'MASTER', 'List users'),
  perm('USER_CREATE', 'ADMINISTRATION', 'MASTER'),
  perm('REPORT_READ', 'REPORTING', 'REPORT', 'Read reports'),
];

describe('RoleFormDialog', () => {
  const uam = { getById: vi.fn(), list: vi.fn(), create: vi.fn(), update: vi.fn() };
  const router = { navigate: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const dialogs = { confirm: vi.fn() };
  const dialogRef = { close: vi.fn() };
  const organizationContext = { canSwitch: false };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  const refresh = (fixture: ComponentFixture<RoleFormDialog>) => fixture.debugElement.injector.get(ChangeDetectorRef).detectChanges();

  function create(opts: { id?: string; dialog?: RoleFormDialogData } = {}): ComponentFixture<RoleFormDialog> {
    TestBed.resetTestingModule();
    const providers: unknown[] = [
      { provide: UamService, useValue: uam },
      { provide: Router, useValue: router },
      { provide: NotificationService, useValue: notify },
      { provide: AppDialogService, useValue: dialogs },
      { provide: HqPrivilegeAccessService, useValue: access },
      { provide: OrganizationContextService, useValue: organizationContext },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(opts.id ? { id: opts.id } : {}) } } },
    ];
    if (opts.dialog) {
      providers.push({ provide: MAT_DIALOG_DATA, useValue: opts.dialog }, { provide: MatDialogRef, useValue: dialogRef });
    }
    TestBed.configureTestingModule({
      declarations: [RoleFormDialog],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: providers as never[],
    });
    const fixture = TestBed.createComponent(RoleFormDialog);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    forgetPermissionCatalogue();
    granted = null;
    organizationContext.canSwitch = false;
    uam.list.mockReturnValue(of({ content: PERMISSIONS, totalPages: 1, last: true }));
    uam.getById.mockReturnValue(of({ id: 'r1', code: 'EDITOR', name: 'Editor', description: 'Edits', systemRole: false, permissions: ['USER_LIST'] } as UamRole));
    uam.create.mockReturnValue(of({}));
    uam.update.mockReturnValue(of({}));
    dialogs.confirm.mockReturnValue(of(true));
  });

  describe('page mode: creating', () => {
    it('reads the permission catalogue once and reuses it for the next editor', () => {
      create();
      expect(uam.list).toHaveBeenCalledTimes(1);

      const second = create().componentInstance;

      expect(uam.list).toHaveBeenCalledTimes(1);
      expect(second.allPermissions.length).toBe(3);
      expect(second.isLoadingPermissions).toBe(false);
    });

    it('starts empty and loads the permission catalogue', () => {
      const component = create().componentInstance;

      expect(component.isPageMode).toBe(true);
      expect(component.data.mode).toBe('create');
      expect(component.title).toBe('accessManagement.roles.form.createNewRole');
      expect(uam.list).toHaveBeenCalledWith('/permissions', 0, 200);
      expect(component.allPermissions.length).toBe(3);
      expect(component.isLoadingPermissions).toBe(false);
      expect(component.canSubmit).toBe(false);
      expect(component.hasChanges).toBe(false);
    });

    it('fetches every page of the catalogue and merges them', () => {
      uam.list.mockImplementation((_p: string, page: number) => of({ content: [perm(`P${page}`)], totalPages: 3, last: page === 2 }));

      const component = create().componentInstance;

      expect(uam.list).toHaveBeenCalledTimes(3);
      expect(component.allPermissions.map((p) => p.code)).toEqual(['P0', 'P1', 'P2']);
    });

    it('stops fetching when UAM says the last page has been reached', () => {
      uam.list.mockReturnValue(of({ content: PERMISSIONS, totalPages: 5, last: true }));

      create();

      expect(uam.list).toHaveBeenCalledTimes(1);
    });

    it('stops at the safety limit for a runaway page count', () => {
      uam.list.mockImplementation(() => of({ content: [], totalPages: 1000, last: false }));

      create();

      expect(uam.list).toHaveBeenCalledTimes(50);
    });

    it('keeps the pages that did arrive when a later page fails', () => {
      uam.list.mockImplementation((_p: string, page: number) => (page === 0
        ? of({ content: PERMISSIONS, totalPages: 2, last: false })
        : throwError(() => new Error('x'))));

      const component = create().componentInstance;

      expect(component.allPermissions.length).toBe(3);
      expect(component.isLoadingPermissions).toBe(false);
    });

    it('treats a missing content or page count as an empty single page', () => {
      uam.list.mockReturnValue(of({}));

      expect(create().componentInstance.allPermissions).toEqual([]);
    });

    it('creates the role with the trimmed fields and the chosen permissions, then returns to the list', () => {
      const component = create().componentInstance;
      component.code = '  AUDITOR ';
      component.name = ' ';
      component.description = ' Reads ';
      component.toggle(PERMISSIONS[0]);

      expect(component.hasChanges).toBe(true);
      component.submit();

      expect(uam.create).toHaveBeenCalledWith('/roles', {
        code: 'AUDITOR', name: null, description: 'Reads', permissions: ['USER_LIST'], systemRole: false,
      });
      expect(notify.success).toHaveBeenCalledWith('accessManagement.roles.messages.created');
      expect(router.navigate).toHaveBeenCalledWith(['..'], expect.objectContaining({ relativeTo: expect.anything() }));
      expect(component.hasChanges).toBe(false);
    });

    describe('the role code rule', () => {
      const codeErrorOf = (code: string) => {
        const component = create().componentInstance;
        component.code = code;
        return { error: component.codeError, canSubmit: component.canSubmit };
      };

      it.each(['ABC', 'SUPPORT_AGENT', 'A1_', `A${'B'.repeat(49)}`])('accepts %s', (code) => {
        expect(codeErrorOf(code)).toEqual({ error: null, canSubmit: true });
      });

      it.each(['qa role', 'AB', '1ABC', '_ABC', 'abc', 'ABC-DEF', 'AB C', `A${'B'.repeat(50)}`])('rejects %s and blocks saving', (code) => {
        expect(codeErrorOf(code)).toEqual({ error: 'accessManagement.roles.errors.code', canSubmit: false });
      });

      it('shows no error while the code is empty, and still cannot save', () => {
        expect(codeErrorOf('  ')).toEqual({ error: null, canSubmit: false });
      });

      it('does not send an invalid code', () => {
        const component = create().componentInstance;
        component.code = 'qa role';

        component.submit();

        expect(uam.create).not.toHaveBeenCalled();
      });
    });

    describe('the system role control', () => {
      it('is hidden from a caller who is not organization-wide', () => {
        organizationContext.canSwitch = false;
        const fixture = create();
        refresh(fixture);

        expect(fixture.componentInstance.canSetSystemRole).toBe(false);
        expect(fixture.nativeElement.textContent).not.toContain('accessManagement.roles.form.systemRoleCheckbox');
      });

      it('is offered to an organization-wide caller', () => {
        organizationContext.canSwitch = true;
        const fixture = create();
        refresh(fixture);

        expect(fixture.componentInstance.canSetSystemRole).toBe(true);
        expect(fixture.nativeElement.textContent).toContain('accessManagement.roles.form.systemRoleCheckbox');
      });
    });

    it('does not submit without a code', () => {
      const component = create().componentInstance;

      component.submit();

      expect(uam.create).not.toHaveBeenCalled();
    });

    it('says under the code that it is taken when the server answers 409, until the code is changed', () => {
      const component = create().componentInstance;
      component.code = 'ABC';
      uam.create.mockReturnValueOnce(throwError(() => ({ status: 409, error: { detail: 'Role already exists' } })));

      component.submit();

      expect(component.codeError).toBe('accessManagement.roles.form.codeTaken');
      expect(component.canSubmit).toBe(false);
      component.code = 'ABD';
      expect(component.codeError).toBeNull();
      expect(component.canSubmit).toBe(true);
    });

    it('shows the server detail, or a default message, and stays when saving fails', () => {
      const component = create().componentInstance;
      component.code = 'ABC';

      uam.create.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Code exists' } })));
      component.submit();
      expect(notify.error).toHaveBeenLastCalledWith('Code exists');

      uam.create.mockReturnValueOnce(throwError(() => new Error('x')));
      component.submit();
      expect(notify.error).toHaveBeenLastCalledWith('shared.adminCrud.messages.saveFailed');
      expect(component.isSaving).toBe(false);
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('page mode: editing', () => {
    it('loads the role and fills the form without marking it changed', () => {
      const component = create({ id: 'r1' }).componentInstance;

      expect(uam.getById).toHaveBeenCalledWith('/roles', 'r1');
      expect(component.data.mode).toBe('edit');
      expect(component.title).toBe('accessManagement.roles.form.updateRole');
      expect([component.code, component.name, component.description]).toEqual(['EDITOR', 'Editor', 'Edits']);
      expect(component.isSelected(PERMISSIONS[0])).toBe(true);
      expect(component.isLoadingRole).toBe(false);
      expect(component.hasChanges).toBe(false);
    });

    it('tolerates a role without optional fields', () => {
      uam.getById.mockReturnValue(of({ id: 'r1' }));

      const component = create({ id: 'r1' }).componentInstance;

      expect([component.code, component.name, component.description, component.systemRole]).toEqual(['', '', '', false]);
      expect(component.selectedCodes.size).toBe(0);
    });

    it('reports a role that cannot be loaded', () => {
      uam.getById.mockReturnValue(throwError(() => ({ error: { detail: 'Role r1 was deleted by another admin' } })));
      create({ id: 'r1' });
      expect(notify.error).toHaveBeenLastCalledWith('Role r1 was deleted by another admin');

      // A bare generic "Not found" says nothing useful, so the translated fallback is shown instead.
      uam.getById.mockReturnValue(throwError(() => ({ error: { detail: 'Not found' } })));
      create({ id: 'r1' });
      expect(notify.error).toHaveBeenLastCalledWith('accessManagement.roles.messages.loadFailed');

      uam.getById.mockReturnValue(throwError(() => new Error('x')));
      create({ id: 'r1' });
      expect(notify.error).toHaveBeenLastCalledWith('accessManagement.roles.messages.loadFailed');
    });

    it('updates the role and goes two levels up', () => {
      const component = create({ id: 'r1' }).componentInstance;
      component.name = 'Renamed';

      component.submit();

      expect(uam.update).toHaveBeenCalledWith('/roles', 'r1', expect.objectContaining({ name: 'Renamed', permissions: ['USER_LIST'] }));
      expect(notify.success).toHaveBeenCalledWith('accessManagement.roles.messages.updated');
      expect(router.navigate).toHaveBeenCalledWith(['../..'], expect.anything());
    });
  });

  describe('leaving', () => {
    it('cancel on an untouched form goes straight back', () => {
      create().componentInstance.cancel();

      expect(dialogs.confirm).not.toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['..'], expect.anything());
    });

    it('cancel on a changed form asks first and only leaves when confirmed', () => {
      const component = create().componentInstance;
      component.code = 'TYPED';

      dialogs.confirm.mockReturnValueOnce(of(false));
      component.cancel();
      expect(router.navigate).not.toHaveBeenCalled();

      component.cancel();
      expect(router.navigate).toHaveBeenCalledTimes(1);
      // Confirmed once: the route guard must not ask a second time.
      expect(component.hasPendingChanges()).toBe(false);
    });

    it('the discard prompt is a warning with translated texts', () => {
      create().componentInstance.confirmDiscardChanges().subscribe();

      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'warning', title: 'accessManagement.roles.form.discardDialog.title' }));
    });

    it('warns the browser before unloading a changed page, and not otherwise', () => {
      const component = create().componentInstance;
      const event = { preventDefault: vi.fn(), returnValue: 'unset' } as unknown as BeforeUnloadEvent;

      component.beforeUnload(event);
      expect(event.preventDefault).not.toHaveBeenCalled();

      component.code = 'X';
      component.beforeUnload(event);
      expect(event.preventDefault).toHaveBeenCalled();
      expect(event.returnValue).toBe('');
    });
  });

  describe('permission table', () => {
    it('filters by module, resource and text, and resets', () => {
      const component = create().componentInstance;

      component.moduleDraft = 'ADMINISTRATION';
      component.searchPermissions();
      expect(component.filteredPermissions.map((p) => p.code)).toEqual(['USER_LIST', 'USER_CREATE']);

      component.resourceDraft = 'REPORT';
      component.searchPermissions();
      expect(component.filteredPermissions).toEqual([]);

      component.resetPermissionFilter();
      expect(component.filteredPermissions.length).toBe(3);

      component.searchDraft = ' READ REPORTS ';
      component.searchPermissions();
      expect(component.filteredPermissions.map((p) => p.code)).toEqual(['REPORT_READ']);
      component.searchDraft = 'user_c';
      component.searchPermissions();
      expect(component.filteredPermissions.map((p) => p.code)).toEqual(['USER_CREATE']);
    });

    it('pages client-side with a window of page numbers', () => {
      uam.list.mockReturnValue(of({ content: Array.from({ length: 95 }, (_, i) => perm(`P${i}`)), totalPages: 1, last: true }));
      const component = create().componentInstance;

      expect(component.permTotalPages).toBe(10);
      expect(component.pagedPermissions.length).toBe(10);
      expect([component.permRangeStart, component.permRangeEnd]).toEqual([1, 10]);
      expect(component.permPageNumbers).toEqual([1, 2, 3]);

      component.goToPermPage(5);
      expect(component.permPageNumbers).toEqual([4, 5, 6, 7, 8]);
      component.goToPermPage(99);
      expect(component.permPage).toBe(9);
      expect([component.permRangeStart, component.permRangeEnd]).toEqual([91, 95]);
      component.goToPermPage(-4);
      expect(component.permPage).toBe(0);

      component.onPermPageSizeChange(50);
      expect([component.permPageSize, component.permPage, component.permTotalPages]).toEqual([50, 0, 2]);
    });

    it('reports an empty range for an empty list', () => {
      uam.list.mockReturnValue(of({ content: [], totalPages: 1, last: true }));
      const component = create().componentInstance;

      expect([component.permRangeStart, component.permRangeEnd, component.permTotalPages]).toEqual([0, 0, 1]);
      expect(component.isAllPageSelected).toBe(false);
      expect(component.isAllFilteredSelected).toBe(false);
    });

    it('selects and clears the whole current page', () => {
      const component = create().componentInstance;

      component.toggleAllOnPage();
      expect(component.isAllPageSelected).toBe(true);
      expect(component.selectedCodes.size).toBe(3);
      component.toggleAllOnPage();
      expect(component.selectedCodes.size).toBe(0);
    });

    it('selects and clears everything that matches the filter, across pages', () => {
      uam.list.mockReturnValue(of({ content: Array.from({ length: 25 }, (_, i) => perm(`P${i}`)), totalPages: 1, last: true }));
      const component = create().componentInstance;

      component.toggleAllFiltered();
      expect(component.isAllFilteredSelected).toBe(true);
      expect(component.selectedCodes.size).toBe(25);
      expect(component.isAllPageSelected).toBe(true);
      component.toggleAllFiltered();
      expect(component.selectedCodes.size).toBe(0);
    });

    it('toggles single permissions', () => {
      const component = create().componentInstance;

      component.toggle(PERMISSIONS[1]);
      expect(component.isSelected(PERMISSIONS[1])).toBe(true);
      component.toggle(PERMISSIONS[1]);
      expect(component.isSelected(PERMISSIONS[1])).toBe(false);
    });

    it('takes the new selection from the simple picker', () => {
      const component = create().componentInstance;

      component.onAccessChange(new Set(['REPORT_READ']));

      expect([...component.selectedCodes]).toEqual(['REPORT_READ']);
      expect(component.hasChanges).toBe(true);
    });

    it('renders the advanced table with its rows and the selected count', () => {
      const fixture = create();
      fixture.componentInstance.showAdvanced = true;
      fixture.componentInstance.toggle(PERMISSIONS[0]);
      refresh(fixture);
      const el: HTMLElement = fixture.nativeElement;

      expect(el.querySelectorAll('tbody tr').length).toBe(3);
      expect(el.textContent).toContain('List users');
      expect(el.querySelector('.perm-count')?.textContent).toContain('accessManagement.roles.form.permissionsSelected');

      (el.querySelectorAll('tbody input[type="checkbox"]')[1] as HTMLElement).click();
      expect(fixture.componentInstance.isSelected(PERMISSIONS[1])).toBe(true);
      (el.querySelector('thead input[type="checkbox"]') as HTMLElement).click();
      expect(fixture.componentInstance.isAllPageSelected).toBe(true);
    });
  });

  describe('dialog mode', () => {
    const editData: RoleFormDialogData = {
      mode: 'edit',
      value: { id: 'r1', code: 'OPS', name: 'Ops', description: 'd', systemRole: true, permissions: ['REPORT_READ'] } as UamRole,
    };

    it('starts from the supplied role and never reports page-level changes', () => {
      const component = create({ dialog: editData }).componentInstance;

      expect(component.isPageMode).toBe(false);
      expect(component.code).toBe('OPS');
      expect(component.systemRole).toBe(true);
      expect(uam.getById).not.toHaveBeenCalled();
      component.code = 'CHANGED';
      expect(component.hasChanges).toBe(false);
    });

    it('closes with the payload instead of calling UAM', () => {
      const component = create({ dialog: editData }).componentInstance;

      component.submit();

      expect(dialogRef.close).toHaveBeenCalledWith({
        code: 'OPS', name: 'Ops', description: 'd', permissions: ['REPORT_READ'], systemRole: true,
      });
      expect(uam.update).not.toHaveBeenCalled();
    });

    it('closes with null on cancel', () => {
      create({ dialog: { mode: 'create', value: null } }).componentInstance.cancel();

      expect(dialogRef.close).toHaveBeenCalledWith(null);
    });
  });

  describe('privilege-gated Save', () => {
    it('is hidden from someone who may not create a role', () => {
      granted = new Set(['ROLE_UPDATE']);

      expect(create().nativeElement.querySelector('.btn-primary')).toBeNull();
    });

    it('is shown to someone who may create, and to the roles manager', () => {
      granted = new Set(['ROLE_CREATE']);
      expect(create().nativeElement.querySelector('.btn-primary')).not.toBeNull();

      granted = new Set(['UAM_ROLES_MANAGE']);
      expect(create().nativeElement.querySelector('.btn-primary')).not.toBeNull();
    });

    it('needs the update privilege when editing', () => {
      granted = new Set(['ROLE_CREATE']);
      expect(create({ id: 'r1' }).nativeElement.querySelector('.btn-primary')).toBeNull();

      granted = new Set(['ROLE_UPDATE']);
      expect(create({ id: 'r1' }).nativeElement.querySelector('.btn-primary')).not.toBeNull();
    });

    it('applies to the dialog footer too', () => {
      granted = new Set();
      expect(create({ dialog: { mode: 'create', value: null } }).nativeElement.querySelector('.btn-accent')).toBeNull();

      granted = new Set(['ROLE_CREATE']);
      expect(create({ dialog: { mode: 'create', value: null } }).nativeElement.querySelector('.btn-accent')).not.toBeNull();
    });
  });
});
