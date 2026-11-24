import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, RouterModule, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqApplicationsService, HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { FranchiseMembers } from './franchise.models';
import { FranchiseAddAgentDialog } from './franchise-add-agent-dialog';
import { FranchiseMembersScreen } from './franchise-members';
import { ADD_AGENT_DIALOG_WIDTH, FRANCHISE_MEMBERS_PERMISSION, FRANCHISE_MEMBERS_SCREEN_KEY } from './franchise-members.constants';
import { dayOf, driversInWindow, driverWindow, memberErrorKey } from './franchise-members.helpers';

const result: FranchiseMembers = {
  franchiseId: 'f1',
  totalAgentLimit: 25,
  totalAgentCount: 3,
  applications: [{ applicationCode: 'MATRIMONY', agentLimit: 20, agentCount: 2, fee: { code: 'INR', value: 1000 } }],
  members: {
    items: [{
      memberId: 'm1', userId: 'u1', fullName: 'Asha', email: 'asha@example.com', phone: null, applicationCode: 'MATRIMONY', memberType: 'AGENT',
      status: 'ACTIVE', roleCode: null, onboardedAt: '2026-10-31T10:00:00Z',
    }],
    page: 0, size: 20, totalElements: 1,
  },
};

const api = { mine: vi.fn(), members: vi.fn(), adminMembers: vi.fn(), byId: vi.fn(), removeAgent: vi.fn(), adminRemoveAgent: vi.fn() };
const dialogs = { confirm: vi.fn() };
let dialogResult: boolean | undefined = true;
const matDialog = { open: vi.fn(() => ({ afterClosed: () => of(dialogResult) })) };
let routeData: Record<string, unknown> = {};
let routeId: string | null = null;
const notify = { success: vi.fn(), error: vi.fn() };
const applicationNames: Record<string, string> = { MATRIMONY: 'Matrimony' };
const applications = { load: () => of([]), nameOf: (code: string) => applicationNames[code] ?? code };
let zone = 'Asia/Kolkata';
const timezone = { effectiveTimezone: () => zone };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

