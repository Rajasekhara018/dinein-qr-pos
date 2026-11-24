import { describe, expect, it } from 'vitest';
import { Location } from './franchise.models';
import {
  allowedParentTypes, deepestSelectable, emptyLocationForm, importInputError, importRowCount, importTemplateCsv, isParentAllowed, levelOfType,
  locationFormError, locationQueryString, locationToForm, placePath, planQueryString, toLocationRequest,
} from './franchise-locations.helpers';
import { IMPORT_CSV_EXAMPLE, IMPORT_CSV_HEADER, IMPORT_MAX_ROWS, LOCATION_PRIVILEGES, LOCATION_TYPES } from './franchise-locations.constants';

const place = (over: Partial<Location> = {}): Location => ({
  id: 'l1', countryCode: 'IN', type: 'TOWN', level: 'URBAN', levelNumber: 2, code: 'NUZVID', name: 'Nuzvid', parentId: 'd1',
  parent: { id: 'd1', type: 'DISTRICT', level: 'REGION', code: 'AP-KR', name: 'Krishna' }, active: true, sortOrder: 0, version: 3, ...over,
});

describe('location query strings', () => {
  it('leaves empty values out and upper-cases the country', () => {
    expect(locationQueryString({})).toBe('');
    expect(locationQueryString({ countryCode: 'in', type: 'TOWN', level: 'URBAN', parentId: 'p', active: false, usable: true, q: ' nuz ', page: 0, size: 20 }))
      .toBe('?countryCode=IN&type=TOWN&level=URBAN&parentId=p&active=false&usable=true&q=nuz&page=0&size=20');
    expect(locationQueryString({ type: '', active: null, includeInactive: true })).toBe('?includeInactive=true');
  });

  it('always asks the plans for a page, and only sends the filters that are set', () => {
    expect(planQueryString({})).toBe('?page=0');
    expect(planQueryString({ applicationCode: 'A', scope: 'TYPE_DEFAULT', locationType: 'CITY', level: 'URBAN', stateId: 's', districtId: 'd', q: 'x y', page: 2, size: 20 }))
      .toBe('?applicationCode=A&scope=TYPE_DEFAULT&locationType=CITY&level=URBAN&stateId=s&districtId=d&q=x+y&page=2&size=20');
  });
});

describe('place types', () => {
  it('knows the level of each of the eight types', () => {
    expect(LOCATION_TYPES.length).toBe(8);
    expect(levelOfType('STATE')).toBe('REGION');
    expect(levelOfType('TOWN')).toBe('URBAN');
    expect(levelOfType('VILLAGE')).toBe('LOCAL');
    expect(levelOfType('OTHER')).toBe('');
  });

  it('offers only the parent types the model allows', () => {
    expect(allowedParentTypes('')).toEqual([]);
    expect(allowedParentTypes('STATE')).toEqual([]);
    expect(allowedParentTypes('DISTRICT')).toEqual(['STATE']);
    expect(allowedParentTypes('VILLAGE')).toContain('MANDAL');
    expect(isParentAllowed('TOWN', 'DISTRICT')).toBe(true);
    expect(isParentAllowed('TOWN', 'VILLAGE')).toBe(false);
    expect(isParentAllowed('TOWN', null)).toBe(false);
  });

  it('joins a chain into a path', () => {
    expect(placePath([{ name: 'Andhra Pradesh' }, { name: 'Krishna' }, { name: 'Nuzvid' }])).toBe('Andhra Pradesh > Krishna > Nuzvid');
    expect(placePath(undefined)).toBe('');
  });
});

