import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { FranchiseApi } from './franchise-api';
import { Franchise } from './franchise.models';
import { FranchisesList } from './franchises-list';

const MATRI = 'PASUPU_KUMKUMA_MATRIMONY_APP';
const RIDE = 'BESTRIDE_RIDER_APP';

const franchise: Franchise = {
  unitId: 'u1', code: 'RBM-00001', name: 'RBM-00001', ownerUserId: 'o1', status: 'ACTIVE', totalAgentLimit: 50, totalAgentCount: 3,
  applications: [
    { applicationCode: MATRI, agentLimit: 20, agentCount: 1, fee: { code: 'INR', value: 100000 } },
    { applicationCode: RIDE, agentLimit: 30, agentCount: 2, fee: { code: 'INR', value: 200000 } },
  ],
};

const api = { list: vi.fn() };
const uam = { list: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
const router = { navigate: vi.fn(() => Promise.resolve(true)) };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

function create(): { fixture: ComponentFixture<FranchisesList>; component: FranchisesList; el: HTMLElement } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    declarations: [FranchisesList],
    imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
    providers: [
      { provide: FranchiseApi, useValue: api },
      { provide: UamService, useValue: uam },
      { provide: NotificationService, useValue: notify },
      { provide: HqPrivilegeAccessService, useValue: access },
      { provide: Router, useValue: router },
    ],
  });
  const fixture = TestBed.createComponent(FranchisesList);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('FranchisesList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    api.list.mockReturnValue(of([franchise]));
    uam.list.mockReturnValue(of({ content: [{ code: MATRI, name: 'Matrimony' }, { code: RIDE, name: 'Ride' }] }));
  });

  it('loads the franchises and the registered applications', () => {
    const { component } = create();

    expect(component.rows).toEqual([franchise]);
    expect(component.applications.map((a) => a.code)).toEqual([MATRI, RIDE]);
    expect(component.isLoading).toBe(false);
    expect(component.hasError).toBe(false);
  });

  it('shows the error and tells the user when the franchises cannot be loaded', () => {
    api.list.mockReturnValue(throwError(() => new Error('down')));

    const { component } = create();

    expect(component.hasError).toBe(true);
    expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errorMessage');
  });

  it('tells the user when the applications cannot be loaded', () => {
    uam.list.mockReturnValue(throwError(() => new Error('down')));

    create();

    expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.applicationsUnavailable');
  });

  it('renders each franchise with its code, status and the names of its applications', () => {
    const { el } = create();

    const text = el.textContent as string;
    expect(text).toContain('RBM-00001');
    expect(text).toContain('ACTIVE');
    expect(text).toContain('Matrimony, Ride');
  });

  it('shows the open button only to someone who may create franchises', () => {
    granted = new Set(['FRANCHISE_LIST']);
    expect(create().el.querySelector('.table-create-btn')).toBeNull();

    granted = new Set(['FRANCHISE_CREATE']);
    expect(create().el.querySelector('.table-create-btn')).not.toBeNull();
  });

  it('opens the page that opens a franchise', () => {
    create().component.openFranchise();

    expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchises', 'new']);
  });

  it('opens the franchise setup on its first step, handing the row over, from the pencil on a row', () => {
    const { el } = create();

    (el.querySelector('[data-testid="open-setup"]') as HTMLButtonElement).click();

    expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchises', 'u1', 'setup', 'details'],
      { state: { franchise, name: franchise.code } });
  });

  it('pages through the franchises in the footer', () => {
    const many = Array.from({ length: 25 }, (_, i) => ({ ...franchise, unitId: `u${i}`, code: `RBM-${i}` }));
    api.list.mockReturnValue(of(many));
    const { component } = create();

    expect(component.pageCount).toBe(3);
    expect(component.pageRows).toHaveLength(10);

    component.goToPage(2);
    expect(component.pageRows.map((row) => row.code)).toEqual(['RBM-20', 'RBM-21', 'RBM-22', 'RBM-23', 'RBM-24']);
    component.goToPage(99);
    expect(component.page).toBe(2);

    component.onPageSizeChange(50);
    expect(component.page).toBe(0);
    expect(component.pageRows).toHaveLength(25);
  });

  it('names an application by its code when the registry does not know it', () => {
    const { component } = create();

    expect(component.nameOf(MATRI)).toBe('Matrimony');
    expect(component.nameOf('UNKNOWN_APP')).toBe('UNKNOWN_APP');
  });

  it('shows the owner, the place, the created date and a translated status', () => {
    api.list.mockReturnValue(of([{
      ...franchise, ownerName: 'Asha Rao', ownerEmail: 'asha@example.com', createdAt: '2026-11-01T10:00:00Z', status: 'SUSPENDED',
      applications: [{ ...franchise.applications[0], locationName: 'Hyderabad' }],
    }]));

    const { component, el } = create();

    expect(component.columns.map((column) => column.key)).toEqual(['code', 'owner', 'place', 'status', 'applications', 'usage', 'createdAt']);
    expect(el.textContent).toContain('Asha Rao');
    expect(el.textContent).toContain('Hyderabad');
    expect(el.textContent).toContain('accessManagement.franchises.statuses.SUSPENDED');
  });

  it('falls back to the raw status code and to dashes when the answer has no owner, place or date', () => {
    api.list.mockReturnValue(of([{ ...franchise, status: 'ODD' }]));

    const { component, el } = create();

    expect(component.statusLabel('ODD')).toBe('ODD');
    expect(component.ownerLabel(franchise)).toBe('—');
    expect(component.placeLabel(franchise)).toBe('—');
    expect(el.textContent).toContain('ODD');
  });

  it('opens the read-only members page of a franchise for an administrator who may read them', () => {
    const { component, el } = create();

    (el.querySelector('.row-action-btn .bi-people')!.closest('button') as HTMLButtonElement).click();

    expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchises', 'u1', 'members']);
    expect(component.privileges.listAdminMembers).toBe('FRANCHISE_ADMIN_MEMBER_LIST');
  });

  it('hides the members action from someone without the privilege', () => {
    granted = new Set(['FRANCHISE_LIST']);

    const { el } = create();

    expect(el.querySelector('.row-action-btn .bi-people')).toBeNull();
  });

  it('has no separate onboarding or legal details actions: both are steps of the franchise setup', () => {
    const { el } = create();

    expect(el.querySelector('[data-testid="open-onboarding"]')).toBeNull();
    expect(el.querySelector('[data-testid="open-legal-profile"]')).toBeNull();
    expect(el.querySelector('[data-testid="open-setup"]')).not.toBeNull();
  });

  it('shows no DIRECT notice when no franchise is flagged', () => {
    const { el, component } = create();

    expect(component.activeInDirect).toBe(0);
    expect(el.querySelector('[data-testid="direct-organization-notice"]')).toBeNull();
  });

  it('asks the administrator to close the franchises still active in an organization that is now DIRECT', () => {
    api.list.mockReturnValue(of([
      { ...franchise, activeInDirectOrganization: true },
      { ...franchise, unitId: 'u2', code: 'RBM-00002', status: 'CLOSED', activeInDirectOrganization: false },
    ]));

    const { el, component } = create();

    expect(component.activeInDirect).toBe(1);
    const notice = el.querySelector('[data-testid="direct-organization-notice"]') as HTMLElement;
    expect(notice).not.toBeNull();
    expect(notice.getAttribute('role')).toBe('alert');
    expect(notice.textContent).toContain('accessManagement.franchises.directNotice.title');
    expect(notice.textContent).toContain('accessManagement.franchises.directNotice.body');
  });
});