function create() {
  const fixture = TestBed.createComponent(FranchiseMembersScreen);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('FranchiseMembersScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    zone = 'Asia/Kolkata';
    routeData = {};
    routeId = null;
    dialogResult = true;
    api.adminMembers.mockReturnValue(of(result));
    api.byId.mockReturnValue(of({ unitId: 'f9', code: 'RBM-9' }));
    api.mine.mockReturnValue(of([{ unitId: 'f1', code: 'RBM-1' }]));
    api.members.mockReturnValue(of(result));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [FranchiseMembersScreen],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      imports: [FormsModule, RouterModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        provideRouter([]),
        { provide: FranchiseApi, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: HqApplicationsService, useValue: applications },
        { provide: HqTimezoneService, useValue: timezone },
        { provide: AppDialogService, useValue: dialogs },
        { provide: MatDialog, useValue: matDialog },
        { provide: ActivatedRoute, useValue: { snapshot: { get data() { return routeData; }, paramMap: { get: () => routeId } } } },
      ],
    });
  });

  it('shows the members tree on its own route, not one franchise', () => {
    const { component, el } = create();

    expect(component.adminMode).toBe(false);
    expect(el.querySelector('app-franchise-member-tree')).not.toBeNull();
    expect(api.adminMembers).not.toHaveBeenCalled();
    expect(api.members).not.toHaveBeenCalled();
  });

  it('shows "N of M" places, the application by name and the onboarding day in the reader\'s time zone', () => {
    routeData = { admin: true };
    routeId = 'f9';
    const late = { ...result, members: { ...result.members, items: [{ ...result.members.items[0], onboardedAt: '2026-11-06T20:00:00Z' }] } };
    api.adminMembers.mockReturnValue(of(late));

    const { component, el } = create();

    expect(el.querySelector('[data-testid="total-places"]')?.textContent).toContain('accessManagement.franchiseMembers.usage');
    expect(el.textContent).toContain('Asha');
    expect(el.textContent).toContain('Matrimony');
    expect(el.textContent).toContain('1,000.00');
    // 20:00 UTC on 6 November is already 7 November in India
    expect(component.day('2026-11-06T20:00:00Z')).toBe('2026-11-07');
    expect(el.textContent).toContain('2026-11-07');
  });

  it('narrows to one application and pages', () => {
    routeData = { admin: true };
    routeId = 'f9';
    api.adminMembers.mockReturnValue(of({ ...result, members: { ...result.members, totalElements: 45 } }));
    const { component } = create();

    expect(component.isLast).toBe(false);
    expect(component.isFirstPage).toBe(true);
    component.chooseApplication('MATRIMONY');
    expect(api.adminMembers).toHaveBeenLastCalledWith('f9', 'MATRIMONY', 0, 20);
    component.load(1);
    expect(api.adminMembers).toHaveBeenLastCalledWith('f9', 'MATRIMONY', 1, 20);
    expect(component.isFirstPage).toBe(false);
    component.load(-4);
    expect(component.page).toBe(0);
    component.load(2);
    expect(component.isLast).toBe(true);
    expect(component.total).toBe(45);
  });

  it('says why the list could not be shown: not allowed, unknown franchise, service down, anything else', () => {
    routeData = { admin: true };
    routeId = 'f9';
    const { component } = create();
    const cases: [number | undefined, string][] = [
      [403, 'accessManagement.franchiseMembers.errors.forbidden'],
      [404, 'accessManagement.franchiseMembers.errors.notFound'],
      [503, 'accessManagement.franchiseMembers.errors.unavailable'],
      [500, 'accessManagement.franchiseMembers.errors.generic'],
    ];
    for (const [status, key] of cases) {
      api.adminMembers.mockReturnValueOnce(throwError(() => ({ status })));
      component.load();
      expect(component.errorMessage).toBe(key);
      expect(component.result).toBeNull();
      expect(notify.error).toHaveBeenLastCalledWith(key);
    }
  });

  it('shows labels for known types and statuses and the raw code otherwise', () => {
    const { component } = create();

    expect(component.typeLabel('AGENT')).toBe('accessManagement.franchiseMembers.types.AGENT');
    expect(component.typeLabel('BROKER')).toBe('BROKER');
    expect(component.statusLabel('ACTIVE')).toBe('accessManagement.franchiseMembers.statuses.ACTIVE');
    expect(component.statusLabel('PENDING')).toBe('PENDING');
  });

  it('has the permission key and the audit key of the contract', () => {
    expect(FRANCHISE_MEMBERS_PERMISSION).toBe('view_franchise_members');
    expect(FRANCHISE_MEMBERS_SCREEN_KEY).toBe('franchise.members');
  });

  it('goes to a page and changes the page size from the footer', () => {
    routeData = { admin: true };
    routeId = 'f9';
    const { component } = create();

    component.goToPage(1);
    expect(api.adminMembers).toHaveBeenLastCalledWith('f9', '', 1, 20);

    component.onPageSizeChange(50);
    expect(component.pageSize).toBe(50);
    expect(api.adminMembers).toHaveBeenLastCalledWith('f9', '', 0, 50);
  });


  it('reads any franchise\'s members for an administrator, without the owner\'s own franchises', () => {
    routeData = { admin: true };
    routeId = 'f9';

    const { component, el } = create();

    expect(component.adminMode).toBe(true);
    expect(api.mine).not.toHaveBeenCalled();
    expect(api.members).not.toHaveBeenCalled();
    expect(api.adminMembers).toHaveBeenCalledWith('f9', '', 0, 20);
    expect(component.adminFranchiseCode).toBe('RBM-9');
    expect(el.textContent).toContain('Asha');
    expect(el.textContent).not.toContain('accessManagement.franchiseMembers.noFranchises');
  });

  it('lets an administrator with the privileges add and remove agents, and shows neither without them', () => {
    routeData = { admin: true };
    routeId = 'f9';
    granted = new Set(['FRANCHISE_ADMIN_MEMBER_LIST', 'FRANCHISE_READ', 'FRANCHISE_ADMIN_AGENT_CREATE', 'FRANCHISE_ADMIN_AGENT_DELETE']);
    const allowed = create();
    expect(allowed.el.querySelector('[data-testid="admin-add-agent"]')).not.toBeNull();
    expect(allowed.el.querySelector('[data-testid="remove-agent"]')).not.toBeNull();
    expect(allowed.component.removePrivilege).toBe('FRANCHISE_ADMIN_AGENT_DELETE');

    granted = new Set(['FRANCHISE_ADMIN_MEMBER_LIST', 'FRANCHISE_READ', 'FRANCHISE_AGENT_CREATE', 'FRANCHISE_AGENT_DELETE']);
    const viewOnly = create();
    expect(viewOnly.el.querySelector('[data-testid="admin-add-agent"]')).toBeNull();
    expect(viewOnly.el.querySelector('[data-testid="remove-agent"]')).toBeNull();
  });

  it('keeps the owner\'s remove privilege and never opens the add dialog outside the administrator\'s page', () => {
    const { component } = create();

    expect(component.adminMode).toBe(false);
    expect(component.removePrivilege).toBe('FRANCHISE_AGENT_DELETE');
    component.openAddAgent();
    expect(matDialog.open).not.toHaveBeenCalled();
  });


  it('opens the add-agent dialog with the franchise and its places, and reads the page again once an agent was added', () => {
    routeData = { admin: true };
    routeId = 'f9';
    const { el } = create();
    api.adminMembers.mockClear();

    (el.querySelector('[data-testid="admin-add-agent"]') as HTMLButtonElement).click();

    expect(matDialog.open).toHaveBeenCalledWith(FranchiseAddAgentDialog, expect.objectContaining({
      data: { franchiseId: 'f9', franchiseCode: 'RBM-9', applications: result.applications },
      width: ADD_AGENT_DIALOG_WIDTH,
    }));
    expect(api.adminMembers).toHaveBeenCalledWith('f9', '', 0, 20);
  });

  it('does not read the page again when the dialog is closed without adding', () => {
    routeData = { admin: true };
    routeId = 'f9';
    dialogResult = false;
    const { component } = create();
    api.adminMembers.mockClear();

    component.openAddAgent();

    expect(matDialog.open).toHaveBeenCalled();
    expect(api.adminMembers).not.toHaveBeenCalled();
  });

  it('removes an agent through the administrator\'s call in the administrator\'s view', () => {
    routeData = { admin: true };
    routeId = 'f9';
    dialogs.confirm.mockReturnValue(of(true));
    api.adminRemoveAgent.mockReturnValue(of(null));
    const { el } = create();
    api.adminMembers.mockClear();

    (el.querySelector('[data-testid="remove-agent"]') as HTMLButtonElement).click();

    expect(api.adminRemoveAgent).toHaveBeenCalledWith('f9', 'm1');
    expect(api.removeAgent).not.toHaveBeenCalled();
    expect(api.adminMembers).toHaveBeenCalledWith('f9', '', 0, 20);
  });

  it('does not ask for the franchise\'s details or members without the privileges', () => {
    routeData = { admin: true };
    routeId = 'f9';
    granted = new Set(['FRANCHISES_NONE']);

    const { component } = create();

    expect(api.byId).not.toHaveBeenCalled();
    expect(api.adminMembers).not.toHaveBeenCalled();
    expect(component.result).toBeNull();
  });

  it('shows an administrator the error of a franchise that cannot be read', () => {
    routeData = { admin: true };
    routeId = 'f9';
    api.adminMembers.mockReturnValue(throwError(() => ({ status: 404 })));

    const { component } = create();

    expect(component.errorMessage).toBe('accessManagement.franchiseMembers.errors.notFound');
  });

  it('offers no remove action for the owner row or a removed agent', () => {
    routeData = { admin: true };
    routeId = 'f9';
    const owner = { ...result, members: { ...result.members, items: [{ ...result.members.items[0], memberType: 'OWNER' }] } };
    api.adminMembers.mockReturnValue(of(owner));
    const { el, component } = create();

    expect(el.querySelector('[data-testid="remove-agent"]')).toBeNull();
    expect(component.canRemove(owner.members.items[0])).toBe(false);
    expect(component.canRemove({ ...result.members.items[0], status: 'REMOVED' })).toBe(false);
    expect(component.canRemove(result.members.items[0])).toBe(true);
  });
});


