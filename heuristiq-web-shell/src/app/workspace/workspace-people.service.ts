import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqLookupService, HqPickerPage } from 'hq-shared-lib';
import { Observable, map, of } from 'rxjs';
import { UamUser } from '../shared/models/uam.models';
import { OrganizationContextService } from '../shared/services/organization-context-service';
import { SearchApiService, SearchCondition, UAM_SEARCH_MODELS } from '../shared/services/search-api-service';
import { PEOPLE_SEARCH_FIELDS, PEOPLE_SORT_FIELD, PEOPLE_TENANT_FIELD } from './workspace.constants';
import { personName } from './workspace.helpers';
import { WorkspacePerson } from './workspace.models';

/**
 * Chooses a colleague of the session's organization to assign a task or ticket to: the paged people dialog over the search
 * index (never the whole user table), narrowed by the typed text on user name, email or full name.
 */
@Injectable({ providedIn: 'root' })
export class WorkspacePeopleService {
  private readonly lookup = inject(HqLookupService);
  private readonly searchApi = inject(SearchApiService);
  private readonly organizations = inject(OrganizationContextService);
  private readonly translate = inject(TranslateService);

  /** Opens the dialog; the person chosen, or null when it was closed without a choice. */
  pick(): Observable<WorkspacePerson | null> {
    return this.lookup.open<UamUser>({
      title: this.translate.instant('workspace.people.title'),
      subtitle: this.translate.instant('workspace.people.subtitle'),
      multiple: false,
      idOf: (user) => user.id,
      columns: [
        { key: 'displayName', label: this.translate.instant('workspace.people.name'), render: (user) => personName(user) },
        { key: 'userName', label: this.translate.instant('workspace.people.userName') },
        { key: 'email', label: this.translate.instant('workspace.people.email') },
      ],
      chipLabel: (user) => personName(user),
      fetch: (query, page, size) => this.search(query, page, size),
    }).pipe(map((chosen) => {
      const user = chosen?.[0];
      return user ? { id: user.id, name: personName(user) } : null;
    }));
  }

  private search(query: string, page: number, size: number): Observable<HqPickerPage<UamUser>> {
    const tenantId = this.organizations.currentTenantId();
    if (!tenantId) return of({ items: [], total: 0 });
    const text = query.trim();
    const any: SearchCondition[] = text ? PEOPLE_SEARCH_FIELDS.map((field) => ({ field, operator: 'LIKE' as const, value: text })) : [];
    return this.searchApi.getAll('user', [{ field: PEOPLE_TENANT_FIELD, operator: 'EQ', value: tenantId }], any, page, size, PEOPLE_SORT_FIELD)
      .pipe(map((result) => ({ items: result.content.map(UAM_SEARCH_MODELS.users.toRow), total: result.totalElements })));
  }
}

