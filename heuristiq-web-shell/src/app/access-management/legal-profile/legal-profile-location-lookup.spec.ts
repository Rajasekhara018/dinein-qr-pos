import { TestBed } from '@angular/core/testing';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FranchiseApi } from '../franchises/franchise-api';
import { Location } from '../franchises/franchise.models';
import { LEGAL_PROFILE_LOCATION_COUNTRIES, LEGAL_PROFILE_LOCATION_PRIVILEGES } from './legal-profile.constants';
import { LegalProfileLocationLookup } from './legal-profile-location-lookup';

const place = (id: string, name: string, type: string): Location => ({
  id, name, type, active: true, countryCode: 'IN', level: type, levelNumber: 1, code: id, parentId: null, parent: null, sortOrder: 0, version: 1,
}) as unknown as Location;
const page = (items: Location[]) => ({ items, page: 0, size: items.length, totalElements: items.length });

const api = { placeOptions: vi.fn(), placeOptionChildren: vi.fn() };
let granted: Set<string>;
const access = { can: (codes: string | readonly string[]) => [codes].flat().every((c) => granted.has(c)) };

describe('LegalProfileLocationLookup', () => {
  let lookup: LegalProfileLocationLookup;

  beforeEach(() => {
    vi.clearAllMocks();
    granted = new Set(Object.values(LEGAL_PROFILE_LOCATION_PRIVILEGES));
    api.placeOptions.mockReturnValue(of(page([place('s2', 'Telangana', 'STATE'), place('s1', 'Karnataka', 'STATE')])));
    api.placeOptionChildren.mockImplementation((id: string) =>
      of(page(id === 's2' ? [place('d2', 'Rangareddy', 'DISTRICT'), place('d1', 'Hyderabad', 'DISTRICT')] : [place('d3', 'Bengaluru Urban', 'DISTRICT')])));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: FranchiseApi, useValue: api }, { provide: HqPrivilegeAccessService, useValue: access }],
    });
    lookup = TestBed.inject(LegalProfileLocationLookup);
  });

  it('serves India only for now, and the list of countries is one constant', () => {
    expect(LEGAL_PROFILE_LOCATION_COUNTRIES).toEqual(['IN']);
    expect(lookup.supports('IN')).toBe(true);
    expect(lookup.supports('in')).toBe(true);
    expect(lookup.supports('AE')).toBe(false);
    expect(lookup.supports('')).toBe(false);
  });

  it('serves nobody who may not read place options', () => {
    granted = new Set([LEGAL_PROFILE_LOCATION_PRIVILEGES.states]);
    expect(lookup.supports('IN')).toBe(false);
    granted = new Set();
    expect(lookup.supports('IN')).toBe(false);
  });

  it('reads the states of the country from the place options and lists them by name', () => {
    let options: { value: string; label: string }[] = [];
    lookup.states('IN').subscribe((o) => (options = o));
    expect(api.placeOptions).toHaveBeenCalledWith({ countryCode: 'IN', type: 'STATE', size: 500 });
    expect(options).toEqual([{ value: 'Karnataka', label: 'Karnataka' }, { value: 'Telangana', label: 'Telangana' }]);
  });

  it('reads the states once however many readers ask', () => {
    lookup.states('IN').subscribe();
    lookup.states('IN').subscribe();
    lookup.districts('IN', 'Telangana').subscribe();
    expect(api.placeOptions).toHaveBeenCalledTimes(1);
  });

  it('reads the districts under the state with the stored name', () => {
    let options: { value: string; label: string }[] = [];
    lookup.districts('IN', 'telangana').subscribe((o) => (options = o));
    expect(api.placeOptionChildren).toHaveBeenCalledWith('s2', { type: 'DISTRICT', size: 500 });
    expect(options.map((o) => o.label)).toEqual(['Hyderabad', 'Rangareddy']);
  });

  it('lists no districts for a state it does not know (a free-text state), without asking for any', () => {
    let options: unknown[] | null = null;
    lookup.districts('IN', 'Atlantis').subscribe((o) => (options = o));
    expect(options).toEqual([]);
    expect(api.placeOptionChildren).not.toHaveBeenCalled();
  });

  it('refuses a country it does not serve instead of asking the franchise service', () => {
    let failed = false;
    lookup.states('AE').subscribe({ error: () => (failed = true) });
    expect(failed).toBe(true);
    expect(api.placeOptions).not.toHaveBeenCalled();
  });

  it('shows a failure to the reader and asks again on the next try', () => {
    api.placeOptions.mockReturnValueOnce(throwError(() => new Error('down')));
    let failed = false;
    lookup.states('IN').subscribe({ error: () => (failed = true) });
    expect(failed).toBe(true);
    let options: unknown[] = [];
    lookup.states('IN').subscribe((o) => (options = o));
    expect(api.placeOptions).toHaveBeenCalledTimes(2);
    expect(options.length).toBe(2);
  });

  it('answers late readers from the one read that is still on its way', () => {
    const pending = new Subject<ReturnType<typeof page>>();
    api.placeOptions.mockReturnValueOnce(pending);
    const seen: number[] = [];
    lookup.states('IN').subscribe((o) => seen.push(o.length));
    lookup.states('IN').subscribe((o) => seen.push(o.length));
    pending.next(page([place('s1', 'Goa', 'STATE')]));
    expect(api.placeOptions).toHaveBeenCalledTimes(1);
    expect(seen).toEqual([1, 1]);
  });
});
