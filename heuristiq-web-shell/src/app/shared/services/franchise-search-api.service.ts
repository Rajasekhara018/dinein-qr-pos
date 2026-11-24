import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, forkJoin, map, of, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FRANCHISE_SEARCH_MODELS } from './franchise-search-models';

interface Model {
  model: string; path: string; global: boolean; owned: boolean;
  slots: Record<string, string>; filters: Record<string, string>;
}
interface Node { field?: string; operator?: string; value?: string; criteria1?: number; group?: Node[]; }
export interface FranchiseSearchPage<T> { items: T[]; page: number; size: number; totalElements: number; }
interface Hit { payload?: unknown; }
const MODELS: readonly Model[] = FRANCHISE_SEARCH_MODELS as unknown as readonly Model[];

/** The existing UAM GetAll contract, with source-owned franchise slot bindings. No source list fallback. */
@Injectable({ providedIn: 'root' })
export class FranchiseSearchApiService {
  private readonly base = environment.gatewayUrl.replace(/\/$/, '') + '/v1/search/elastic';
  constructor(private readonly http: HttpClient) { }

  page<T>(model: string, filters: Record<string, unknown> = {}, page = 0, size = 20): Observable<FranchiseSearchPage<T>> {
    const spec = this.model(model);
    page = Math.max(0, Math.trunc(page)); size = Math.max(1, Math.min(1000, Math.trunc(size)));
    return this.collect<T>(spec, this.query(spec, filters), page * size, size).pipe(
      map(result => ({ ...result, page, size })));
  }

  all<T>(model: string, filters: Record<string, unknown> = {}): Observable<T[]> {
    const spec = this.model(model);
    return this.collect<T>(spec, this.query(spec, filters), 0, null).pipe(map(result => result.items));
  }

  /** Converts an existing source-client list contract to a model GetAll call; details/actions return null. */
  readSource<T>(source: string, params: Record<string, unknown> = {}): Observable<T> | null {
    const url = new URL(source || '/', window.location.origin);
    // Absolute source URLs retain their path. Relative source-client paths are below the franchise API.
    let path = /^https?:\/\//.test(source) ? new URL(source).pathname : url.pathname;
    if (!path.startsWith('/api/')) path = '/api/v1/franchise' + path;
    path = path.replace(/^\/api\/franchise(?=\/|$)/, '/api/v1/franchise').replace(/\/$/, '');
    let spec: Model | undefined;
    let context: Record<string, string> = {};
    for (const candidate of MODELS) {
      const keys: string[] = [];
      const pattern = candidate.path.split('/').map(part => {
        if (part.startsWith('{')) { keys.push(part.slice(1, -1)); return '([^/]+)'; }
        return part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      }).join('/');
      const match = path.match(new RegExp('^' + pattern + '$'));
      if (match) { spec = candidate; keys.forEach((key, i) => context[key] = decodeURIComponent(match[i + 1])); break; }
    }
    if (!spec) return null;
    const filters: Record<string, unknown> = Object.fromEntries(url.searchParams);
    Object.assign(filters, params);
    const page = Number(filters['page'] ?? 0), size = Number(filters['size'] ?? 20);
    const paged = filters['page'] !== undefined || filters['size'] !== undefined;
    delete filters['page']; delete filters['size'];
    if (context['franchiseId']) filters['franchiseId'] = context['franchiseId'];
    if (context['subscriptionId']) filters['subscriptionId'] = context['subscriptionId'];
    if (context['id']) filters['parentId'] = context['id'];
    const selected = spec;
    if (selected.model.includes('PLACE_OPTION') || (selected.model === 'FRANCHISE_LOCATION_CHILD' && filters['includeInactive'] !== true && filters['includeInactive'] !== 'true')) filters['usable'] = true;
    if (selected.model === 'FRANCHISE_DRIVER_SUBSCRIPTION_PLAN' && filters['includeInactive'] !== true && filters['includeInactive'] !== 'true') filters['active'] = true;
    delete filters['includeInactive'];
    if (selected.model === 'FRANCHISE_PLAN') {
      const optionsUrl = environment.gatewayUrl + '/v1/franchise/plans/options';
      const rows = paged ? this.page(selected.model, filters, page, size)
        : this.all(selected.model, filters).pipe(map(items => ({ items, page: 0, size: items.length, totalElements: items.length })));
      return forkJoin({ rows, options: this.http.get<{ success: boolean; responseObject: Record<string, unknown> }>(optionsUrl) }).pipe(
        map(({ rows, options }) => {
          if (!options.success) throw this.failed('Franchise plan options could not be loaded');
          return { ...options.responseObject, plans: rows.items, page: rows.page, size: rows.size, totalElements: rows.totalElements } as T;
        }));
    }
    if (selected.model === 'FRANCHISE_MEMBER' || selected.model === 'FRANCHISE_ADMIN_MEMBER') {
      const franchiseId = String(filters['franchiseId']);
      const summaryUrl = environment.gatewayUrl + '/v1/franchise' +
        selected.path.slice('/api/v1/franchise'.length).replace('{franchiseId}', encodeURIComponent(franchiseId)) + '/summary';
      const summaryParams: Record<string, string> = filters['applicationCode'] ? { applicationCode: String(filters['applicationCode']) } : {};
      return forkJoin({
        members: this.page(selected.model, filters, page, size),
        summary: this.http.get<{ success: boolean; message?: string; responseObject: Record<string, unknown> }>(summaryUrl, { params: summaryParams }),
      }).pipe(map(({ members, summary }) => {
        if (!summary.success) throw this.failed(summary.message);
        return { ...summary.responseObject, members } as T;
      }));
    }
    if (['FRANCHISE_FRANCHISE', 'FRANCHISE_MINE', 'FRANCHISE_AGENT', 'FRANCHISE_EVENT_TYPE', 'FRANCHISE_DRIVER_SUBSCRIPTION_PLAN'].includes(selected.model)) {
      return this.all(selected.model, filters) as Observable<T>;
    }
    const result = this.page(selected.model, filters, page, size);
    return (selected.model.startsWith('FRANCHISE_DRIVER_SUBSCRIPTION')
      ? result.pipe(map(value => ({ items: value.items, page: value.page, size: value.size, total: value.totalElements })))
      : result) as Observable<T>;
  }

