import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { RouterModule, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqApplicationsService, HqChartsModule, HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseAddAgentDialog } from './franchise-add-agent-dialog';
import { FranchiseApi } from './franchise-api';
import { FranchiseMemberTree } from './franchise-member-tree';
import { applicationCodesOf, branchKey, groupByApplication, hasDriverApplication, withLiveDriverCounts } from './franchise-member-tree.helpers';
import { driverAsMember, placesUsed } from './franchise-application.helpers';
import { Franchise, FranchiseMembers } from './franchise.models';

function franchise(id: string, code: string, owner: string, apps: [string, number, number][]): Franchise {
  return {
    unitId: id, code, name: code, ownerUserId: 'u-' + id, ownerName: owner, status: 'ACTIVE', totalAgentLimit: 0, totalAgentCount: 0,
    applications: apps.map(([applicationCode, agentCount, agentLimit]) => ({ applicationCode, agentCount, agentLimit, fee: { code: 'INR', value: 1 } })),
  };
}

const franchises = [
  franchise('f2', 'RBM-2', 'Kiran', [['MATRIMONY', 1, 5]]),
  franchise('f1', 'RBM-1', 'Asha', [['MATRIMONY', 2, 2], ['RIDER', 0, 3]]),
];

function members(total = 1): FranchiseMembers {
  return {
    franchiseId: 'f1', totalAgentLimit: 2, totalAgentCount: 2, applications: [],
    members: {
      items: [{ memberId: 'm1', userId: 'u9', fullName: 'QA Agent One', email: 'qa1@example.com', phone: null, applicationCode: 'MATRIMONY',
        memberType: 'AGENT', status: 'ACTIVE', roleCode: null, onboardedAt: '2026-11-07T05:00:00Z' }],
      page: 0, size: 25, totalElements: total,
    },
  };
}

const api = {
  list: vi.fn(), mine: vi.fn(), livePlaces: vi.fn(), members: vi.fn(), adminMembers: vi.fn(), drivers: vi.fn(), adminDrivers: vi.fn(),
  removeAgent: vi.fn(), adminRemoveAgent: vi.fn(),
};
const ravi = { memberId: 'dm1', userId: '2a94fffe', name: 'ravi', phone: '+919000000001', email: null, onboardedAt: '2026-11-15T05:00:00Z' };
const drivers = (total = 1) => ({ items: [ravi], page: 0, size: 25, totalElements: total });
const dialogs = { confirm: vi.fn(() => of(true)) };
let dialogResult: boolean | undefined = true;
const matDialog = { open: vi.fn(() => ({ afterClosed: () => of(dialogResult) })) };
const names: Record<string, string> = { MATRIMONY: 'Pasupu Kumkuma Matrimony', RIDER: 'BestRide Rider' };
const applications = { load: () => of([]), nameOf: (code: string) => names[code] ?? code };
let granted: Set<string> | null = null;
const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)) };

