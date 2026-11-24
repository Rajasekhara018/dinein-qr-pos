import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { vi } from 'vitest';
import { HqSharedLibModule, HqProgress } from 'hq-shared-lib';
import { Permission } from '../../shared/models/uam.models';
import { RoleAccessPicker } from './role-access-picker';

const permission = (code: string, action: Permission['action'], apipath: string, description: string | null = null): Permission => ({
  id: code, code, applicationCode: 'UAM', module: null, resource: null, action, displayName: null, description,
  active: true, systemPermission: true, attributes: { apipath }, audit: null,
});

const CATALOG: Permission[] = [
  permission('USER_LIST', 'LIST', '/api/v1/uam/users', 'Allows viewing users'),
  permission('USER_CREATE', 'CREATE', '/api/v1/uam/users'),
  permission('USER_DELETE', 'DELETE', '/api/v1/uam/users/{id}'),
  permission('ROLE_LIST', 'LIST', '/api/v1/uam/roles'),
  permission('TRAIN_BOOKING_LIST', 'LIST', '/api/v1/train/bookings'),
  permission('TRAIN_BOOKING_UPDATE', 'UPDATE', '/api/v1/train/bookings/{id}'),
  permission('MYSTERY_READ', 'READ', '/api/v1/unmapped/x'),
];

