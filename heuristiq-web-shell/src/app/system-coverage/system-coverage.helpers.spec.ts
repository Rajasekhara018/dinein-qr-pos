import { COVERAGE_ERROR_KEYS, COVERAGE_MAX_UPLOAD_BYTES } from './system-coverage.constants';
import {
  ageOf,
  averageLinesPct,
  buildRows,
  buildTrendPoints,
  classifyProject,
  countLevels,
  filterRows,
  isStale,
  methodsOrFunctions,
  parseCollectorText,
  projectScore,
  readFileText,
  sortRows,
  thresholdsOf,
  trendPolyline,
  uploadErrorOf,
  validateCollectorDocument,
} from './system-coverage.helpers';
import { CoverageDocument, CoverageFilters, CoverageMetric, CoverageProject, CoverageRow } from './system-coverage.models';

const metric = (pct: number): CoverageMetric => ({ covered: pct, total: 100, pct });
const THRESHOLDS = { floor: 70, target: 90 };
const NOW = Date.parse('2026-10-21T12:00:00Z');

function project(key: string, lines: number, branches: number, over: Partial<CoverageProject> = {}): CoverageProject {
  return {
    key,
    kind: 'web',
    status: 'ok',
    measuredAt: '2026-10-20T12:00:00Z',
    metrics: { lines: metric(lines), branches: metric(branches), functions: metric(55), methods: metric(66) },
    groups: [],
    ...over,
  };
}

const missing = (key: string): CoverageProject => ({ key, kind: 'backend', status: 'missing', measuredAt: null, metrics: null, groups: [] });

function doc(projects: CoverageProject[]): CoverageDocument {
  return { schemaVersion: 1, generatedAt: '2026-10-21T10:00:00Z', source: { gitBranch: 'dev', gitCommit: 'abc' }, thresholds: THRESHOLDS, projects };
}

describe('classification', () => {
  it.each([
    [69.99, 'BELOW_FLOOR'],
    [70, 'OK'],
    [89.99, 'OK'],
    [90, 'AT_TARGET'],
    [100, 'AT_TARGET'],
  ])('puts %s %% in %s', (pct, level) => {
    expect(classifyProject(project('p', pct, pct), THRESHOLDS)).toBe(level);
  });

  it('uses the lower of lines and branches', () => {
    expect(projectScore(project('p', 95, 60))).toBe(60);
    expect(classifyProject(project('p', 95, 60), THRESHOLDS)).toBe('BELOW_FLOOR');
    expect(classifyProject(project('p', 95, 80), THRESHOLDS)).toBe('OK');
  });

  it('treats a project that was not measured as MISSING', () => {
    expect(classifyProject(missing('m'), THRESHOLDS)).toBe('MISSING');
    expect(projectScore(missing('m'))).toBeNull();
    expect(projectScore(project('p', 80, 80, { metrics: { lines: metric(80), branches: null } }))).toBeNull();
    expect(projectScore(project('p', 80, 80, { status: 'missing' }))).toBeNull();
  });

  it('reads the thresholds of the document, with defaults when it has none', () => {
    expect(thresholdsOf(doc([]))).toEqual(THRESHOLDS);
    expect(thresholdsOf(null)).toEqual({ floor: 70, target: 90 });
    expect(thresholdsOf({ ...doc([]), thresholds: { floor: 50, target: 80 } })).toEqual({ floor: 50, target: 80 });
    expect(thresholdsOf({ ...doc([]), thresholds: undefined as never })).toEqual({ floor: 70, target: 90 });
  });

  it('takes methods for backend and functions for web projects', () => {
    expect(methodsOrFunctions(project('w', 80, 80))?.pct).toBe(55);
    expect(methodsOrFunctions(project('b', 80, 80, { kind: 'backend' }))?.pct).toBe(66);
    expect(methodsOrFunctions(missing('m'))).toBeNull();
    expect(methodsOrFunctions(project('b', 80, 80, { kind: 'backend', metrics: { lines: metric(1), branches: metric(1) } }))).toBeNull();
  });
});

describe('staleness and age', () => {
  it('marks a project measured more than 7 days ago as stale', () => {
    expect(isStale('2026-10-14T12:00:01Z', NOW)).toBe(false);
    expect(isStale('2026-10-14T12:00:00Z', NOW)).toBe(false);
    expect(isStale('2026-10-14T11:59:59Z', NOW)).toBe(true);
    expect(isStale('2026-10-14T11:59:59Z', NOW, 30)).toBe(false);
  });

  it('is never stale without a readable date', () => {
    expect(isStale(null, NOW)).toBe(false);
    expect(isStale('not a date', NOW)).toBe(false);
  });

  it('describes an age in the largest fitting unit', () => {
    const at = (offsetMs: number) => ageOf(new Date(NOW - offsetMs).toISOString(), NOW);
    expect(at(30_000)).toEqual({ key: 'systemCoverage.age.now', count: 0 });
    expect(at(-5_000)).toEqual({ key: 'systemCoverage.age.now', count: 0 });
    expect(at(5 * 60_000)).toEqual({ key: 'systemCoverage.age.minutes', count: 5 });
    expect(at(3 * 3_600_000 + 1)).toEqual({ key: 'systemCoverage.age.hours', count: 3 });
    expect(at(2 * 86_400_000 + 1)).toEqual({ key: 'systemCoverage.age.days', count: 2 });
    expect(ageOf(null, NOW)).toBeNull();
    expect(ageOf('garbage', NOW)).toBeNull();
  });
});

