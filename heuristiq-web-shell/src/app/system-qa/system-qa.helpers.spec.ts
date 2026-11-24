import {
  buildTrendPoints,
  distinctPilotIds,
  distinctTags,
  durationParts,
  filterScenarios,
  passRate,
  renderMarkdown,
  safeHttpUrl,
  sizeOf,
  sortDocuments,
  sortFailuresFirst,
  trendPolyline,
  trendValues,
} from './system-qa.helpers';
import { QaDocumentSummary, QaRunSummary, QaScenario, QaScenarioFilters } from './system-qa.models';

const run = (id: string, passed: number, failed: number, skipped = 0): QaRunSummary => ({
  id,
  environment: 'qa',
  tags: '@smoke',
  status: failed > 0 ? 'FAILED' : 'PASSED',
  total: passed + failed + skipped,
  passed,
  failed,
  skipped,
  durationMs: 1000,
  startedAt: `2026-10-0${id}T10:00:00Z`,
  jenkinsJob: 'qa',
  jenkinsBuildNumber: Number(id),
  jenkinsUrl: null,
  documentId: null,
  createdAt: `2026-10-0${id}T10:05:00Z`,
});

const scenario = (name: string, status: string, over: Partial<QaScenario> = {}): QaScenario => ({
  feature: 'Login',
  name,
  tags: ['@smoke'],
  pilotId: null,
  status,
  durationMs: 10,
  failureMessage: null,
  ...over,
});

const noFilters = (over: Partial<QaScenarioFilters> = {}): QaScenarioFilters => ({ status: '', query: '', tag: '', pilotId: '', ...over });

