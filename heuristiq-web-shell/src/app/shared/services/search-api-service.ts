import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TranslateService } from '@ngx-translate/core';
import { NmsTemplate } from '../../nms-templates/nms-template.model';
import {
  SpringPage,
  UamUser, UamRole, Tenant, Application, ApiClient, SecurityPolicy,
} from '../models/uam.models';

/**
 * Talks to Rider_SearchAPI (Elasticsearch-backed, Eureka id REALBOOM-SEARCH-API) via the API
 * Gateway's search-api route: every list and lookup read is POST {gatewayUrl}/v1/search/elastic/{model}/getall
 * (the index's getall API), one page at a time.
 *
 * The index is a single generic EAV-style document (`SearchDocument`): every model's fields live
 * in positional slots (text1..15, amount1..5, number1..5, date1..5, datetime1..5) plus
 * `model`/`modelKey`/`ownerKey`. What each slot means is owned by the producing entity's
 * `getSearchIndexFields()` in Rider_UAM -- `UAM_SEARCH_MODELS` below mirrors those methods and
 * must be kept in sync with them.
 *
 * Kept separate from UamService on purpose: different service, different base URL, and a
 * POST-a-query-DSL contract rather than UAM's REST-per-entity one.
 */

/** Mirrors Rider_SearchAPI's com.railcab.search.model.SearchModel (subset used by the web portal). */
export type SearchModel = 'USER' | 'ROLE' | 'GROUP' | 'TENANT' | 'APPLICATION' | 'API_CLIENT' | 'SECURITY_POLICY'
  | 'NOTIFICATION_TEMPLATE';

/** Mirrors com.railcab.search.query.Operator. text* slots accept EQ/NE/LIKE/IN only;
 *  amount/number/date/datetime slots accept EQ/NE/GE/GT/LE/LT only. LIKE is a case-sensitive
 *  "contains" match on the slot's .keyword sub-field. */
export type SearchOperator = 'EQ' | 'NE' | 'GE' | 'GT' | 'LE' | 'LT' | 'LIKE' | 'IN';

/** Mirrors com.railcab.search.query.SearchCondition. */
export interface SearchCondition {
  field: string;
  operator: SearchOperator;
  value: string;
}

/** Mirrors com.railcab.search.query.SearchGroup -- a flat group, no nested sub-groups. */
export interface SearchGroup {
  logic: 'AND' | 'OR';
  conditions: SearchCondition[];
}

/** Mirrors com.railcab.search.api.dto.SearchRequest(SearchQuery query, int page, int size). */
export interface SearchRequest {
  query: { model: SearchModel; group: SearchGroup | null };
  page: number;
  size: number;
}

/** Mirrors com.railcab.search.document.SearchDocument. Only slot fields -- never the full entity. */
export interface SearchDocument {
  id: string;
  model: string;
  modelKey: string;
  ownerKey: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  [slot: string]: string | number | null | undefined;
}

/** How one list-screen filter key maps onto a search slot. */
export interface SearchFilterBinding {
  field: string;
  operator: SearchOperator;
}

/** Per-model contract: which slot each filter targets, and how a document becomes a table row. */
export interface SearchModelBinding<T> {
  model: SearchModel;
  filters: Record<string, SearchFilterBinding>;
  toRow: (doc: SearchDocument) => T;
}

const str = (doc: SearchDocument, slot: string): string | null => {
  const v = doc[slot];
  return v === undefined || v === null || v === '' ? null : String(v);
};
const bool = (doc: SearchDocument, slot: string): boolean | null => {
  const v = str(doc, slot);
  return v === null ? null : v === 'true';
};
const csv = (doc: SearchDocument, slot: string): string[] => {
  const v = str(doc, slot);
  return v ? v.split(',') : [];
};
/** Returns `n` placeholder ids so screens that render `row.xxx.length` show the indexed count. */
const countList = (doc: SearchDocument, slot: string): string[] => {
  const n = Number(doc[slot] ?? 0);
  return Number.isFinite(n) && n > 0 ? new Array<string>(n).fill('') : [];
};

/**
 * Slot mappings, mirroring Rider_UAM's `getSearchIndexFields()` per entity. Rows are partial
 * entities (only what the list table/actions need: `id` from modelKey + the indexed columns),
 * hence the casts -- don't pass a search row into an edit form; the edit screens re-fetch by id.
 */
