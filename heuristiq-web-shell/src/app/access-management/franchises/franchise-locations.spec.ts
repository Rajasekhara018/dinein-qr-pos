import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { Router } from '@angular/router';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseApi } from './franchise-api';
import { Location } from './franchise.models';
import { FranchiseLocations } from './franchise-locations';

const krishna: Location = {
  id: 'kr', countryCode: 'IN', type: 'DISTRICT', level: 'REGION', levelNumber: 1, code: 'AP-KR', name: 'Krishna', parentId: 'ap',
  parent: { id: 'ap', type: 'STATE', level: 'REGION', code: 'AP', name: 'Andhra Pradesh' }, active: true, sortOrder: 2, version: 4,
};
const nuzvid: Location = {
  id: 'nz', countryCode: 'IN', type: 'TOWN', level: 'URBAN', levelNumber: 2, code: 'NUZVID', name: 'Nuzvid', parentId: 'kr',
  parent: { id: 'kr', type: 'DISTRICT', level: 'REGION', code: 'AP-KR', name: 'Krishna' }, active: false, sortOrder: 0, version: 7,
};

const api = {
  locations: vi.fn(), location: vi.fn(), addLocation: vi.fn(), changeLocation: vi.fn(), setLocationActive: vi.fn(),
};
const notify = { success: vi.fn(), error: vi.fn() };
const router = { navigate: vi.fn(() => Promise.resolve(true)) };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

function configure(): void {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    declarations: [FranchiseLocations],
    imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
    providers: [
      { provide: FranchiseApi, useValue: api },
      { provide: NotificationService, useValue: notify },
      { provide: HqPrivilegeAccessService, useValue: access },
      { provide: Router, useValue: router },
    ],
    schemas: [NO_ERRORS_SCHEMA],
  });
}

