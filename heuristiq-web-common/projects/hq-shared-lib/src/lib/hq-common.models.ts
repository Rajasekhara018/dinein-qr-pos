// Shared models for the hq-* components (hq-shared-lib).

/** Mirrors Rider_DMS com.railcab.dms.document.dto.DocumentResponse field-for-field. */
export interface HqDocument {
  id: string;
  category: string;
  ownerId: string;
  originalFileName: string;
  storedFileName: string;
  publicUrl: string | null;
  provider: string;
  versionId: string | null;
  checksumSha256: string | null;
  contentType: string;
  sizeBytes: number;
  status: string;
  scanStatus: string;
  scanMessage: string | null;
  retentionUntil: string | null;
  legalHold: boolean;
  createdAt: string;
}

/** Mirrors Rider_DMS DocumentDownloadUrlResponse. */
export interface HqDownloadUrl {
  url: string;
  expiresAt: string;
}

/** Envelope every Rider_DMS / Rider_UAM endpoint returns. */
export interface HqCommonResponse<T> {
  success: boolean;
  message?: string;
  responseObject?: T;
}

/** Data for the hq-close confirm/thank-you dialog (tou-close's CloseDialog). */
export interface HqCloseDialogData {
  /** true = yes/no confirmation; false = single "Close" acknowledgement. */
  confirm: boolean;
  message: string;
  title?: string;
}

/** Optional image-crop hook -- an app that bundles a cropper provides HQ_IMAGE_CROPPER. */
export type HqImageCropper = (event: Event, file: File, aspectRatio: number) => Promise<File | null>;

// ── Lookup (hq-common-model / HqCommonSearchService) ───────────────────────

export type HqLookupOperator = 'EQ' | 'NE' | 'LIKE';

export interface HqLookupColumn {
  /** Property path on a row, dot-separated for nested values (e.g. 'fullName.firstName'). */
  key: string;
  label: string;
  /** Offered as a quick-search chip (tou-common-model's searchField chips). Default true. */
  searchable?: boolean;
  type?: 'text' | 'date';
}

/** One advanced-filter row (tou's AdvancedQuery). */
export interface HqAdvancedQuery {
  fieldName: string;
  operator: HqLookupOperator;
  value: string;
  /** How this row combines with the previous ones. */
  condition: 'AND' | 'OR';
}

export interface HqLookupPage<T = any> {
  rows: T[];
  total: number;
}

export interface HqLookupFetchRequest {
  page: number;
  size: number;
  search: string;
  searchField: string;
}

/** Registry entry describing one lookup table (replaces tou's per-tableID switch statements). */
export interface HqLookupTable {
  id: string;
  title: string;
  columns: HqLookupColumn[];
  /** Column the quick search targets by default. */
  defaultSearchField: string;
  /** Row property used as the selected key (tou's modelKey). */
  keyField: string;
  /** Row property shown as the selected value (tou's displayValue). */
  displayField: string;
  /** Loads one page; when the backend has no server-side search the service filters the page. */
  fetch: (req: HqLookupFetchRequest) => import('rxjs').Observable<HqLookupPage>;
  /** true when fetch() already applies req.search server-side. */
  serverSearch?: boolean;
  /** Set when the table is served by the search index: the service then sends quick search and filters to the server. */
  elastic?: HqElasticSource;
}

/**
 * A lookup table served by the Search API's index (POST /api/v1/search/elastic/{model}/getall) instead of the owning
 * service's list endpoint. Paging, the quick search, the advanced filters and the pre-filters all run on the server, so
 * the dialog stays fast with lakhs of records and never loads a whole table.
 */
export interface HqElasticSource {
  /** Model named in the URL, e.g. 'user' or 'role'. */
  model: string;
  /** Row property -> index slot (text1.., amount1.., number1.., date1..). Only mapped properties can be searched or filtered on. */
  slots: Record<string, string>;
  /** Slot the pages are ordered by. Default: the slot of the table's display field. */
  sortSlot?: string;
  /** Builds a row from an index document. Default: `id` from the document's modelKey plus every mapped property from its slot. */
  toRow?: (doc: Record<string, any>) => any;
}

/** What register() accepts: a table with either its own fetch() or an `elastic` source (fetch is then supplied). */
export type HqLookupTableInput = Omit<HqLookupTable, 'fetch'> & {
  fetch?: HqLookupTable['fetch'];
  elastic?: HqElasticSource;
};

/**
 * Which form field takes what from the row picked in the lookup popup: the field's name -> the row property to copy (a dotted
 * path such as 'owner.name' reads inside the row), or a function of the row for a value that has to be worked out.
 * Example for a pincode table: `{ pincode: 'pin', town: 'town', district: 'district', state: 'state', country: 'country' }`.
 */
export type HqLookupFieldMap = Record<string, string | ((row: any) => unknown)>;

/** Anything with a `patchValue`, such as an Angular FormGroup. */
export interface HqLookupFormTarget {
  patchValue(value: Record<string, unknown>): void;
}

/** Data passed to / returned from hq-common-model (tou's ModalDialogData). */
export interface HqLookupDialogData {
  lookupTable: string;
  multiSelect?: boolean;
  /** Pre-filters always applied (tou's filter / filter2). */
  filters?: HqAdvancedQuery[];
  /** Single-select result. */
  modelKey?: string;
  displayValue?: string;
  element?: any;
  /** Multi-select result. */
  selected?: Array<{ key: string; value: string; element: any }>;
}
