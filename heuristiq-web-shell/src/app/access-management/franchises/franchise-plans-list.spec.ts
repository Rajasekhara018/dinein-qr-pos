import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { FranchiseApi } from './franchise-api';
import { FranchisePlan, Location, PlanResolution } from './franchise.models';
import { FranchisePlansList } from './franchise-plans-list';

const MATRI = 'PASUPU_KUMKUMA_MATRIMONY_APP';
const chain = [
  { id: 'ap', type: 'STATE' as const, level: 'REGION' as const, code: 'AP', name: 'Andhra Pradesh' },
  { id: 'kr', type: 'DISTRICT' as const, level: 'REGION' as const, code: 'AP-KR', name: 'Krishna' },
  { id: 'nz', type: 'TOWN' as const, level: 'URBAN' as const, code: 'NUZVID', name: 'Nuzvid' },
];
const ownPlan: FranchisePlan = {
  id: 'p1', applicationCode: MATRI, countryCode: 'IN', countryName: 'India', stateCode: 'AP', stateName: 'Andhra Pradesh', city: 'Nuzvid', districtCode: 'KR',
  agentLimit: 20, fee: { code: 'INR', value: 100000 }, scope: 'PLACE', locationId: 'nz', locationType: 'TOWN', locationLevel: 'URBAN', chain,
};
const typeDefault: FranchisePlan = {
  ...ownPlan, id: 'p2', scope: 'TYPE_DEFAULT', locationId: null, locationType: null, locationLevel: null, scopeType: 'CITY', scopeLocationId: 'kr', chain: chain.slice(0, 2),
};
const countryWide: FranchisePlan = {
  ...ownPlan, id: 'p3', scope: 'TYPE_DEFAULT', locationId: null, locationType: null, locationLevel: null, scopeType: 'TOWN', scopeLocationId: null, chain: [],
};
const general: FranchisePlan = {
  ...ownPlan, id: 'p4', scope: 'GENERAL', countryCode: '', countryName: '', stateCode: '', stateName: '', city: '', districtCode: '', locationId: null,
  locationType: null, locationLevel: null, chain: [], agentLimit: 5, fee: { code: 'INR', value: 50000 },
};
const countryPlan: FranchisePlan = { ...general, id: 'p5', scope: 'COUNTRY', countryCode: 'IN', countryName: 'India' };

const loc = (id: string, name: string, type: Location['type'] = 'STATE'): Location => ({
  id, countryCode: 'IN', type, level: 'REGION', levelNumber: 1, code: id, name, parentId: null, parent: null, active: true, sortOrder: 0, version: 0,
});

const api = {
  plansPage: vi.fn(), addPlan: vi.fn(), changePlan: vi.fn(), deletePlan: vi.fn(), locations: vi.fn(), resolvePlan: vi.fn(),
};
const uam = { list: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
const dialogs = { confirm: vi.fn() };
const router = { navigate: vi.fn(() => Promise.resolve(true)) };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

function configure(): void {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    declarations: [FranchisePlansList],
    imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
    providers: [
      { provide: FranchiseApi, useValue: api },
      { provide: UamService, useValue: uam },
      { provide: AppDialogService, useValue: dialogs },
      { provide: NotificationService, useValue: notify },
      { provide: HqPrivilegeAccessService, useValue: access },
      { provide: Router, useValue: router },
    ],
    schemas: [NO_ERRORS_SCHEMA],
  });
}

