import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { UamCommonRequest, UamCommonResponse } from '../../shared/models/uam.models';
import { AgentKyc } from './agent-kyc.models';
import { KycDocumentRequest, ReviewRequest } from './franchise-onboarding.models';
import { OnboardingSubject } from './franchise-onboarding.constants';

/** The calls behind an agent's KYC page. The owner's calls live under `/mine`; the organization and caller come from the token. */
@Injectable({ providedIn: 'root' })
export class AgentKycService {
  constructor(private http: HttpClient, private translate: TranslateService) {}

  get(subject: OnboardingSubject, franchiseId: string, memberId: string): Observable<AgentKyc> {
    return this.http.get<UamCommonResponse<AgentKyc>>(this.url(subject, franchiseId, memberId, 'kyc')).pipe(map((res) => this.unwrap(res)));
  }

  add(subject: OnboardingSubject, franchiseId: string, memberId: string, request: KycDocumentRequest): Observable<AgentKyc> {
    return this.http.post<UamCommonResponse<AgentKyc>>(this.url(subject, franchiseId, memberId, 'kyc-documents'), this.wrap(request))
      .pipe(map((res) => this.unwrap(res)));
  }

  review(franchiseId: string, memberId: string, documentId: string, request: ReviewRequest): Observable<AgentKyc> {
    return this.http.put<UamCommonResponse<AgentKyc>>(
      this.url('franchise', franchiseId, memberId, `kyc-documents/${encodeURIComponent(documentId)}/review`), this.wrap(request))
      .pipe(map((res) => this.unwrap(res)));
  }

  private url(subject: OnboardingSubject, franchiseId: string, memberId: string, path: string): string {
    const base = subject === 'myFranchise' ? `${environment.franchiseApiUrl}/mine` : environment.franchiseApiUrl;
    return `${base}/${encodeURIComponent(franchiseId)}/agents/${encodeURIComponent(memberId)}/${path}`;
  }

  private wrap<T>(payload: T): UamCommonRequest {
    return { requestObject: payload, doesInputHaveEncryptedData: false, success: false, pageNo: 0, pageSize: 0, useOrOperator: false };
  }

  private unwrap<T>(res: UamCommonResponse<T>): T {
    if (!res.success) {
      const fallback = this.translate.instant('shared.errors.requestFailed');
      throw { error: { detail: res.message || fallback, message: res.message || fallback } };
    }
    return res.responseObject as T;
  }
}
