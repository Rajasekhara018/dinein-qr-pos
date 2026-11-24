import { QA_BAR_VIEW, QA_STATUS_KEYS } from './qa-dashboard.constants';
import {
  buildBars,
  buildDonut,
  buildLinePoints,
  clampPage,
  durationParts,
  emptyFilters,
  emptyTotals,
  filterParams,
  formatPct,
  hasActiveFilters,
  isValidTagExpression,
  labelFor,
  normalizeExecutiveSummary,
  normalizeFacets,
  normalizeFailures,
  normalizeOverview,
  normalizeRow,
  normalizeTestCasePage,
  normalizeTotals,
  parseWorkspace,
  polyline,
  reportLink,
  shortMessage,
  statusTone,
  subModulesOf,
  testSetFormProblem,
} from './qa-dashboard.helpers';
import { QaFacets, QaReleasePoint } from './qa-dashboard.models';

const release = (name: string, passed: number, failed: number, skipped: number): QaReleasePoint => ({
  release: name, workspace: 'UI', suite: 'REGRESSION', environment: 'qa', startedAt: '2026-10-01T00:00:00Z', passed, failed, skipped, total: passed + failed + skipped,
});

describe('qa-dashboard helpers', () => {
  describe('workspace and filters', () => {
    it('reads the workspace from the query value and falls back to UI', () => {
      expect(parseWorkspace('API')).toBe('API');
      expect(parseWorkspace('api')).toBe('API');
      expect(parseWorkspace('ui')).toBe('UI');
      expect(parseWorkspace('nonsense')).toBe('UI');
      expect(parseWorkspace(null)).toBe('UI');
      expect(parseWorkspace(undefined)).toBe('UI');
    });

    it('gives a fresh, separate filter set each time', () => {
      const a = emptyFilters({ suite: 'SMOKE' });
      const b = emptyFilters();
      expect(a.suite).toBe('SMOKE');
      expect(b.suite).toBe('');
      a.q = 'x';
      expect(b.q).toBe('');
    });

    it('knows when a filter narrows the list, optionally ignoring the fixed suite', () => {
      expect(hasActiveFilters(emptyFilters())).toBe(false);
      expect(hasActiveFilters(emptyFilters({ q: '   ' }))).toBe(false);
      expect(hasActiveFilters(emptyFilters({ status: 'FAILED' }))).toBe(true);
      expect(hasActiveFilters(emptyFilters({ suite: 'SMOKE' }))).toBe(true);
      expect(hasActiveFilters(emptyFilters({ suite: 'SMOKE' }), ['suite'])).toBe(false);
    });

    it('builds query parameters without empty values and with trimmed text', () => {
      const params = filterParams('API', emptyFilters({ q: '  login ', environment: 'qa' }), { page: 2 });
      expect(params).toEqual({ workspace: 'API', q: 'login', environment: 'qa', page: 2 });
    });

    it('clamps the page request to what the API accepts', () => {
      expect(clampPage(-3, 500)).toEqual({ page: 0, size: 50 });
      expect(clampPage(2.9, 0)).toEqual({ page: 2, size: 1 });
      expect(clampPage(Number.NaN, Number.NaN)).toEqual({ page: 0, size: 1 });
    });

    it('lists the sub-modules of the chosen module only', () => {
      const facets: QaFacets = { modules: [{ module: 'wallet', subModules: ['wallet.home', 'wallet.agent'] }], environments: [], releases: [], suites: [] };
      expect(subModulesOf(facets, 'wallet')).toEqual(['wallet.home', 'wallet.agent']);
      expect(subModulesOf(facets, 'uam')).toEqual([]);
      expect(subModulesOf(facets, '')).toEqual([]);
      expect(subModulesOf(null, 'wallet')).toEqual([]);
    });
  });

  describe('display', () => {
    it('gives each status its tone and a neutral one to an unknown status', () => {
      expect(statusTone('PASSED')).toBe('success');
      expect(statusTone('FAILED')).toBe('danger');
      expect(statusTone('UNSTABLE')).toBe('warning');
      expect(statusTone('SKIPPED')).toBe('neutral');
      expect(statusTone('WEIRD')).toBe('neutral');
    });

    it('translates a code through its key map and falls back to the raw code', () => {
      const t = (key: string): string => (key === 'qaDashboard.status.PASSED' ? 'Passed' : key);
      expect(labelFor(t, QA_STATUS_KEYS, 'PASSED')).toBe('Passed');
      expect(labelFor(t, QA_STATUS_KEYS, 'FAILED')).toBe('FAILED');
      expect(labelFor(t, QA_STATUS_KEYS, 'NEW_CODE')).toBe('NEW_CODE');
      expect(labelFor(t, QA_STATUS_KEYS, null)).toBe('');
      expect(labelFor(t, QA_STATUS_KEYS, undefined)).toBe('');
    });

    it('splits a duration into units', () => {
      expect(durationParts(250)).toEqual([{ unit: 'ms', value: 250 }]);
      expect(durationParts(1500)).toEqual([{ unit: 's', value: 2 }]);
      expect(durationParts(120_000)).toEqual([{ unit: 'm', value: 2 }]);
      expect(durationParts(125_000)).toEqual([{ unit: 'm', value: 2 }, { unit: 's', value: 5 }]);
      expect(durationParts(3_600_000)).toEqual([{ unit: 'h', value: 1 }]);
      expect(durationParts(3_900_000)).toEqual([{ unit: 'h', value: 1 }, { unit: 'm', value: 5 }]);
      expect(durationParts(-5)).toEqual([{ unit: 'ms', value: 0 }]);
      expect(durationParts(Number.NaN)).toEqual([{ unit: 'ms', value: 0 }]);
    });

    it('formats a percentage with one decimal and no trailing zero', () => {
      expect(formatPct(49.5)).toBe('49.5');
      expect(formatPct(50)).toBe('50');
      expect(formatPct(33.333)).toBe('33.3');
      expect(formatPct(null)).toBe('0');
      expect(formatPct(undefined)).toBe('0');
    });

    it('opens a report only when its address is a plain http(s) link', () => {
      expect(reportLink('https://reports.example.com/qa/1/index.html')).toBe('https://reports.example.com/qa/1/index.html');
      expect(reportLink('http://192.168.100.39/reports/a.html')).toBe('http://192.168.100.39/reports/a.html');
      expect(reportLink('javascript:alert(1)')).toBeNull();
      expect(reportLink('file:///c:/report.html')).toBeNull();
      expect(reportLink('not a url')).toBeNull();
      expect(reportLink('')).toBeNull();
      expect(reportLink(null)).toBeNull();
      expect(reportLink(undefined)).toBeNull();
    });

    it('shortens a failure message to its first line', () => {
      expect(shortMessage(null)).toBe('');
      expect(shortMessage('first\nsecond')).toBe('first');
      expect(shortMessage('x'.repeat(200), 10)).toBe(`${'x'.repeat(10)}…`);
    });
  });

  describe('charts', () => {
    it('stacks pass, fail and skip for each release, oldest first, and leaves empty segments out', () => {
      const bars = buildBars([release('R1', 8, 2, 0), release('R2', 0, 5, 5)]);
      expect(bars.map((b) => b.release)).toEqual(['R1', 'R2']);
      expect(bars[0].segments.map((s) => s.status)).toEqual(['PASSED', 'FAILED']);
      expect(bars[1].segments.map((s) => s.status)).toEqual(['FAILED', 'SKIPPED']);
      expect(bars[0].total).toBe(10);
      const base = QA_BAR_VIEW.height - QA_BAR_VIEW.paddingBottom;
      const lowest = bars[0].segments[0];
      expect(lowest.y + lowest.height).toBeCloseTo(base, 1);
      // The tallest bar fills the plot area.
      const top = bars[0].segments[bars[0].segments.length - 1];
      expect(top.y).toBeCloseTo(QA_BAR_VIEW.paddingTop, 1);
    });

    it('shows at most the newest twelve releases and nothing for no releases', () => {
      const many = Array.from({ length: 15 }, (_, i) => release(`R${i}`, 1, 0, 0));
      const bars = buildBars(many);
      expect(bars).toHaveLength(QA_BAR_VIEW.maxBars);
      expect(bars[0].release).toBe('R3');
      expect(buildBars([])).toEqual([]);
    });

    it('draws a ring from the pass / fail / skip counts', () => {
      expect(buildDonut({ passed: 0, failed: 0, skipped: 0 })).toEqual([]);
      const slices = buildDonut({ passed: 3, failed: 1, skipped: 0 });
      expect(slices.map((s) => s.status)).toEqual(['PASSED', 'FAILED']);
      expect(slices[0].path).toMatch(/^M .* A 62 62 0 1 1 /);
      expect(slices[1].path).toMatch(/A 62 62 0 0 1 /);
    });

    it('draws a full ring as two halves when everything has one status', () => {
      const slices = buildDonut({ passed: 5, failed: 0, skipped: 0 });
      expect(slices).toHaveLength(1);
      expect(slices[0].status).toBe('PASSED');
      expect(slices[0].path.match(/A /g)).toHaveLength(2);
    });

    it('places line points evenly over a shared scale', () => {
      const points = buildLinePoints([{ label: 'a', value: 0 }, { label: 'b', value: 5 }, { label: 'c', value: 10 }]);
      expect(points.map((p) => p.x)).toEqual([8, 160, 312]);
      expect(points[0].y).toBeGreaterThan(points[2].y);
      const shared = buildLinePoints([{ label: 'a', value: 5 }], 10);
      const alone = buildLinePoints([{ label: 'a', value: 5 }]);
      expect(shared[0].y).toBeGreaterThan(alone[0].y);
      expect(alone[0].x).toBe(160);
      expect(buildLinePoints([])).toEqual([]);
      expect(polyline(points)).toBe(points.map((p) => `${p.x},${p.y}`).join(' '));
    });
  });

  describe('normalising what the API returns', () => {
    it('fills a missing totals object with zeros and keeps what is there', () => {
      expect(normalizeTotals(null)).toEqual(emptyTotals());
      expect(normalizeTotals({ total: 4, passed: 3 }).passed).toBe(3);
      expect(normalizeTotals({ total: 4, passed: 3 }).failed).toBe(0);
    });

    it('never lets a missing list break a screen', () => {
      expect(normalizeOverview(undefined)).toEqual({ mode: 'unique', totals: emptyTotals(), releases: [], trend: [], volume: [] });
      expect(normalizeFacets(null)).toEqual({ modules: [], environments: [], releases: [], suites: [] });
      expect(normalizeFailures(null)).toEqual({ totalFailed: 0, byCategory: [], byModule: [], topFailing: [], flaky: [] });
      const summary = normalizeExecutiveSummary(null);
      expect(summary.previousRelease).toBeNull();
      expect(summary.headline).toEqual([]);
      expect(summary.workspace).toBe('UI');
    });

    it('normalises rows and the test case page', () => {
      const row = normalizeRow({ id: '1', name: 'x' });
      expect(row.tcId).toBeNull();
      expect(row.iteration).toBe(0);
      expect(row.suites).toEqual([]);
      expect(row.reportUrl).toBeNull();
      expect(normalizeRow({ reportUrl: 'https://r.example.com/x' }).reportUrl).toBe('https://r.example.com/x');
      const page = normalizeTestCasePage({ items: [{ id: '1' }] as never, totals: { total: 1, passed: 1, failed: 0, skipped: 0 } }, 2, 20);
      expect(page.page).toBe(2);
      expect(page.size).toBe(20);
      expect(page.totalElements).toBe(1);
      expect(page.totalPages).toBe(1);
      expect(normalizeTestCasePage(null, 0, 20).totalPages).toBe(0);
    });

    it('keeps the previous release when there is one', () => {
      const summary = normalizeExecutiveSummary({ previousRelease: { release: 'QA_REG_OCT25_R1', totals: { total: 2 } as never }, headline: ['ALL_GREEN'], topRisks: [{ id: '1' }] as never });
      expect(summary.previousRelease?.totals.failed).toBe(0);
      expect(summary.topRisks[0].tcId).toBeNull();
      expect(summary.headline).toEqual(['ALL_GREEN']);
    });
  });

  describe('test set form', () => {
    it.each([
      ['@regression', true],
      ['@regression and not @slow', true],
      ['(@smoke or @uam) and @readonly', true],
      ['@screen-wallet.agent_load', true],
      ['', false],
      ['   ', false],
      ['regression', false],
      ['@a; rm -rf /', false],
      ['(@a', false],
      ['@a)', false],
      [')(@a', false],
      [`@${'a'.repeat(400)}`, false],
    ])('checks the tag expression %j', (text, valid) => {
      expect(isValidTagExpression(text)).toBe(valid);
    });

    it('names the first field that is wrong', () => {
      const ok = { name: 'Nightly UI', suite: 'REGRESSION', tagExpression: '@regression', description: '' };
      expect(testSetFormProblem(ok)).toBeNull();
      expect(testSetFormProblem({ ...ok, name: 'ab' })).toBe('name');
      expect(testSetFormProblem({ ...ok, name: 'x'.repeat(101) })).toBe('name');
      expect(testSetFormProblem({ ...ok, suite: '' })).toBe('suite');
      expect(testSetFormProblem({ ...ok, tagExpression: 'nope' })).toBe('tagExpression');
      expect(testSetFormProblem({ ...ok, description: 'x'.repeat(501) })).toBe('description');
    });
  });
});
