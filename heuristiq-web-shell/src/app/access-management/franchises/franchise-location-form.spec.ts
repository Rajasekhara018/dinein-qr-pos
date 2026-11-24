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
import { FranchiseApi } from './franchise-api';
import { Location } from './franchise.models';
import { FranchiseLocationForm } from './franchise-location-form';

const krishna: Location = {
  id: 'kr', countryCode: 'IN', type: 'DISTRICT', level: 'REGION', levelNumber: 1, code: 'AP-KR', name: 'Krishna', parentId: 'ap',
  parent: { id: 'ap', type: 'STATE', level: 'REGION', code: 'AP', name: 'Andhra Pradesh' }, active: true, sortOrder: 2, version: 4,
};
const nuzvid: Location = {
  id: 'nz', countryCode: 'IN', type: 'TOWN', level: 'URBAN', levelNumber: 2, code: 'NUZVID', name: 'Nuzvid', parentId: 'kr',
  parent: { id: 'kr', type: 'DISTRICT', level: 'REGION', code: 'AP-KR', name: 'Krishna' }, active: false, sortOrder: 0, version: 7,
};

const api = { location: vi.fn(), addLocation: vi.fn(), changeLocation: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
const router = { navigate: vi.fn(() => Promise.resolve(true)) };
const dialogs = { confirm: vi.fn() };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};
const KEY = 'accessManagement.franchiseLocations.';

function create(id: string | null = null, country: string | null = null): { fixture: ComponentFixture<FranchiseLocationForm>; component: FranchiseLocationForm } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    declarations: [FranchiseLocationForm],
    imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
    providers: [
      { provide: FranchiseApi, useValue: api },
      { provide: NotificationService, useValue: notify },
      { provide: HqPrivilegeAccessService, useValue: access },
      { provide: Router, useValue: router },
      { provide: AppDialogService, useValue: dialogs },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => id }, queryParamMap: { get: () => country } } } },
    ],
    schemas: [NO_ERRORS_SCHEMA],
  });
  const fixture = TestBed.createComponent(FranchiseLocationForm);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

