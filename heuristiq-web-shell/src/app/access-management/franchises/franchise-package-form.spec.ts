import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSearchableSelect, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { FranchiseApi } from './franchise-api';
import { LOCATION_TYPE_LABEL_KEYS } from './franchise-locations.constants';
import { NamedPlanKind, NamedPlanRow, PackageTemplate } from './franchise-packages.models';
import { FranchisePackageForm } from './franchise-package-form';

const MATRI = 'PASUPU_KUMKUMA_MATRIMONY_APP';
const RIDE = 'RIDE_APP';
const template: PackageTemplate = {
  id: 't1', name: 'Gold', applicationCode: MATRI, description: 'Best', active: true, version: 3,
  feePlanCode: 'GOLD_FEE', feePlanName: 'Gold fee', commissionPlanCode: 'AGENT15', commissionPlanName: 'Agent 15%',
  referralPlanCode: null, referralPlanName: null,
};
const named = (planCode: string, planName: string, extra: Partial<NamedPlanRow> = {}): NamedPlanRow => ({
  id: `${planCode}-1`, scope: { level: 'PLAN', planCode, vertical: MATRI }, planName, effectiveFrom: '2026-01-01', effectiveTo: null, ...extra,
});
/** The named plans each application has, per kind. */
const PLANS: Record<string, Record<NamedPlanKind, NamedPlanRow[]>> = {
  [MATRI]: {
    fee: [{ ...named('GOLD_FEE', 'Gold fee'), currency: 'INR', terms: { feeType: 'PERCENTAGE', percentageBp: 200 } } as NamedPlanRow],
    commission: [{ ...named('AGENT15', 'Agent 15%'), lines: [{ party: 'AGENT', basisPoints: 1500 }] } as NamedPlanRow],
    referral: [named('REF2', 'Two percent')],
  },
  [RIDE]: {
    fee: [named('RIDE_FEE', 'Ride fee')],
    commission: [named('AGENT15', 'Agent 15% (ride)')],
    referral: [],
  },
};

const plansOf = (kind: NamedPlanKind, app: string): Observable<NamedPlanRow[]> => of(PLANS[app]?.[kind] ?? []);
const api = {
  packageTemplate: vi.fn(), addPackageTemplate: vi.fn(), changePackageTemplate: vi.fn(),
  namedPlans: vi.fn(plansOf),
};
const uam = { list: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
const router = { navigate: vi.fn(() => Promise.resolve(true)) };
const dialogs = { confirm: vi.fn() };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};
const KEY = 'accessManagement.franchisePackages.';
const FOLLOW = `${KEY}plans.followRules`;

function create(id: string | null = null): { fixture: ComponentFixture<FranchisePackageForm>; component: FranchisePackageForm; el: HTMLElement } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    declarations: [FranchisePackageForm],
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
  const fixture = TestBed.createComponent(FranchisePackageForm);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

function fill(component: FranchisePackageForm): void {
  Object.assign(component.form, { name: 'Gold', applicationCode: MATRI });
}

function pickerOf(fixture: ComponentFixture<FranchisePackageForm>, kind: NamedPlanKind): HqSearchableSelect {
  const found = fixture.debugElement.queryAll(By.directive(HqSearchableSelect))
    .map((debug) => debug.componentInstance as HqSearchableSelect)
    .find((select) => select.inputId === `pkg-plan-${kind}`);
  if (!found) { throw new Error(`no picker for ${kind}`); }
  return found;
}