export const UAM_SEARCH_MODELS = {
  // UamUser: text1=userName text2=email text3=phone(concat) text4=status text5=tenantId
  //          text6=displayName text7=fullName text8=roles(csv)
  users: {
    model: 'USER',
    filters: {
      userName: { field: 'text1', operator: 'LIKE' },
      email: { field: 'text2', operator: 'LIKE' },
      roleCode: { field: 'text8', operator: 'LIKE' },
      status: { field: 'text4', operator: 'EQ' },
      application: { field: 'text9', operator: 'LIKE' },
      // The user's department id, so a person limited to their department sees only that department's people.
      department: { field: 'text10', operator: 'IN' },
    },
    toRow: (d) => ({
      id: d.modelKey,
      userName: str(d, 'text1') ?? '',
      email: str(d, 'text2'),
      phoneNumber: str(d, 'text3') ? { countryCode: null, number: str(d, 'text3') } : null,
      status: str(d, 'text4'),
      tenantId: str(d, 'text5'),
      displayName: str(d, 'text6'),
      // Indexed as one pre-joined string; the Full Name column joins first+last, so carry it whole.
      fullName: str(d, 'text7') ? { firstName: str(d, 'text7'), middleName: null, lastName: null } : null,
      roles: csv(d, 'text8'),
      applications: csv(d, 'text9'),
    }) as unknown as UamUser,
  } as SearchModelBinding<UamUser>,

  // UamRole: text1=code text2=name text3=description text4=tenantId text5=active
  //          text6=assignmentType text7=permissions(csv) text8=systemRole
  roles: {
    model: 'ROLE',
    filters: {
      code: { field: 'text1', operator: 'LIKE' },
      name: { field: 'text2', operator: 'LIKE' },
    },
    toRow: (d) => ({
      id: d.modelKey,
      code: str(d, 'text1'),
      name: str(d, 'text2'),
      description: str(d, 'text3'),
      tenantId: str(d, 'text4'),
      active: bool(d, 'text5'),
      permissions: csv(d, 'text7'),
      systemRole: bool(d, 'text8'),
    }) as unknown as UamRole,
  } as SearchModelBinding<UamRole>,

  // Tenant: text1=code text2=name text3=legalName text4=status text5=type text6=emailDomain
  //         text7=country text8=supportEmail
  organizations: {
    model: 'TENANT',
    filters: {
      code: { field: 'text1', operator: 'LIKE' },
      name: { field: 'text2', operator: 'LIKE' },
      status: { field: 'text4', operator: 'EQ' },
      type: { field: 'text5', operator: 'EQ' },
    },
    toRow: (d) => ({
      id: d.modelKey,
      code: str(d, 'text1'),
      name: str(d, 'text2'),
      legalName: str(d, 'text3'),
      status: str(d, 'text4'),
      type: str(d, 'text5'),
      supportEmail: str(d, 'text8'),
    }) as unknown as Tenant,
  } as SearchModelBinding<Tenant>,

  // Application: text1=code text2=name text3=type text4=authenticationMode text5=baseUrl
  //              text6=description text7=active text8=ownerTeam
  applications: {
    model: 'APPLICATION',
    filters: {
      code: { field: 'text1', operator: 'LIKE' },
      name: { field: 'text2', operator: 'LIKE' },
      type: { field: 'text3', operator: 'EQ' },
    },
    toRow: (d) => ({
      id: d.modelKey,
      code: str(d, 'text1'),
      name: str(d, 'text2'),
      type: str(d, 'text3'),
      authenticationMode: str(d, 'text4'),
      baseUrl: str(d, 'text5'),
      description: str(d, 'text6'),
      active: bool(d, 'text7'),
      ownerTeam: str(d, 'text8'),
    }) as unknown as Application,
  } as SearchModelBinding<Application>,

  // ApiClient: text1=clientId text2=name text3=clientType text4=authenticationMethod text5=active
  //            text6=tenantId text7=applicationId text8=description
  clients: {
    model: 'API_CLIENT',
    filters: {
      clientId: { field: 'text1', operator: 'LIKE' },
      clientType: { field: 'text3', operator: 'EQ' },
      active: { field: 'text5', operator: 'EQ' },
    },
    toRow: (d) => ({
      id: d.modelKey,
      clientId: str(d, 'text1'),
      name: str(d, 'text2'),
      clientType: str(d, 'text3'),
      authenticationMethod: str(d, 'text4'),
      active: bool(d, 'text5'),
      tenantId: str(d, 'text6'),
      applicationId: str(d, 'text7'),
      description: str(d, 'text8'),
    }) as unknown as ApiClient,
  } as SearchModelBinding<ApiClient>,

  // SecurityPolicy: text1=name text2=tenantId text3=applicationId text4=active text5=description
  //                 text6=systemPolicy
  securityPolicies: {
    model: 'SECURITY_POLICY',
    filters: {
      name: { field: 'text1', operator: 'LIKE' },
      tenantId: { field: 'text2', operator: 'EQ' },
    },
    toRow: (d) => ({
      id: d.modelKey,
      name: str(d, 'text1'),
      tenantId: str(d, 'text2'),
      applicationId: str(d, 'text3'),
      active: bool(d, 'text4'),
      description: str(d, 'text5'),
      systemPolicy: bool(d, 'text6'),
    }) as unknown as SecurityPolicy,
  } as SearchModelBinding<SecurityPolicy>,

  // Rider_NMS NotificationTemplate (indexed by NMS, not UAM): text1=name text2=event text3=channel
  //                 text4=ACTIVE|INACTIVE text5=externalTemplateId; date1=createdAt date2=updatedAt
  nmsTemplates: {
    model: 'NOTIFICATION_TEMPLATE',
    filters: {
      name: { field: 'text1', operator: 'LIKE' },
      event: { field: 'text2', operator: 'LIKE' },
      channel: { field: 'text3', operator: 'EQ' },
      status: { field: 'text4', operator: 'EQ' },
    },
    toRow: (d) => ({
      id: d.modelKey,
      name: str(d, 'text1') ?? '',
      event: str(d, 'text2') ?? '',
      channel: str(d, 'text3'),
      active: str(d, 'text4') === 'ACTIVE',
      externalTemplateId: str(d, 'text5'),
      tenantId: d.ownerKey ?? null,
      createdAt: d['date1'] ? new Date(Number(d['date1'])).toISOString() : null,
      updatedAt: d['date2'] ? new Date(Number(d['date2'])).toISOString() : null,
    }) as unknown as NmsTemplate,
  } as SearchModelBinding<NmsTemplate>,
};