describe('the place form', () => {
  const valid = () => ({ ...emptyLocationForm('in'), type: 'TOWN' as const, code: ' NUZVID ', name: ' Nuzvid ' });

  it('accepts a complete form and refuses each missing or wrong part with its own message', () => {
    expect(locationFormError(valid(), false)).toBeNull();
    expect(locationFormError({ ...valid(), countryCode: 'India' }, false)).toBe('accessManagement.franchiseLocations.errors.country');
    expect(locationFormError({ ...valid(), type: '' }, false)).toBe('accessManagement.franchiseLocations.errors.type');
    expect(locationFormError({ ...valid(), code: ' ' }, false)).toBe('accessManagement.franchiseLocations.errors.code');
    expect(locationFormError({ ...valid(), code: 'x'.repeat(41) }, false)).toBe('accessManagement.franchiseLocations.errors.code');
    expect(locationFormError({ ...valid(), name: '' }, false)).toBe('accessManagement.franchiseLocations.errors.name');
    expect(locationFormError({ ...valid(), sortOrder: -1 }, false)).toBe('accessManagement.franchiseLocations.errors.order');
    expect(locationFormError({ ...valid(), sortOrder: 1.5 }, false)).toBe('accessManagement.franchiseLocations.errors.order');
    expect(locationFormError({ ...valid(), sortOrder: null }, false)).toBeNull();
  });

  it('refuses a parent of a type that is not allowed, or from another country', () => {
    expect(locationFormError({ ...valid(), parent: place({ type: 'DISTRICT' }) }, false)).toBeNull();
    expect(locationFormError({ ...valid(), parent: place({ type: 'VILLAGE' }) }, false)).toBe('accessManagement.franchiseLocations.errors.parent');
    expect(locationFormError({ ...valid(), parent: place({ type: 'DISTRICT', countryCode: 'US' }) }, false))
      .toBe('accessManagement.franchiseLocations.errors.parentCountry');
  });

  it('does not ask for the fixed country and type when changing a place', () => {
    const form = locationToForm(place());
    expect(form).toMatchObject({ countryCode: 'IN', type: 'TOWN', code: 'NUZVID', name: 'Nuzvid', parent: null, active: true, sortOrder: 0 });
    expect(locationFormError({ ...form, countryCode: '', type: '' }, true)).toBeNull();
  });

  it('sends country and type on create, and the version (not country or type) on update', () => {
    const parent = place({ id: 'd1', type: 'DISTRICT' });
    expect(toLocationRequest({ ...valid(), parent }, null)).toEqual({
      code: 'NUZVID', name: 'Nuzvid', active: true, sortOrder: 0, parentId: 'd1', countryCode: 'IN', type: 'TOWN',
    });
    const update = toLocationRequest({ ...locationToForm(place()), sortOrder: null }, 3);
    expect(update).toEqual({ code: 'NUZVID', name: 'Nuzvid', active: true, sortOrder: 0, version: 3 });
    expect(update.parentId).toBeUndefined();
  });
});

describe('deepestSelectable', () => {
  const state = place({ id: 's', type: 'STATE' });
  const district = place({ id: 'd', type: 'DISTRICT' });
  const village = place({ id: 'v', type: 'VILLAGE' });

  it('returns the deepest chosen place of an allowed type, walking back up', () => {
    expect(deepestSelectable([state, district, village], null)?.id).toBe('v');
    expect(deepestSelectable([state, district, village], ['STATE', 'DISTRICT'])?.id).toBe('d');
    expect(deepestSelectable([state, null], ['STATE', 'DISTRICT'])?.id).toBe('s');
    expect(deepestSelectable([village], ['STATE'])).toBeNull();
    expect(deepestSelectable([], null)).toBeNull();
  });
});

describe('import text', () => {
  it('builds the template from the header and one example line', () => {
    expect(importTemplateCsv()).toBe(`${IMPORT_CSV_HEADER}\n${IMPORT_CSV_EXAMPLE}\n`);
  });

  it('counts data rows, ignoring the header, blank lines, a BOM and line breaks inside quotes', () => {
    expect(importRowCount('')).toBe(0);
    expect(importRowCount(importTemplateCsv())).toBe(1);
    expect(importRowCount('﻿a,b\r\n1,2\r\n\r\n3,4\r\n')).toBe(2);
    expect(importRowCount('a,b\n"x\ny",2\n3,4')).toBe(2);
  });

  it('refuses text that is empty, has no rows, is too big or has too many rows', () => {
    const key = 'accessManagement.franchiseLocations.import.errors.';
    expect(importInputError('  ')).toBe(`${key}empty`);
    expect(importInputError(IMPORT_CSV_HEADER)).toBe(`${key}noRows`);
    expect(importInputError(importTemplateCsv())).toBeNull();
    expect(importInputError(`${IMPORT_CSV_HEADER}\n${'x\n'.repeat(IMPORT_MAX_ROWS + 1)}`)).toBe(`${key}tooManyRows`);
    expect(importInputError(`${IMPORT_CSV_HEADER}\n${'x'.repeat(2_000_001)}`)).toBe(`${key}tooBig`);
    expect(importInputError(`${IMPORT_CSV_HEADER}\n${'x\n'.repeat(IMPORT_MAX_ROWS)}`)).toBeNull();
  });
});

describe('privilege codes', () => {
  it('uses the exact privilege names of the catalog', () => {
    expect(Object.values(LOCATION_PRIVILEGES)).toEqual([
      'LOCATION_LIST', 'LOCATION_READ', 'LOCATION_CHILDREN_LIST', 'LOCATION_CREATE', 'LOCATION_UPDATE', 'LOCATION_STATUS_UPDATE',
      'LOCATION_IMPORT_CREATE', 'FRANCHISE_PLAN_RESOLVE_READ',
    ]);
  });
});