function create(): { fixture: ComponentFixture<FranchiseLocations>; component: FranchiseLocations; el: HTMLElement } {
  const fixture = TestBed.createComponent(FranchiseLocations);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

const KEY = 'accessManagement.franchiseLocations.';

describe('FranchiseLocations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    api.locations.mockReturnValue(of({ items: [krishna, nuzvid], page: 0, size: 20, totalElements: 2 }));
    api.location.mockReturnValue(of({ ...nuzvid, version: 8 }));
    api.addLocation.mockReturnValue(of(nuzvid));
    api.changeLocation.mockReturnValue(of(nuzvid));
    api.setLocationActive.mockReturnValue(of(nuzvid));
    configure();
  });

  it('lists the first page of places, with each type next to its level', () => {
    const { component, el } = create();

    expect(api.locations).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(component.items.map((place) => place.id)).toEqual(['kr', 'nz']);
    expect(component.total).toBe(2);
    const text = el.textContent as string;
    expect(text).toContain(`${KEY}types.DISTRICT`);
    expect(text).toContain(`${KEY}levels.REGION`);
    expect(text).toContain(`${KEY}types.TOWN`);
    expect(text).toContain(`${KEY}levels.URBAN`);
  });

  it('shows the raw code for a type or level that has no label', () => {
    const { component } = create();

    expect(component.typeLabel('FORT')).toBe('FORT');
    expect(component.levelLabel('GALAXY')).toBe('GALAXY');
  });

  it('does not ask for places without the list privilege', () => {
    granted = new Set(['LOCATION_CREATE']);

    const { component } = create();

    expect(api.locations).not.toHaveBeenCalled();
    expect(component.items).toEqual([]);
  });

  it('sends the filters, and clears them', () => {
    const { component } = create();
    component.filters = { countryCode: 'IN', type: 'TOWN', level: 'URBAN', active: 'false', q: ' nuz ' };

    component.load();
    expect(api.locations).toHaveBeenLastCalledWith({ page: 0, size: 20, countryCode: 'IN', type: 'TOWN', level: 'URBAN', active: false, q: ' nuz ' });

    component.clearFilters();
    expect(api.locations).toHaveBeenLastCalledWith({ page: 0, size: 20 });
    expect(component.filters.q).toBe('');
  });

  it('drills into the places under a place and back out', () => {
    const { component } = create();

    component.drillInto(krishna);
    expect(api.locations).toHaveBeenLastCalledWith({ page: 0, size: 20, parentId: 'kr' });
    expect(component.parent).toEqual({ id: 'kr', name: 'Krishna' });

    component.clearParent();
    expect(api.locations).toHaveBeenLastCalledWith({ page: 0, size: 20 });
  });

  it('pages through the places with the common footer, and starts again from the first page when the page size changes', () => {
    api.locations.mockReturnValue(of({ items: [krishna], page: 0, size: 20, totalElements: 45 }));
    const { component } = create();

    expect(component.totalPages).toBe(3);
    expect(component.page).toBe(0);
    component.goToPage(1);
    expect(api.locations).toHaveBeenLastCalledWith({ page: 1, size: 20 });
    component.goToPage(2);
    expect(component.page).toBe(2);

    component.onPageSizeChange(50);
    expect(api.locations).toHaveBeenLastCalledWith({ page: 0, size: 50 });
    expect(component.page).toBe(0);
    expect(component.totalPages).toBe(1);
  });

  it('draws the places in the common table: type and status as badges, the columns translated', () => {
    const { fixture, component } = create();
    const el: HTMLElement = fixture.nativeElement;

    expect(component.columns.map((c) => c.key)).toEqual(['name', 'type', 'code', 'countryCode', 'parent.name', 'active', 'sortOrder']);
    expect(el.querySelector('hq-common-table table.common-table')).not.toBeNull();
    expect(el.querySelector('hq-table-footer')).not.toBeNull();
    expect(el.querySelectorAll('tbody td .franchise-badge').length).toBeGreaterThan(0);
    expect(el.querySelector('table.franchise-table')).toBeNull();
    expect(el.querySelector('.franchise-pager')).toBeNull();
  });

  it('tells the user when the places cannot be loaded', () => {
    api.locations.mockReturnValue(throwError(() => ({ status: 500 })));

    const { component } = create();

    expect(component.hasError).toBe(true);
    expect(notify.error).toHaveBeenCalledWith(`${KEY}errorMessage`);
  });

  describe('privileges', () => {
    it('shows no change actions to someone who may only view', () => {
      granted = new Set(['LOCATION_LIST']);

      const { el } = create();

      expect(el.querySelector('.table-create-btn')).toBeNull();
      expect(el.querySelector('.location-import-toggle')).toBeNull();
      expect(el.querySelector('.row-action-btn')).toBeNull();
    });

    it('shows each action only with its own privilege', () => {
      granted = new Set(['LOCATION_LIST', 'LOCATION_UPDATE']);
      const edit = create().el;
      expect(edit.querySelectorAll('.row-action-btn').length).toBe(2);
      expect(edit.querySelector('.row-action-btn--toggle')).toBeNull();
      expect(edit.querySelector('.table-create-btn')).toBeNull();

      configure();
      granted = new Set(['LOCATION_LIST', 'LOCATION_STATUS_UPDATE', 'LOCATION_CREATE', 'LOCATION_IMPORT_CREATE']);
      const manage = create().el;
      expect(manage.querySelectorAll('.row-action-btn--toggle').length).toBe(2);
      expect(manage.querySelector('.table-create-btn')).not.toBeNull();
      expect(manage.querySelector('.location-import-toggle')).not.toBeNull();
    });

    it('shows the drill-down only with the children privilege', () => {
      granted = new Set(['LOCATION_LIST', 'LOCATION_CHILDREN_LIST']);

      const { el } = create();

      expect(el.querySelectorAll('.row-action-btn').length).toBe(2);
    });
  });

  describe('adding and changing a place open their own pages', () => {
    it('opens the add page, carrying the country the list is filtered to', () => {
      const { component } = create();

      component.addPlace();
      expect(router.navigate).toHaveBeenLastCalledWith(['/access-management', 'franchise-locations', 'new'], { queryParams: {} });

      component.filters.countryCode = 'AE';
      component.addPlace();
      expect(router.navigate).toHaveBeenLastCalledWith(['/access-management', 'franchise-locations', 'new'], { queryParams: { country: 'AE' } });
    });

    it('opens the change page of a place, handing the row over', () => {
      const { component } = create();

      component.editPlace(nuzvid);

      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-locations', 'nz', 'edit'], { state: { place: nuzvid } });
    });

    it('opens the add page from the Add place button and the change page from the edit button of a row', () => {
      const { el } = create();

      (el.querySelector('.table-create-btn') as HTMLButtonElement).click();
      expect(router.navigate).toHaveBeenLastCalledWith(['/access-management', 'franchise-locations', 'new'], { queryParams: {} });

      (el.querySelector('tbody .row-action-btn[title="shared.listPage.rowActions.edit"]') as HTMLButtonElement).click();
      expect(router.navigate).toHaveBeenLastCalledWith(['/access-management', 'franchise-locations', 'kr', 'edit'], { state: { place: krishna } });
    });

    it('keeps no add or change form on the list itself', () => {
      const { el } = create();

      expect(el.querySelector('.franchise-form')).toBeNull();
      expect(el.querySelector('app-location-picker[ng-reflect-selectable-types]')).toBeNull();
    });
  });

  describe('activating and deactivating', () => {
    it('switches the place with its version and reloads', () => {
      const { component } = create();

      component.toggleActive(krishna);
      expect(api.setLocationActive).toHaveBeenCalledWith('kr', { active: false, version: 4 });
      expect(notify.success).toHaveBeenCalledWith(`${KEY}messages.deactivated`);

      component.toggleActive(nuzvid);
      expect(api.setLocationActive).toHaveBeenCalledWith('nz', { active: true, version: 7 });
      expect(notify.success).toHaveBeenCalledWith(`${KEY}messages.activated`);
    });

    it('shows the 409 text when plans use the place', () => {
      const message = 'The location is used by franchise plans and cannot be deactivated';
      api.setLocationActive.mockReturnValue(throwError(() => ({ status: 409, error: { message } })));
      const { component } = create();

      component.toggleActive(krishna);

      expect(notify.error).toHaveBeenCalledWith(message);
    });

    it('falls back to a translated text for a server fault', () => {
      api.setLocationActive.mockReturnValue(throwError(() => ({ status: 500, error: { message: 'NullPointerException at x.Y(Z.java:1)' } })));
      const { component } = create();

      component.toggleActive(krishna);

      expect(notify.error).toHaveBeenCalledWith(`${KEY}messages.statusFailed`);
    });
  });

  it('opens and closes the import panel, and reloads after an import', () => {
    const { component } = create();

    component.toggleImport();
    expect(component.showImport).toBe(true);
    component.onImported();
    expect(api.locations).toHaveBeenCalledTimes(2);
    component.toggleImport();
    expect(component.showImport).toBe(false);
  });
});
