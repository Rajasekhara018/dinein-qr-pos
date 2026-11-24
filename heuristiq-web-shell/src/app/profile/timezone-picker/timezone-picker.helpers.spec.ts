import { filterTimezones } from './timezone-picker.helpers';

const ZONES = ['America/New_York', 'Asia/Kolkata', 'Asia/Dubai', 'Europe/London', 'Pacific/Auckland', 'UTC'];

describe('filterTimezones', () => {
  it('lists the first zones for an empty query, up to the limit', () => {
    expect(filterTimezones(ZONES, '', 3)).toEqual(ZONES.slice(0, 3));
    expect(filterTimezones(ZONES, '   ', 2)).toEqual(ZONES.slice(0, 2));
  });

  it('ignores case, spaces and underscores', () => {
    expect(filterTimezones(ZONES, 'new york', 10)).toEqual(['America/New_York']);
    expect(filterTimezones(ZONES, 'KOLKATA', 10)).toEqual(['Asia/Kolkata']);
  });

  it('ranks names that start with the text before names that only contain it', () => {
    expect(filterTimezones(ZONES, 'asia', 10)).toEqual(['Asia/Kolkata', 'Asia/Dubai']);
    expect(filterTimezones(ZONES, 'ia/d', 10)).toEqual(['Asia/Dubai']);
  });

  it('returns nothing when no zone matches', () => {
    expect(filterTimezones(ZONES, 'mars', 10)).toEqual([]);
  });

  it('respects the limit', () => {
    expect(filterTimezones(ZONES, 'a', 2).length).toBe(2);
  });
});