describe('RoleAccessPicker', () => {
  /** Re-renders after the test changed the picker's state directly. */
  const refresh = (fixture: ComponentFixture<RoleAccessPicker>) => fixture.debugElement.injector.get(ChangeDetectorRef).detectChanges();

  function create(selected: string[] = [], permissions: Permission[] = CATALOG): { fixture: ComponentFixture<RoleAccessPicker>; picker: RoleAccessPicker; emitted: Set<string>[] } {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ declarations: [RoleAccessPicker], imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule, HqProgress] });
    const fixture = TestBed.createComponent(RoleAccessPicker);
    const picker = fixture.componentInstance;
    const emitted: Set<string>[] = [];
    picker.selectedChange.subscribe((s) => emitted.push(s));
    fixture.componentRef.setInput('permissions', permissions);
    fixture.componentRef.setInput('selected', new Set(selected));
    fixture.detectChanges();
    return { fixture, picker, emitted };
  }

  const area = (picker: RoleAccessPicker, key: string) => picker.areas.find((a) => a.key === key)!;
  const feature = (picker: RoleAccessPicker, areaKey: string, key: string) => area(picker, areaKey).features.find((f) => f.key === key)!;

  it('separates product applications from shared applications and services', () => {
    const { picker } = create();
    picker.setCategory('products');
    expect(picker.visibleApplications.map(app => app.key)).toEqual(['train']);
    picker.setCategory('shared');
    expect(picker.visibleApplications.map(app => app.key)).toEqual(['platform']);
    picker.setApplication('platform');
    picker.setService('access');
    expect(picker.visibleApplications[0].areas.map(area => area.key)).toEqual(['access']);
    expect(picker.isOpen(area(picker, 'access'))).toBe(true);
    picker.clearFilters();
    expect(picker.category).toBe('all');
    expect(picker.service).toBe('');
  });

  it('limits quick actions to the chosen service and preserves permissions elsewhere', () => {
    const { picker, emitted } = create(['TRAIN_BOOKING_LIST', 'RETIRED_CODE']);
    picker.setCategory('shared');
    picker.setService('access');
    picker.setEverything('manage');
    expect(emitted.at(-1)?.has('USER_CREATE')).toBe(true);
    expect(emitted.at(-1)?.has('TRAIN_BOOKING_LIST')).toBe(true);
    expect(emitted.at(-1)?.has('TRAIN_BOOKING_UPDATE')).toBe(false);
    expect(emitted.at(-1)?.has('RETIRED_CODE')).toBe(true);
    picker.setEverything('none');
    expect(emitted.at(-1)?.has('TRAIN_BOOKING_LIST')).toBe(true);
  });

  it('drops a service filter when its permissions disappear', () => {
    const { fixture, picker } = create();
    picker.setService('access');
    fixture.componentRef.setInput('permissions', [CATALOG[4]]);
    fixture.detectChanges();
    expect(picker.service).toBe('');
  });

  it('groups the catalogue into areas, with unmapped paths under "other"', () => {
    const { picker } = create();

    expect(picker.areas.map((a) => a.key)).toEqual(['access', 'train', 'other']);
    expect(picker.totalFeatures).toBe(4);
    expect(picker.visibleAreas.length).toBe(3);
  });

  it('rebuilds the areas when the permission list changes', () => {
    const { fixture, picker } = create();

    fixture.componentRef.setInput('permissions', [CATALOG[3]]);
    fixture.detectChanges();

    expect(picker.areas.map((a) => a.key)).toEqual(['access']);
  });

  it('names an area by translation, or from its key when there is none', () => {
    const { picker } = create();

    expect(picker.areaName(area(picker, 'train'))).toBe('Train');
    expect(picker.areaNameByKey('access')).toBe('Access');
  });

  it('summarizes what the selection allows', () => {
    const { picker } = create(['USER_LIST', 'ROLE_LIST', 'TRAIN_BOOKING_LIST', 'TRAIN_BOOKING_UPDATE']);

    expect(picker.permissionCount).toBe(4);
    expect(picker.areasOn).toBe(2);
    expect(picker.summary.manage).toBe(1);
    expect(picker.summary.view).toBe(2);
    expect(picker.percentOf(picker.summary.manage)).toBeCloseTo(25);
    expect(picker.review.map((l) => l.area)).toEqual(['access', 'train']);
  });

  it('reports 0 percent with no features at all', () => {
    const { picker } = create([], []);

    expect(picker.percentOf(3)).toBe(0);
    expect(picker.visibleAreas).toEqual([]);
  });

  it('shows what each feature is set to', () => {
    const { picker } = create(['USER_LIST', 'USER_CREATE', 'ROLE_LIST', 'TRAIN_BOOKING_LIST', 'TRAIN_BOOKING_UPDATE']);

    expect(picker.levelOf(feature(picker, 'access', 'USER'))).toBe('custom');
    expect(picker.levelOf(feature(picker, 'access', 'ROLE'))).toBe('view');
    expect(picker.levelOf(feature(picker, 'train', 'TRAIN_BOOKING'))).toBe('manage');
    expect(picker.levelOf(feature(picker, 'other', 'MYSTERY'))).toBe('none');
    expect(picker.areaLevel(area(picker, 'train'))).toBe('manage');
    expect(picker.areaLevel(area(picker, 'access'))).toBeNull();
    expect(picker.everythingLevel).toBeNull();
  });

  it('knows which levels a feature offers', () => {
    const { picker } = create();

    expect(picker.can(feature(picker, 'other', 'MYSTERY'), 'view')).toBe(true);
    expect(picker.can(feature(picker, 'other', 'MYSTERY'), 'manage')).toBe(false);
  });

  it('emits a new selection when a feature, an area or everything is set', () => {
    const { picker, emitted } = create();

    picker.setLevel(feature(picker, 'access', 'ROLE'), 'view');
    expect([...emitted[0]]).toEqual(['ROLE_LIST']);
    picker.setArea(area(picker, 'train'), 'manage');
    expect([...emitted[1]].sort()).toEqual(['TRAIN_BOOKING_LIST', 'TRAIN_BOOKING_UPDATE']);
    picker.setEverything('view');
    expect([...emitted[2]].sort()).toEqual(['MYSTERY_READ', 'ROLE_LIST', 'TRAIN_BOOKING_LIST', 'USER_LIST']);
  });

  it('caches the computed levels until the selection changes', () => {
    const { fixture, picker } = create(['ROLE_LIST']);
    const before = picker.areaLevel(area(picker, 'access'));

    expect(picker.areaLevel(area(picker, 'access'))).toBe(before);
    fixture.componentRef.setInput('selected', new Set(['ROLE_LIST', 'USER_LIST', 'USER_CREATE', 'USER_DELETE']));
    fixture.detectChanges();
    expect(picker.areaLevel(area(picker, 'access'))).toBe('manage');
  });

  describe('filtering', () => {
    it('narrows by text and offers a way back', () => {
      const { fixture, picker } = create();

      picker.searchText = 'booking';
      expect(picker.filtering).toBe(true);
      expect(picker.visibleAreas.map((a) => a.key)).toEqual(['train']);
      expect(picker.shownFeatures).toBe(1);
      expect(picker.isOpen(area(picker, 'train'))).toBe(true);
      refresh(fixture);
      expect(fixture.nativeElement.querySelector('.ra__showing')).not.toBeNull();

      picker.clearFilters();
      expect(picker.filtering).toBe(false);
      expect(picker.visibleAreas.length).toBe(3);
    });

    it('can show only what the role already has', () => {
      const { picker } = create(['ROLE_LIST']);

      picker.onlyOn = true;

      expect(picker.visibleAreas.map((a) => a.key)).toEqual(['access']);
      expect(picker.featuresOf(area(picker, 'access')).map((f) => f.key)).toEqual(['ROLE']);
    });

    it('shows the empty state when nothing matches', () => {
      const { fixture, picker } = create();

      picker.searchText = 'zzzz';
      refresh(fixture);

      expect(fixture.nativeElement.querySelector('.ra__empty')).not.toBeNull();
      (fixture.nativeElement.querySelector('.ra__empty button') as HTMLElement).click();
      expect(picker.searchText).toBe('');
    });
  });

  describe('opening areas', () => {
    it('toggles one area, expands all and collapses all', () => {
      const { picker } = create();
      const access = area(picker, 'access');

      expect(picker.isOpen(access)).toBe(false);
      picker.toggle(access);
      expect(picker.isOpen(access)).toBe(true);
      picker.toggle(access);
      expect(picker.isOpen(access)).toBe(false);
      picker.expandAll();
      expect(picker.areas.every((a) => picker.isOpen(a))).toBe(true);
      picker.collapseAll();
      expect(picker.areas.some((a) => picker.isOpen(a))).toBe(false);
    });

    it('reports progress per area and an icon, with a fallback icon', () => {
      const { picker } = create(['USER_LIST']);

      expect(picker.progress(area(picker, 'access'))).toEqual({ on: 1, total: 2, percent: 50 });
      expect(picker.icon(area(picker, 'train'))).toBe('bi-train-front');
      expect(picker.icon({ key: 'unknown-area', features: [] })).toBe('bi-three-dots');
      expect(picker.progress({ key: 'e', features: [] }).percent).toBe(0);
    });
  });

  describe('rendered controls', () => {
    it('lists the features of an opened area with their description and a sensitive tag', () => {
      const { fixture, picker } = create();
      picker.expandAll();
      refresh(fixture);
      const el: HTMLElement = fixture.nativeElement;

      expect(el.textContent).toContain('User');
      expect(el.textContent).toContain('Allows viewing users');
      expect(el.querySelectorAll('.ra__sensitive').length).toBe(1);
      expect(picker.sensitive(feature(picker, 'access', 'USER'))).toBe(true);
      expect(picker.sensitive(feature(picker, 'access', 'ROLE'))).toBe(false);
    });

    it('clicking None / View / Manage on a feature emits the matching selection', () => {
      const { fixture, picker, emitted } = create();
      picker.expandAll();
      refresh(fixture);
      const radios = fixture.nativeElement.querySelectorAll('.ra__feature [role="radio"]') as NodeListOf<HTMLButtonElement>;

      // First feature is "Role" (sorted): None, View, Manage. Manage is unavailable (view-only feature).
      expect(radios[2].disabled).toBe(true);
      radios[1].click();
      expect([...emitted[0]]).toEqual(['ROLE_LIST']);
    });

    it('the quick-start buttons set everything', () => {
      const { fixture, emitted } = create();
      const templates = fixture.nativeElement.querySelectorAll('.ra__template') as NodeListOf<HTMLButtonElement>;

      templates[0].click();
      templates[2].click();

      expect([...emitted[0]].sort()).toEqual(['MYSTERY_READ', 'ROLE_LIST', 'TRAIN_BOOKING_LIST', 'USER_LIST']);
      expect(emitted[1].size).toBe(0);
    });

    it('marks the quick-start matching the current level as pressed', () => {
      const { fixture } = create(['MYSTERY_READ', 'ROLE_LIST', 'TRAIN_BOOKING_LIST', 'USER_LIST']);
      const templates = fixture.nativeElement.querySelectorAll('.ra__template') as NodeListOf<HTMLButtonElement>;

      expect(templates[0].getAttribute('aria-pressed')).toBe('true');
      expect(templates[1].getAttribute('aria-pressed')).toBe('false');
    });

    it('the area buttons set a whole area, and the search clear button empties the search', () => {
      const { fixture, picker, emitted } = create();
      const spy = vi.spyOn(picker, 'setArea');

      (fixture.nativeElement.querySelectorAll('.ra__seg--area button')[2] as HTMLElement).click();
      expect(spy).toHaveBeenCalledWith(area(picker, 'access'), 'manage');
      expect(emitted.length).toBe(1);

      picker.searchText = 'x';
      refresh(fixture);
      (fixture.nativeElement.querySelector('.ra__search-clear') as HTMLElement).click();
      expect(picker.searchText).toBe('');
    });

    it('tracks areas and features by key', () => {
      const { picker } = create();

      expect(picker.trackArea(0, area(picker, 'train'))).toBe('train');
      expect(picker.trackFeature(0, feature(picker, 'access', 'ROLE'))).toBe('ROLE');
    });
  });
  describe('applications', () => {
    it('groups the areas under their applications', () => {
      const { picker } = create();

      expect(picker.applications.map((a) => a.key)).toEqual(['platform', 'train']);
      expect(picker.visibleApplications.map((a) => a.key)).toEqual(['platform', 'train']);
    });

    it('narrows the list to one application and back with the chips', () => {
      const { picker } = create();

      picker.setApplication('train');
      expect(picker.visibleApplications.map((a) => a.key)).toEqual(['train']);
      expect(picker.visibleApplications[0].areas.map((a) => a.key)).toEqual(['train']);
      picker.setApplication('train');
      expect(picker.application).toBe('');
      expect(picker.visibleApplications.length).toBe(2);
    });

    it('combines the application chip with the search', () => {
      const { picker } = create();

      picker.searchText = 'users';
      expect(picker.visibleApplications.map((a) => a.key)).toEqual(['platform']);
      picker.setApplication('train');
      expect(picker.visibleApplications).toEqual([]);
    });

    it('counts what is on per application and sets a whole application at once', () => {
      const { picker, emitted } = create(['TRAIN_BOOKING_LIST']);
      const train = picker.applications.find((a) => a.key === 'train')!;

      expect(picker.applicationProgress(train).on).toBe(1);
      expect(picker.applicationProgress(train).total).toBe(1);
      picker.setApplicationLevel(picker.visibleApplications.find((a) => a.key === 'train')!, 'manage');
      expect([...emitted[0]]).toContain('TRAIN_BOOKING_UPDATE');
      expect(picker.applicationLevel(picker.visibleApplications.find((a) => a.key === 'train')!)).toBe('view');
    });

    it('clears the application chip with the other filters, and drops it when its application disappears', () => {
      const { fixture, picker } = create();

      picker.setApplication('train');
      picker.clearFilters();
      expect(picker.application).toBe('');
      picker.setApplication('train');
      fixture.componentRef.setInput('permissions', CATALOG.filter((p) => !p.code.startsWith('TRAIN')));
      fixture.detectChanges();
      expect(picker.application).toBe('');
    });

    it('names an application by translation, or from its key when there is none', () => {
      const { picker } = create();

      expect(picker.applicationName('rider')).toBe('Rider');
      expect(picker.applicationIcon('train')).toBe('bi-train-front');
      expect(picker.applicationIcon('unknown')).toBe('bi-gear-wide-connected');
    });
  });
});
