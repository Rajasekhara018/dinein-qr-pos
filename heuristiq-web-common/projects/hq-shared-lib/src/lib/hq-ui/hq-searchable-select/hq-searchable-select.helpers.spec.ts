import { hqSearchableFilter, hqSearchableMatch, hqSearchableSegments, hqSearchableStep } from './hq-searchable-select.helpers';

describe('hq-searchable-select helpers', () => {
  const options = ['Telugu', 'Tamil', 'Hindi', 'Kannada'];

  it('lists every option for an empty or blank term and returns a copy', () => {
    const all = hqSearchableFilter(options, '  ');
    expect(all).toEqual(options);
    expect(all).not.toBe(options);
  });

  it('filters by a case-insensitive contains', () => {
    expect(hqSearchableFilter(options, 'TA')).toEqual(['Tamil']);
    expect(hqSearchableFilter(options, 'an')).toEqual(['Kannada']);
    expect(hqSearchableFilter(options, 'zzz')).toEqual([]);
  });

  it('matches a value to a listed option ignoring case and spaces', () => {
    expect(hqSearchableMatch(options, ' telugu ')).toBe('Telugu');
    expect(hqSearchableMatch(options, 'Tulu')).toBeUndefined();
    expect(hqSearchableMatch(options, '')).toBeUndefined();
    expect(hqSearchableMatch(options, null)).toBeUndefined();
  });

  it('steps without wrapping and starts at the first or last row', () => {
    expect(hqSearchableStep(-1, 1, 3)).toBe(0);
    expect(hqSearchableStep(-1, -1, 3)).toBe(2);
    expect(hqSearchableStep(0, 1, 3)).toBe(1);
    expect(hqSearchableStep(2, 1, 3)).toBe(2);
    expect(hqSearchableStep(0, -1, 3)).toBe(0);
    expect(hqSearchableStep(0, 1, 0)).toBe(-1);
  });

  it('splits an option around the first match so it can be highlighted', () => {
    expect(hqSearchableSegments('Karnataka', 'nat')).toEqual([
      { text: 'Kar', match: false },
      { text: 'nat', match: true },
      { text: 'aka', match: false },
    ]);
    expect(hqSearchableSegments('Goa', 'GO')).toEqual([{ text: 'Go', match: true }, { text: 'a', match: false }]);
    expect(hqSearchableSegments('Goa', 'oa')).toEqual([{ text: 'G', match: false }, { text: 'oa', match: true }]);
    expect(hqSearchableSegments('Goa', '')).toEqual([{ text: 'Goa', match: false }]);
    expect(hqSearchableSegments('Goa', 'zz')).toEqual([{ text: 'Goa', match: false }]);
  });
});
