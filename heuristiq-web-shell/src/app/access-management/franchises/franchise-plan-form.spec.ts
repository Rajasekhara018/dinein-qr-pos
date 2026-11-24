import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { FranchiseApi } from './franchise-api';
import { FranchisePlan, Location } from './franchise.models';
import { FranchisePlanForm } from './franchise-plan-form';

const MATRI = 'PASUPU_KUMKUMA_MATRIMONY_APP';
const countryPlan: FranchisePlan = {
  id: 'p5', applicationCode: MATRI, countryCode: 'IN', countryName: 'India', stateCode: '', stateName: '', city: '', districtCode: '',
  agentLimit: 5, fee: { code: 'INR', value: 50000 }, scope: 'COUNTRY', locationId: null, locationType: null, locationLevel: null, chain: [],
};

const loc = (id: string, name: string, type: Location['type'] = 'STATE'): Location => ({
  id, countryCode: 'IN', type, level: 'REGION', levelNumber: 1, code: id, name, parentId: null, parent: null, active: true, sortOrder: 0, version: 0,
});

const api = { plansPage: vi.fn(), plans: vi.fn(), plan: vi.fn(), addPlan: vi.fn(), changePlan: vi.fn() };
const uam = { list: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
const router = { navigate: vi.fn(() => Promise.resolve(true)) };
const dialogs = { confirm: vi.fn() };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};
const KEY = 'accessManagement.franchisePlans.';