describe('FranchisePackageForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    history.replaceState(null, '');
    api.packageTemplate.mockReturnValue(of({ ...template, version: 4 }));
    api.addPackageTemplate.mockReturnValue(of(template));
    api.changePackageTemplate.mockReturnValue(of(template));
    uam.list.mockReturnValue(of({ content: [{ code: MATRI, name: 'Matrimony' }, { code: RIDE, name: 'Ride' }] }));
    dialogs.confirm.mockReturnValue(of(true));
    api.namedPlans.mockImplementation(plansOf);
  });

  describe('adding a package', () => {
    it('starts empty and active, following the rules that apply, with the applications to choose from', () => {
      const { component } = create();

      expect(component.form).toMatchObject({ name: '', active: true, feePlanCode: '', commissionPlanCode: '', referralPlanCode: '' });
      expect(component.titleKey).toBe(`${KEY}newTitle`);
      expect(component.applications.map((a) => a.code)).toEqual([MATRI, RIDE]);
      expect(component.isLoading).toBe(false);
      // No application yet: no plan is asked for.
      expect(api.namedPlans).not.toHaveBeenCalled();
    });

    it('refuses an incomplete form without calling the service', () => {
      const { component } = create();

      component.save();

      expect(api.addPackageTemplate).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith(expect.stringContaining('name'));
    });

    it('sends the package with no plan picked as null codes, says so and goes back to the list', () => {
      const { component } = create();
      fill(component);

      component.save();

      expect(api.addPackageTemplate).toHaveBeenCalledWith({
        name: 'Gold', applicationCode: MATRI, placeType: null, feePlanCode: null, commissionPlanCode: null, referralPlanCode: null,
        active: true,
      });
      expect(notify.success).toHaveBeenCalledWith(`${KEY}messages.added`);
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-packages']);
    });

    it('is for any place until a type of place is chosen, and then sends that type', () => {
      const { component } = create();
      fill(component);
      expect(component.form.placeType).toBe('');

      component.form.placeType = 'TOWN';
      component.save();

      expect(api.addPackageTemplate).toHaveBeenCalledWith(expect.objectContaining({ placeType: 'TOWN' }));
    });

    it('offers every type of place and names each through its translation (the code when it has none)', () => {
      const { component } = create();

      expect(component.placeTypes).toEqual(['STATE', 'DISTRICT', 'CITY', 'MUNICIPALITY', 'TOWN', 'MANDAL', 'WARD', 'VILLAGE']);
      // Without translation files the translate service answers the key itself, so the label is the key the map names for the type.
      expect(component.placeTypeLabel('TOWN')).toBe(LOCATION_TYPE_LABEL_KEYS['TOWN']);
      expect(component.placeTypeLabel('FORT')).toBe('FORT');
    });

    it('sends the picked commission and referral plan codes and never a fee plan', () => {
      const { component } = create();
      fill(component);
      component.onApplicationChange();
      component.pickPlan('commission', 'AGENT15');
      component.pickPlan('referral', 'REF2');

      component.save();

      expect(api.addPackageTemplate).toHaveBeenCalledWith(expect.objectContaining({
        feePlanCode: null, commissionPlanCode: 'AGENT15', referralPlanCode: 'REF2',
      }));
    });

    it('shows the service refusal (a taken name, an unknown plan) as given and stays on the page', () => {
      api.addPackageTemplate.mockReturnValue(throwError(() => ({ status: 400, error: { message: 'Unknown fee plan GOLD_FEE' } })));
      const { component } = create();
      fill(component);

      component.save();

      expect(notify.error).toHaveBeenCalledWith('Unknown fee plan GOLD_FEE');
      expect(component.saving).toBe(false);
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('plan pickers', () => {
    it('loads the named commission and referral plans of the chosen application, never fee plans', () => {
      const { component } = create();
      component.form.applicationCode = MATRI;

      component.onApplicationChange();

      expect(api.namedPlans).not.toHaveBeenCalledWith('fee', expect.anything());
      expect(api.namedPlans).toHaveBeenCalledWith('commission', MATRI);
      expect(api.namedPlans).toHaveBeenCalledWith('referral', MATRI);
      expect(component.pickers.commission.options.map((o) => [o.code, o.name])).toEqual([['AGENT15', 'Agent 15%']]);
      expect(component.pickers.referral.options.map((o) => o.code)).toEqual(['REF2']);
      expect(component.pickers.commission.loading).toBe(false);
    });

    it('offers "follow the rules that apply" first, then the plans', () => {
      const { fixture, component } = create();
      component.form.applicationCode = MATRI;
      component.onApplicationChange();
      fixture.componentRef.changeDetectorRef.detectChanges();

      expect(pickerOf(fixture, 'commission').listed).toEqual([FOLLOW, 'Agent 15%']);
      expect(pickerOf(fixture, 'referral').listed).toEqual([FOLLOW, 'Two percent']);
      expect(() => pickerOf(fixture, 'fee')).toThrow();
    });

    it('goes back to the rules that apply when the empty choice is picked', () => {
      const { component } = create('t1');

      component.pickPlan('referral', '');
      component.pickPlan('commission', null);

      expect(component.form.referralPlanCode).toBe('');
      expect(component.form.commissionPlanCode).toBe('');
      component.save();
      expect(api.changePackageTemplate).toHaveBeenCalledWith('t1', expect.objectContaining({ referralPlanCode: null, commissionPlanCode: null }));
    });

    it('reloads the options when the application changes and clears picks the new application does not list', () => {
      const { component } = create('t1');
      component.pickPlan('referral', 'REF2');
      api.namedPlans.mockClear();

      component.form.applicationCode = RIDE;
      component.onApplicationChange();

      expect(api.namedPlans).toHaveBeenCalledWith('commission', RIDE);
      expect(component.pickers.commission.options.map((o) => o.code)).toEqual(['AGENT15']);
      // REF2 belongs to the matrimony application; AGENT15 is a ride plan too, so it stays.
      expect(component.form).toMatchObject({ commissionPlanCode: 'AGENT15', referralPlanCode: '' });
    });

    it('keeps the picks of an opened package, even one the list does not show, under its known name', () => {
      api.namedPlans.mockImplementation((kind: NamedPlanKind) => of(kind === 'commission' ? [] : PLANS[MATRI][kind]));
      const { component } = create('t1');

      expect(component.form).toMatchObject({ feePlanCode: '', commissionPlanCode: 'AGENT15', referralPlanCode: '' });
      expect(component.pickers.commission.options).toEqual([{ code: 'AGENT15', name: 'Agent 15%', current: null }]);
      expect(component.hasPendingChanges()).toBe(false);
    });

    it('keeps a pick when its list cannot be loaded, and says the list failed', () => {
      const { component } = create('t1');
      api.namedPlans.mockImplementation((kind: NamedPlanKind) => kind === 'commission' ? throwError(() => new Error('down')) : of([]));

      component.onApplicationChange();

      expect(component.pickers.commission.loadFailed).toBe(true);
      expect(component.form.commissionPlanCode).toBe('AGENT15');
      expect(component.form.referralPlanCode).toBe('');
    });

    it('drops the answer for an application left behind', () => {
      const slow = new Subject<NamedPlanRow[]>();
      const { component } = create();
      api.namedPlans.mockImplementation((kind: NamedPlanKind, app: string) => app === MATRI ? slow : of(PLANS[app][kind]));
      component.form.applicationCode = MATRI;
      component.onApplicationChange();
      component.form.applicationCode = RIDE;
      component.onApplicationChange();

      slow.next(PLANS[MATRI].commission);
      slow.complete();

      expect(component.pickers.commission.options.map((o) => o.name)).toEqual(['Agent 15% (ride)']);
    });

    it('shows the picked plan\'s current terms in one line', () => {
      const { component } = create('t1');

      expect(component.planSummaryText('commission')).toBe('accessManagement.myEarnings.parties.AGENT 15%');
      expect(component.planSummaryText('referral')).toBe('');
    });

    it('shows a picker disabled with a hint, and does not ask for its plans, without the list privilege', async () => {
      granted = new Set(['FRANCHISE_PACKAGE_TEMPLATE_READ', 'FRANCHISE_PACKAGE_TEMPLATE_UPDATE', 'REFERRAL_PLAN_LIST']);
      const { fixture, component, el } = create('t1');
      await fixture.whenStable();
      fixture.componentRef.changeDetectorRef.detectChanges();

      expect(component.canList('commission')).toBe(false);
      expect(component.canList('referral')).toBe(true);
      expect(api.namedPlans).not.toHaveBeenCalledWith('commission', expect.anything());
      expect(api.namedPlans).toHaveBeenCalledWith('referral', MATRI);
      expect(pickerOf(fixture, 'commission').disabled).toBe(true);
      expect(pickerOf(fixture, 'referral').disabled).toBe(false);
      expect(el.textContent).toContain(`${KEY}plans.noAccess`);
      // The package's own pick still shows by name.
      expect(component.pickers.commission.options.map((o) => o.name)).toEqual(['Agent 15%']);
    });

    it('links to the plan screens the caller may open', () => {
      granted = new Set(['FRANCHISE_PACKAGE_TEMPLATE_CREATE', 'COMMISSION_PLAN_LIST']);
      const { el } = create();

      const links = Array.from(el.querySelectorAll('a.ffp__plan-link')).map((a) => a.textContent?.trim());
      expect(links).toEqual(['nav.items.franchiseCommissionPlans']);
    });
  });

  describe('changing a package', () => {
    it('reads the fresh copy, with its version and picks, when the caller may read one', () => {
      const { component } = create('t1');

      expect(api.packageTemplate).toHaveBeenCalledWith('t1');
      expect(component.form).toMatchObject({ name: 'Gold', version: 4, feePlanCode: '', commissionPlanCode: 'AGENT15' });
      expect(component.titleKey).toBe(`${KEY}editTitle`);
    });

    it('uses the row the list handed over when the caller may not read one', () => {
      granted = new Set(['FRANCHISE_PACKAGE_TEMPLATE_UPDATE']);
      history.replaceState({ template }, '');

      const { component } = create('t1');

      expect(api.packageTemplate).not.toHaveBeenCalled();
      expect(component.form).toMatchObject({ name: 'Gold', version: 3 });
    });

    it('says so, and shows no form, when the package cannot be read or was not handed over', () => {
      api.packageTemplate.mockReturnValue(throwError(() => ({ status: 404 })));
      const failed = create('t1');
      expect(failed.component.loadFailed).toBe(true);
      expect(failed.el.querySelector('form')).toBeNull();

      granted = new Set(['FRANCHISE_PACKAGE_TEMPLATE_UPDATE']);
      expect(create('t1').component.loadFailed).toBe(true);
    });

    it('changes the package with its id and goes back to the list', () => {
      const { component } = create('t1');

      component.save();

      expect(api.changePackageTemplate).toHaveBeenCalledWith('t1', expect.objectContaining({
        name: 'Gold', version: 4, feePlanCode: null, commissionPlanCode: 'AGENT15', referralPlanCode: null,
      }));
      expect(notify.success).toHaveBeenCalledWith(`${KEY}messages.changed`);
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-packages']);
    });
  });

  describe('leaving with unsaved changes', () => {
    it('has nothing pending until a field is changed, and not after saving', () => {
      const { component } = create('t1');
      expect(component.hasPendingChanges()).toBe(false);

      component.pickPlan('referral', 'REF2');
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

      component.form.name = 'Something';
      dialogs.confirm.mockReturnValue(of(false));
      component.confirmDiscardChanges().subscribe((v) => (answer = v));
      expect(dialogs.confirm).toHaveBeenCalledTimes(1);
      expect(answer).toBe(false);
    });

    it('goes back to the list on cancel', () => {
      create().component.cancel();

      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-packages']);
    });
  });

  it('lays the fields out in the form grid with a commission and a referral picker and no fee, commission or referral fields', () => {
    const { el } = create();

    expect(el.querySelector('form.ffp__grid')).not.toBeNull();
    expect(el.textContent).not.toContain(`${KEY}plans.fee`);
    expect(el.textContent).toContain(`${KEY}plans.commission`);
    expect(el.textContent).toContain(`${KEY}plans.referral`);
    expect(el.querySelector('#pkg-fixed')).toBeNull();
    expect(el.querySelector('app-franchise-lines-editor')).toBeNull();
  });

  it('stops listening for plan answers once the page is closed', () => {
    const { fixture, component } = create();
    fixture.destroy();
    api.namedPlans.mockClear();

    component.form.applicationCode = MATRI;
    component.onApplicationChange();

    expect(api.namedPlans).not.toHaveBeenCalled();
  });
});