describe('rows', () => {
  const rows = (): CoverageRow[] =>
    buildRows(
      doc([
        project('b-ok', 80, 75),
        project('a-low', 40, 90, { kind: 'backend', groups: [{ name: 'z', lines: { covered: 9, total: 10, pct: 90 } }, { name: 'a', lines: { covered: 1, total: 10, pct: 10 } }] }),
        project('c-top', 95, 92),
        project('d-old', 95, 92, { measuredAt: '2026-09-01T00:00:00Z' }),
        missing('e-missing'),
      ]),
      NOW,
    );

  it('builds one row per project with level, score, staleness and worst-first groups', () => {
    const built = rows();
    expect(built.map((r) => r.level)).toEqual(['OK', 'BELOW_FLOOR', 'AT_TARGET', 'AT_TARGET', 'MISSING']);
    expect(built[1].score).toBe(40);
    expect(built[1].groups.map((g) => g.name)).toEqual(['a', 'z']);
    expect(built[3].stale).toBe(true);
    expect(built[2].stale).toBe(false);
    expect(built[0].methodsOrFunctions).toBe(55);
    expect(built[4]).toMatchObject({ lines: null, branches: null, methodsOrFunctions: null, score: null, stale: false });
  });

  it('copes with a document without a projects list', () => {
    expect(buildRows({ ...doc([]), projects: undefined as never }, NOW)).toEqual([]);
    expect(averageLinesPct({ ...doc([]), projects: undefined as never })).toBeNull();
  });

  it('sorts worst-first by default, unmeasured last, ties by name', () => {
    const built = [...rows(), ...buildRows(doc([project('b-tie', 95, 92)]), NOW)];
    const worst = sortRows(built, 'worst').map((r) => r.key);
    expect(worst).toEqual(['a-low', 'b-ok', 'b-tie', 'c-top', 'd-old', 'e-missing']);
    const best = sortRows(built, 'best').map((r) => r.key);
    expect(best).toEqual(['b-tie', 'c-top', 'd-old', 'b-ok', 'a-low', 'e-missing']);
    expect(sortRows(built, 'name').map((r) => r.key)[0]).toBe('a-low');
    expect(built[0].key).toBe('b-ok');
  });

  it('keeps unmeasured projects in name order among themselves', () => {
    const two = buildRows(doc([missing('z'), missing('m'), project('x', 99, 99)]), NOW);
    expect(sortRows(two, 'worst').map((r) => r.key)).toEqual(['x', 'm', 'z']);
  });

  it('filters by kind, level and text', () => {
    const all = rows();
    const f = (over: Partial<CoverageFilters>): string[] => filterRows(all, { kind: '', level: '', query: '', ...over }).map((r) => r.key);
    expect(f({})).toHaveLength(5);
    expect(f({ kind: 'backend' })).toEqual(['a-low', 'e-missing']);
    expect(f({ level: 'AT_TARGET' })).toEqual(['c-top', 'd-old']);
    expect(f({ query: '  TOP ' })).toEqual(['c-top']);
    expect(f({ kind: 'web', level: 'MISSING' })).toEqual([]);
  });

  it('counts the projects of each level', () => {
    expect(countLevels(rows())).toEqual({ BELOW_FLOOR: 1, OK: 1, AT_TARGET: 2, MISSING: 1 });
  });
});

describe('trend', () => {
  it('averages the lines of measured projects only', () => {
    expect(averageLinesPct(doc([project('a', 80, 1), project('b', 61, 1), missing('m')]))).toBe(70.5);
    expect(averageLinesPct(doc([project('a', 33.333, 1), project('b', 33.333, 1), project('c', 33.334, 1)]))).toBe(33.33);
    expect(averageLinesPct(doc([missing('m')]))).toBeNull();
    expect(averageLinesPct(doc([]))).toBeNull();
  });

  it('places values in the viewBox: oldest at the left, 100% at the top, 0% at the bottom', () => {
    const v = (value: number, i: number) => ({ runId: 'r' + i, createdAt: '2026-10-2' + i + 'T00:00:00Z', value });
    const pts = buildTrendPoints([v(0, 1), v(50, 2), v(100, 3)]);
    expect(pts.map((p) => p.x)).toEqual([4, 50, 96]);
    expect(pts.map((p) => p.y)).toEqual([36, 20, 4]);
    expect(trendPolyline(pts)).toBe('4,36 50,20 96,4');
  });

  it('centres a single point and clamps out-of-range values', () => {
    const [p] = buildTrendPoints([{ runId: 'r', createdAt: 'x', value: 140 }]);
    expect(p).toMatchObject({ x: 50, y: 4 });
    expect(buildTrendPoints([{ runId: 'r', createdAt: 'x', value: -5 }])[0].y).toBe(36);
    expect(buildTrendPoints([])).toEqual([]);
    expect(trendPolyline([])).toBe('');
  });
});

