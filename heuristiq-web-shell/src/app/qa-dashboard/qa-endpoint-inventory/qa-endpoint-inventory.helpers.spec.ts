import {
  emptyEndpointQuery,
  endpointParams,
  endpointSubModules,
  hasActiveEndpointQuery,
  mappedChips,
  methodClass,
  normalizeCoverage,
  normalizeEndpoint,
  normalizeEndpointDetail,
  normalizeEndpointFilters,
  normalizeEndpointPage,
  progressPercent,
} from './qa-endpoint-inventory.helpers';

describe('endpoint inventory helpers', () => {
  describe('query', () => {
    it('gives a fresh, separate query each time', () => {
      const a = emptyEndpointQuery({ method: 'GET' });
      const b = emptyEndpointQuery();
      expect(a.method).toBe('GET');
      expect(b.method).toBe('');
      a.q = 'x';
      expect(b.q).toBe('');
    });

    it('knows when anything narrows the list', () => {
      expect(hasActiveEndpointQuery(emptyEndpointQuery())).toBe(false);
      expect(hasActiveEndpointQuery(emptyEndpointQuery({ q: '   ' }))).toBe(false);
      expect(hasActiveEndpointQuery(emptyEndpointQuery({ module: 'uam' }))).toBe(true);
      expect(hasActiveEndpointQuery(emptyEndpointQuery({ quick: 'untested' }))).toBe(true);
    });

    it('sends only what is set, trimmed, and clamps the page request', () => {
      expect(endpointParams(emptyEndpointQuery({ q: '  login ', module: 'uam', subModule: 'users', method: 'POST' }), 2, 500)).toEqual({
        q: 'login', module: 'uam', subModule: 'users', method: 'POST', page: 2, size: 50,
      });
      expect(endpointParams(emptyEndpointQuery())).toEqual({});
    });

    it.each([
      ['tested', { tested: 'true' }],
      ['untested', { tested: 'false' }],
      ['undocumented', { documented: 'false' }],
      ['', {}],
    ] as const)('turns the %s quick filter into the flags the API knows', (quick, expected) => {
      expect(endpointParams(emptyEndpointQuery({ quick }))).toEqual(expected);
    });

    it('lists the sub-modules of the chosen module only', () => {
      const options = { modules: [{ module: 'uam', subModules: ['users', 'roles'] }], methods: [] };
      expect(endpointSubModules(options, 'uam')).toEqual(['users', 'roles']);
      expect(endpointSubModules(options, 'wallet')).toEqual([]);
      expect(endpointSubModules(options, '')).toEqual([]);
      expect(endpointSubModules(null, 'uam')).toEqual([]);
    });
  });

  describe('display', () => {
    it('gives each method its badge style and an unknown method a neutral one', () => {
      expect(methodClass('GET')).toBe('qa-method--get');
      expect(methodClass('post')).toBe('qa-method--post');
      expect(methodClass('PUT')).toBe('qa-method--put');
      expect(methodClass('PATCH')).toBe('qa-method--patch');
      expect(methodClass('DELETE')).toBe('qa-method--delete');
      expect(methodClass('TRACE')).toBe('qa-method--other');
      expect(methodClass('')).toBe('qa-method--other');
    });

    it('works out the progress bar from tested and documented', () => {
      expect(progressPercent(150, 200)).toBe(75);
      expect(progressPercent(0, 0)).toBe(0);
      expect(progressPercent(5, 0)).toBe(0);
      expect(progressPercent(300, 200)).toBe(100);
      expect(progressPercent(-4, 10)).toBe(0);
    });

    it('lists the first three mapped test cases and counts the rest', () => {
      expect(mappedChips(['a', 'b', 'c', 'd', 'e'])).toEqual({ visible: ['a', 'b', 'c'], extra: 2 });
      expect(mappedChips(['a', 'b'])).toEqual({ visible: ['a', 'b'], extra: 0 });
      expect(mappedChips([])).toEqual({ visible: [], extra: 0 });
      expect(mappedChips(['a', 'b', 'c'], 1)).toEqual({ visible: ['a'], extra: 2 });
    });
  });

  describe('normalising what the API returns', () => {
    it('fills missing coverage with zeros and a missing sync time with null', () => {
      expect(normalizeCoverage(null)).toEqual({
        documented: 0, tested: 0, untested: 0, coveragePct: 0, undocumentedTested: 0, totalEndpoints: 0,
        modulesCovered: 0, subModulesCovered: 0, mappedScenarios: 0, lastSyncedAt: null,
      });
      expect(normalizeCoverage({ tested: 4, lastSyncedAt: '2026-10-26T09:00:00Z' }).lastSyncedAt).toBe('2026-10-26T09:00:00Z');
    });

    it('fills a missing endpoint field and counts the mapped test cases when the server did not', () => {
      const e = normalizeEndpoint({ id: '1', mappedTestKeys: ['a', 'b'] });
      expect(e.documented).toBe(true);
      expect(e.mappedCount).toBe(2);
      expect(e.hitCount).toBe(0);
      expect(e.privilegeName).toBeNull();
      expect(normalizeEndpoint({ mappedCount: 9 }).mappedCount).toBe(9);
      expect(normalizeEndpoint({ documented: false }).documented).toBe(false);
    });

    it('normalises a page and a missing page', () => {
      const page = normalizeEndpointPage({ items: [{ id: '1' }] as never }, 2, 20);
      expect(page.page).toBe(2);
      expect(page.size).toBe(20);
      expect(page.totalElements).toBe(1);
      expect(page.totalPages).toBe(1);
      expect(page.coverage.documented).toBe(0);
      const none = normalizeEndpointPage(null, 0, 10);
      expect(none.items).toEqual([]);
      expect(none.totalPages).toBe(0);
    });

    it('normalises the details with their test cases', () => {
      const d = normalizeEndpointDetail({ id: '1', tests: [{ testKey: 'k', name: 'n', status: 'PASSED' }] as never });
      expect(d.description).toBe('');
      expect(d.operation).toBe('');
      expect(d.tests).toEqual([{ id: '', tcId: null, testKey: 'k', name: 'n', status: 'PASSED', lastRunAt: null }]);
      expect(normalizeEndpointDetail(null).tests).toEqual([]);
    });

    it('never lets a missing filter list break the bar', () => {
      expect(normalizeEndpointFilters(null)).toEqual({ modules: [], methods: [] });
      expect(normalizeEndpointFilters({ methods: ['GET'] })).toEqual({ modules: [], methods: ['GET'] });
    });
  });
});