function create(): { fixture: ComponentFixture<FranchisePlansList>; component: FranchisePlansList; el: HTMLElement } {
  const fixture = TestBed.createComponent(FranchisePlansList);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

const KEY = 'accessManagement.franchisePlans.';

describe('FranchisePlansList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    api.plansPage.mockReturnValue(of({ defaultCurrency: 'INR', plans: [ownPlan, typeDefault, countryWide, general, countryPlan], countries: [], page: 0, size: 20, totalElements: 5 }));
    api.locations.mockReturnValue(of({ items: [loc('ap', 'Andhra Pradesh')], page: 0, size: 100, totalElements: 1 }));
    api.addPlan.mockReturnValue(of(ownPlan));
    api.changePlan.mockReturnValue(of(ownPlan));
    api.deletePlan.mockReturnValue(of({}));
    uam.list.mockReturnValue(of({ content: [{ code: MATRI, name: 'Matrimony' }] }));
    dialogs.confirm.mockReturnValue(of(true));
    configure();
  });

  describe('the list', () => {
    it('asks for one filtered page and keeps the total', () => {
      const { component } = create();

      expect(api.plansPage).toHaveBeenCalledWith({
        applicationCode: '', scope: '', locationType: '', level: '', stateId: '', districtId: '', q: '', page: 0, size: 20,
      });
      expect(component.plans.length).toBe(5);
      expect(component.total).toBe(5);
      expect(component.nameOf(MATRI)).toBe('Matrimony');
      expect(component.nameOf('OTHER_APP')).toBe('OTHER_APP');
    });

    it('shows where each plan applies: the chain, the type default, the country, or everywhere', () => {
      const { component, el } = create();

      expect(component.region(ownPlan)).toBe('Andhra Pradesh > Krishna > Nuzvid');
      expect(component.region(typeDefault)).toBe(`${KEY}scopeDescription.underPlace`);
      expect(component.region(countryWide)).toBe(`${KEY}scopeDescription.inCountry`);
      expect(component.region(countryPlan)).toBe('India');
      expect(component.region(general)).toBe('accessManagement.franchises.regionGeneral');
      expect(el.textContent).toContain('Andhra Pradesh > Krishna > Nuzvid');
    });

    it('shows a scope badge for every plan and the type with its level for a place plan, without float math on the fee', () => {
      const { el } = create();

      const badges = Array.from(el.querySelectorAll('tbody .franchise-badge--rule')).map((badge) => badge.textContent?.trim());
      expect(badges).toEqual([`${KEY}scopes.PLACE`, `${KEY}scopes.TYPE_DEFAULT`, `${KEY}scopes.TYPE_DEFAULT`, `${KEY}scopes.GENERAL`, `${KEY}scopes.COUNTRY`]);
      expect(el.textContent).toContain('accessManagement.franchiseLocations.types.TOWN');
      expect(el.textContent).toContain('accessManagement.franchiseLocations.levels.URBAN');
      expect(el.textContent).toMatch(/(?:1,00,000|100,000)\.00/);
    });

    it('infers the scope of an answer from the first release that has none', () => {
      const { component } = create();

      expect(component.scopeOf({ ...general, scope: undefined })).toBe('GENERAL');
      expect(component.scopeOf(ownPlan)).toBe('PLACE');
    });

    it('sends the filters, clears them, and loads the districts of a chosen state', () => {
      api.locations.mockReturnValue(of({ items: [loc('kr', 'Krishna', 'DISTRICT')], page: 0, size: 100, totalElements: 1 }));
      const { component } = create();
      component.filters = { applicationCode: MATRI, scope: 'PLACE', locationType: 'TOWN', level: 'URBAN', stateId: 'ap', districtId: 'old', q: 'nuz' };

      component.onFilterStateChange();
      expect(api.locations).toHaveBeenLastCalledWith({ type: 'DISTRICT', parentId: 'ap', usable: true, size: 100 });
      expect(component.filters.districtId).toBe('');
      expect(component.filterDistricts.map((place) => place.id)).toEqual(['kr']);

      component.load();
      expect(api.plansPage).toHaveBeenLastCalledWith(expect.objectContaining({ applicationCode: MATRI, scope: 'PLACE', locationType: 'TOWN', level: 'URBAN', stateId: 'ap', q: 'nuz' }));

      component.clearFilters();
      expect(component.filters.q).toBe('');
      expect(component.filterDistricts).toEqual([]);
    });

    it('does not read the states for someone without the locations privilege', () => {
      granted = new Set(['FRANCHISE_PLAN_LIST']);

      const { component } = create();

      expect(api.locations).not.toHaveBeenCalled();
      component.filters.stateId = 'ap';
      component.onFilterStateChange();
      expect(api.locations).not.toHaveBeenCalled();
    });

    it('pages on the server', () => {
      api.plansPage.mockReturnValue(of({ defaultCurrency: 'INR', plans: [ownPlan], countries: [], totalElements: 45 }));
      const { component } = create();

      expect(component.pageCount).toBe(3);
      component.load(1);
      expect(api.plansPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20 }));
      expect(component.page).toBe(1);
      component.goToPage(2);
      expect(api.plansPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, size: 20 }));
    });

    it('reloads from the first page with the chosen page size', () => {
      const { component } = create();

      component.onPageSizeChange(50);

      expect(component.pageSize).toBe(50);
      expect(api.plansPage).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 50 }));
    });

    it('shows the error and tells the user when the plans or applications cannot be loaded', () => {
      api.plansPage.mockReturnValue(throwError(() => new Error('down')));
      uam.list.mockReturnValue(throwError(() => new Error('down')));

      const { component } = create();

      expect(component.hasError).toBe(true);
      expect(notify.error).toHaveBeenCalledWith(`${KEY}errorMessage`);
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.applicationsUnavailable');
    });
  });

  describe('privileges', () => {
    it('requires both read and update privileges before offering the editor', () => {
      granted = new Set(['FRANCHISE_PLAN_LIST', 'FRANCHISE_PLAN_UPDATE']);
      expect(create().el.querySelector('.bi-pencil')).toBeNull();
      configure();
      granted = new Set(['FRANCHISE_PLAN_LIST', 'FRANCHISE_PLAN_READ']);
      expect(create().el.querySelector('.bi-pencil')).toBeNull();
    });

    it('shows the fee-grid, edit and delete buttons, and the resolve panel, only to someone who may use them', () => {
      granted = new Set(['FRANCHISE_PLAN_LIST']);
      const denied = create().el;
      expect(denied.querySelector('#plans-tab-fees')).toBeNull();
      expect(denied.querySelector('#plans-tab-resolve')).toBeNull();
      expect(denied.querySelector('.row-action-btn')).toBeNull();
      expect(denied.querySelector('.plan-resolve')).toBeNull();

      configure();
      granted = new Set(['FRANCHISE_PLAN_CREATE', 'FRANCHISE_PLAN_READ', 'FRANCHISE_PLAN_UPDATE', 'FRANCHISE_PLAN_DELETE', 'FRANCHISE_PLAN_RESOLVE_READ']);
      const { fixture, el: allowed } = create();
      expect(allowed.querySelector('#plans-tab-fees')).not.toBeNull();
      expect(allowed.querySelector('#plans-tab-resolve')).not.toBeNull();
      expect(allowed.querySelectorAll('.row-action-btn').length).toBe(10);
      (allowed.querySelector('#plans-tab-resolve') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(allowed.querySelector('.plan-resolve')).not.toBeNull();
    });

    it('picks places from the list only with both the list and the children privilege', () => {
      granted = new Set(['LOCATION_LIST']);
      expect(create().component.canPickPlaces).toBe(false);

      configure();
      granted = new Set(['LOCATION_LIST', 'LOCATION_CHILDREN_LIST']);
      expect(create().component.canPickPlaces).toBe(true);
    });
  });

  describe('changing a plan', () => {
    it('opens the plan editor by id without carrying the list row', () => {
      const { component } = create();

      component.editPlan(ownPlan);

      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-plans', 'p1', 'edit']);
    });

    it('opens the page from the pencil on a row', () => {
      const { el } = create();

      (el.querySelector('.row-action-btn') as HTMLButtonElement).click();

      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-plans', 'p1', 'edit']);
    });

    it('opens and closes the fee-by-type grid', () => {
      const { component } = create();

      component.toggleTypeFees();
      expect(component.showTypeFees).toBe(true);
      component.toggleTypeFees();
      expect(component.showTypeFees).toBe(false);
    });

    it('shows the plans first, and one tab at a time for the fees by type and the which-plan check', () => {
      const { fixture, component, el } = create();
      const tabs = () => Array.from(el.querySelectorAll('[role="tab"]')) as HTMLButtonElement[];
      const shown = (selector: string) => el.querySelector(selector) !== null;

      expect(tabs().length).toBe(3);
      expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
      expect(shown('hq-common-table')).toBe(true);
      expect(shown('app-franchise-type-fees')).toBe(false);
      expect(shown('.plan-resolve')).toBe(false);

      tabs()[1].click();
      fixture.detectChanges();
      expect(component.view).toBe('fees');
      expect(shown('app-franchise-type-fees')).toBe(true);
      expect(shown('hq-common-table')).toBe(false);
      expect(tabs()[1].getAttribute('aria-selected')).toBe('true');

      tabs()[2].click();
      fixture.detectChanges();
      expect(component.view).toBe('resolve');
      expect(shown('.plan-resolve')).toBe(true);
      expect(shown('app-franchise-type-fees')).toBe(false);

      tabs()[0].click();
      fixture.detectChanges();
      expect(shown('hq-common-table')).toBe(true);
      expect(shown('.plan-resolve')).toBe(false);
    });
  });

  describe('deleting', () => {
    it('asks first and names the place in the question', () => {
      const { component } = create();

      component.confirmDelete(ownPlan);

      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger' }));
      expect(api.deletePlan).toHaveBeenCalledWith('p1');
      expect(notify.success).toHaveBeenCalledWith(`${KEY}messages.deleted`);
    });

    it('does nothing when the user says no, and shows the service text when deleting fails', () => {
      const { component } = create();
      dialogs.confirm.mockReturnValueOnce(of(false));
      component.confirmDelete(ownPlan);
      expect(api.deletePlan).not.toHaveBeenCalled();

      api.deletePlan.mockReturnValue(throwError(() => ({ status: 404, error: { message: 'Plan not found' } })));
      component.confirmDelete(ownPlan);
      expect(notify.error).toHaveBeenCalledWith('Plan not found');
    });
  });

  describe('"Which plan applies?"', () => {
    const resolution: PlanResolution = {
      plan: { ...typeDefault, fee: { code: 'INR', value: 25000.5 }, agentLimit: 12 }, rule: 'TYPE_DEFAULT', inherited: true,
      source: chain[1], place: chain,
    };

    it('needs an application', () => {
      const { component } = create();

      component.resolve();

      expect(api.resolvePlan).not.toHaveBeenCalled();
      expect(component.resolveError).toBe(`${KEY}errors.application`);
    });

    it('shows the plan, the rule that matched and where the price was inherited from', () => {
      api.resolvePlan.mockReturnValue(of(resolution));
      const { fixture, component, el } = create();
      (el.querySelector('#plans-tab-resolve') as HTMLButtonElement).click();
      fixture.detectChanges();
      component.resolveForm = { applicationCode: MATRI, countryCode: 'IN', locationId: 'nz' };

      component.resolve();
      fixture.detectChanges();

      expect(api.resolvePlan).toHaveBeenCalledWith(MATRI, 'nz');
      const panel = el.querySelector('.plan-resolve__result') as HTMLElement;
      expect(panel.textContent).toContain(`${KEY}rules.TYPE_DEFAULT`);
      expect(panel.textContent).toContain('25,000.50');
      expect(panel.textContent).toContain(`${KEY}resolve.inheritedFrom`);
      expect(panel.textContent).toContain('Andhra Pradesh');
      expect(panel.textContent).toContain('Nuzvid');
    });

    it('says the place has its own plan, and asks for the general plan when no place is chosen', () => {
      api.resolvePlan.mockReturnValue(of({ plan: ownPlan, rule: 'OWN', inherited: false, source: chain[2], place: chain } as PlanResolution));
      const { fixture, component, el } = create();
      (el.querySelector('#plans-tab-resolve') as HTMLButtonElement).click();
      fixture.detectChanges();
      component.resolveForm = { applicationCode: MATRI, countryCode: '', locationId: '' };

      component.resolve();
      fixture.detectChanges();

      expect(api.resolvePlan).toHaveBeenCalledWith(MATRI, undefined);
      expect((el.querySelector('.plan-resolve__result') as HTMLElement).textContent).toContain(`${KEY}resolve.own`);
    });

    it('says the price comes from the country plan when there is no source place', () => {
      api.resolvePlan.mockReturnValue(of({ plan: countryPlan, rule: 'PARENT', inherited: true, source: null, place: chain } as PlanResolution));
      const { fixture, component, el } = create();
      (el.querySelector('#plans-tab-resolve') as HTMLButtonElement).click();
      fixture.detectChanges();
      component.resolveForm = { applicationCode: MATRI, countryCode: 'IN', locationId: 'nz' };

      component.resolve();
      fixture.detectChanges();

      expect((el.querySelector('.plan-resolve__result') as HTMLElement).textContent).toContain(`${KEY}resolve.inheritedCountry`);
    });

    it('shows the service text when no plan applies, and clears the answer when the place changes', () => {
      api.resolvePlan.mockReturnValueOnce(of(resolution));
      const { component } = create();
      component.resolveForm = { applicationCode: MATRI, countryCode: 'IN', locationId: 'nz' };
      component.resolve();
      expect(component.resolution).not.toBeNull();

      component.onResolvePlacePicked(loc('kr', 'Krishna', 'DISTRICT'));
      expect(component.resolveForm.locationId).toBe('kr');
      expect(component.resolution).toBeNull();

      api.resolvePlan.mockReturnValue(throwError(() => ({ status: 404, error: { message: 'No franchise plan applies to this place for MATRIMONY' } })));
      component.resolve();
      expect(component.resolveError).toBe('No franchise plan applies to this place for MATRIMONY');
      expect(component.resolving).toBe(false);

      component.onResolveCountryChange();
      expect(component.resolveForm.locationId).toBe('');
    });

    it('labels rules and scopes, falling back to the raw code', () => {
      const { component } = create();

      expect(component.ruleLabel('PARENT')).toBe(`${KEY}rules.PARENT`);
      expect(component.ruleLabel('NEW_RULE')).toBe('NEW_RULE');
      expect(component.scopeLabel('NEW_SCOPE')).toBe('NEW_SCOPE');
      expect(component.typeLabel(null)).toBe('');
      expect(component.levelLabel('GALAXY')).toBe('GALAXY');
    });
  });
});
