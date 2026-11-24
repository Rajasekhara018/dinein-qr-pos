import { describe, expect, it } from 'vitest';
import { Location } from '../franchises/franchise.models';
import { locationOptions, normalizeCountry, stateIdByName } from './legal-profile-location.helpers';

const place = (id: string, name: string, active = true): Location => ({
  id, name, active, countryCode: 'IN', type: 'STATE', level: 'STATE', levelNumber: 1, code: id, parentId: null, parent: null, sortOrder: 0, version: 1,
}) as unknown as Location;

describe('legal profile location helpers', () => {
  it('normalizes a country code the way places are keyed', () => {
    expect(normalizeCountry(' in ')).toBe('IN');
    expect(normalizeCountry(null)).toBe('');
    expect(normalizeCountry(undefined)).toBe('');
  });

  it('lists active places by name, sorted, each name once, blank names and inactive places left out', () => {
    const options = locationOptions([
      place('3', 'Telangana'), place('1', 'Andhra Pradesh'), place('2', 'Karnataka'), place('4', 'telangana'),
      place('5', '  '), place('6', 'Old State', false),
    ]);
    expect(options).toEqual([
      { value: 'Andhra Pradesh', label: 'Andhra Pradesh' },
      { value: 'Karnataka', label: 'Karnataka' },
      { value: 'Telangana', label: 'Telangana' },
    ]);
  });

  it('finds a state id by its stored name, ignoring case and spaces, and answers empty when it is unknown', () => {
    const rows = [place('s1', 'Telangana'), place('s2', 'Karnataka')];
    expect(stateIdByName(rows, ' telangana ')).toBe('s1');
    expect(stateIdByName(rows, 'Goa')).toBe('');
    expect(stateIdByName(rows, '')).toBe('');
  });
});