describe('system-qa helpers', () => {
  describe('passRate', () => {
    it('is the share of executed scenarios that passed, to one decimal, ignoring skipped ones', () => {
      expect(passRate(run('1', 2, 1, 5))).toBe(66.7);
      expect(passRate(run('1', 4, 0))).toBe(100);
      expect(passRate(run('1', 0, 3))).toBe(0);
    });

    it('is null when nothing was executed', () => {
      expect(passRate(run('1', 0, 0, 4))).toBeNull();
    });

    it('treats missing counts as zero', () => {
      expect(passRate({} as QaRunSummary)).toBeNull();
      expect(passRate({ passed: 3 } as QaRunSummary)).toBe(100);
    });
  });

  describe('trend', () => {
    it('turns newest-first runs into oldest-first values and drops runs with nothing executed', () => {
      const values = trendValues([run('3', 3, 1), run('2', 0, 0, 2), run('1', 1, 1)]);
      expect(values.map((v) => v.id)).toEqual(['1', '3']);
      expect(values.map((v) => v.value)).toEqual([50, 75]);
      expect(trendValues([])).toEqual([]);
    });

    it('places values in the viewBox: 100% at the top, 0% at the bottom, spread evenly', () => {
      const pts = buildTrendPoints([
        { id: 'a', startedAt: '', value: 0 },
        { id: 'b', startedAt: '', value: 100 },
        { id: 'c', startedAt: '', value: 150 },
        { id: 'd', startedAt: '', value: -5 },
      ]);
      expect(pts.map((p) => p.x)).toEqual([4, 34.67, 65.33, 96]);
      expect(pts.map((p) => p.y)).toEqual([36, 4, 4, 36]);
    });

    it('centres a single value and joins points into a polyline', () => {
      const [only] = buildTrendPoints([{ id: 'a', startedAt: '', value: 50 }]);
      expect(only.x).toBe(50);
      expect(only.y).toBe(20);
      expect(trendPolyline([only, { ...only, x: 60, y: 10 }])).toBe('50,20 60,10');
      expect(trendPolyline([])).toBe('');
    });
  });

  describe('durationParts', () => {
    it('shows milliseconds under a second, and zero for nothing or garbage', () => {
      expect(durationParts(850)).toEqual([{ unit: 'ms', value: 850 }]);
      expect(durationParts(0)).toEqual([{ unit: 'ms', value: 0 }]);
      expect(durationParts(null)).toEqual([{ unit: 'ms', value: 0 }]);
      expect(durationParts(Number.NaN)).toEqual([{ unit: 'ms', value: 0 }]);
      expect(durationParts(-5)).toEqual([{ unit: 'ms', value: 0 }]);
    });

    it('keeps the two largest units', () => {
      expect(durationParts(12_400)).toEqual([{ unit: 's', value: 12 }]);
      expect(durationParts(184_000)).toEqual([{ unit: 'm', value: 3 }, { unit: 's', value: 4 }]);
      expect(durationParts(3_723_000)).toEqual([{ unit: 'h', value: 1 }, { unit: 'm', value: 2 }]);
      expect(durationParts(3_600_000)).toEqual([{ unit: 'h', value: 1 }]);
    });
  });

  describe('scenarios', () => {
    const list = [
      scenario('ok-1', 'PASSED', { pilotId: 'A10', tags: ['@smoke', '@A10'] }),
      scenario('skip', 'SKIPPED', { feature: 'Payments', pilotId: 'A2', tags: ['@A2'] }),
      scenario('boom', 'FAILED', { failureMessage: 'x', tags: ['@smoke'] }),
      scenario('ok-2', 'PASSED', { feature: 'Payments', tags: [] }),
      scenario('boom-2', 'FAILED'),
    ];

    it('sorts failures first, then skipped, keeping the original order inside each group, without touching the input', () => {
      const sorted = sortFailuresFirst(list);
      expect(sorted.map((s) => s.name)).toEqual(['boom', 'boom-2', 'skip', 'ok-1', 'ok-2']);
      expect(list[0].name).toBe('ok-1');
      expect(sortFailuresFirst([scenario('x', 'WEIRD'), scenario('y', 'FAILED')]).map((s) => s.name)).toEqual(['y', 'x']);
    });

    it('filters by status, tag, pilot id and text', () => {
      const names = (f: Partial<QaScenarioFilters>): string[] => filterScenarios(list, noFilters(f)).map((s) => s.name);
      expect(names({})).toHaveLength(5);
      expect(names({ status: 'FAILED' })).toEqual(['boom', 'boom-2']);
      expect(names({ tag: '@A2' })).toEqual(['skip']);
      expect(names({ pilotId: 'A10' })).toEqual(['ok-1']);
      expect(names({ query: '  PAYMENTS ' })).toEqual(['skip', 'ok-2']);
      expect(names({ query: 'a10' })).toEqual(['ok-1']);
      expect(names({ query: 'boom', status: 'FAILED', tag: '@smoke' })).toEqual(['boom', 'boom-2']);
      expect(names({ query: 'boom-2', status: 'FAILED', tag: '@smoke' })).toEqual(['boom-2']);
      expect(names({ query: 'nothing like this' })).toEqual([]);
    });

    it('copes with scenarios that have no tag list', () => {
      const bare = [{ ...scenario('bare', 'PASSED'), tags: undefined as unknown as string[] }];
      expect(filterScenarios(bare, noFilters({ tag: '@x' }))).toEqual([]);
      expect(distinctTags(bare)).toEqual([]);
    });

    it('lists distinct tags and pilot ids, sorted (pilot ids naturally)', () => {
      expect(distinctTags(list)).toEqual(['@A10', '@A2', '@smoke']);
      expect(distinctPilotIds([...list, scenario('dup', 'PASSED', { pilotId: 'A10' })])).toEqual(['A2', 'A10']);
      expect(distinctPilotIds([])).toEqual([]);
    });
  });

  describe('documents and sizes', () => {
    const doc = (name: string, updatedAt: string): QaDocumentSummary => ({ name, title: name, updatedAt, sizeBytes: 1 });

    it('sorts documents newest first, unreadable dates last, ties by name', () => {
      const sorted = sortDocuments([
        doc('b', '2026-10-01T00:00:00Z'),
        doc('bad', 'not a date'),
        doc('c', '2026-10-02T00:00:00Z'),
        doc('a', '2026-10-01T00:00:00Z'),
        doc('bad2', ''),
      ]);
      expect(sorted.map((d) => d.name)).toEqual(['c', 'a', 'b', 'bad', 'bad2']);
    });

    it('turns bytes into a value and a unit', () => {
      expect(sizeOf(0)).toEqual({ value: 0, unit: 'B' });
      expect(sizeOf(null)).toEqual({ value: 0, unit: 'B' });
      expect(sizeOf(1023)).toEqual({ value: 1023, unit: 'B' });
      expect(sizeOf(1536)).toEqual({ value: 1.5, unit: 'KB' });
      expect(sizeOf(5 * 1024 * 1024)).toEqual({ value: 5, unit: 'MB' });
    });
  });

  describe('safeHttpUrl', () => {
    it('keeps http and https addresses only', () => {
      expect(safeHttpUrl('https://jenkins.example.com/job/qa/7/')).toBe('https://jenkins.example.com/job/qa/7/');
      expect(safeHttpUrl('http://localhost:8080')).toBe('http://localhost:8080/');
      expect(safeHttpUrl('javascript:alert(1)')).toBeNull();
      expect(safeHttpUrl('data:text/html,hi')).toBeNull();
      expect(safeHttpUrl('/relative/path')).toBeNull();
      expect(safeHttpUrl('')).toBeNull();
      expect(safeHttpUrl(null)).toBeNull();
      expect(safeHttpUrl(undefined)).toBeNull();
    });
  });

  describe('renderMarkdown', () => {
    it('renders headings, lists and tables', () => {
      const html = renderMarkdown('# Plan\n\n- one\n- two\n\n| a | b |\n|---|---|\n| 1 | 2 |\n');
      expect(html).toContain('<h1>Plan</h1>');
      expect(html).toContain('<li>one</li>');
      expect(html).toContain('<table>');
    });

    it('returns an empty string for nothing', () => {
      expect(renderMarkdown('')).toBe('');
      expect(renderMarkdown(null)).toBe('');
      expect(renderMarkdown(undefined)).toBe('');
    });

    it('opens http links in a new tab with rel=noopener noreferrer', () => {
      const html = renderMarkdown('[docs](https://example.com/a?b=1&c=2 "The docs")');
      expect(html).toContain('href="https://example.com/a?b=1&amp;c=2"');
      expect(html).toContain('target="_blank"');
      expect(html).toContain('rel="noopener noreferrer"');
      expect(html).toContain('title="The docs"');
    });

    it('drops the link, keeping its text, when the address is not http(s)', () => {
      const html = renderMarkdown('[click](javascript:alert(1)) and [rel](/x)');
      expect(html).not.toContain('<a');
      expect(html).toContain('click');
    });

    it('shows raw HTML as text instead of passing it through', () => {
      const html = renderMarkdown('<script>alert(1)</script>\n\nhello <b onclick="x()">bold</b>');
      expect(html).not.toContain('<script>');
      expect(html).not.toContain('<b ');
      expect(html).toContain('&lt;script&gt;');
    });
  });
});
