import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Observable, defer, forkJoin, map, of, switchMap } from 'rxjs';
import { HqWebSearchConfig, HqWebSearchEnvelope, HqWebSearchModel, HqWebSearchNode, HqWebSearchPage } from './hq-web-search.models';

/**
 * Explicit screen-service calls to the Elasticsearch GetAll API (POST {baseUrl}/{model}/getall), shared by every web app.
 *
 * Not provided in root on purpose: each app (and each federated remote) owns its model catalogue, so it extends this
 * class with its own `@Injectable({ providedIn: 'root' })` subclass that passes a {@link HqWebSearchConfig}. One root
 * instance in the shared library would be shared by the host and every remote with a single catalogue.
 *
 * `getAll` returns a page, `all` every row, and `sourceGetAll` adapts the result to a screen's existing DTO shape.
 * Unknown models and unsupported filters fail with a 400-style error instead of being ignored.
 */
export class HqWebSearchApiService {
  private readonly models: readonly HqWebSearchModel[];

  constructor(private readonly http: HttpClient, private readonly config: HqWebSearchConfig) {
    this.models = config.models as readonly HqWebSearchModel[];
  }

  getAll<T>(model: string, filters: Record<string,unknown> = {}, page = 0, size = 20): Observable<HqWebSearchPage<T>> {
    return defer(() => {
      const spec = this.specification(model);
      this.validatePage(page, size);
      return this.collect<T>(spec, filters, page * size, size).pipe(map(result => ({ ...result, page, size })));
    });
  }

  all<T>(model: string, filters: Record<string,unknown> = {}): Observable<T[]> {
    return defer(() => this.collect<T>(this.specification(model), filters, 0, null).pipe(map(result => result.items)));
  }

  /** Keeps a screen's existing DTO and page convention while making its model selection explicit. */
  sourceGetAll<T>(model: string, parameters: Record<string,unknown> = {}): Observable<T> {
    return defer(() => {
      const spec = this.specification(model);
      const params = { ...spec.defaults, ...parameters };
      const page = Number(params['page'] ?? params['pageNo'] ?? spec.requestPageBase ?? 0) - (spec.requestPageBase ?? 0);
      const size = Number(params['size'] ?? params['pageSize'] ?? 20);
      this.validatePage(page, size);
      delete params['page']; delete params['pageNo']; delete params['size']; delete params['pageSize'];
      const paged = spec.requestPaged ?? spec.sourcePaged;
      return this.collect<unknown>(spec, params, paged ? page * size : 0, paged ? size : null).pipe(
        map(result => this.sourceResult(spec, result, page, size) as T));
    });
  }

