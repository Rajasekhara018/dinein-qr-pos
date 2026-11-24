import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { AUDIT_EVENTS_PATH } from './lifecycle.constants';
import { auditPageToTimeline, timelineErrorFor } from './lifecycle.helpers';
import { AuditEnvelope, AuditEventRaw, AuditPageRaw, LifecycleEvent, LifecyclePage, TimelineError } from './lifecycle.models';

/**
 * A document's history, read from the central audit trail (Rider_AuditService, `GET {gateway}/v1/audit/events`,
 * same contract the Audit logs screen uses: CommonResponseObject envelope, Spring page, newest first).
 * Needs the AUDIT_EVENT_LIST privilege; the gateway answers 403 without it.
 */
@Injectable({ providedIn: 'root' })
export class LifecycleAuditService {
  private readonly http = inject(HttpClient);

  /** One page of the events whose subject is this document (subjectType=DOCUMENT&subjectId=...). */
  documentEvents(documentId: string, page: number, size: number): Observable<LifecyclePage<LifecycleEvent>> {
    const params = new HttpParams()
      .set('subjectType', 'DOCUMENT')
      .set('subjectId', documentId)
      .set('page', String(Math.max(page, 0)))
      .set('size', String(size));
    return this.http
      .get<AuditEnvelope<AuditPageRaw<AuditEventRaw>>>(`${environment.gatewayUrl}${AUDIT_EVENTS_PATH}`, { params })
      .pipe(map((res) => auditPageToTimeline(res?.responseObject, page, size)));
  }

  /** 403 / 401 mean "no audit privilege" (shown as a note); anything else is a failure worth a retry. */
  classifyError(err: unknown): TimelineError {
    return timelineErrorFor(err instanceof HttpErrorResponse ? err.status : 0);
  }
}
