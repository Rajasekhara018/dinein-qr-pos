import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterModule, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { FranchiseDriver, PageResult } from './franchise.models';
import { FranchiseDriversScreen } from './franchise-drivers';
import {
  FRANCHISE_DRIVERS_PAGE_SIZE, FRANCHISE_DRIVERS_PERMISSION, FRANCHISE_DRIVERS_PRIVILEGES, FRANCHISE_DRIVERS_SCREEN_KEY,
} from './franchise-drivers.constants';
import { driverErrorKey, driverOnboardingQuery } from './franchise-drivers.helpers';

const page = (items: FranchiseDriver[], totalElements = items.length): PageResult<FranchiseDriver> => ({
  items, page: 0, size: FRANCHISE_DRIVERS_PAGE_SIZE, totalElements,
});
const ravi: FranchiseDriver = {
  memberId: 'm1', userId: 'u1', name: 'Ravi Kumar', phone: '+919876543210', email: null, onboardedAt: '2026-11-06T20:00:00Z',
};

const api = { drivers: vi.fn(), mine: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
const timezone = { effectiveTimezone: () => 'Asia/Kolkata' };
let routeId: string | null = 'f1';
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

function create() {
  const fixture = TestBed.createComponent(FranchiseDriversScreen);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

describe('FranchiseDriversScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    routeId = 'f1';
    history.replaceState(null, '');
    api.drivers.mockReturnValue(of(page([ravi])));
    api.mine.mockReturnValue(of([{ unitId: 'f0', code: 'RBM-0' }, { unitId: 'f1', code: 'RBM-1' }]));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [FranchiseDriversScreen],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      imports: [RouterModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        provideRouter([]),
        { provide: FranchiseApi, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: HqTimezoneService, useValue: timezone },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
      ],
    });
  });

  afterEach(() => history.replaceState(null, ''));

  it('declares its screen permission and a stable audit screen key', () => {
    expect(FRANCHISE_DRIVERS_PERMISSION).toBe('view_franchise_drivers');
    expect(FRANCHISE_DRIVERS_SCREEN_KEY).toBe('access_management.franchise_drivers');
  });

  it('reads the first page of the franchise\'s drivers and shows them, the joined day in the reader\'s time zone', () => {
    const { component, el } = create();

    expect(api.drivers).toHaveBeenCalledWith('f1', 0, FRANCHISE_DRIVERS_PAGE_SIZE);
    expect(component.drivers).toEqual([ravi]);
    expect(component.total).toBe(1);
    expect(component.isLast).toBe(true);
    expect(el.textContent).toContain('Ravi Kumar');
    // 20:00 UTC on 6 November is already 7 November in India
    expect(el.textContent).toContain('2026-11-07');
    expect(component.day(null)).toBe('');
  });

  it('reads the franchise code from the owner\'s franchises when it was not handed over', () => {
    const { component } = create();

    expect(api.mine).toHaveBeenCalled();
    expect(component.franchiseCode).toBe('RBM-1');
  });

  it('uses the code handed over by the opening screen without reading the franchises again', () => {
    history.replaceState({ code: 'RBM-7' }, '');
    const { component } = create();

    expect(api.mine).not.toHaveBeenCalled();
    expect(component.franchiseCode).toBe('RBM-7');
  });

  it('leaves the code empty when the owner\'s franchises cannot be read or the privilege is missing', () => {
    api.mine.mockReturnValue(throwError(() => ({ status: 503 })));
    expect(create().component.franchiseCode).toBe('');

    granted = new Set([FRANCHISE_DRIVERS_PRIVILEGES.listDrivers]);
    api.mine.mockClear();
    const { component } = create();
    expect(api.mine).not.toHaveBeenCalled();
    expect(component.franchiseCode).toBe('');
  });

  it('pages and changes the page size', () => {
    api.drivers.mockReturnValue(of(page([ravi], 45)));
    const { component } = create();
    expect(component.isLast).toBe(false);

    component.goToPage(2);
    expect(api.drivers).toHaveBeenLastCalledWith('f1', 2, FRANCHISE_DRIVERS_PAGE_SIZE);
    expect(component.isLast).toBe(true);

    component.onPageSizeChange(50);
    expect(component.pageSize).toBe(50);
    expect(api.drivers).toHaveBeenLastCalledWith('f1', 0, 50);

    component.goToPage(-3);
    expect(component.page).toBe(0);
  });

  it('shows the empty state when the franchise has no drivers', () => {
    api.drivers.mockReturnValue(of(page([])));
    const { component, el } = create();

    expect(component.drivers).toEqual([]);
    expect(el.querySelector('hq-common-table')?.textContent).toContain('accessManagement.franchiseDrivers.noDrivers');
  });

  it('shows the error for the answer\'s status and tells the user', () => {
    api.drivers.mockReturnValue(throwError(() => ({ status: 404 })));
    const { component, el } = create();

    expect(component.result).toBeNull();
    expect(component.isLoading).toBe(false);
    expect(component.errorMessage).toBe('accessManagement.franchiseDrivers.errors.notFound');
    expect(notify.error).toHaveBeenCalledWith('accessManagement.franchiseDrivers.errors.notFound');
    expect(el.querySelector('[data-testid="drivers-error"]')?.textContent).toContain('errors.notFound');
    expect(component.drivers).toEqual([]);
    expect(component.total).toBe(0);
  });

  it('does not read the list without the list privilege or without a franchise', () => {
    granted = new Set([FRANCHISE_DRIVERS_PRIVILEGES.onboardDriver]);
    const { component, el } = create();
    expect(api.drivers).not.toHaveBeenCalled();
    expect(component.result).toBeNull();
    expect(el.querySelector('hq-common-table')).toBeNull();

    granted = null;
    routeId = null;
    create();
    expect(api.drivers).not.toHaveBeenCalled();
  });

  it('opens the admin remote\'s onboarding form with the franchise preset', () => {
    history.replaceState({ code: 'RBM-1' }, '');
    const { el } = create();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (el.querySelector('[data-testid="onboard-driver"]') as HTMLButtonElement).click();

    expect(navigate).toHaveBeenCalledWith(['/admin', 'franchise-drivers', 'onboard'],
      { queryParams: { franchiseId: 'f1', franchiseCode: 'RBM-1' } });
  });

  it('does not navigate without a franchise', () => {
    routeId = null;
    const { component } = create();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    component.onboardDriver();

    expect(navigate).not.toHaveBeenCalled();
  });

  it('hides the onboard button without its privilege', () => {
    granted = new Set([FRANCHISE_DRIVERS_PRIVILEGES.listDrivers]);
    const { el } = create();

    expect(el.querySelector('[data-testid="onboard-driver"]')).toBeNull();
  });

  it('stops listening when the screen closes', () => {
    const { fixture, component } = create();
    fixture.destroy();
    // a late answer after destroy must not throw
    expect(() => component.ngOnDestroy()).not.toThrow();
  });
});

describe('franchise driver helpers', () => {
  it('maps the answer status to a message key, with a fallback', () => {
    expect(driverErrorKey(403)).toBe('accessManagement.franchiseDrivers.errors.forbidden');
    expect(driverErrorKey(503)).toBe('accessManagement.franchiseDrivers.errors.unavailable');
    expect(driverErrorKey(500)).toBe('accessManagement.franchiseDrivers.errors.generic');
    expect(driverErrorKey(undefined)).toBe('accessManagement.franchiseDrivers.errors.generic');
  });

  it('presets the franchise id, and the code only when known', () => {
    expect(driverOnboardingQuery('f1', 'RBM-1')).toEqual({ franchiseId: 'f1', franchiseCode: 'RBM-1' });
    expect(driverOnboardingQuery('f1', '')).toEqual({ franchiseId: 'f1' });
  });
});
