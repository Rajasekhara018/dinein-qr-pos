import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';
import { HqLegalProfile, hqNormalizeLegalProfile } from 'hq-shared-lib';
import { environment } from '../../../environments/environment';
import { UamCommonRequest, UamCommonResponse } from '../../shared/models/uam.models';
import { LEGAL_PROFILE_ROUTE, LegalProfileSubject } from './legal-profile.constants';

/**
 * Reads and saves the legal profile (entity, tax ids, addresses) of an organization (UAM) or a franchise (franchise service):
 * `/api/v1/uam/organizations/{id}/legal-profile`, `/api/v1/franchise/{id}/legal-profile` for administrators and
 * `/api/v1/franchise/mine/{id}/legal-profile` for the franchise's owner. Same envelope as the other UAM-style APIs.
 */
@Injectable({ providedIn: 'root' })
export class LegalProfileService {
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);

  get(subject: LegalProfileSubject, id: string): Observable<HqLegalProfile> {
    return this.http.get<UamCommonResponse<HqLegalProfile>>(this.url(subject, id)).pipe(map((res) => this.unwrap(res)));
  }

  /** Saves the whole profile, normalised; the services answer 409 when it changed since `profile.version` was read. */
  save(subject: LegalProfileSubject, id: string, profile: HqLegalProfile): Observable<HqLegalProfile> {
    const body: UamCommonRequest = {
      requestObject: hqNormalizeLegalProfile(profile), doesInputHaveEncryptedData: false, success: false, pageNo: 0, pageSize: 0, useOrOperator: false,
    };
    return this.http.put<UamCommonResponse<HqLegalProfile>>(this.url(subject, id), body).pipe(map((res) => this.unwrap(res)));
  }

  private url(subject: LegalProfileSubject, id: string): string {
    const owner = encodeURIComponent(id);
    switch (subject) {
      case 'organization':
        return `${environment.uamApiUrl}/organizations/${owner}/${LEGAL_PROFILE_ROUTE}`;
      case 'franchise':
        return `${environment.franchiseApiUrl}/${owner}/${LEGAL_PROFILE_ROUTE}`;
      case 'myFranchise':
        return `${environment.franchiseApiUrl}/mine/${owner}/${LEGAL_PROFILE_ROUTE}`;
    }
  }

  private unwrap<T>(res: UamCommonResponse<T>): T {
    if (!res.success) {
      const fallback = this.translate.instant('shared.errors.requestFailed');
      throw { error: { detail: res.message || fallback, message: res.message || fallback } };
    }
    return res.responseObject as T;
  }
}
