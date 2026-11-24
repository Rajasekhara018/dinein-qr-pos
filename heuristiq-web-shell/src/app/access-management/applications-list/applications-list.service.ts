import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Application, SpringPage, UamCommonResponse } from '../../shared/models/uam.models';
import { APPLICATIONS_LIST_PATH, ApplicationsListFilters } from './applications-list.constants';
import { emptyPage } from './applications-list.helpers';

/**
 * The Applications list's data: UAM's GET /applications, which lists only the applications linked to the session
 * organization (from the token), one page at a time with the code (exact), name (contains) and type filters. It replaces the
 * search index, which is not scoped by organization and could show another organization's applications.
 */
@Injectable({ providedIn: 'root' })
export class ApplicationsListService {
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);
  private readonly base = environment.uamApiUrl;

  page(filters: ApplicationsListFilters, page: number, size: number): Observable<SpringPage<Application>> {
    let params = new HttpParams().set('page', page).set('size', size);
    (Object.keys(filters) as (keyof ApplicationsListFilters)[]).forEach((key) => {
      const value = filters[key]?.trim();
      if (value) params = params.set(key, value);
    });
    return this.http.get<UamCommonResponse<SpringPage<Application>>>(`${this.base}${APPLICATIONS_LIST_PATH}`, { params }).pipe(
      map((res) => {
        if (!res?.success) {
          const message = res?.message || this.translate.instant('shared.errors.requestFailed');
          throw { error: { detail: message, message } };
        }
        return res.responseObject ?? emptyPage<Application>(page, size);
      }),
    );
  }
}