  private specification(model: string): HqWebSearchModel {
    const spec = this.models.find(candidate => candidate.model === model);
    if (!spec) throw this.invalid('Unknown web GetAll model: ' + model);
    return spec;
  }
  private validatePage(page: number, size: number): void {
    if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 10000)
      throw this.invalid('Invalid GetAll page');
  }
  private collect<T>(spec: HqWebSearchModel, supplied: Record<string,unknown>, offset: number, take: number | null): Observable<{ items: T[]; totalElements: number; counts?: Record<string,number> }> {
    const params: Record<string,unknown> = { ...spec.defaults, ...supplied };
    let headers = this.config.headers?.() ?? new HttpHeaders();
    if (!spec.filters['scope']) {
      if (params['scope'] === 'ALL') headers = headers.set('X-View-All-Organizations', 'true');
      else if (params['scope'] && params['scope'] !== 'OWN') throw this.invalid('Invalid organization scope');
      delete params['scope'];
    }
    const group: HqWebSearchNode[] = [];
    for (const [name, raw] of Object.entries(params)) {
      if (raw === null || raw === undefined || String(raw).trim() === '') continue;
      if (spec.ignoredFilterValues?.[name]?.some(value => value.toLowerCase() === String(raw).toLowerCase())) continue;
      const binding = spec.filters[name];
      if (!binding) throw this.invalid('Unsupported ' + spec.model + ' filter: ' + name);
      if (binding[2] === 'trueOnly' && String(raw).toLowerCase() !== 'true') continue;
      let value = String(raw).trim();
      if (binding[2] === 'availableBoolean') value = String(['AVAILABLE','ACTIVE'].includes(value.toUpperCase()));
      if (binding[2] === 'activeBoolean') value = String(['ACTIVE','ENABLED'].includes(value.toUpperCase()));
      if (binding[2] === 'upper') value = value.toUpperCase();
      if (spec.normalizers[binding[0]] === 'lower' || binding[0] === 'text13') value = value.toLowerCase();
      if (/^(date|datetime)\d+$/.test(binding[0]) && value.length === 10) value += 'T00:00:00Z';
      group.push({ field: binding[0], operator: binding[1], value });
    }
    const endpoint = this.config.baseUrl().replace(/\/$/,'') + '/' + spec.model + '/getall';
    const chunk = Math.min(take ?? 50, 50), first = Math.floor(offset / chunk), skip = offset % chunk;
    const read = (index: number, accumulated: T[]): Observable<{ items: T[]; totalElements: number }> => {
      const body = { requestType: 'LIST', pageNo: index, pageSize: chunk,
        ...(group.length ? { searchQuery: { group: { criteria1: 0, group } } } : {}),
        sort: spec.sort ?? { field1:'createdAt', directionField1:'DESC', field2:'modelKey', directionField2:'DESC' } };
      return this.http.post<HqWebSearchEnvelope>(endpoint, body, { headers }).pipe(switchMap(response => {
        const object = this.result(response);
        const hits = object.content ?? [], totalElements = object.page?.totalElements ?? object.totalElements ?? hits.length;
        const rows = hits.map(hit => {
          if (!hit.payload || typeof hit.payload !== 'object') throw this.unavailable('The source model requires reindexing');
          return hit.payload as T;
        }).slice(index === first ? skip : 0);
        const items = accumulated.concat(rows);
        return hits.length && (index+1)*chunk < totalElements && (take === null || items.length < take)
          ? read(index+1,items) : of({ items: take === null ? items : items.slice(0,take), totalElements });
      }));
    };
    return read(first, []).pipe(switchMap(found => {
      const facets = Object.entries(spec.facets ?? {});
      if (!facets.length) return of(found);
      return forkJoin(facets.map(([name,node]) => this.http.post<HqWebSearchEnvelope>(endpoint, {
        requestType:'LIST', pageNo:0, pageSize:1,
        ...(node ? { searchQuery:{ group:{ criteria1:0, group:[node] } } } : {}),
      }, { headers }).pipe(map(response => {
        const object = this.result(response);
        return [name, object.page?.totalElements ?? object.totalElements ?? 0] as const;
      })))).pipe(map(counts => ({ ...found, counts:Object.fromEntries(counts) })));
    }));
  }
  private result(response: HqWebSearchEnvelope): NonNullable<HqWebSearchEnvelope['object']> {
    if (response?.success !== true || !response.object) throw this.unavailable(response?.errors?.[0] ?? 'Search request failed');
    return response.object;
  }
  private sourceResult(spec: HqWebSearchModel, result: { items:unknown[]; totalElements:number; counts?:Record<string,number> }, page:number, size:number):unknown {
    const totalPages = Math.ceil(result.totalElements / size);
    if (spec.format === 'array') return result.items;
    if (spec.format === 'trainItemsPage') return { items:result.items,total:result.totalElements,page:page+1,pageSize:size,
      ...(result.counts ? { statusCounts:result.counts } : {}) };
    if (spec.format === 'itemsPage') return { items:result.items,total:result.totalElements,page,size };
    if (spec.format === 'itemsSpringPage') return { items:result.items,totalElements:result.totalElements,totalPages,page,size };
    if (spec.format === 'adminPage') return { content:result.items,totalElements:result.totalElements,totalPages,currentPage:page,pageSize:size,
      first:page===0,last:page+1>=totalPages };
    return { content:result.items,totalElements:result.totalElements,totalPages,page,number:page,size,first:page===0,last:page+1>=totalPages };
  }
  private invalid(message:string):HttpErrorResponse { return new HttpErrorResponse({status:400,error:{message,errorCode:'VALIDATION_ERROR'}}); }
  private unavailable(message:string):HttpErrorResponse { return new HttpErrorResponse({status:503,error:{message,errorCode:'SEARCH_INDEX_UNAVAILABLE'}}); }
}