describe('client-side validation', () => {
  const valid = () => JSON.parse(JSON.stringify(doc([project('p', 80, 80)]))) as Record<string, unknown>;

  it('accepts a collector document', () => {
    expect(validateCollectorDocument(valid())).toBeNull();
  });

  it.each([
    ['null', null, COVERAGE_ERROR_KEYS.notObject],
    ['an array', [], COVERAGE_ERROR_KEYS.notObject],
    ['a string', 'x', COVERAGE_ERROR_KEYS.notObject],
  ])('rejects %s', (_n, value, key) => {
    expect(validateCollectorDocument(value)).toBe(key);
  });

  it('rejects the first problem it finds', () => {
    const with_ = (over: Record<string, unknown>) => ({ ...valid(), ...over });
    expect(validateCollectorDocument(with_({ schemaVersion: 2 }))).toBe(COVERAGE_ERROR_KEYS.schemaVersion);
    expect(validateCollectorDocument(with_({ schemaVersion: '1' }))).toBe(COVERAGE_ERROR_KEYS.schemaVersion);
    expect(validateCollectorDocument(with_({ generatedAt: undefined }))).toBe(COVERAGE_ERROR_KEYS.generatedAt);
    expect(validateCollectorDocument(with_({ generatedAt: 'yesterday' }))).toBe(COVERAGE_ERROR_KEYS.generatedAt);
    expect(validateCollectorDocument(with_({ thresholds: undefined }))).toBe(COVERAGE_ERROR_KEYS.thresholds);
    expect(validateCollectorDocument(with_({ thresholds: { floor: 70 } }))).toBe(COVERAGE_ERROR_KEYS.thresholds);
    expect(validateCollectorDocument(with_({ projects: [] }))).toBe(COVERAGE_ERROR_KEYS.noProjects);
    expect(validateCollectorDocument(with_({ projects: 'x' }))).toBe(COVERAGE_ERROR_KEYS.noProjects);
  });

  it('parses text: size, syntax, then content', () => {
    const ok = parseCollectorText(JSON.stringify(valid()));
    expect(ok.errorKey).toBeNull();
    expect(ok.document?.projects).toHaveLength(1);
    expect(parseCollectorText('{oops')).toEqual({ document: null, errorKey: COVERAGE_ERROR_KEYS.notJson });
    expect(parseCollectorText('[1]')).toEqual({ document: null, errorKey: COVERAGE_ERROR_KEYS.notObject });
    expect(parseCollectorText('x'.repeat(11), 10)).toEqual({ document: null, errorKey: COVERAGE_ERROR_KEYS.tooLarge });
    expect(parseCollectorText('é'.repeat(6), 10).errorKey).toBe(COVERAGE_ERROR_KEYS.tooLarge);
    expect(COVERAGE_MAX_UPLOAD_BYTES).toBe(2 * 1024 * 1024);
  });

  it('maps API failures to a message key and the server detail', () => {
    expect(uploadErrorOf({ status: 403 }).key).toBe(COVERAGE_ERROR_KEYS.forbidden);
    expect(uploadErrorOf({ status: 400, error: { message: 'schemaVersion must be 1' } })).toEqual({ key: COVERAGE_ERROR_KEYS.rejected, detail: 'schemaVersion must be 1' });
    expect(uploadErrorOf({ status: 413 }).key).toBe(COVERAGE_ERROR_KEYS.rejected);
    expect(uploadErrorOf({ status: 422, error: { detail: 'd' } })).toEqual({ key: COVERAGE_ERROR_KEYS.rejected, detail: 'd' });
    expect(uploadErrorOf({ status: 500, error: { message: 5 } })).toEqual({ key: COVERAGE_ERROR_KEYS.failed, detail: '' });
    expect(uploadErrorOf(null).key).toBe(COVERAGE_ERROR_KEYS.failed);
    expect(uploadErrorOf({ status: 400, error: { message: 'x'.repeat(500) } }).detail).toHaveLength(300);
  });
});

describe('readFileText', () => {
  it('reads the text of a file', async () => {
    await expect(readFileText(new File(['{"a":1}'], 'r.json', { type: 'application/json' }))).resolves.toBe('{"a":1}');
  });

  it('rejects when the browser cannot read the file', async () => {
    class FailingReader {
      error = new Error('boom');
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      readAsText(): void {
        this.onerror?.();
      }
    }
    const original = globalThis.FileReader;
    (globalThis as unknown as { FileReader: unknown }).FileReader = FailingReader;
    try {
      await expect(readFileText(new Blob(['x']))).rejects.toThrow('boom');
    } finally {
      globalThis.FileReader = original;
    }
  });
});
