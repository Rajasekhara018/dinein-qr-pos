import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, from, of } from 'rxjs';
import { catchError, concatMap, map, toArray } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { UamCommonResponse } from '../shared/models/uam.models';
import { APPLICATION_ACCESS_PATH, applicationAccessRevokePath } from './application-access.constants';
import { AccessListResult, AccessSubjectType, ApplicationAccessGrant, GrantOutcome } from './application-access.models';
import { httpStatusOf } from './application-access.helpers';

/**
 * The applications a user has been given directly (Rider_UAM's application-access API: list, grant, revoke). Reads never throw into a view
 * (a result state instead); writes fail as errors the caller maps with `accessFailureKey`. The organization is decided by
 * Rider_UAM from the session token.
 */
@Injectable({ providedIn: 'root' })
export class ApplicationAccessService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.uamApiUrl;

  /** Every application the user has been given directly. */
  listForUser(userId: string): Observable<AccessListResult> {
    const params = new HttpParams().set('subjectType', 'USER').set('subjectId', userId);
    return this.http.get<UamCommonResponse<ApplicationAccessGrant[]>>(`${this.base}${APPLICATION_ACCESS_PATH}`, { params }).pipe(
      map((res): AccessListResult => (res?.success ? { state: 'ready', grants: Array.isArray(res.responseObject) ? res.responseObject : [] } : { state: 'unavailable', grants: [] })),
      catchError((err: unknown) => of<AccessListResult>({ state: httpStatusOf(err) === 403 ? 'forbidden' : 'unavailable', grants: [] })),
    );
  }

  /**
   * Gives each subject access, one request after the other. A failure of one subject does not stop the rest: every
   * outcome is reported so the caller can say what was done and keep the failed ones selected.
   */
  grant(code: string, subjectType: AccessSubjectType, subjectIds: readonly string[], note: string): Observable<GrantOutcome[]> {
    const trimmed = note.trim();
    return from(subjectIds).pipe(
      concatMap((subjectId) =>
        this.http
          .post<UamCommonResponse<ApplicationAccessGrant>>(`${this.base}${APPLICATION_ACCESS_PATH}`, {
            requestObject: { applicationCode: code, subjectType, subjectId, note: trimmed ? trimmed : null },
            doesInputHaveEncryptedData: false,
          })
          .pipe(
            map((res): GrantOutcome => ({ subjectId, ok: !!res?.success, status: res?.success ? 201 : 0 })),
            catchError((err: unknown) => of<GrantOutcome>({ subjectId, ok: false, status: httpStatusOf(err) })),
          ),
      ),
      toArray(),
    );
  }

  /** Withdraws one grant (errors as HTTP errors). */
  revoke(id: string): Observable<void> {
    return this.http.delete<UamCommonResponse<unknown>>(`${this.base}${applicationAccessRevokePath(id)}`).pipe(
      map((res) => {
        if (!res?.success) throw { status: 0, error: { message: res?.message ?? '' } };
      }),
    );
  }
}