function create() {
  const fixture = TestBed.createComponent(FranchiseMemberTree);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('FranchiseMemberTree', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    dialogResult = true;
    api.list.mockReturnValue(of(franchises));
    api.mine.mockReturnValue(of([franchises[1]]));
    api.adminMembers.mockReturnValue(of(members()));
    api.members.mockReturnValue(of(members()));
    api.livePlaces.mockReturnValue(of([]));
    api.drivers.mockReturnValue(of(drivers()));
    api.adminDrivers.mockReturnValue(of(drivers()));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [FranchiseMemberTree],
      imports: [FormsModule, RouterModule, TranslateModule.forRoot(), HqSharedLibModule, HqChartsModule],
      providers: [
        provideRouter([]),
        { provide: FranchiseApi, useValue: api },
        { provide: NotificationService, useValue: { success: vi.fn(), error: vi.fn() } },
        { provide: AppDialogService, useValue: dialogs },
        { provide: MatDialog, useValue: matDialog },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: HqApplicationsService, useValue: applications },
        { provide: HqTimezoneService, useValue: { effectiveTimezone: () => 'Asia/Kolkata' } },
      ],
    });
  });

  it('shows an administrator every application with all its franchises and their places', () => {
    const { component, el } = create();

    expect(component.orgWide).toBe(true);
    expect(component.isDriverApplication('BESTRIDE_RIDER_APP')).toBe(true);
    expect(component.isDriverApplication('PASUPU_KUMKUMA_MATRIMONY_APP')).toBe(false);
    expect(api.list).toHaveBeenCalled();
    expect(api.mine).not.toHaveBeenCalled();
    expect(el.querySelectorAll('[data-testid="tree-application"]')).toHaveLength(2);
    expect(component.tree[1].applicationCode).toBe('MATRIMONY');
    expect(component.tree[1].franchises.map((r) => r.franchise.code)).toEqual(['RBM-1', 'RBM-2']);
    expect(component.tree[1]).toMatchObject({ used: 3, limit: 7 });
    expect(el.textContent).toContain('Pasupu Kumkuma Matrimony');
    expect(el.textContent).toContain('Asha');
  });

  it("shows a franchise owner only their own franchises, with the owner's calls", () => {
    granted = new Set(['FRANCHISE_MINE_LIST', 'FRANCHISE_MEMBER_LIST', 'FRANCHISE_AGENT_DELETE']);
    const { component } = create();

    expect(component.orgWide).toBe(false);
    expect(api.list).not.toHaveBeenCalled();
    expect(api.mine).toHaveBeenCalled();
    component.toggleFranchise('MATRIMONY', component.tree[1].franchises[0]);
    expect(api.members).toHaveBeenCalledWith('f1', 'MATRIMONY', 0, 25);
    expect(api.adminMembers).not.toHaveBeenCalled();
    expect(component.removePrivilege).toBe('FRANCHISE_AGENT_DELETE');
  });

  it('reads the agents of a franchise when its branch opens, and closes it again', () => {
    const { component, fixture, el } = create();
    const matrimony = el.querySelectorAll('[data-testid="tree-application"]')[1];
    (matrimony.querySelector('[data-testid="tree-toggle"]') as HTMLButtonElement).click();
    fixture.componentRef.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    expect(api.adminMembers).toHaveBeenCalledWith('f1', 'MATRIMONY', 0, 25);
    expect(el.querySelectorAll('[data-testid="tree-member"]')).toHaveLength(1);
    expect(el.textContent).toContain('QA Agent One');
    expect(el.textContent).toContain('2026-11-07');

    component.toggleFranchise('MATRIMONY', component.tree[1].franchises[0]);
    expect(component.branch('MATRIMONY', 'f1')).toBeUndefined();
  });

  it('pages the agents with "show more" and reports a failed read', () => {
    api.adminMembers.mockReturnValue(of(members(30)));
    const { component } = create();
    const row = component.tree[1].franchises[0];
    component.toggleFranchise('MATRIMONY', row);
    const branch = component.branch('MATRIMONY', 'f1')!;
    expect(component.hasMore(branch)).toBe(true);

    component.showMore('MATRIMONY', 'f1');
    expect(api.adminMembers).toHaveBeenLastCalledWith('f1', 'MATRIMONY', 1, 25);
    expect(component.branch('MATRIMONY', 'f1')!.members).toHaveLength(2);

    api.adminMembers.mockReturnValueOnce(throwError(() => ({ status: 503 })));
    component.showMore('MATRIMONY', 'f1');
    expect(component.branch('MATRIMONY', 'f1')!.failed).toBe(true);
  });

  it('filters by application and by franchise code or owner', () => {
    const { component } = create();
    component.setApplication('RIDER');
    expect(component.tree.map((g) => g.applicationCode)).toEqual(['RIDER']);
    component.setApplication('');
    component.setQuery('kiran');
    expect(component.tree.map((g) => g.franchises.map((r) => r.franchise.code))).toEqual([['RBM-2']]);
    component.setQuery('nobody');
    expect(component.tree).toEqual([]);
  });

  it('folds and unfolds an application', () => {
    const { component } = create();
    component.toggleApplication('MATRIMONY');
    expect(component.isFolded('MATRIMONY')).toBe(true);
    component.toggleApplication('MATRIMONY');
    expect(component.isFolded('MATRIMONY')).toBe(false);
  });

  it("lets an administrator add an agent to a franchise and remove one with the administrator's call", () => {
    api.adminRemoveAgent.mockReturnValue(of(null));
    const { component } = create();
    const row = component.tree[1].franchises[0];

    component.addAgent(row);
    expect(matDialog.open).toHaveBeenCalledWith(FranchiseAddAgentDialog, expect.objectContaining({
      data: { franchiseId: 'f1', franchiseCode: 'RBM-1', applications: row.franchise.applications },
    }));
    expect(api.list).toHaveBeenCalledTimes(2);

    component.toggleFranchise('MATRIMONY', row);
    component.removeAgent('MATRIMONY', row, component.branch('MATRIMONY', 'f1')!.members[0]);
    expect(api.adminRemoveAgent).toHaveBeenCalledWith('f1', 'm1');
    expect(component.removePrivilege).toBe('FRANCHISE_ADMIN_AGENT_DELETE');
  });

  it('never opens the add dialog for an owner, and offers no remove for the owner row or a removed agent', () => {
    granted = new Set(['FRANCHISE_MINE_LIST', 'FRANCHISE_MEMBER_LIST']);
    const { component } = create();
    component.addAgent(component.tree[0].franchises[0]);
    expect(matDialog.open).not.toHaveBeenCalled();
    const agent = members().members.items[0];
    expect(component.canRemove(agent)).toBe(true);
    expect(component.canRemove({ ...agent, memberType: 'OWNER' })).toBe(false);
    expect(component.canRemove({ ...agent, status: 'REMOVED' })).toBe(false);
  });

  it('reads nothing without a privilege, and explains a failed list', () => {
    granted = new Set<string>();
    expect(create().component.allowed).toBe(false);
    expect(api.list).not.toHaveBeenCalled();
    expect(api.mine).not.toHaveBeenCalled();

    granted = null;
    api.list.mockReturnValue(throwError(() => ({ status: 503 })));
    expect(create().component.errorMessage).toBe('accessManagement.franchiseMembers.errors.unavailable');
  });

  it('labels statuses and types, and shows the owner or a dash', () => {
    const { component } = create();
    expect(component.franchiseStatus('ACTIVE')).toBe('accessManagement.franchises.statuses.ACTIVE');
    expect(component.franchiseStatus('ODD')).toBe('ODD');
    expect(component.typeLabel('AGENT')).toBe('accessManagement.franchiseMembers.types.AGENT');
    expect(component.statusLabel('X')).toBe('X');
    expect(component.ownerOf({ ...franchises[0], ownerName: null, ownerEmail: 'k@x.com' })).toBe('k@x.com');
    expect(component.ownerOf({ ...franchises[0], ownerName: null, ownerEmail: null })).toBe('—');
  });

  it("counts a driver franchise's ACTIVE drivers and lists them live from the administrator's drivers call", () => {
    const rbm7 = franchise('f7', 'RBM-00007', 'Owner', [['BESTRIDE_RIDER_APP', 0, 50]]);
    const live = franchise('f7', 'RBM-00007', 'Owner', [['BESTRIDE_RIDER_APP', 0, 50]]);
    live.applications[0].driverCount = 1;
    api.livePlaces.mockReturnValue(of([live]));
    api.list.mockReturnValue(of([rbm7]));
    const { component, fixture, el } = create();

    expect(api.livePlaces).toHaveBeenCalledWith(true);
    expect(component.tree[0]).toMatchObject({ applicationCode: 'BESTRIDE_RIDER_APP', used: 1, limit: 50 });
    expect(el.textContent).toContain('accessManagement.franchiseMembers.driverUsage');
    component.toggleFranchise('BESTRIDE_RIDER_APP', component.tree[0].franchises[0]);
    fixture.componentRef.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    expect(api.adminDrivers).toHaveBeenCalledWith('f7', 0, 25);
    expect(api.adminMembers).not.toHaveBeenCalled();
    const branch = component.branch('BESTRIDE_RIDER_APP', 'f7')!;
    expect(branch.total).toBe(1);
    expect(branch.members[0]).toMatchObject({ fullName: 'ravi', memberType: 'DRIVER', status: 'ACTIVE', applicationCode: 'BESTRIDE_RIDER_APP' });
    expect(component.canRemove(branch.members[0])).toBe(false);
    expect(el.textContent).toContain('ravi');
    expect(el.textContent).not.toContain('accessManagement.franchiseDrivers.noDrivers');
  });

  it("reads an owner's drivers with the owner's call, and only with the drivers privilege", () => {
    granted = new Set(['FRANCHISE_MINE_LIST', 'FRANCHISE_MEMBER_LIST', 'FRANCHISE_DRIVER_LIST']);
    api.mine.mockReturnValue(of([franchises[1]]));
    const { component } = create();
    const rider = component.tree.find((g) => g.applicationCode === 'RIDER')!;
    component.toggleFranchise('RIDER', rider.franchises[0]);
    expect(api.livePlaces).toHaveBeenCalledWith(false);
    expect(api.drivers).toHaveBeenCalledWith('f1', 0, 25);
    expect(api.members).not.toHaveBeenCalled();
    expect(component.branch('RIDER', 'f1')!.members).toHaveLength(1);

    granted = new Set(['FRANCHISE_MINE_LIST', 'FRANCHISE_MEMBER_LIST']);
    api.drivers.mockClear();
    api.livePlaces.mockReturnValue(throwError(() => ({ status: 503 })));
    const second = create().component;
    second.toggleFranchise('RIDER', second.tree.find((g) => g.applicationCode === 'RIDER')!.franchises[0]);
    expect(api.drivers).not.toHaveBeenCalled();
    expect(second.branch('RIDER', 'f1')!.failed).toBe(true);
    expect(second.tree.find((g) => g.applicationCode === 'RIDER')).toMatchObject({ used: 0, limit: 3 });
    expect(second.errorMessage).toBe('');
  });
});

