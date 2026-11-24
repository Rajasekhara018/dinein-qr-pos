import { ChangeDetectorRef, Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqProgress, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { FranchiseApi } from './franchise-api';
import { Franchise, FranchisePlan, Location } from './franchise.models';
import { PackageTemplate } from './franchise-packages.models';
import { FranchiseForm } from './franchise-form';
import { FranchisePlanPreviewService } from './franchise-plan-preview.service';
import { FranchisePlanPreview } from './franchise-plan-preview';
import { PlanPreview } from './franchise-plan-preview.models';

const planPreview = { preview: vi.fn(), rules: vi.fn() };
const PREVIEW: PlanPreview = {
  commission: { source: 'plan', planName: 'Gold', lines: [{ eventType: 'Ride completed', placeType: '', summary: 'Owner 15%' }] },
  referral: { source: 'rules', planName: '', lines: [] },
};

const MATRI = 'PASUPU_KUMKUMA_MATRIMONY_APP';
const RIDE = 'BESTRIDE_RIDER_APP';

const franchise: Franchise = {
  unitId: 'u1', code: 'RBM-00001', name: 'RBM-00001', ownerUserId: 'o1', status: 'ACTIVE', totalAgentLimit: 50, totalAgentCount: 3,
  applications: [
    { applicationCode: MATRI, agentLimit: 20, agentCount: 1, fee: { code: 'INR', value: 100000 } },
    { applicationCode: RIDE, agentLimit: 30, agentCount: 2, fee: { code: 'INR', value: 200000 } },
  ],
};

const offers = {
  defaultCurrency: 'INR',
  plans: [
    { id: 'p-in-ts', applicationCode: MATRI, countryCode: 'IN', countryName: 'India', stateCode: 'TS', stateName: 'Telangana', city: '', districtCode: '', agentLimit: 20, fee: { code: 'INR', value: 100000 } },
    { id: 'p-ae', applicationCode: MATRI, countryCode: 'AE', countryName: 'United Arab Emirates', stateCode: '', stateName: '', city: '', districtCode: '', agentLimit: 10, fee: { code: 'AED', value: 900 } },
    { id: 'p-ride', applicationCode: RIDE, countryCode: '', countryName: '', stateCode: '', stateName: '', city: '', districtCode: '', agentLimit: 30, fee: { code: 'INR', value: 200000 } },
  ],
};
const api = { list: vi.fn(), setStatus: vi.fn(), open: vi.fn(), addApplication: vi.fn(), updateApplication: vi.fn(), plans: vi.fn(), resolvePlan: vi.fn(), packageTemplates: vi.fn((_query?: { applicationCode?: string }) => of({ items: [] as PackageTemplate[] })), ruleInForce: vi.fn(), eventTypes: vi.fn(), changePackage: vi.fn() };

/** The place picker, without its own requests: the tests drive the form's handlers directly. */
@Component({ selector: 'app-location-picker', template: '', standalone: false })
class LocationPickerStub {
  @Input() countryCode = '';
  @Input() franchiseScoped = false;
  @Output() selected = new EventEmitter<Location | null>();
}

const town = { id: 'town-1', name: 'Nuzvid' } as Location;
const resolution = {
  plan: { applicationCode: MATRI, agentLimit: 12, fee: { code: 'INR', value: 2500 } }, rule: 'PARENT', inherited: true,
  source: { id: 'd1', type: 'DISTRICT', code: 'KR', name: 'Krishna' }, place: [],
};
const uam = { list: vi.fn() };
const router = { navigate: vi.fn(() => Promise.resolve(true)) };
const dialogs = { confirm: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

let routeId: string | null = null;

/** The page opened for a new franchise (no id), or for managing the franchise with that id. */
function create(id: string | null = null): { fixture: ComponentFixture<FranchiseForm>; component: FranchiseForm } {
  routeId = id;
  const fixture = TestBed.createComponent(FranchiseForm);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

describe('FranchiseForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    routeId = null;
    history.replaceState(null, '');
    dialogs.confirm.mockReturnValue(of(true));
    api.list.mockReturnValue(of([franchise]));
    api.plans.mockReturnValue(of(offers));
    api.ruleInForce.mockReturnValue(of(null));
    api.eventTypes.mockReturnValue(of([]));
    api.packageTemplates.mockImplementation(() => of({ items: [] as PackageTemplate[] }));
    planPreview.rules.mockReturnValue(of(null));
    planPreview.preview.mockReturnValue(of(PREVIEW));
    uam.list.mockReturnValue(of({ content: [{ code: MATRI, name: 'Matrimony' }, { code: RIDE, name: 'Ride' }, { code: 'FOOD_DELIVER_APP', name: 'Food' }] }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [FranchiseForm, LocationPickerStub, FranchisePlanPreview],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule, HqProgress],
      providers: [
        { provide: FranchiseApi, useValue: api },
        { provide: UamService, useValue: uam },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: Router, useValue: router },
        { provide: AppDialogService, useValue: dialogs },
        { provide: FranchisePlanPreviewService, useValue: planPreview },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
      ],
    });
  });

  it('opens a new franchise with a blank form, the applications to choose from, and nothing pending', () => {
    const { component } = create();

    expect(component.isNew).toBe(true);
    expect(component.titleKey).toBe('accessManagement.franchises.openTitle');
    expect(component.franchise).toBeNull();
    expect(component.form.rows).toHaveLength(1);
    expect(component.applications.map((a) => a.code)).toEqual([MATRI, RIDE, 'FOOD_DELIVER_APP']);
    expect(component.isLoading).toBe(false);
    expect(component.hasPendingChanges()).toBe(false);
  });

  it('tells the user when the applications cannot be loaded', () => {
    uam.list.mockReturnValue(throwError(() => new Error('down')));

    create();

    expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.applicationsUnavailable');
  });

  describe('preconfigured offers', () => {
    it('loads the offers and the default currency, and works out the user\'s country from the time zone', () => {
      vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({ timeZone: 'Asia/Kolkata' } as Intl.ResolvedDateTimeFormatOptions);

      const { component } = create();

      expect(component.plans).toHaveLength(3);
      expect(component.defaultCurrency).toBe('INR');
      expect(component.userCountry).toBe('IN');
      vi.restoreAllMocks();
    });

    it('never calls an empty offers list a failed load: it asks for an application first, then simply says there are none', () => {
      api.plans.mockReturnValue(of([]));
      const { fixture, component } = create();
      const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';

      // No application chosen yet: the box waits for it.
      expect(text()).toContain('accessManagement.franchises.regionChooseApplication');
      expect(text()).not.toContain('Could not load the list');

      // An application without preset offers: the box is off with its own placeholder, still no "could not load" notice.
      component.pickApplication(component.form.rows[0], component.applicationLabelOf(RIDE));
      fixture.detectChanges();
      expect(text()).not.toContain('accessManagement.franchises.regionChooseApplication');
      expect(text()).not.toContain('Could not load the list');
      expect(component.plansFor(component.form.rows[0])).toEqual([]);
    });

    it('carries on with hand-typed terms when the offers cannot be loaded', () => {
      api.plans.mockReturnValue(throwError(() => ({ status: 403 })));

      const { component } = create();

      expect(component.plans).toEqual([]);
      expect(component.defaultCurrency).toBe('INR');
      expect(component.plansFor({ applicationCode: MATRI, agentLimit: null, fee: null })).toEqual([]);
    });

    it('leaves the offer on "custom" and the fee empty when an application is chosen, even when offers exist', () => {
      vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({ timeZone: 'Asia/Kolkata' } as Intl.ResolvedDateTimeFormatOptions);
      const { component } = create();
      const row = { applicationCode: MATRI, agentLimit: null, fee: null, currency: null, planKey: '' };

      component.onApplicationChange(row);

      expect(component.plansFor(row).length).toBeGreaterThan(0);
      expect(row).toMatchObject({ agentLimit: null, fee: null, currency: null, planKey: '' });
      vi.restoreAllMocks();
    });

    it('lets any other offer be chosen, which replaces the places, fee and currency', () => {
      const { component } = create();
      const row = { applicationCode: MATRI, agentLimit: 20, fee: 100000, currency: 'INR', planKey: 'p-in-ts' };
      row.planKey = 'p-ae';

      component.onPlanChange(row);

      expect(row).toMatchObject({ agentLimit: 10, fee: 900, currency: 'AED' });
      expect(component.currencyOf(row)).toBe('AED');
    });

    it('keeps what was typed when "custom" is chosen, and clears the offer when the application has none', () => {
      const { component } = create();
      const typed = { applicationCode: MATRI, agentLimit: 7, fee: 12.5, currency: 'INR', planKey: '' };
      component.onPlanChange(typed);
      expect(typed).toMatchObject({ agentLimit: 7, fee: 12.5 });

      const none = { applicationCode: 'FOOD_DELIVER_APP', agentLimit: 7, fee: 12.5, currency: 'AED', planKey: 'x' };
      component.onApplicationChange(none);
      expect(none).toMatchObject({ planKey: '', currency: null });
      expect(component.currencyOf(none)).toBe('INR');
    });

    it('names each offer in words: its region, the fee in its own currency and the number of agents', () => {
      const { component } = create();
      const translate = TestBed.inject(TranslateService);
      translate.setTranslation('en', { accessManagement: { franchises: { planOption: '{{region}} ({{fee}}, {{agents}} agents)', regionGeneral: 'General' } } });
      translate.use('en');

      // digits are grouped the way the browser's locale does (1,00,000 or 100,000), so compare without the separators
      const ae = component.planLabel(offers.plans[1]);
      const general = component.planLabel(offers.plans[2]);
      expect(ae.replace(/,/g, '')).toMatch(/^United Arab Emirates \(.*900\.00.*10 agents\)$/);
      expect(general.replace(/,/g, '')).toMatch(/^General \(.*200000\.00.*30 agents\)$/);
    });

    it('fixes the places and fee once an offer is chosen, and unlocks them for custom terms', async () => {
      const { fixture, component } = create();
      const refresh = async (): Promise<void> => {
        fixture.debugElement.injector.get(ChangeDetectorRef).markForCheck();
        fixture.detectChanges();
        await fixture.whenStable(); // the form directive applies "disabled" a moment after the check
        fixture.detectChanges();
      };
      const numberInputs = (): boolean[] =>
        [...(fixture.nativeElement.querySelectorAll('input[type="number"], [data-testid="amount-box"]') as NodeListOf<HTMLInputElement>)].map((input) => input.disabled);
      component.form.rows = [{ applicationCode: MATRI, agentLimit: 20, fee: 100000, currency: 'INR', planKey: 'p-in-ts' }];
      await refresh();
      expect(numberInputs()).toEqual([true, true]);

      component.form.rows[0].planKey = '';
      await refresh();
      expect(numberInputs()).toEqual([false, false]);
    });

    it('rounds the fee to two decimals when typing is done, and shows the currency symbol and formatted amount', () => {
      const { component } = create();
      const row = { applicationCode: MATRI, agentLimit: 5, fee: 1999.999, currency: 'INR', planKey: '' };

      component.roundFee(row);

      expect(row.fee).toBe(2000);
      expect(component.symbolOf(row)).toBe('₹');
      expect(component.money(2000, 'INR')).toContain('2,000.00');
    });

    it('sends the chosen currency with the franchise when it is opened', () => {
      api.open.mockReturnValue(of(franchise));
      const { component } = create();
      component.form.ownerName = 'Ravi';
      component.form.ownerEmail = 'ravi@example.com';
      component.form.rows = [{ applicationCode: MATRI, agentLimit: 10, fee: 900, currency: 'AED', planKey: 'p-ae' }];

      component.submitOpening();

      expect(api.open).toHaveBeenCalledWith({
        applications: [{ applicationCode: MATRI, agentLimit: 10, fee: { code: 'AED', value: 900 } }], ownerEmail: 'ravi@example.com', ownerName: 'Ravi',
      });
    });
  });

  describe('opening a franchise', () => {
    it('previews the fee, agent places, commission and referral of a row, following its package, and hides it again', () => {
      const { component, fixture } = create();
      const row = component.form.rows[0];
      Object.assign(row, { applicationCode: MATRI, agentLimit: 12, fee: 900, currency: 'INR' });
      component.templatesByApplication[MATRI] = [{ id: 't1', name: 'Gold', applicationCode: MATRI, active: true, commissionPlanCode: 'GOLD', version: 1 }];

      component.togglePreview(row);
      fixture.detectChanges();
      expect(planPreview.preview).toHaveBeenCalledWith(MATRI, null);
      expect(component.previewOf(row)).toEqual({ open: true, loading: false, data: PREVIEW });
      const shown = fixture.nativeElement.querySelector('[data-testid="plan-preview"]') as HTMLElement;
      expect(shown.textContent).toContain('Owner 15%');
      expect(shown.textContent).toContain('12');

      row.templateId = 't1';
      component.onTemplateChange(row);
      expect(planPreview.preview).toHaveBeenLastCalledWith(MATRI, expect.objectContaining({ id: 't1' }));
      // Choosing the package keeps the terms already in the row.
      expect(row).toMatchObject({ agentLimit: 12, fee: 900, templateId: 't1' });

      component.togglePreview(row);
      expect(component.previewOf(row)?.open).toBe(false);
      planPreview.preview.mockClear();
      component.onTemplateChange(row);
      expect(planPreview.preview).not.toHaveBeenCalled();
    });

    it('goes back to the list on cancel', () => {
      create().component.cancel();

      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchises']);
    });

    it('does not send the franchise when the phone number of the owner is not valid', () => {
      const { component } = create();
      component.form.ownerName = 'Ravi';
      component.form.ownerEmail = 'ravi@example.com';
      component.form.rows = [{ applicationCode: MATRI, agentLimit: 5, fee: 1 }];

      component.submitOpening(true);

      expect(api.open).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.phone');
    });

    it('adds and removes application rows', () => {
      const { component } = create();

      component.addRow();
      expect(component.form.rows).toHaveLength(2);
      component.removeRow(0);
      expect(component.form.rows).toHaveLength(1);
    });

    it('does not offer an application another row has already picked', () => {
      const { component } = create();
      component.addRow();
      component.form.rows[0].applicationCode = MATRI;

      expect(component.optionsFor(1).map((a) => a.code)).toEqual([RIDE, 'FOOD_DELIVER_APP']);
      expect(component.optionsFor(0).map((a) => a.code)).toEqual([MATRI, RIDE, 'FOOD_DELIVER_APP']);
    });

    it('sends nothing and says why when the form is incomplete', () => {
      const { component } = create();

      component.submitOpening();

      expect(api.open).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.owner');
    });

    it('opens the franchise with the applications, fees and limits and continues with its setup', () => {
      api.open.mockReturnValue(of({ ...franchise, code: 'RBM-00002' }));
      const { component } = create();
      component.form.ownerName = 'Ravi';
      component.form.ownerEmail = 'ravi@example.com';
      component.form.rows = [{ applicationCode: MATRI, agentLimit: 20, fee: 100000 },
        { applicationCode: RIDE, agentLimit: 30, fee: 200000, locationId: 'town-1' }];

      component.submitOpening();

      expect(api.open).toHaveBeenCalledWith({
        applications: [{ applicationCode: MATRI, agentLimit: 20, fee: { code: 'INR', value: 100000 } },
          { applicationCode: RIDE, agentLimit: 30, fee: { code: 'INR', value: 200000 }, locationId: 'town-1' }],
        ownerEmail: 'ravi@example.com', ownerName: 'Ravi',
      });
      expect(notify.success).toHaveBeenCalledWith('accessManagement.franchises.messages.opened');
      expect(notify.info).toHaveBeenCalledWith('accessManagement.franchises.messages.signInAgain', 8000);
      expect(notify.warning).not.toHaveBeenCalled();
      // The setup of the new franchise continues with its legal details, KYC, bank account, agreement and activation.
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchises', 'u1', 'setup', 'legal'], { state: { name: 'RBM-00002' } });
      expect(component.hasPendingChanges()).toBe(false);
    });

    it('refuses to open a franchise for an application sold by place without a place', () => {
      const { component } = create();
      component.form.ownerName = 'Ravi';
      component.form.ownerEmail = 'ravi@example.com';
      component.form.rows = [{ applicationCode: RIDE, agentLimit: 30, fee: 200000 }];

      component.submitOpening();

      expect(api.open).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.placeRequired');
      expect(component.placeRequired(RIDE)).toBe(true);
      expect(component.placeRequired(MATRI)).toBe(false);
    });

    it('warns after opening when the organization is company-run, as the franchise will get no sales', () => {
      api.open.mockReturnValue(of({ ...franchise, code: 'RBM-00003', activeInDirectOrganization: true }));
      const { component } = create();
      component.form.ownerName = 'Ravi';
      component.form.ownerEmail = 'ravi@example.com';
      component.form.rows = [{ applicationCode: MATRI, agentLimit: 20, fee: 100000 }];

      component.submitOpening();

      expect(notify.info).toHaveBeenCalledWith('accessManagement.franchises.messages.signInAgain', 8000);
      expect(notify.warning).toHaveBeenCalledWith('accessManagement.franchises.messages.openedInDirect', 8000);
    });

    it('shows a refusal that names an owner field right under that field too, and clears it when the field is edited', () => {
      api.open.mockReturnValue(throwError(() => ({ error: { message: 'Owner phone is required' } })));
      const { fixture, component } = create();
      component.form.ownerName = 'Ravi';
      component.form.ownerEmail = 'ravi@example.com';
      component.form.rows = [{ applicationCode: MATRI, agentLimit: 5, fee: 1 }];

      component.submitOpening();
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(notify.error).toHaveBeenCalledWith('Owner phone is required');
      expect(el.querySelector('[data-testid="owner-phone-error"]')?.textContent).toContain('Owner phone is required');
      expect(el.querySelector('[data-testid="owner-email-error"]')).toBeNull();
      expect(el.querySelector('[data-testid="owner-name-error"]')).toBeNull();

      // Typing in the phone box (the input event bubbles up to its field) clears the message; a redraw alone does not.
      fixture.detectChanges();
      expect(el.querySelector('[data-testid="owner-phone-error"]')).not.toBeNull();
      el.querySelector('[data-testid="owner-phone-error"]')?.parentElement?.dispatchEvent(new Event('input', { bubbles: true }));
      fixture.detectChanges();
      expect(el.querySelector('[data-testid="owner-phone-error"]')).toBeNull();
    });

    it('shows a refusal that names no owner field only as a message', () => {
      api.open.mockReturnValue(throwError(() => ({ error: { detail: 'The role FRANCHISE_OWNER does not exist yet' } })));
      const { fixture, component } = create();
      component.form.ownerName = 'Ravi';
      component.form.ownerEmail = 'ravi@example.com';
      component.form.rows = [{ applicationCode: MATRI, agentLimit: 5, fee: 1 }];

      component.submitOpening();
      fixture.detectChanges();

      expect(component.openErrors).toEqual({});
      expect((fixture.nativeElement as HTMLElement).querySelector('.ffp__error')).toBeNull();
    });

    it('shows the reason UAM gave when opening fails, and keeps the form', () => {
      api.open.mockReturnValue(throwError(() => ({ error: { detail: 'The role FRANCHISE_OWNER does not exist yet' } })));
      const { component } = create();
      component.form.ownerName = 'Ravi';
      component.form.ownerEmail = 'ravi@example.com';
      component.form.rows = [{ applicationCode: MATRI, agentLimit: 5, fee: 1 }];

      component.submitOpening();

      expect(notify.error).toHaveBeenCalledWith('The role FRANCHISE_OWNER does not exist yet');
      expect(component.saving).toBe(false);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('falls back to a general message when UAM gives no reason', () => {
      api.open.mockReturnValue(throwError(() => ({})));
      const { component } = create();
      component.form.ownerName = 'Ravi';
      component.form.ownerEmail = 'ravi@example.com';
      component.form.rows = [{ applicationCode: MATRI, agentLimit: 5, fee: 1 }];

      component.submitOpening();

      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.messages.openFailed');
    });
  });

  describe('the place of a franchise', () => {
    function opened(): FranchiseForm {
      const { component } = create();
      component.form.rows = [{ applicationCode: MATRI, agentLimit: null, fee: null }];
      return component;
    }

    it('fills the agent places and fee from the plan that applies to the chosen place and says where it came from', () => {
      api.resolvePlan.mockReturnValue(of(resolution));
      const component = opened();
      const row = component.form.rows[0];

      component.onPlacePicked(row, town);

      expect(api.resolvePlan).toHaveBeenCalledWith(MATRI, 'town-1');
      expect(row).toMatchObject({ locationId: 'town-1', locationName: 'Nuzvid', agentLimit: 12, fee: 2500, currency: 'INR', planKey: '' });
      expect(row.placeNote).toEqual({ key: 'accessManagement.franchisePlans.resolve.inheritedFrom', params: { name: 'Krishna' } });
    });

    it('keeps what was typed and says no plan is set when none covers the place, but still records the place', () => {
      api.resolvePlan.mockReturnValue(throwError(() => new Error('No franchise plan applies')));
      const component = opened();
      const row = component.form.rows[0];
      row.agentLimit = 7;
      row.fee = 99;

      component.onPlacePicked(row, town);

      expect(row).toMatchObject({ locationId: 'town-1', agentLimit: 7, fee: 99 });
      expect(row.placeNote?.key).toBe('accessManagement.franchises.placePlanNone');
    });

    it('clears the place and its note when the choice is cleared, without asking for a plan', () => {
      api.resolvePlan.mockReturnValue(of(resolution));
      const component = opened();
      const row = component.form.rows[0];
      component.onPlacePicked(row, town);
      api.resolvePlan.mockClear();

      component.onPlacePicked(row, null);

      expect(row).toMatchObject({ locationId: '', locationName: '', placeNote: null });
      expect(api.resolvePlan).not.toHaveBeenCalled();
    });

    it('ignores the answer for a place that is no longer the chosen one', () => {
      const late = new Subject<typeof resolution>();
      const other = new Subject<typeof resolution>();
      api.resolvePlan.mockImplementation((_application: string, locationId: string) => (locationId === 'town-1' ? late : other));
      const component = opened();
      const row = component.form.rows[0];
      component.onPlacePicked(row, town);
      component.onPlacePicked(row, { id: 'town-2', name: 'Gudivada' } as Location);

      late.next(resolution);

      expect(row.locationId).toBe('town-2');
      expect(row.agentLimit).toBeNull();
      expect(row.placeNote).toBeNull();
    });

    it('asks again when the application is chosen after the place', () => {
      api.resolvePlan.mockReturnValue(of(resolution));
      const component = opened();
      const row = component.form.rows[0];
      row.applicationCode = '';
      component.onPlacePicked(row, town);
      expect(api.resolvePlan).not.toHaveBeenCalled();

      row.applicationCode = RIDE;
      component.onApplicationChange(row);

      expect(api.resolvePlan).toHaveBeenCalledWith(RIDE, 'town-1');
    });

    it('does not ask for the plan of a place without the privilege to, and offers the pickers only with the place lists', () => {
      granted = new Set(['FRANCHISE_CREATE']);
      const component = opened();
      expect(component.canPickPlaces).toBe(false);

      component.onPlacePicked(component.form.rows[0], town);
      expect(api.resolvePlan).not.toHaveBeenCalled();

      granted = new Set(['LOCATION_LIST', 'LOCATION_CHILDREN_LIST']);
      expect(component.canPickPlaces).toBe(true);
      expect(component.franchisePlaceOptions).toBe(false);
      granted = new Set(['LOCATION_LIST']);
      expect(component.canPickPlaces).toBe(false);
    });

    it('offers the pickers from the franchise form\'s own place list to whoever holds its two privileges', () => {
      granted = new Set(['FRANCHISE_CREATE', 'FRANCHISE_PLACE_OPTION_LIST', 'FRANCHISE_PLACE_OPTION_CHILDREN_LIST']);
      const component = opened();

      expect(component.canPickPlaces).toBe(true);
      expect(component.franchisePlaceOptions).toBe(true);
      granted = new Set(['FRANCHISE_CREATE', 'FRANCHISE_PLACE_OPTION_LIST']);
      expect(component.franchisePlaceOptions).toBe(false);
      expect(component.canPickPlaces).toBe(false);
    });

    it('says a place is needed and cannot be chosen when the place lists are not granted', () => {
      granted = new Set(['FRANCHISE_CREATE']);
      const { component } = create();
      component.form.rows[0].applicationCode = RIDE;

      // The template shows accessManagement.franchises.placeNoAccess exactly when both hold.
      expect(component.canPickPlaces).toBe(false);
      expect(component.placeRequired(component.form.rows[0].applicationCode)).toBe(true);
      component.submitOpening();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.owner');
    });

    it('starts the pickers from the person\'s own country, else India', () => {
      const component = opened();

      component.userCountry = 'AE';
      expect(component.placeCountry).toBe('AE');
      component.userCountry = null;
      expect(component.placeCountry).toBe('IN');
    });

    it('sends the place with the franchise being opened', () => {
      api.resolvePlan.mockReturnValue(of(resolution));
      api.open.mockReturnValue(of(franchise));
      const component = opened();
      component.form.ownerName = 'Ravi';
      component.form.ownerEmail = 'ravi@example.com';
      component.onPlacePicked(component.form.rows[0], town);

      component.submitOpening();

      expect(api.open).toHaveBeenCalledWith(expect.objectContaining({
        applications: [expect.objectContaining({ applicationCode: MATRI, agentLimit: 12, fee: { code: 'INR', value: 2500 }, locationId: 'town-1' })],
      }));
    });

    it('sets the place of an application the franchise already has without touching its terms, and sends it on save', () => {
      api.updateApplication.mockReturnValue(of(franchise));
      const { component } = create('u1');

      component.onEditPlacePicked(component.edits[MATRI], town);
      expect(component.edits[MATRI]).toMatchObject({ locationId: 'town-1', agentLimit: 20, fee: 100000 });
      expect(api.resolvePlan).not.toHaveBeenCalled();
      component.saveTerms(franchise.applications[0]);

      expect(api.updateApplication).toHaveBeenCalledWith('u1', expect.objectContaining({ applicationCode: MATRI, locationId: 'town-1' }));
    });

    it('shows the place of each application of a franchise, or a dash', () => {
      api.list.mockReturnValue(of([{ ...franchise, applications: [
        { ...franchise.applications[0], locationId: 'town-1', locationName: 'Nuzvid' }, franchise.applications[1]] }]));

      granted = new Set(['FRANCHISE_LIST', 'FRANCHISE_APPLICATION_UPDATE']); // no place lists: no picker, a dash instead
      const { fixture } = create('u1');

      const cells = Array.from(fixture.nativeElement.querySelectorAll('tbody tr')).map((r) => (r as HTMLElement).textContent);
      expect(cells[0]).toContain('Nuzvid');
      expect(cells[1]).toContain('\u2014');
    });
  });

  describe('managing a franchise', () => {
    it('shows the commission and referral in force for each application and where they come from', () => {
      api.ruleInForce.mockImplementation((kind: string, _id: string, app: string) => {
        if (kind === 'COMMISSION_PLAN' && app === MATRI) {
          return of({ kind, date: '2026-11-16', scope: { level: 'PLAN', planCode: 'GOLD' },
            rule: { id: 'c1', scope: { level: 'PLAN', planCode: 'GOLD' }, lines: [{ party: 'OWNER', valueType: 'PERCENTAGE', basisPoints: 1500 }] } });
        }
        if (kind === 'REFERRAL_PLAN' && app === RIDE) return throwError(() => ({ status: 500 }));
        return of(null);
      });
      history.replaceState({ franchise }, '');
      const { component, fixture } = create('u1');
      fixture.detectChanges();

      expect(api.ruleInForce).toHaveBeenCalledWith('COMMISSION_PLAN', 'u1', MATRI);
      expect(api.ruleInForce).toHaveBeenCalledWith('REFERRAL_PLAN', 'u1', RIDE);
      expect(component.columns.map((column) => column.key)).toContain('rules');
      expect(component.ruleOf(MATRI, 'COMMISSION_PLAN')).toEqual({
        sourceKey: 'accessManagement.franchises.rulesInForce.sources.plan', sourceParams: { code: 'GOLD' }, summary: expect.stringContaining('15%'),
      });
      expect(component.ruleOf(MATRI, 'REFERRAL_PLAN')).toBeNull();
      expect(component.ruleOf(RIDE, 'REFERRAL_PLAN')).toBe('failed');
      expect(fixture.nativeElement.querySelector('[data-testid="rule-COMMISSION_PLAN-' + MATRI + '"]')?.textContent).toContain('15%');
    });

    it("lists the application's rules by event when no rule for every event is in force", () => {
      planPreview.rules.mockImplementation((kind: string) => (kind === 'commission'
        ? of({ source: 'rules', planName: '', lines: [{ eventType: 'Ride completed', placeType: '', summary: 'Provider 100%' }] })
        : of({ source: 'rules', planName: '', lines: [] })));
      history.replaceState({ franchise }, '');
      const { component, fixture } = create('u1');
      fixture.detectChanges();

      expect(planPreview.rules).toHaveBeenCalledWith('commission', MATRI);
      expect(component.ruleOf(MATRI, 'COMMISSION_PLAN')).toEqual({
        sourceKey: 'accessManagement.franchises.rulesInForce.sources.byEvent', sourceParams: {}, summary: '',
        lines: [{ eventType: 'Ride completed', placeType: '', summary: 'Provider 100%' }],
      });
      expect(component.ruleOf(MATRI, 'REFERRAL_PLAN')).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="rule-COMMISSION_PLAN-' + MATRI + '"]')?.textContent).toContain('Provider 100%');
    });

    it('lists the commission in force for each event type of the application, with where each comes from', () => {
      api.eventTypes.mockReturnValue(of([{ code: 'RIDE_COMPLETED' }, { code: 'DRIVER_SUBSCRIPTION' }]));
      api.ruleInForce.mockImplementation((kind: string, _id: string, _app: string, eventType?: string) => (kind === 'COMMISSION_PLAN' && eventType === 'RIDE_COMPLETED'
        ? of({ kind, date: '2026-11-16', scope: { level: 'PLAN', planCode: 'GOLD' },
          rule: { id: 'c1', scope: { level: 'PLAN', planCode: 'GOLD', eventType }, lines: [{ party: 'OWNER', valueType: 'PERCENTAGE', basisPoints: 1500 }] } })
        : of(null)));
      history.replaceState({ franchise }, '');
      const { component, fixture } = create('u1');
      fixture.detectChanges();

      expect(api.eventTypes).toHaveBeenCalledWith(MATRI);
      expect(api.ruleInForce).toHaveBeenCalledWith('COMMISSION_PLAN', 'u1', MATRI, 'RIDE_COMPLETED');
      const rule = component.ruleOf(MATRI, 'COMMISSION_PLAN');
      expect(rule).toMatchObject({ sourceKey: 'accessManagement.franchises.rulesInForce.sources.byEvent' });
      expect(rule !== 'failed' && rule?.eventLines).toEqual([{
        eventType: 'Ride completed', summary: expect.stringContaining('15%'), sourceKey: 'accessManagement.franchises.rulesInForce.sources.plan',
        sourceParams: { code: 'GOLD' },
      }]);
      expect(planPreview.rules).not.toHaveBeenCalledWith('commission', MATRI);
    });

    it("shows each application's package and switches it, after asking, for someone who may", () => {
      const gold: PackageTemplate = { id: 't1', name: 'Gold', applicationCode: MATRI, active: true, version: 1 };
      api.packageTemplates.mockImplementation((query?: { applicationCode?: string }) => of({ items: query?.applicationCode === MATRI ? [gold] : [] }));
      api.changePackage.mockReturnValue(of({ ...franchise, applications: franchise.applications.map((a) => (a.applicationCode === MATRI ? { ...a, packageTemplateId: 't1' } : a)) }));
      history.replaceState({ franchise }, '');
      const { component, fixture } = create('u1');
      fixture.detectChanges();
      const matri = franchise.applications[0];

      expect(component.columns.map((column) => column.key)).toContain('package');
      expect(component.packageName(MATRI, '')).toBe('accessManagement.franchises.package.none');
      expect(component.packageChanged(matri)).toBe(false);
      component.pickPackage(matri, 't1');
      expect(component.packageChanged(matri)).toBe(true);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="apply-package-' + MATRI + '"]')).not.toBeNull();
      component.applyPackage(matri);

      expect(dialogs.confirm).toHaveBeenCalled();
      expect(api.changePackage).toHaveBeenCalledWith('u1', MATRI, 't1');
      expect(notify.success).toHaveBeenCalledWith('accessManagement.franchises.package.done');
      expect(component.currentPackage(component.franchise!.applications[0])).toBe('t1');
      expect(component.packageChanged(component.franchise!.applications[0])).toBe(false);
      expect(component.packageName(MATRI, 't1')).toBe('Gold');
      expect(component.packageName(MATRI, 'old')).toBe('old');
    });

    it('switches an application back to no package, and changes nothing when the administrator declines', () => {
      api.changePackage.mockReturnValue(of(franchise));
      history.replaceState({ franchise: { ...franchise, applications: [{ ...franchise.applications[0], packageTemplateId: 't1' }] } }, '');
      const { component } = create('u1');
      const matri = component.franchise!.applications[0];

      component.pickPackage(matri, '');
      dialogs.confirm.mockReturnValueOnce(of(false));
      component.applyPackage(matri);
      expect(api.changePackage).not.toHaveBeenCalled();

      component.applyPackage(matri);
      expect(api.changePackage).toHaveBeenCalledWith('u1', MATRI, null);
    });

    it('shows the package read-only to someone who may not switch it', () => {
      granted = new Set(['FRANCHISE_LIST']);
      history.replaceState({ franchise }, '');
      const { fixture } = create('u1');
      fixture.detectChanges();

      const cell = fixture.nativeElement.querySelector('[data-testid="package-' + MATRI + '"]') as HTMLElement;
      expect(cell.querySelector('hq-searchable-select')).toBeNull();
      expect(cell.textContent).toContain('accessManagement.franchises.package.none');
    });

    it('does not read or show the rules in force to someone without FRANCHISE_RULE_RESOLVE_READ', () => {
      granted = new Set(['FRANCHISE_LIST']);
      history.replaceState({ franchise }, '');
      const { component } = create('u1');

      expect(api.ruleInForce).not.toHaveBeenCalled();
      expect(component.columns.map((column) => column.key)).not.toContain('rules');
    });

    it('reports the franchise it read, and after a status change, to the setup stepper it is embedded in', () => {
      history.replaceState({ franchise }, '');
      routeId = 'u1';
      const fixture = TestBed.createComponent(FranchiseForm);
      const seen: Franchise[] = [];
      fixture.componentInstance.franchiseLoaded.subscribe((value) => seen.push(value));
      fixture.componentInstance.embedded = true;
      fixture.detectChanges();

      expect(seen).toEqual([franchise]);
      expect(fixture.nativeElement.querySelector('.ffp__header')).toBeNull();
      expect(fixture.nativeElement.querySelector('.ffp--embedded')).not.toBeNull();
      expect(fixture.componentInstance.missingFields()).toEqual([]);

      api.setStatus.mockReturnValue(of({ ...franchise, status: 'SUSPENDED' }));
      fixture.componentInstance.changeStatus('SUSPENDED');
      expect(seen[seen.length - 1].status).toBe('SUSPENDED');
    });

    it('opens with each application current limit and fee, from the row the list handed over', () => {
      history.replaceState({ franchise }, '');

      const { component } = create('u1');

      expect(api.list).not.toHaveBeenCalled();
      expect(component.isNew).toBe(false);
      expect(component.titleKey).toBe('accessManagement.franchises.editTitle');
      expect(component.edits[MATRI]).toEqual({ applicationCode: MATRI, agentLimit: 20, fee: 100000, currency: 'INR', planKey: '' });
      expect(component.hasPendingChanges()).toBe(false);
    });

    it('finds the franchise among the organization franchises when opened directly', () => {
      const { component } = create('u1');

      expect(api.list).toHaveBeenCalled();
      expect(component.franchise).toEqual(franchise);
    });

    it('says so, and shows nothing to change, when the franchise cannot be found or read', () => {
      const missing = create('gone');
      expect(missing.component.loadFailed).toBe(true);

      api.list.mockReturnValue(throwError(() => ({ status: 500 })));
      const failed = create('u1');
      expect(failed.component.loadFailed).toBe(true);
      expect(notify.error).toHaveBeenCalled();
    });

    it('offers only the applications the franchise does not have yet, and names them', () => {
      const { component } = create('u1');

      expect(component.addable().map((a) => a.code)).toEqual(['FOOD_DELIVER_APP']);
      expect(component.nameOf(MATRI)).toBe('Matrimony');
      expect(component.nameOf('UNKNOWN_APP')).toBe('UNKNOWN_APP');
    });

    it('saves a changed limit and fee for one application, and shows the franchise as the service answers', () => {
      const answer = { ...franchise, totalAgentLimit: 55, applications: [{ ...franchise.applications[0], agentLimit: 25, fee: { code: 'INR', value: 150000 } }, franchise.applications[1]] };
      api.updateApplication.mockReturnValue(of(answer));
      const { component } = create('u1');
      component.edits[MATRI].agentLimit = 25;
      component.edits[MATRI].fee = 150000;
      expect(component.hasPendingChanges()).toBe(true);

      component.saveTerms(franchise.applications[0]);

      expect(api.updateApplication).toHaveBeenCalledWith('u1', { applicationCode: MATRI, agentLimit: 25, fee: { code: 'INR', value: 150000 } });
      expect(notify.success).toHaveBeenCalledWith('accessManagement.franchises.messages.termsSaved');
      expect(component.franchise?.totalAgentLimit).toBe(55);
      expect(component.edits[MATRI].agentLimit).toBe(25);
      expect(component.hasPendingChanges()).toBe(false);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('refuses to save the terms of an application sold by place until it has a place, and saves once one is chosen', () => {
      api.updateApplication.mockReturnValue(of(franchise));
      const { component } = create('u1');

      component.saveTerms(franchise.applications[1]);
      expect(api.updateApplication).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.placeRequired');

      component.onEditPlacePicked(component.edits[RIDE], town);
      component.saveTerms(franchise.applications[1]);
      expect(api.updateApplication).toHaveBeenCalledWith('u1', expect.objectContaining({ applicationCode: RIDE, locationId: 'town-1' }));
    });

    it('refuses to add an application sold by place without a place', () => {
      const { component } = create('u1');
      component.newApplication = { applicationCode: 'FOOD_DELIVER_APP', agentLimit: 10, fee: 50000 };

      component.addApplication();

      expect(api.addApplication).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.placeRequired');
    });

    it('refuses to save an invalid limit or fee', () => {
      const { component } = create('u1');
      component.edits[MATRI].agentLimit = 0;

      component.saveTerms(franchise.applications[0]);

      expect(api.updateApplication).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.terms');
    });

    it('shows the reason UAM gave when a limit cannot be lowered below the agents placed', () => {
      api.updateApplication.mockReturnValue(throwError(() => ({ error: { detail: 'already has 1 agents' } })));
      const { component } = create('u1');

      component.saveTerms(franchise.applications[0]);

      expect(notify.error).toHaveBeenCalledWith('already has 1 agents');
      expect(component.saving).toBe(false);
    });

    it('adds another application to a franchise', () => {
      api.addApplication.mockReturnValue(of(franchise));
      const { component } = create('u1');
      component.newApplication = { applicationCode: 'FOOD_DELIVER_APP', agentLimit: 10, fee: 50000, locationId: 'town-1' };

      component.addApplication();

      expect(api.addApplication).toHaveBeenCalledWith('u1', { applicationCode: 'FOOD_DELIVER_APP', agentLimit: 10, fee: { code: 'INR', value: 50000 }, locationId: 'town-1' });
      expect(notify.success).toHaveBeenCalledWith('accessManagement.franchises.messages.applicationAdded');
      expect(component.newApplication.applicationCode).toBe('');
    });

    it('refuses to add an application without a choice, places or fee', () => {
      const { component } = create('u1');

      component.addApplication();

      expect(api.addApplication).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.terms');
    });

    it('adds an application from a package with a fee plan, keeping the fee already typed', () => {
      api.addApplication.mockReturnValue(of(franchise));
      const pkg: PackageTemplate = {
        id: 't1', name: 'Gold', applicationCode: 'FOOD_DELIVER_APP', active: true, version: 1,
        feePlanCode: 'GOLD_FEE', feePlanName: 'Gold fee', commissionPlanCode: null, commissionPlanName: null, referralPlanCode: 'REF2', referralPlanName: null,
      };
      api.packageTemplates.mockImplementation((query?: { applicationCode?: string }) => of({ items: query?.applicationCode === 'FOOD_DELIVER_APP' ? [pkg] : [] }));
      const { component } = create('u1');
      const translate = TestBed.inject(TranslateService);
      const instant = vi.spyOn(translate, 'instant');
      component.newApplication = { applicationCode: 'FOOD_DELIVER_APP', agentLimit: null, fee: 900, templateId: '', locationId: 'town-1' };
      component.loadTemplates('FOOD_DELIVER_APP');
      component.newApplication.templateId = 't1';

      component.onTemplateChange(component.newApplication);
      component.templatePlansText(component.newApplication);

      expect(component.newApplication).toMatchObject({ agentLimit: null, fee: 900, templateId: 't1' });
      expect(instant).toHaveBeenCalledWith('accessManagement.franchisePackages.templatePlans', {
        commission: 'accessManagement.franchisePackages.plans.rulesApply', referral: 'REF2',
      });
      component.addApplication();
      expect(api.addApplication).toHaveBeenCalledWith('u1', expect.objectContaining({ applicationCode: 'FOOD_DELIVER_APP', fee: expect.objectContaining({ value: 900 }), templateId: 't1' }));
      expect(api.addApplication.mock.calls[0][1]).not.toHaveProperty('agentLimit');
      expect(component.templatePlansText({ applicationCode: 'FOOD_DELIVER_APP', agentLimit: 1, fee: 1, templateId: '' })).toBe('');
    });

    it('reports a failure to add an application', () => {
      api.addApplication.mockReturnValue(throwError(() => ({})));
      const { component } = create('u1');
      component.newApplication = { applicationCode: 'FOOD_DELIVER_APP', agentLimit: 10, fee: 1, locationId: 'town-1' };

      component.addApplication();

      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.messages.applicationFailed');
    });
  });

  describe('leaving with unsaved changes', () => {
    it('asks before discarding edits, and leaves without asking when nothing changed', () => {
      const { component } = create();
      let answer: boolean | undefined;
      component.confirmDiscardChanges().subscribe((v) => (answer = v));
      expect(answer).toBe(true);
      expect(dialogs.confirm).not.toHaveBeenCalled();

      component.form.ownerName = 'Ravi';
      expect(component.hasPendingChanges()).toBe(true);
      dialogs.confirm.mockReturnValue(of(false));
      component.confirmDiscardChanges().subscribe((v) => (answer = v));
      expect(dialogs.confirm).toHaveBeenCalledTimes(1);
      expect(answer).toBe(false);
    });
  });

  describe('suspending, reactivating and closing', () => {
    const button = (el: HTMLElement, target: string): HTMLButtonElement | null => el.querySelector(`[data-testid="status-${target}"]`);

    it('offers suspend and close on an active franchise to someone who may change the status', () => {
      const { fixture } = create('u1');

      expect(button(fixture.nativeElement, 'SUSPENDED')).not.toBeNull();
      expect(button(fixture.nativeElement, 'CLOSED')).not.toBeNull();
      expect(button(fixture.nativeElement, 'ACTIVE')).toBeNull();
    });

    it('asks first, then suspends and shows what the server answered', () => {
      api.setStatus.mockReturnValue(of({ ...franchise, status: 'SUSPENDED' }));
      const { fixture, component } = create('u1');

      button(fixture.nativeElement, 'SUSPENDED')!.click();

      expect(dialogs.confirm).toHaveBeenCalledTimes(1);
      expect(api.setStatus).toHaveBeenCalledWith('u1', 'SUSPENDED');
      expect(component.franchise?.status).toBe('SUSPENDED');
      expect(notify.success).toHaveBeenCalled();
      fixture.detectChanges();
      expect(button(fixture.nativeElement, 'ACTIVE')).not.toBeNull();
    });

    it('changes nothing when the administrator declines', () => {
      dialogs.confirm.mockReturnValue(of(false));
      const { fixture, component } = create('u1');

      button(fixture.nativeElement, 'CLOSED')!.click();

      expect(api.setStatus).not.toHaveBeenCalled();
      expect(component.franchise?.status).toBe('ACTIVE');
    });

    it('shows the reason the server gave when the change is refused', () => {
      api.setStatus.mockReturnValue(throwError(() => ({ error: { detail: 'Another active franchise covers its place' } })));
      const { fixture, component } = create('u1');

      button(fixture.nativeElement, 'CLOSED')!.click();

      expect(notify.error).toHaveBeenCalledWith('Another active franchise covers its place');
      expect(component.franchise?.status).toBe('ACTIVE');
      expect(component.changingStatus).toBe(false);
    });

    it('offers nothing on a closed franchise, which stays closed', () => {
      api.list.mockReturnValue(of([{ ...franchise, status: 'CLOSED' }]));
      const { fixture, component } = create('u1');

      expect(component.statusTargets(component.franchise!)).toEqual([]);
      expect(button(fixture.nativeElement, 'SUSPENDED')).toBeNull();
    });

    it('hides the status actions from someone without the privilege', () => {
      granted = new Set(['FRANCHISE_LIST', 'FRANCHISE_APPLICATION_UPDATE']);
      const { fixture } = create('u1');

      expect(button(fixture.nativeElement, 'SUSPENDED')).toBeNull();
    });

    it('names an unknown status by its code', () => {
      const { component } = create('u1');

      expect(component.statusLabel('ODD')).toBe('ODD');
      expect(component.statusLabel('CLOSED')).toBe('accessManagement.franchises.statuses.CLOSED');
    });
  });

  it('adds the place type to an offer that has one, so places sharing a name can be told apart', () => {
    const { component } = create();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      accessManagement: { franchises: { planOption: '{{region}} ({{fee}}, {{agents}} agents)', regionGeneral: 'General' }, franchiseLocations: { types: { CITY: 'City' } } },
    });
    translate.use('en');

    const typed = component.planLabel({ ...offers.plans[0], city: 'Hyderabad', locationType: 'CITY' } as FranchisePlan);
    const untyped = component.planLabel(offers.plans[0] as FranchisePlan);

    expect(typed).toContain('India - Telangana - Hyderabad · City (');
    expect(untyped).not.toContain('·');
  });
});