function create(id: string | null = null): { fixture: ComponentFixture<FranchisePlanForm>; component: FranchisePlanForm; el: HTMLElement } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    declarations: [FranchisePlanForm],
    imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
    providers: [
      { provide: FranchiseApi, useValue: api },
      { provide: UamService, useValue: uam },
      { provide: NotificationService, useValue: notify },
      { provide: HqPrivilegeAccessService, useValue: access },
      { provide: Router, useValue: router },
      { provide: AppDialogService, useValue: dialogs },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => id } } } },
    ],
    schemas: [NO_ERRORS_SCHEMA],
  });
  const fixture = TestBed.createComponent(FranchisePlanForm);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('FranchisePlanForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    history.replaceState(null, '');
    api.plansPage.mockReturnValue(of({ defaultCurrency: 'INR', plans: [], countries: [], totalElements: 0 }));
    api.plans.mockReturnValue(of({ plans: [countryPlan] }));
    api.plan.mockReturnValue(of(countryPlan));
    api.addPlan.mockReturnValue(of(countryPlan));
    api.changePlan.mockReturnValue(of(countryPlan));
    uam.list.mockReturnValue(of({ content: [{ code: MATRI, name: 'Matrimony' }] }));
    dialogs.confirm.mockReturnValue(of(true));
  });

  describe('adding a plan', () => {
    it('starts a place plan with the cascading picker, in the only allowed country', () => {
      api.plansPage.mockReturnValue(of({ defaultCurrency: 'INR', plans: [], countries: ['IN'], totalElements: 0 }));

      const { component, el } = create();

      expect(component.form).toMatchObject({ scope: 'PLACE', currency: 'INR' });
      expect(component.titleKey).toBe(`${KEY}newTitle`);
      expect(component.countries.map((country) => country.code)).toEqual(['IN']);
      expect(component.applications.map((application) => application.code)).toEqual([MATRI]);
      expect(el.querySelector('app-location-picker')).not.toBeNull();
    });

    it('falls back to the older text fields for a place plan when the places cannot be read', () => {
      granted = new Set(['FRANCHISE_PLAN_CREATE']);

      const { el } = create();

      expect(el.querySelector('app-location-picker')).toBeNull();
      expect(el.querySelector('input[name="planStateName"]')).not.toBeNull();
    });

    it('starts the place choice again when the scope or the country changes', () => {
      const { component } = create();
      Object.assign(component.form, { scope: 'TYPE_DEFAULT', scopeType: 'TOWN', scopeLocationId: 'kr', locationId: 'nz' });

      component.onScopeChange();
      expect(component.form).toMatchObject({ locationId: '', scopeLocationId: '', scopeType: '', chain: [] });

      component.form.locationId = 'nz';
      component.onCountryChange();
      expect(component.form.locationId).toBe('');
    });

    it('takes the deepest place chosen, and the state or district a type default is under', () => {
      const { component } = create();

      component.onPlacePicked(loc('nz', 'Nuzvid', 'TOWN'));
      expect(component.form.locationId).toBe('nz');
      component.onPlacePicked(null);
      expect(component.form.locationId).toBe('');

      component.onUnderPicked(loc('kr', 'Krishna', 'DISTRICT'));
      expect(component.form).toMatchObject({ scopeLocationId: 'kr', scopeParentType: 'DISTRICT' });
      component.onScopeTypeChange();
      expect(component.form).toMatchObject({ scopeLocationId: '', scopeParentType: '' });
    });

    it('refuses an incomplete plan without calling the service', () => {
      const { component } = create();

      component.save();
      expect(notify.error).toHaveBeenLastCalledWith(`${KEY}errors.application`);

      Object.assign(component.form, { applicationCode: MATRI, agentLimit: 5, fee: 100 });
      component.save();
      expect(notify.error).toHaveBeenLastCalledWith(`${KEY}errors.place`);
      expect(api.addPlan).not.toHaveBeenCalled();
    });

    it('adds a place plan by its place only, says so and goes back to the list', () => {
      const { component } = create();
      Object.assign(component.form, { applicationCode: MATRI, agentLimit: 5, fee: 1234.5 });
      component.onPlacePicked(loc('nz', 'Nuzvid', 'TOWN'));

      component.save();

      expect(api.addPlan).toHaveBeenCalledWith({ applicationCode: MATRI, agentLimit: 5, fee: { code: 'INR', value: 1234.5 }, locationId: 'nz' });
      expect(notify.success).toHaveBeenCalledWith(`${KEY}messages.added`);
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-plans']);
    });

    it('adds a type default under a district, and a country-wide one', () => {
      const { component } = create();
      Object.assign(component.form, { applicationCode: MATRI, agentLimit: 5, fee: 10, scope: 'TYPE_DEFAULT', scopeType: 'TOWN', countryCode: 'IN' });
      component.onUnderPicked(loc('kr', 'Krishna', 'DISTRICT'));

      component.save();
      expect(api.addPlan).toHaveBeenLastCalledWith(expect.objectContaining({ scopeType: 'TOWN', scopeLocationId: 'kr' }));

      Object.assign(component.form, { scopeType: 'CITY', scopeLocationId: '', scopeParentType: '', chain: [] });
      component.save();
      expect(api.addPlan).toHaveBeenLastCalledWith(expect.objectContaining({ scopeType: 'CITY', countryCode: 'IN' }));
      expect((api.addPlan.mock.calls[1][0] as Record<string, unknown>)['scopeLocationId']).toBeUndefined();
    });

    it('shows the duplicate text of the service as given and stays on the page', () => {
      api.addPlan.mockReturnValue(throwError(() => ({ status: 409, error: { message: 'A plan for this application and area already exists' } })));
      const { component } = create();
      Object.assign(component.form, { applicationCode: MATRI, agentLimit: 5, fee: 10, scope: 'GENERAL' });

      component.save();

      expect(notify.error).toHaveBeenCalledWith('A plan for this application and area already exists');
      expect(component.saving).toBe(false);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('rounds the fee to two decimals', () => {
      const { component } = create();
      component.form.fee = 10.456;

      component.roundFee();

      expect(component.form.fee).toBe(10.46);
      expect(component.currenciesOf(component.form)).toContain('INR');
    });
  });

  describe('changing a plan', () => {
    it('reads the current plan even when the list handed over a stale row', () => {
      history.replaceState({ plan: { ...countryPlan, agentLimit: 999 } }, '');

      const { component } = create('p5');

      expect(api.plans).not.toHaveBeenCalled();
      expect(api.plan).toHaveBeenCalledWith('p5');
      expect(component.titleKey).toBe(`${KEY}editTitle`);
      expect(component.form).toMatchObject({ scope: 'COUNTRY', countryCode: 'IN', applicationCode: MATRI, agentLimit: 5 });
    });

    it('reads one plan by id when it is opened directly', () => {
      const { component } = create('p5');

      expect(api.plan).toHaveBeenCalledWith('p5');
      expect(api.plans).not.toHaveBeenCalled();
      expect(component.form).toMatchObject({ scope: 'COUNTRY' });
      expect(component.isLoading).toBe(false);
    });

    it('says so, and shows no form, when the plan cannot be found or read', () => {
      history.replaceState({ plan: countryPlan }, '');
      api.plan.mockReturnValue(throwError(() => ({ status: 404 })));
      const missing = create('gone');
      expect(missing.component.loadFailed).toBe(true);
      expect(missing.el.querySelector('form')).toBeNull();

      api.plan.mockReturnValue(throwError(() => ({ status: 500 })));
      const failed = create('p5');
      expect(failed.component.loadFailed).toBe(true);
      expect(notify.error).toHaveBeenCalled();
    });

    it('changes the plan with its id and goes back to the list', () => {
      history.replaceState({ plan: countryPlan }, '');
      const { component } = create('p5');

      component.save();

      expect(api.changePlan).toHaveBeenCalledWith('p5', expect.objectContaining({ countryCode: 'IN', applicationCode: MATRI }));
      expect(notify.success).toHaveBeenCalledWith(`${KEY}messages.changed`);
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-plans']);
    });
  });

  describe('leaving with unsaved changes', () => {
    it('has nothing pending until a field is changed, and not after saving', () => {
      history.replaceState({ plan: countryPlan }, '');
      const { component } = create('p5');
      expect(component.hasPendingChanges()).toBe(false);

      component.form.fee = 1;
      expect(component.hasPendingChanges()).toBe(true);

      component.save();
      expect(component.hasPendingChanges()).toBe(false);
    });

    it('asks before discarding edits, and leaves without asking when nothing changed', () => {
      const { component } = create();
      let answer: boolean | undefined;
      component.confirmDiscardChanges().subscribe((v) => (answer = v));
      expect(answer).toBe(true);
      expect(dialogs.confirm).not.toHaveBeenCalled();

      component.form.fee = 7;
      dialogs.confirm.mockReturnValue(of(false));
      component.confirmDiscardChanges().subscribe((v) => (answer = v));
      expect(dialogs.confirm).toHaveBeenCalledTimes(1);
      expect(answer).toBe(false);
    });

    it('goes back to the list on cancel', () => {
      create().component.cancel();

      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-plans']);
    });
  });

  it('lays the fields out in the two-column form grid with a label above each control', () => {
    const { el } = create();

    expect(el.querySelector('form.ffp__grid')).not.toBeNull();
    expect(el.querySelectorAll('.ffp__field').length).toBeGreaterThanOrEqual(5);
    expect(el.querySelector('.ffp__actions button[type="submit"]')).not.toBeNull();
  });
});
