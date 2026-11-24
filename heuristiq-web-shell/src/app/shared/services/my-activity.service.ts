import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { MY_ACTIVITY_PATH } from './my-activity.constants';
import { MyActivityEnvelope, MyActivityEvent, MyActivityPage } from '../models/my-activity.models';

/**
 * The signed-in user's own trail from Rider_AuditService (`GET /api/v1/audit/me/events`): screens opened, requests
 * made, notifications and payments. The service takes the user from the token, so there is no user id to send.
 */
@Injectable({ providedIn: 'root' })
export class MyActivityService {
  private readonly http = inject(HttpClient);

  /** One page of the caller's events, newest first. */
  list(page: number, size: number): Observable<MyActivityPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<MyActivityEnvelope<MyActivityPage>>(`${environment.gatewayUrl}${MY_ACTIVITY_PATH}`, { params })
      .pipe(map((res) => res.responseObject ?? { content: [] as MyActivityEvent[], totalElements: 0 }));
  }
}
