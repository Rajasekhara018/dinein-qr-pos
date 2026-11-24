import { HqSharedLibModule } from 'hq-shared-lib';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FranchiseApi } from './franchise-api';
import { Location, LocationType } from './franchise.models';
import { LocationPicker } from './location-picker';

const loc = (id: string, type: LocationType, name: string, parentId: string | null = null): Location => ({
  id, countryCode: 'IN', type, level: type === 'STATE' || type === 'DISTRICT' ? 'REGION' : 'URBAN', levelNumber: 1, code: id.toUpperCase(), name,
  parentId, parent: null, active: true, sortOrder: 0, version: 0,
});
const page = (items: Location[], total = items.length) => ({ items, page: 0, size: 100, totalElements: total });

const ap = loc('ap', 'STATE', 'Andhra Pradesh');
const krishna = loc('kr', 'DISTRICT', 'Krishna', 'ap');
const nuzvid = loc('nz', 'TOWN', 'Nuzvid', 'kr');

const api = { locations: vi.fn(), locationChildren: vi.fn(), placeOptions: vi.fn(), placeOptionChildren: vi.fn() };

function create(inputs: Partial<LocationPicker> = {}): { fixture: ComponentFixture<LocationPicker>; picker: LocationPicker; chosen: (Location | null)[] } {
  const fixture = TestBed.createComponent(LocationPicker);
  const picker = fixture.componentInstance;
  const chosen: (Location | null)[] = [];
  picker.selected.subscribe((value) => chosen.push(value));
  Object.assign(picker, inputs);
  picker.ngOnChanges({ countryCode: {} as never, chain: {} as never });
  fixture.detectChanges();
  return { fixture, picker, chosen };
}

describe('LocationPicker', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.locations.mockReturnValue(of(page([ap])));
    api.locationChildren.mockImplementation((id: string) => of(page(id === 'ap' ? [krishna] : id === 'kr' ? [nuzvid] : [])));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [LocationPicker],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [{ provide: FranchiseApi, useValue: api }],
    });
  });

  it('offers nothing until a country is chosen', () => {
    const { picker, fixture } = create({ countryCode: '' });

    expect(api.locations).not.toHaveBeenCalled();
    expect(picker.levels).toEqual([]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('accessManagement.franchiseLocations.picker.chooseCountry');
  });

  it('asks for the usable states of the country first', () => {
    create({ countryCode: 'IN' });

    expect(api.locations).toHaveBeenCalledWith({ countryCode: 'IN', type: 'STATE', usable: true, q: '', size: 100 });
  });

  it('reads the franchise form\'s own place list when franchise-scoped, never the Locations screen\'s', () => {
    api.placeOptions.mockReturnValue(of(page([ap])));
    api.placeOptionChildren.mockImplementation((id: string) => of(page(id === 'ap' ? [krishna] : [])));
    const { picker, chosen } = create({ countryCode: 'IN', franchiseScoped: true });

    picker.choose(0, 'ap');

    expect(api.placeOptions).toHaveBeenCalledWith({ countryCode: 'IN', type: 'STATE', q: '', size: 100 });
    expect(api.placeOptionChildren).toHaveBeenCalledWith('ap', { q: '', size: 100 });
    expect(api.locations).not.toHaveBeenCalled();
    expect(api.locationChildren).not.toHaveBeenCalled();
    expect(chosen.at(-1)?.id).toBe('ap');
  });

  it('opens the next level with the places under a chosen place, down to the deepest, and reports the deepest chosen', () => {
    const { picker, chosen } = create({ countryCode: 'IN' });

    picker.choose(0, 'ap');
    expect(api.locationChildren).toHaveBeenCalledWith('ap', { q: '', size: 100 });
    expect(picker.levels.length).toBe(2);
    picker.choose(1, 'kr');
    expect(picker.levels.length).toBe(3);
    picker.choose(2, 'nz');

    expect(picker.levels.length).toBe(3);
    expect(chosen.map((value) => value?.id)).toEqual(['ap', 'kr', 'nz']);
  });

  it('starts the deeper levels again when a shallower choice changes or is cleared', () => {
    const { picker, chosen } = create({ countryCode: 'IN' });
    picker.choose(0, 'ap');
    picker.choose(1, 'kr');

    picker.choose(0, '');

    expect(picker.levels.length).toBe(1);
    expect(picker.levels[0].selectedId).toBe('');
    expect(chosen[chosen.length - 1]).toBeNull();
  });

  it('reports only a place of a selectable type, so a parent picker never returns a deeper place', () => {
    const { picker, chosen } = create({ countryCode: 'IN', selectableTypes: ['STATE', 'DISTRICT'] });

    picker.choose(0, 'ap');
    picker.choose(1, 'kr');
    picker.choose(2, 'nz');

    expect(chosen.map((value) => value?.id)).toEqual(['ap', 'kr']);
  });

  it('shows an existing path as already chosen and reports its deepest place', () => {
    const chain = [
      { id: 'ap', type: 'STATE' as const, level: 'REGION' as const, code: 'AP', name: 'Andhra Pradesh' },
      { id: 'kr', type: 'DISTRICT' as const, level: 'REGION' as const, code: 'KR', name: 'Krishna' },
    ];

    const { picker, chosen } = create({ countryCode: 'IN', chain });

    expect(picker.levels.map((level) => level.selectedId)).toEqual(['ap', 'kr', '']);
    expect(chosen.map((value) => value?.id)).toEqual(['kr']);
  });

  it('stops at the level where a path place is no longer offered (for example deactivated)', () => {
    const chain = [{ id: 'gone', type: 'STATE' as const, level: 'REGION' as const, code: 'X', name: 'Gone' }];

    const { picker, chosen } = create({ countryCode: 'IN', chain });

    expect(picker.levels[0].selectedId).toBe('');
    expect(chosen).toEqual([]);
  });

  it('asks again with the typed text for a long level, clearing what was chosen there', () => {
    api.locations.mockReturnValue(of(page([ap], 250)));
    const { picker, fixture } = create({ countryCode: 'IN' });
    expect((fixture.nativeElement as HTMLElement).querySelector('.location-picker__search')).not.toBeNull();

    api.locations.mockReturnValue(of(page([loc('tg', 'STATE', 'Telangana')], 1)));
    picker.search(0, ' tel ');

    expect(api.locations).toHaveBeenLastCalledWith({ countryCode: 'IN', type: 'STATE', usable: true, q: 'tel', size: 100 });
    expect(picker.levels[0].options.map((option) => option.id)).toEqual(['tg']);
  });

  it('says so when the places cannot be loaded', () => {
    api.locations.mockReturnValue(throwError(() => new Error('down')));

    const { picker, fixture } = create({ countryCode: 'IN' });

    expect(picker.failed).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('accessManagement.franchiseLocations.picker.loadFailed');
  });

  it('reloads from the start when the country changes', () => {
    const { picker } = create({ countryCode: 'IN' });
    picker.choose(0, 'ap');

    picker.countryCode = 'US';
    picker.ngOnChanges({ countryCode: {} as never });

    expect(api.locations).toHaveBeenLastCalledWith(expect.objectContaining({ countryCode: 'US' }));
    expect(picker.levels.length).toBe(1);
    expect(picker.levels[0].selectedId).toBe('');
  });
});
