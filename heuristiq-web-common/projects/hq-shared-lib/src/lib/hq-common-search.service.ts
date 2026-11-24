import { Inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { Observable, map, of, catchError, tap } from 'rxjs';
import { HQ_CONFIG, HqSharedLibConfig, hqUrls } from './hq-common.config';
// Circular with the dialog (which injects this service) but only dereferenced at call time.
import { HqCommonModel } from './hq-common-model/hq-common-model';
import {
  HqAdvancedQuery, HqCommonResponse, HqLookupDialogData, HqLookupFetchRequest, HqLookupFieldMap, HqLookupFormTarget,
  HqLookupPage, HqLookupTable, HqLookupTableInput,
} from './hq-common.models';

interface SpringPage<T> { content: T[]; totalElements: number; }
/** A node of the getall filter tree: a group (criteria1 0 = AND, 1 = OR) or a condition. */
interface ElasticNode { criteria1?: number; group?: ElasticNode[]; field?: string; operator?: string; value?: string; }
/** Rider_SearchAPI's getall envelope. `object` is the Spring page (flat, or with the figures nested under `page`). */
interface ElasticResponse { success: boolean; errors?: string[]; object?: any; }
/** Rider_AdminAPI's ResponseObject envelope ({status, data, message}). */
interface AdminResponse<T> { status: boolean; data: T; message?: string; }

/**
 * Lookup-table registry + query helpers behind hq-common-model (tou-common-search). Where tou
 * kept one switch statement per concern keyed by tableID, each Rider table is one HqLookupTable
 * entry here; apps add their own with register().
 */
@Injectable({ providedIn: 'root' })
export class HqCommonSearchService {
  private readonly tables = new Map<string, HqLookupTable>();
  private readonly urls: ReturnType<typeof hqUrls>;

  constructor(private http: HttpClient, private dialog: MatDialog, @Inject(HQ_CONFIG) config: HqSharedLibConfig) {
    this.urls = hqUrls(config);
    this.registerDefaults();
  }

  /** Adds a table. One with an `elastic` source needs no fetch(): its pages come from the search index. */
  register(input: HqLookupTableInput): void {
    const table: HqLookupTable = {
      ...input,
      serverSearch: input.elastic ? true : input.serverSearch,
      fetch: input.fetch ?? ((req) => this.elasticPage(table, req)),
    };
    this.tables.set(table.id, table);
  }

  getTable(id: string): HqLookupTable {
    const table = this.tables.get(id);
    if (!table) throw new Error(`Unknown hq lookup table '${id}'`);
    return table;
  }

  /** Opens the lookup dialog; resolves with the updated data, or undefined when dismissed. */
  openLookup(data: HqLookupDialogData, width = '960px'): Observable<HqLookupDialogData | undefined> {
    return this.dialog
      .open<HqCommonModel, HqLookupDialogData, HqLookupDialogData>(HqCommonModel, {
        data: { ...data }, width, maxWidth: '95vw', autoFocus: false,
      })
      .afterClosed();
  }

  /**
   * The values a picked row gives to a form: each field of `map` takes the row property it names (or the result of its function).
   * A property the row does not have gives null, so a field filled by an earlier pick is cleared, not left stale.
   */
  mapRow(row: any, map: HqLookupFieldMap): Record<string, unknown> {
    const values: Record<string, unknown> = {};
    for (const [field, source] of Object.entries(map)) {
      const value = typeof source === 'function' ? source(row) : this.read(row, source);
      values[field] = value === undefined ? null : value;
    }
    return values;
  }

  /**
   * Opens the lookup popup and, when a row is picked, fills the form from it (see mapRow). Nothing changes when the popup is
   * dismissed without a pick. Resolves with the popup's result so the screen can still react to the pick.
   */
  openLookupInto(
    target: HqLookupFormTarget,
    data: HqLookupDialogData,
    map: HqLookupFieldMap,
    width = '960px',
  ): Observable<HqLookupDialogData | undefined> {
    return this.openLookup(data, width).pipe(
      tap((result) => {
        if (result?.element) target.patchValue(this.mapRow(result.element, map));
      }),
    );
  }

  /**
   * One page for the dialog: the table's own fetch, then quick search / advanced filters applied
   * client-side whenever the backend can't do them itself.
   */
  query(
    tableId: string,
    req: HqLookupFetchRequest,
    advanced: HqAdvancedQuery[] = [],
    preFilters: HqAdvancedQuery[] = [],
  ): Observable<HqLookupPage> {
    const table = this.getTable(tableId);
    // An index-backed table filters on the server, so a page never has to be loaded just to be filtered here.
    if (table.elastic) return this.elasticPage(table, req, advanced, preFilters);
    const clientFilters = [...preFilters, ...advanced].filter((f) => f.fieldName && f.value !== '');
    const quick = req.search?.trim();
    const needsClientFilter = clientFilters.length > 0 || (!!quick && !table.serverSearch);
    return table.fetch(req).pipe(
      map((page) => {
        if (!needsClientFilter) return page;
        let rows = page.rows;
        if (quick && !table.serverSearch) {
          rows = rows.filter((row) => this.matches(row, { fieldName: req.searchField, operator: 'LIKE', value: quick, condition: 'AND' }));
        }
        if (clientFilters.length) rows = rows.filter((row) => this.matchesAll(row, clientFilters));
        return { rows, total: rows.length };
      }),
    );
  }

  /** Resolves a stored key back to its display value (tou's getSearchUsingModelKey). */
  resolveDisplayValue(tableId: string, key: string): Observable<{ displayValue: string; element: any } | null> {
    if (!key) return of(null);
    const table = this.getTable(tableId);
    // An index-backed table looks the key up directly when its key property has a slot, instead of scanning a page.
    const keyFilter: HqAdvancedQuery[] = table.elastic?.slots[table.keyField]
      ? [{ fieldName: table.keyField, operator: 'EQ', value: String(key), condition: 'AND' }] : [];
    const lookup = keyFilter.length
      ? this.elasticPage(table, { page: 0, size: 1, search: '', searchField: table.defaultSearchField }, [], keyFilter)
      : table.fetch({ page: 0, size: 200, search: '', searchField: table.defaultSearchField });
    return lookup.pipe(
      map((page) => {
        const element = page.rows.find((row) => String(this.read(row, table.keyField)) === String(key));
        return element ? { displayValue: String(this.read(element, table.displayField) ?? ''), element } : null;
      }),
      catchError(() => of(null)),
    );
  }

  /** Reads a dot-separated property path. */
  read(row: any, path: string): any {
    return path.split('.').reduce((acc, part) => (acc == null ? acc : acc[part]), row);
  }

  // ── The search index (getall API) ─────────────────────────────────────────
  /**
   * One page from POST /search/elastic/{model}/getall. The quick search (on the chosen column), the advanced filters and
   * the pre-filters are all sent as one AND/OR tree, so only the asked-for page comes back. A filter on a property that
   * has no slot cannot be run by the index and is left out.
   */
  private elasticPage(
    table: HqLookupTable,
    req: HqLookupFetchRequest,
    advanced: HqAdvancedQuery[] = [],
    preFilters: HqAdvancedQuery[] = [],
  ): Observable<HqLookupPage> {
    const source = table.elastic!;
    const toNode = (f: HqAdvancedQuery): ElasticNode | null => {
      const slot = source.slots[f.fieldName];
      return slot && f.value !== '' && f.fieldName
        ? { field: slot, operator: f.operator, value: String(f.value).trim() } : null;
    };
    // Left to right, each filter's AND/OR says how it joins everything before it.
    const chain = (filters: HqAdvancedQuery[]): ElasticNode | null => filters.reduce<ElasticNode | null>((acc, f) => {
      const node = toNode(f);
      if (!node) return acc;
      return acc ? { criteria1: f.condition === 'OR' ? 1 : 0, group: [acc, node] } : node;
    }, null);
    const quick = req.search?.trim();
    const parts = [
      chain(preFilters),
      chain(advanced),
      quick ? toNode({ fieldName: req.searchField, operator: 'LIKE', value: quick, condition: 'AND' }) : null,
    ].filter((n): n is ElasticNode => !!n);
    const sortSlot = source.sortSlot ?? source.slots[table.displayField];
    const body = {
      requestType: 'LIST',
      searchQuery: parts.length ? { group: { criteria1: 0, group: parts } } : undefined,
      pageNo: req.page,
      pageSize: req.size,
      sort: sortSlot ? { field1: sortSlot, directionField1: 'ASC' } : undefined,
    };
    return this.http.post<ElasticResponse>(`${this.urls.search}/elastic/${encodeURIComponent(source.model)}/getall`, body).pipe(
      map((res) => {
        if (!res.success) throw { error: { message: res.errors?.[0] || 'Lookup failed' } };
        const content: any[] = res.object?.content ?? [];
        const meta = res.object?.page ?? res.object ?? {};
        return { rows: content.map((doc) => this.elasticRow(source, doc)), total: meta.totalElements ?? content.length };
      }),
    );
  }

  private elasticRow(source: NonNullable<HqLookupTable['elastic']>, doc: Record<string, any>): any {
    if (source.toRow) return source.toRow(doc);
    const row: Record<string, any> = { id: doc['modelKey'] };
    Object.entries(source.slots).forEach(([prop, slot]) => {
      const value = doc[slot];
      if (value === undefined || value === null || value === '') return;
      // 'a.b' builds a nested row property, which the dialog reads back with the same path.
      const path = prop.split('.');
      path.slice(0, -1).reduce((o, k) => (o[k] ??= {}), row)[path[path.length - 1]] = value;
    });
    return row;
  }

  private matchesAll(row: any, filters: HqAdvancedQuery[]): boolean {
    // Left-to-right: each filter's condition says how it joins the result so far.
    return filters.reduce<boolean | null>((acc, filter) => {
      const hit = this.matches(row, filter);
      if (acc === null) return hit;
      return filter.condition === 'OR' ? acc || hit : acc && hit;
    }, null) ?? true;
  }

  private matches(row: any, filter: HqAdvancedQuery): boolean {
    const cell = String(this.read(row, filter.fieldName) ?? '').toLowerCase();
    const value = String(filter.value ?? '').trim().toLowerCase();
    switch (filter.operator) {
      case 'EQ': return cell === value;
      case 'NE': return cell !== value;
      default: return cell.includes(value);
    }
  }

  // ── Built-in Rider tables ─────────────────────────────────────────────────
  private registerDefaults(): void {
    this.register({
      id: 'UAM_USERS',
      title: 'Users',
      keyField: 'id',
      displayField: 'userName',
      defaultSearchField: 'userName',
      columns: [
        { key: 'userName', label: 'Username' },
        { key: 'displayName', label: 'Name' },
        { key: 'email', label: 'Email' },
        { key: 'status', label: 'Status' },
      ],
      // Served by the search index. Slots mirror Rider_UAM's UamUser.getSearchIndexFields().
      elastic: {
        model: 'user',
        slots: { userName: 'text1', email: 'text2', status: 'text4', tenantId: 'text5', displayName: 'text6' },
      },
    });
    this.register({
      id: 'UAM_ROLES',
      title: 'Roles',
      keyField: 'code',
      displayField: 'code',
      defaultSearchField: 'code',
      columns: [
        { key: 'code', label: 'Code' },
        { key: 'name', label: 'Name' },
        { key: 'description', label: 'Description' },
      ],
      // Slots mirror Rider_UAM's UamRole.getSearchIndexFields().
      elastic: {
        model: 'role',
        slots: { code: 'text1', name: 'text2', description: 'text3', tenantId: 'text4' },
      },
    });
    this.register({
      id: 'VENDORS',
      title: 'Vendors',
      keyField: 'id',
      displayField: 'businessName',
      defaultSearchField: 'businessName',
      columns: [
        { key: 'businessName', label: 'Business' },
        { key: 'owner_name', label: 'Owner' },
        { key: 'city', label: 'City' },
        { key: 'status', label: 'Status' },
      ],
      fetch: (req) => this.adminPage('/admin/vendors', req),
    });
    this.register({
      id: 'VEHICLES',
      title: 'Vehicles',
      keyField: 'id',
      displayField: 'vehicleNumber',
      defaultSearchField: 'vehicleNumber',
      serverSearch: true,
      columns: [
        { key: 'vehicleNumber', label: 'Number' },
        { key: 'make', label: 'Make' },
        { key: 'model', label: 'Model' },
        { key: 'vehicleType', label: 'Type' },
        { key: 'status', label: 'Status' },
      ],
      // AdminAPI's vehicle list searches server-side across its own fields.
      fetch: (req) => this.adminPage('/admin/vehicles', req, req.search ? { search: req.search.trim() } : {}),
    });
    // No DRIVERS table yet: Rider_DispatchService only lists drivers on its service-token
    // /api/v1/internal/admin/drivers route, which a browser can't call. Register one once a
    // user-facing driver list endpoint exists.
  }

  private uamPage(path: string, req: HqLookupFetchRequest): Observable<HqLookupPage> {
    const params = new HttpParams().set('page', req.page).set('size', req.size);
    return this.http.get<HqCommonResponse<SpringPage<any>>>(`${this.urls.uam}${path}`, { params }).pipe(
      map((res) => {
        if (!res.success) throw { error: { message: res.message || 'Lookup failed' } };
        return { rows: res.responseObject?.content ?? [], total: res.responseObject?.totalElements ?? 0 };
      }),
    );
  }

  private adminPage(path: string, req: HqLookupFetchRequest, extra: Record<string, string> = {}): Observable<HqLookupPage> {
    let params = new HttpParams().set('page', req.page).set('size', req.size);
    Object.entries(extra).forEach(([k, v]) => (params = params.set(k, v)));
    return this.http.get<AdminResponse<SpringPage<any>>>(`${this.urls.admin}${path}`, { params }).pipe(
      map((res) => {
        if (!res.status) throw { error: { message: res.message || 'Lookup failed' } };
        return { rows: res.data?.content ?? [], total: res.data?.totalElements ?? 0 };
      }),
    );
  }
}
