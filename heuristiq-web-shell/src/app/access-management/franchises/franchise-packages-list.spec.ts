import { NO_ERRORS_SCHEMA } from '@angular/core';
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
import { PackageTemplate } from './franchise-packages.models';
import { FranchisePackagesList } from './franchise-packages-list';

const MATRI = 'PASUPU_KUMKUMA_MATRIMONY_APP';
const gold: PackageTemplate = {
  id: 't1', name: 'Gold', applicationCode: MATRI, active: true, version: 3,
  feePlanCode: 'GOLD_FEE', feePlanName: 'Gold fee', commissionPlanCode: 'AGENT15', commissionPlanName: 'Agent 15%',
  referralPlanCode: 'REF2', referralPlanName: null,
};
const silver: PackageTemplate = {
  id: 't2', name: 'Silver', applicationCode: MATRI, active: false, version: 1,
  feePlanCode: null, feePlanName: null, commissionPlanCode: null, commissionPlanName: null, referralPlanCode: null, referralPlanName: null,
};

const api = { packageTemplates: vi.fn(), changePackageTemplate: vi.fn() };
const uam = { list: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
const router = { navigate: vi.fn(() => Promise.resolve(true)) };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};
const KEY = 'accessManagement.franchisePackages.';

function create(): { fixture: ComponentFixture<FranchisePackagesList>; component: FranchisePackagesList; el: HTMLElement } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    declarations: [FranchisePackagesList],
    imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
    providers: [
      { provide: FranchiseApi, useValue: api },
      { provide: UamService, useValue: uam },
      { provide: NotificationService, useValue: notify },
      { provide: HqPrivilegeAccessService, useValue: access },
      { provide: Router, useValue: router },
    ],
    schemas: [NO_ERRORS_SCHEMA],
  });
  const fixture = TestBed.createComponent(FranchisePackagesList);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('FranchisePackagesList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    api.packageTemplates.mockReturnValue(of({ items: [gold, silver], page: 0, size: 20, totalElements: 2 }));
    api.changePackageTemplate.mockReturnValue(of(gold));
    uam.list.mockReturnValue(of({ content: [{ code: MATRI, name: 'Matrimony' }] }));
  });

  it('asks for one filtered page and keeps the total', () => {
    const { component } = create();

    expect(api.packageTemplates).toHaveBeenCalledWith({ applicationCode: '', q: '', page: 0, size: 20 });
    expect(component.rows.length).toBe(2);
    expect(component.total).toBe(2);
    expect(component.nameOf(MATRI)).toBe('Matrimony');
    expect(component.nameOf('X')).toBe('X');
  });

  it('shows the picked plans by name (the code without a name) and "rules that apply" without a pick', () => {
    const { component, el } = create();

    expect(component.planText(gold, 'fee')).toBe('Gold fee');
    expect(component.planText(gold, 'commission')).toBe('Agent 15%');
    expect(component.planText(gold, 'referral')).toBe('REF2');
    expect(component.planText(silver, 'fee')).toBe(`${KEY}plans.rulesApply`);
    expect(component.columns.map((column) => column.key)).toEqual(
      ['name', 'applicationCode', 'commissionPlanCode', 'referralPlanCode', 'active']);
    expect(el.textContent).toContain('Gold fee');
    expect(el.textContent).toContain('Agent 15%');
    expect(el.textContent).toContain(`${KEY}plans.rulesApply`);
  });

  it('sends the filters, including the status, and clears them', () => {
    const { component } = create();
    component.filters = { applicationCode: MATRI, active: 'false', q: 'sil' };

    component.load();
    expect(api.packageTemplates).toHaveBeenLastCalledWith({ applicationCode: MATRI, active: false, q: 'sil', page: 0, size: 20 });

    component.clearFilters();
    expect(component.filters).toEqual({ applicationCode: '', active: '', q: '' });
  });

  it('pages on the server and reloads from the first page with another page size', () => {
    api.packageTemplates.mockReturnValue(of({ items: [gold], totalElements: 45 }));
    const { component } = create();
    expect(component.pageCount).toBe(3);

    component.goToPage(2);
    expect(api.packageTemplates).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, size: 20 }));

    component.onPageSizeChange(50);
    expect(api.packageTemplates).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 50 }));
  });

  it('shows the error and tells the user when packages or applications cannot be loaded', () => {
    api.packageTemplates.mockReturnValue(throwError(() => new Error('down')));
    uam.list.mockReturnValue(throwError(() => new Error('down')));

    const { component } = create();

    expect(component.hasError).toBe(true);
    expect(notify.error).toHaveBeenCalledWith(`${KEY}errorMessage`);
    expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.applicationsUnavailable');
  });

  describe('opening the form pages', () => {
    it('opens the add page', () => {
      create().component.addPackage();

      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-packages', 'new']);
    });

    it('opens the change page with the row handed over', () => {
      create().component.editPackage(gold);

      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-packages', 't1', 'edit'], { state: { template: gold } });
    });
  });

  describe('switching on and off', () => {
    it('sends the whole template with the new switch, then reloads', () => {
      const { component } = create();

      component.toggleActive(gold);

      expect(api.changePackageTemplate).toHaveBeenCalledWith('t1', expect.objectContaining({
        active: false, version: 3, feePlanCode: 'GOLD_FEE', commissionPlanCode: 'AGENT15', referralPlanCode: 'REF2',
      }));
      expect(notify.success).toHaveBeenCalledWith(`${KEY}messages.switchedOff`);
      expect(api.packageTemplates).toHaveBeenCalledTimes(2);
    });

    it('shows the service text when switching fails', () => {
      api.changePackageTemplate.mockReturnValue(throwError(() => ({ status: 409, error: { message: 'stale' } })));
      const { component } = create();

      component.toggleActive(silver);

      expect(notify.error).toHaveBeenCalledWith('stale');
    });
  });

  it('shows the add, edit and switch buttons only to someone who may use them', () => {
    granted = new Set(['FRANCHISE_PACKAGE_TEMPLATE_LIST']);
    const denied = create().el;
    expect(denied.querySelector('.table-create-btn')).toBeNull();
    expect(denied.querySelector('.row-action-btn')).toBeNull();

    granted = new Set(['FRANCHISE_PACKAGE_TEMPLATE_CREATE', 'FRANCHISE_PACKAGE_TEMPLATE_UPDATE']);
    const allowed = create().el;
    expect(allowed.querySelector('.table-create-btn')).not.toBeNull();
    expect(allowed.querySelectorAll('.row-action-btn').length).toBe(4);
  });
});