describe('member tree helpers', () => {
  it('groups franchises by application, sorted by name and code, with totals', () => {
    const tree = groupByApplication(franchises, (c) => names[c] ?? c);
    expect(tree.map((g) => g.applicationCode)).toEqual(['RIDER', 'MATRIMONY']);
    expect(tree[1].franchises.map((r) => r.franchise.code)).toEqual(['RBM-1', 'RBM-2']);
    expect(applicationCodesOf(franchises).sort()).toEqual(['MATRIMONY', 'RIDER']);
    expect(branchKey('A', 'f')).toBe('A|f');
    expect(groupByApplication([], (c) => c)).toEqual([]);
  });

  it('takes the driver application counts from the live read and leaves agents alone', () => {
    const indexed = [franchise('f1', 'RBM-1', 'Asha', [['MATRIMONY', 2, 2], ['BESTRIDE_RIDER_APP', 0, 50]]), franchise('f2', 'RBM-2', 'K', [['MATRIMONY', 1, 5]])];
    const live = [franchise('f1', 'RBM-1', 'Asha', [['MATRIMONY', 9, 9], ['BESTRIDE_RIDER_APP', 7, 50]])];
    live[0].applications[1].driverCount = 3;
    const merged = withLiveDriverCounts(indexed, live);
    expect(merged[0].applications[0]).toEqual(indexed[0].applications[0]);
    expect(merged[0].applications[1]).toMatchObject({ agentCount: 0, driverCount: 3 });
    expect(merged[1]).toEqual(indexed[1]);
    expect(hasDriverApplication(indexed)).toBe(true);
    expect(hasDriverApplication([indexed[1]])).toBe(false);
  });
});

describe('franchise application helpers', () => {
  it('uses the drivers for the driver application and the agents for any other', () => {
    expect(placesUsed({ applicationCode: 'BESTRIDE_RIDER_APP', agentCount: 4, driverCount: 2 })).toBe(2);
    expect(placesUsed({ applicationCode: 'RIDER', agentCount: 4 })).toBe(0);
    expect(placesUsed({ applicationCode: 'MATRIMONY', agentCount: 4, driverCount: 9 })).toBe(4);
    expect(driverAsMember(ravi, 'BESTRIDE_RIDER_APP')).toEqual({
      memberId: 'dm1', userId: '2a94fffe', fullName: 'ravi', email: '', phone: '+919000000001', applicationCode: 'BESTRIDE_RIDER_APP',
      memberType: 'DRIVER', status: 'ACTIVE', roleCode: null, onboardedAt: '2026-11-15T05:00:00Z',
    });
    expect(driverAsMember({ ...ravi, onboardedAt: null }, 'X').onboardedAt).toBe('');
  });
});
