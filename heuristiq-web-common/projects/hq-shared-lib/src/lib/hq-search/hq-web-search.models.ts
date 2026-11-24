import { HttpHeaders } from '@angular/common/http';

/** One condition (or nested group) of an Elasticsearch GetAll search query. */
export interface HqWebSearchNode {
  field?: string;
  operator?: string;
  value?: string;
  criteria1?: number;
  group?: HqWebSearchNode[];
}

/** A published web GetAll model (the per-app catalogue in `web-search-models.ts`). */
export interface HqWebSearchModel {
  model: string;
  sourcePaged: boolean;
  requestPaged?: boolean;
  format: string;
  filters: Record<string, readonly string[]>;
  defaults: Record<string, string>;
  normalizers: Record<string, string>;
  requestPageBase?: number;
  sort?: Record<string, string>;
  facets?: Record<string, HqWebSearchNode | null>;
  ignoredFilterValues?: Record<string, readonly string[]>;
}

/** The search API's response envelope. */
export interface HqWebSearchEnvelope {
  success?: boolean;
  errors?: string[];
  object?: { content?: { payload?: unknown }[]; totalElements?: number; page?: { totalElements?: number } };
}

/** One page of a model's rows, with optional facet counts. */
export interface HqWebSearchPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  counts?: Record<string, number>;
}

/**
 * What an app hands the shared search adapter: its own model catalogue, the base URL of the search API's
 * `/elastic` endpoints, and (optionally) extra headers per request, e.g. a Bearer token for a stand-alone remote.
 */
export interface HqWebSearchConfig {
  models: readonly unknown[];
  baseUrl: () => string;
  headers?: () => HttpHeaders;
}