describe('franchise members helpers', () => {
  it('maps a status to a message key and falls back for others', () => {
    expect(memberErrorKey(404)).toBe('accessManagement.franchiseMembers.errors.notFound');
    expect(memberErrorKey(undefined)).toBe('accessManagement.franchiseMembers.errors.generic');
  });

  it('pages over the agents then the drivers as one list', () => {
    // 25 agents, page size 10: page 0 and 1 are agents only (the first driver page is read for the count)
    expect(driverWindow(0, 10, 25)).toEqual({ offset: 0, count: 0, pages: [0] });
    // page 2: agents 20-24, then drivers 0-4
    expect(driverWindow(2, 10, 25)).toEqual({ offset: 0, count: 5, pages: [0] });
    // page 3: drivers 5-14, across driver pages 0 and 1
    const across = driverWindow(3, 10, 25);
    expect(across).toEqual({ offset: 5, count: 10, pages: [0, 1] });
    const page0 = Array.from({ length: 10 }, (_, i) => i);
    const page1 = Array.from({ length: 10 }, (_, i) => i + 10);
    expect(driversInWindow(across, 10, [page0, page1])).toEqual([5, 6, 7, 8, 9, 10, 11, 12, 13, 14]);
    // no agents: drivers only, aligned to the driver pages
    expect(driverWindow(1, 10, 0)).toEqual({ offset: 10, count: 10, pages: [1] });
    expect(driversInWindow(driverWindow(1, 10, 0), 10, [page1])).toEqual(page1);
    expect(driversInWindow(driverWindow(0, 10, 25), 10, [page0])).toEqual([]);
  });

  it('cuts the day (yyyy-MM-dd) from an instant', () => {
    expect(dayOf('2026-10-31T10:00:00Z')).toBe('2026-10-31');
    expect(dayOf('2026-11-06T20:00:00Z', 'Asia/Kolkata')).toBe('2026-11-07');
    expect(dayOf('2026-11-06T20:00:00Z', 'UTC')).toBe('2026-11-06');
    expect(dayOf('2026-11-06T20:00:00Z', 'Not/AZone')).toBe('2026-11-06');
    expect(dayOf(null)).toBe('');
    expect(dayOf(undefined)).toBe('');
  });

});