  private model(code: string): Model {
    const spec = MODELS.find(entry => entry.model === code);
    if (!spec) throw new Error('Unknown franchise search model: ' + code);
    return spec;
  }

  private query(spec: Model, filters: Record<string, unknown>): Node[] {
    const group: Node[] = [];
    for (const [key, raw] of Object.entries(filters)) {
      if (raw === null || raw === undefined || String(raw).trim() === '') continue;
      const value = String(raw).trim();
      if (key === 'activeOnly' && value === 'false') continue;
      if (key === 'usable' && value === 'false') continue;
      if (key === 'q') { group.push({ field: 'text13', operator: 'LIKE', value: value.toLowerCase() }); continue; }
      if (key === 'activeOn') {
        const day = value.slice(0, 10) + 'T00:00:00Z';
        group.push({ field: 'date1', operator: 'LE', value: day },
          { criteria1: 1, group: [{ field: 'date2', operator: 'GE', value: day }, { field: 'date2', operator: 'EMPTY' }] });
        continue;
      }
      if (key === 'from' || key === 'to') {
        const date = value.length === 10 ? value + (key === 'from' ? 'T00:00:00Z' : 'T23:59:59.999Z') : value;
        group.push({ field: 'datetime1', operator: key === 'from' ? 'GE' : 'LE', value: date }); continue;
      }
      const field = key === 'franchiseId' ? 'text14' : key === 'subscriptionId' ? 'text12'
        : key === 'parentId' ? 'text6'
        : key === 'active' && spec.model === 'FRANCHISE_DRIVER_SUBSCRIPTION_PLAN' ? 'text3' : spec.filters[key];
      if (!field) throw new Error('Unsupported ' + spec.model + ' filter: ' + key);
      group.push({ field, operator: 'EQ', value });
    }
    return group;
  }

  private collect<T>(spec: Model, group: Node[], offset: number, take: number | null): Observable<{ items: T[]; totalElements: number }> {
    const chunk = take !== null ? Math.min(take, 50) : 50;
    const first = Math.floor(offset / chunk), skip = offset % chunk;
    const read = (pageNo: number, accumulated: T[], total: number): Observable<{ items: T[]; totalElements: number }> => {
      const body = {
        requestType: 'LIST', pageNo, pageSize: chunk,
        ...(group.length ? { searchQuery: { group: { criteria1: 0, group } } } : {}),
        sort: this.sort(spec)
      };
      return this.http.post<{ success: boolean; errors?: string[]; object?: { content?: Hit[]; totalElements?: number; page?: { totalElements?: number } } }>(
        this.base + '/' + spec.model + '/getall', body).pipe(switchMap(response => {
          if (!response.success || !response.object) return throwError(() => this.failed(response.errors?.[0]));
          const hits = response.object.content ?? [], totalElements = response.object.page?.totalElements ?? response.object.totalElements ?? hits.length;
          const next = hits.map(hit => {
            if (!hit.payload || typeof hit.payload !== 'object') throw this.failed('Franchise search record is incomplete; reindex this model');
            return hit.payload as T;
          }).slice(pageNo === first ? skip : 0);
          const items = accumulated.concat(next);
          if (hits.length && (pageNo + 1) * chunk < totalElements && (take === null || items.length < take)) return read(pageNo + 1, items, totalElements);
          return of({ items: take === null ? items : items.slice(0, take), totalElements });
        }));
    };
    return read(first, [], 0);
  }
  private failed(message?: string): unknown {
    return { error: { message: message || 'Search request failed', detail: message || 'Search request failed' } };
  }

  private sort(spec: Model): Record<string, string> {
    if (spec.global) return { field1: 'number1', directionField1: 'ASC', field2: 'text1', directionField2: 'ASC', field3: 'modelKey', directionField3: 'ASC' };
    const recent: Record<string, string> = {
      FRANCHISE_FEE_SCHEDULE: 'date1', FRANCHISE_COMMISSION_PLAN: 'date1', FRANCHISE_REFERRAL_PLAN: 'date1',
      FRANCHISE_PAYOUT_RULE: 'date1', FRANCHISE_LEDGER_ENTRY: 'datetime1', FRANCHISE_PAYOUT: 'datetime1',
      FRANCHISE_DRIVER_SUBSCRIPTION: 'datetime2', FRANCHISE_DRIVER_SUBSCRIPTION_CHARGE: 'date1', FRANCHISE_SALE_RECONCILIATION: 'date1',
    };
    return { field1: recent[spec.model] || 'text1', directionField1: recent[spec.model] ? 'DESC' : 'ASC', field2: 'modelKey', directionField2: 'ASC' };
  }
}