describe('FranchiseLocationForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    history.replaceState(null, '');
    api.location.mockReturnValue(of({ ...nuzvid, version: 8 }));
    api.addLocation.mockReturnValue(of(nuzvid));
    api.changeLocation.mockReturnValue(of(nuzvid));
    dialogs.confirm.mockReturnValue(of(true));
  });

  describe('adding a place', () => {
    it('starts in the country the list was filtered to, with an active place, and offers only the allowed parents for the type', () => {
      const { component } = create(null, 'AE');

      expect(component.form).toMatchObject({ countryCode: 'AE', type: '', active: true });
      expect(component.editing).toBeNull();
      expect(component.titleKey).toBe(`${KEY}newTitle`);
      expect(component.allowedParentTypes('TOWN')).toEqual(['DISTRICT', 'STATE']);
    });

    it('starts in the default country when the list had no country filter', () => {
      expect(create().component.form.countryCode).toBe('IN');
    });

    it('refuses an incomplete form without calling the service', () => {
      const { component } = create();

      component.save();

      expect(api.addLocation).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith(`${KEY}errors.type`);
    });

    it('sends the place, says so and goes back to the list', () => {
      const { component } = create();
      Object.assign(component.form, { type: 'TOWN', code: 'NUZVID', name: 'Nuzvid' });
      component.onParentPicked(krishna);

      component.save();

      expect(api.addLocation).toHaveBeenCalledWith({
        code: 'NUZVID', name: 'Nuzvid', active: true, sortOrder: 0, parentId: 'kr', countryCode: 'IN', type: 'TOWN',
      });
      expect(notify.success).toHaveBeenCalledWith(`${KEY}messages.added`);
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-locations']);
    });

    it('forgets a parent chosen for another type or country', () => {
      const { component } = create();
      component.onParentPicked(krishna);
      component.onTypeChange();
      expect(component.form.parent).toBeNull();

      component.onParentPicked(krishna);
      component.onCountryChange();
      expect(component.form.parent).toBeNull();
    });

    it('shows the service\'s own refusal, for example a duplicate place, and stays on the page', () => {
      api.addLocation.mockReturnValue(throwError(() => ({ status: 409, error: { message: 'A district named "Barnala" already exists under Punjab (code PB-002)' } })));
      const { component } = create();
      Object.assign(component.form, { type: 'TOWN', code: 'NUZVID', name: 'Nuzvid' });

      component.save();

      expect(notify.error).toHaveBeenCalledWith('A district named "Barnala" already exists under Punjab (code PB-002)');
      expect(component.saving).toBe(false);
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('changing a place', () => {
    it('opens from the row the list handed over, without reading the place again', () => {
      history.replaceState({ place: nuzvid }, '');

      const { component } = create('nz');

      expect(api.location).not.toHaveBeenCalled();
      expect(component.editing).toEqual(nuzvid);
      expect(component.titleKey).toBe(`${KEY}editTitle`);
      expect(component.form).toMatchObject({ name: 'Nuzvid', code: 'NUZVID' });
    });

    it('reads the place by its id when it is opened directly, for example from a bookmark', () => {
      const { component } = create('nz');

      expect(api.location).toHaveBeenCalledWith('nz');
      expect(component.editing?.version).toBe(8);
      expect(component.isLoading).toBe(false);
    });

    it('says so, and shows no form, when the place cannot be read', () => {
      api.location.mockReturnValue(throwError(() => ({ status: 404 })));

      const { component, fixture } = create('nz');

      expect(component.loadFailed).toBe(true);
      expect(notify.error).toHaveBeenCalled();
      expect((fixture.nativeElement as HTMLElement).querySelector('form')).toBeNull();
    });

    it('sends the version read, keeps the parent when none is chosen, and not the fixed country and type', () => {
      history.replaceState({ place: nuzvid }, '');
      const { component } = create('nz');
      component.form.name = ' Nuzvid Town ';

      component.save();

      expect(api.changeLocation).toHaveBeenCalledWith('nz', { code: 'NUZVID', name: 'Nuzvid Town', active: false, sortOrder: 0, version: 7 });
      expect(notify.success).toHaveBeenCalledWith(`${KEY}messages.changed`);
      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-locations']);
    });

    it('reads the place again after "changed by someone else" so the next save carries the stored version, and stays', () => {
      history.replaceState({ place: nuzvid }, '');
      api.changeLocation.mockReturnValue(throwError(() => ({ status: 409, error: { message: 'The location was changed by someone else; reload it and try again' } })));
      const { component } = create('nz');

      component.save();

      expect(notify.error).toHaveBeenCalledWith('The location was changed by someone else; reload it and try again');
      expect(api.location).toHaveBeenCalledWith('nz');
      expect(component.editVersion).toBe(8);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('does not read the place again after a conflict without the privilege to read one', () => {
      history.replaceState({ place: nuzvid }, '');
      granted = new Set(['LOCATION_UPDATE']);
      api.changeLocation.mockReturnValue(throwError(() => ({ status: 409, error: { message: 'stale' } })));
      const { component } = create('nz');

      component.save();

      expect(api.location).not.toHaveBeenCalled();
      expect(component.editVersion).toBe(7);
    });
  });

  describe('leaving with unsaved changes', () => {
    it('has nothing pending until a field is changed, and not after saving', () => {
      const { component } = create();
      expect(component.hasPendingChanges()).toBe(false);

      component.form.name = 'Something';
      expect(component.hasPendingChanges()).toBe(true);

      Object.assign(component.form, { type: 'TOWN', code: 'X' });
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

      expect(router.navigate).toHaveBeenCalledWith(['/access-management', 'franchise-locations']);
    });
  });

  it('lays the fields out in the two-column form grid with a label above each control', () => {
    const el = (create().fixture.nativeElement as HTMLElement);

    expect(el.querySelector('form.ffp__grid')).not.toBeNull();
    expect(el.querySelectorAll('.ffp__field').length).toBe(5);
    expect(el.querySelector('.ffp__actions button[type="submit"]')).not.toBeNull();
  });

  it('does not show the display order field', () => {
    const el = (create().fixture.nativeElement as HTMLElement);

    expect(el.querySelector('#loc-order')).toBeNull();
  });
});