/** True when at least one filter value is non-blank -- list screens use this to decide between a
 *  plain paginated UAM list (no filters) and a SearchAPI query (filters applied). */
export function hasActiveFilters(filters: Record<string, unknown>): boolean {
  return Object.values(filters).some((v) => v !== undefined && v !== null && String(v).trim() !== '');
}

@Injectable({ providedIn: 'root' })
export class SearchApiService {
  /** gateway search-api route: /api/v1/search/** -> Rider_SearchAPI; the index's getall API lives under /elastic. */
  private elasticBase = `${environment.gatewayUrl}/v1/search/elastic`;

  constructor(private http: HttpClient, private translate: TranslateService) {}

  /** One model, a flat AND/OR group of conditions (omitted when empty), one page -- served by the index's getall API. */
  search(model: SearchModel, conditions: SearchCondition[], page: number, size: number,
         logic: 'AND' | 'OR' = 'AND'): Observable<SpringPage<SearchDocument>> {
    return logic === 'OR'
      ? this.getAll(model, [], conditions, page, size)
      : this.getAll(model, conditions, [], page, size);
  }

  /**
   * The index's "getall" API (POST /search/elastic/{model}/getall): lists or searches one model's records, one page at
   * a time. `all` must hold (AND); when `any` has entries at least one of them must hold too (OR). Sorted by `sortField`
   * (default: newest first). Use this for pickers over big sources -- never list a whole table.
   */
  getAll(model: string, all: SearchCondition[], any: SearchCondition[], page: number, size: number,
         sortField?: string): Observable<SpringPage<SearchDocument>> {
    const node = (c: SearchCondition) => ({ field: c.field, operator: c.operator, value: c.value });
    const group: unknown[] = all.map(node);
    if (any.length) group.push({ criteria1: 1, group: any.map(node) });
    const body = {
      requestType: 'LIST',
      searchQuery: group.length ? { group: { criteria1: 0, group } } : undefined,
      pageNo: page,
      pageSize: size,
      sort: sortField ? { field1: sortField, directionField1: 'ASC' } : undefined,
    };
    return this.http.post<{ success: boolean; errors?: string[]; object?: unknown }>(
      `${this.elasticBase}/${encodeURIComponent(model)}/getall`, body,
    ).pipe(map((res) => {
      if (!res.success) {
        const message = res.errors?.[0] || this.translate.instant('shared.errors.requestFailed');
        throw { error: { detail: message, message } };
      }
      return this.toPage(res.object);
    }));
  }

  /** Translates a list screen's `filters` object through a model binding (blank values skipped,
   *  all conditions ANDed) and maps each hit back into a table row. */
  searchRows<T>(binding: SearchModelBinding<T>, filters: Record<string, unknown>,
                page: number, size: number): Observable<SpringPage<T>> {
    const conditions: SearchCondition[] = [];
    Object.entries(filters).forEach(([key, raw]) => {
      const value = raw === undefined || raw === null ? '' : String(raw).trim();
      const slot = binding.filters[key];
      if (value && slot) conditions.push({ field: slot.field, operator: slot.operator, value });
    });
    return this.search(binding.model, conditions, page, size).pipe(
      map((res) => ({ ...res, content: res.content.map(binding.toRow) } as SpringPage<T>)));
  }

  /** SearchController returns a raw Spring `Page`; depending on Spring Data's page serialization
   *  mode it arrives either flat (totalElements/totalPages/number/size) or as a PagedModel with
   *  those nested under `page`. Normalise to SpringPage either way. */
  private toPage(raw: any): SpringPage<SearchDocument> {
    const content: SearchDocument[] = raw?.content ?? [];
    const meta = raw?.page ?? raw ?? {};
    const size = meta.size ?? content.length;
    const number = meta.number ?? 0;
    const totalElements = meta.totalElements ?? content.length;
    const totalPages = meta.totalPages ?? (size ? Math.ceil(totalElements / size) : 1);
    return {
      content,
      totalElements,
      totalPages,
      size,
      number,
      numberOfElements: content.length,
      first: number === 0,
      last: number + 1 >= totalPages,
      empty: content.length === 0,
    };
  }
}
