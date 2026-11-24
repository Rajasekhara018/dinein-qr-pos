import { clampPage } from '../qa-dashboard.helpers';
import {
  QA_ENDPOINT_CHIP_LIMIT,
  QA_ENDPOINT_EMPTY_QUERY,
  QA_METHOD_CLASSES,
  QA_METHOD_CLASS_OTHER,
} from './qa-endpoint-inventory.constants';
import {
  QaEndpointCoverage,
  QaEndpointDetail,
  QaEndpointFilterOptions,
  QaEndpointItem,
  QaEndpointPage,
  QaEndpointQuery,
  QaMappedChips,
} from './qa-endpoint-inventory.models';

/** A fresh, empty query (never share the constant: the screen changes its copy). */
export function emptyEndpointQuery(overrides: Partial<QaEndpointQuery> = {}): QaEndpointQuery {
  return { ...QA_ENDPOINT_EMPTY_QUERY, ...overrides };
}

/** True when anything narrows the list. */
export function hasActiveEndpointQuery(query: QaEndpointQuery): boolean {
  return Object.values(query).some((value) => String(value ?? '').trim() !== '');
}

/**
 * The query parameters of a query: empty values are left out, the quick filter becomes the `tested` / `documented`
 * flags the API knows, and the page request is clamped to what the API accepts.
 */
export function endpointParams(query: QaEndpointQuery, page?: number, size?: number): Record<string, string | number> {
  const params: Record<string, string | number> = {};
  (['q', 'module', 'subModule', 'method'] as const).forEach((key) => {
    const value = query[key].trim();
    if (value) params[key] = value;
  });
  if (query.quick === 'tested') params['tested'] = 'true';
  if (query.quick === 'untested') params['tested'] = 'false';
  if (query.quick === 'undocumented') params['documented'] = 'false';
  if (page !== undefined && size !== undefined) Object.assign(params, clampPage(page, size));
  return params;
}

/** The style class of a method's badge. */
export function methodClass(method: string): string {
  return QA_METHOD_CLASSES[method.toUpperCase()] ?? QA_METHOD_CLASS_OTHER;
}

/** Share of documented endpoints that were tested, 0 to 100, for the progress bar. */
export function progressPercent(tested: number, documented: number): number {
  if (documented <= 0) return 0;
  return Math.min(100, Math.max(0, Math.round((tested / documented) * 100)));
}

/** The first mapped test cases of a row and how many more there are. */
export function mappedChips(keys: readonly string[], limit = QA_ENDPOINT_CHIP_LIMIT): QaMappedChips {
  return { visible: keys.slice(0, limit), extra: Math.max(keys.length - limit, 0) };
}

/** The sub-modules of the chosen module ('' until a module is chosen). */
export function endpointSubModules(options: QaEndpointFilterOptions | null, module: string): string[] {
  if (!options || !module) return [];
  return options.modules.find((m) => m.module === module)?.subModules ?? [];
}

// ---- normalising what the API returns (a missing field never breaks the screen) ----

export function normalizeCoverage(raw: Partial<QaEndpointCoverage> | null | undefined): QaEndpointCoverage {
  return {
    documented: raw?.documented ?? 0,
    tested: raw?.tested ?? 0,
    untested: raw?.untested ?? 0,
    coveragePct: raw?.coveragePct ?? 0,
    undocumentedTested: raw?.undocumentedTested ?? 0,
    totalEndpoints: raw?.totalEndpoints ?? 0,
    modulesCovered: raw?.modulesCovered ?? 0,
    subModulesCovered: raw?.subModulesCovered ?? 0,
    mappedScenarios: raw?.mappedScenarios ?? 0,
    lastSyncedAt: raw?.lastSyncedAt ?? null,
  };
}

export function normalizeEndpoint(raw: Partial<QaEndpointItem>): QaEndpointItem {
  const keys = raw.mappedTestKeys ?? [];
  return {
    id: raw.id ?? '',
    method: raw.method ?? '',
    path: raw.path ?? '',
    privilegeName: raw.privilegeName ?? null,
    module: raw.module ?? '',
    subModule: raw.subModule ?? '',
    documented: raw.documented ?? true,
    hitCount: raw.hitCount ?? 0,
    callCount: raw.callCount ?? 0,
    lastHitAt: raw.lastHitAt ?? null,
    mappedTestKeys: keys,
    mappedCount: raw.mappedCount ?? keys.length,
  };
}

export function normalizeEndpointPage(raw: Partial<QaEndpointPage> | null | undefined, page: number, size: number): QaEndpointPage {
  const items = (raw?.items ?? []).map(normalizeEndpoint);
  return {
    coverage: normalizeCoverage(raw?.coverage),
    items,
    page: raw?.page ?? page,
    size: raw?.size ?? size,
    totalElements: raw?.totalElements ?? items.length,
    totalPages: raw?.totalPages ?? (items.length ? 1 : 0),
  };
}

export function normalizeEndpointDetail(raw: Partial<QaEndpointDetail> | null | undefined): QaEndpointDetail {
  return {
    ...normalizeEndpoint(raw ?? {}),
    description: raw?.description ?? '',
    operation: raw?.operation ?? '',
    tests: (raw?.tests ?? []).map((t) => ({
      id: t.id ?? '',
      tcId: t.tcId ?? null,
      testKey: t.testKey ?? '',
      name: t.name ?? '',
      status: t.status ?? '',
      lastRunAt: t.lastRunAt ?? null,
    })),
  };
}

export function normalizeEndpointFilters(raw: Partial<QaEndpointFilterOptions> | null | undefined): QaEndpointFilterOptions {
  return { modules: raw?.modules ?? [], methods: raw?.methods ?? [] };
}
