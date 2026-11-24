import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { FranchisePlan } from './franchise.models';
import { FranchiseTypeFees } from './franchise-type-fees';

const APP = { code: 'MATRI', name: 'Matrimony' };
const COUNTRIES = [{ code: 'IN', name: 'India' }];

const saved = (over: Partial<FranchisePlan>): FranchisePlan => ({
  id: 'd1', applicationCode: 'MATRI', countryCode: 'IN', countryName: 'India', stateCode: '', stateName: '', city: '', districtCode: '',
  agentLimit: 25, fee: { code: 'INR', value: 100000 }, scope: 'TYPE_DEFAULT', scopeType: 'DISTRICT', scopeLocationId: null, ...over,
});

const api = { plansPage: vi.fn(), addPlan: vi.fn(), changePlan: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
let granted: Set<string>;
const access = { changes$: EMPTY, can: (code: string | readonly string[]) => [code].flat().some((c) => granted.has(c)) };

function create(): { fixture: ComponentFixture<FranchiseTypeFees>; component: FranchiseTypeFees } {
  const fixture = TestBed.createComponent(FranchiseTypeFees);
  const component = fixture.componentInstance;
  component.applications = [APP] as never;
  component.countries = COUNTRIES;
  fixture.detectChanges();
  return { fixture, component };
}

function open(component: FranchiseTypeFees): void {
  component.pickApplication('Matrimony (MATRI)');
  component.countryCode = 'IN';
  component.onCountryChange();
}

describe('FranchiseTypeFees', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = new Set(['FRANCHISE_PLAN_CREATE', 'FRANCHISE_PLAN_UPDATE']);
    api.plansPage.mockReturnValue(of({ plans: [saved({})], totalElements: 1, defaultCurrency: 'INR' }));
    api.addPlan.mockReturnValue(of({}));
    api.changePlan.mockReturnValue(of({}));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [FranchiseTypeFees],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: FranchiseApi, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
  });

  it('reads nothing until an application and a country are chosen, then fills a line per type from the saved type defaults', () => {
    const { component } = create();
    expect(component.ready).toBe(false);
    expect(api.plansPage).not.toHaveBeenCalled();

    open(component);

    expect(api.plansPage).toHaveBeenCalledWith({ applicationCode: 'MATRI', scope: 'TYPE_DEFAULT', page: 0, size: 200 });
    expect(component.rows.map((r) => r.type)).toEqual(['STATE', 'DISTRICT', 'CITY', 'MUNICIPALITY', 'TOWN', 'MANDAL', 'WARD', 'VILLAGE']);
    expect(component.rows.find((r) => r.type === 'DISTRICT')).toMatchObject({ planId: 'd1', agentLimit: 25, fee: 100000 });
  });

  it('creates a type-default plan for a new line and changes the saved one, and reloads and tells the list afterwards', () => {
    const { component } = create();
    const emitted: unknown[] = [];
    component.saved.subscribe(() => emitted.push(true));
    open(component);
    const state = component.rows.find((r) => r.type === 'STATE')!;
    const district = component.rows.find((r) => r.type === 'DISTRICT')!;
    state.agentLimit = 10;
    state.fee = 50000;
    district.fee = 120000;
    api.plansPage.mockClear();

    component.save();

    expect(api.addPlan).toHaveBeenCalledTimes(1);
    expect(api.addPlan).toHaveBeenCalledWith(expect.objectContaining({
      applicationCode: 'MATRI', scopeType: 'STATE', agentLimit: 10, fee: { code: 'INR', value: 50000 }, countryCode: 'IN',
    }));
    expect(api.changePlan).toHaveBeenCalledWith('d1', expect.objectContaining({ scopeType: 'DISTRICT', agentLimit: 25, fee: { code: 'INR', value: 120000 } }));
    expect(notify.success).toHaveBeenCalled();
    expect(api.plansPage).toHaveBeenCalledTimes(1);
    expect(emitted).toHaveLength(1);
  });

  it('refuses to save while a line has only one of its two values, naming the type', () => {
    const { component } = create();
    open(component);
    component.rows.find((r) => r.type === 'TOWN')!.agentLimit = 4;

    component.save();

    expect(api.addPlan).not.toHaveBeenCalled();
    expect(notify.error).toHaveBeenCalledTimes(1);
    expect(component.canSave).toBe(false);
  });

  it('has nothing to save when no line changed, and a cleared saved line does not delete its plan', () => {
    const { component } = create();
    open(component);
    expect(component.canSave).toBe(false);

    const district = component.rows.find((r) => r.type === 'DISTRICT')!;
    district.agentLimit = null;
    district.fee = null;
    component.save();

    expect(component.canSave).toBe(false);
    expect(api.addPlan).not.toHaveBeenCalled();
    expect(api.changePlan).not.toHaveBeenCalled();
  });

  it('shows the service\'s message when saving fails, and reloads so the grid shows what is really saved', () => {
    api.addPlan.mockReturnValue(throwError(() => ({ status: 409, error: { detail: 'A plan for this application and area already exists' } })));
    const { component } = create();
    open(component);
    const town = component.rows.find((r) => r.type === 'TOWN')!;
    town.agentLimit = 2;
    town.fee = 10;
    api.plansPage.mockClear();

    component.save();

    expect(notify.error).toHaveBeenCalledWith('A plan for this application and area already exists');
    expect(component.saving).toBe(false);
    expect(api.plansPage).toHaveBeenCalledTimes(1);
  });

  it('lets someone with only the create privilege add lines but not change a saved one, and the reverse', () => {
    const { component } = create();
    open(component);
    const district = component.rows.find((r) => r.type === 'DISTRICT')!;
    const state = component.rows.find((r) => r.type === 'STATE')!;

    granted = new Set(['FRANCHISE_PLAN_CREATE']);
    expect(component.canEdit(state)).toBe(true);
    expect(component.canEdit(district)).toBe(false);

    granted = new Set(['FRANCHISE_PLAN_UPDATE']);
    expect(component.canEdit(state)).toBe(false);
    expect(component.canEdit(district)).toBe(true);
  });

  it('edits a line’s fee through the shared amount field, which keeps one stable value per line', () => {
    const { component, fixture } = create();
    open(component);
    fixture.detectChanges();
    const row = component.rows[0];

    const first = component.feeAmount(row);
    expect(component.feeAmount(row)).toBe(first);
    expect(first).toEqual({ code: row.currency, value: row.fee });

    component.setFee(row, { code: row.currency, value: 1234.5 });
    expect(row.fee).toBe(1234.5);
    expect(component.feeAmount(row)).toEqual({ code: row.currency, value: 1234.5 });
    component.setFee(row, null);
    expect(row.fee).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="fee-amount"]')).not.toBeNull();
  });

  it("shows each line's wallet amount and per-agent share read-only, following the backend's multiplier and the typed values", () => {
    api.plansPage.mockReturnValue(of({ plans: [saved({})], totalElements: 1, defaultCurrency: 'INR', agentWalletMultiplier: 3 }));
    const { component, fixture } = create();
    open(component);
    fixture.detectChanges();

    expect(component.walletMultiplier).toBe(3);
    const row = component.rows[0];
    row.fee = 1000;
    row.agentLimit = 4;
    expect(component.wallet(row)).toBe(3000);
    expect(component.perAgent(row)).toBe(750);
    row.agentLimit = null;
    expect(component.perAgent(row)).toBeNull();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="wallet-note"]')).not.toBeNull();
    expect(component.columns.map((c) => c.key)).toContain('perAgent');
  });

  it('falls back to twice the fee when the backend names no multiplier', () => {
    const { component } = create();
    open(component);
    expect(component.walletMultiplier).toBe(2);
  });

  it('shows the whole section only to someone who may create or change plans', () => {
    granted = new Set();
    const { fixture } = create();
    expect((fixture.nativeElement as HTMLElement).querySelector('section.tf')).toBeNull();
  });

  it('shows the section, its filters and the hint to choose first to someone who may create plans', () => {
    granted = new Set(['FRANCHISE_PLAN_CREATE']);
    const el = (create().fixture.nativeElement as HTMLElement);

    expect(el.querySelector('section.tf')).not.toBeNull();
    expect(el.querySelectorAll('.tf__filters .tf__field').length).toBe(2);
    expect(el.querySelector('.tf__empty')).not.toBeNull();
  });

  it('keeps an error state when the saved plans cannot be read', () => {
    api.plansPage.mockReturnValue(throwError(() => ({ status: 500 })));
    const { component } = create();
    open(component);

    expect(component.failed).toBe(true);
    expect(component.rows).toEqual([]);
    expect(notify.error).toHaveBeenCalled();
  });
});
