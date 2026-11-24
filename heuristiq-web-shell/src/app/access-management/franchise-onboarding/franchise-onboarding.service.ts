import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UamCommonRequest, UamCommonResponse } from '../../shared/models/uam.models';
import { OnboardingSubject } from './franchise-onboarding.constants';
import {
  AgreementRequest, BankAccountRequest, FranchiseOnboarding, KycDocumentRequest, ReviewRequest,
} from './franchise-onboarding.models';

/**
 * The franchise service's onboarding endpoints: the checklist, KYC documents, payout bank account, agreement and activation.
 * Administrators use `/api/v1/franchise/{id}/...`, the owner `/api/v1/franchise/mine/{id}/...`; every call answers with the
 * whole onboarding read model, so the page simply shows what comes back.
 */
@Injectable({ providedIn: 'root' })
export class FranchiseOnboardingService {
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);

  get(subject: OnboardingSubject, id: string): Observable<FranchiseOnboarding> {
    return this.http.get<UamCommonResponse<FranchiseOnboarding>>(this.url(subject, id, 'onboarding')).pipe(map((res) => this.unwrap(res)));
  }

  addDocument(subject: OnboardingSubject, id: string, request: KycDocumentRequest): Observable<FranchiseOnboarding> {
    return this.send('post', this.url(subject, id, 'kyc-documents'), request);
  }

  /** The owner withdraws a document that is not verified yet. */
  withdrawDocument(id: string, documentId: string): Observable<FranchiseOnboarding> {
    return this.http.delete<UamCommonResponse<FranchiseOnboarding>>(this.url('myFranchise', id, `kyc-documents/${encodeURIComponent(documentId)}`))
      .pipe(map((res) => this.unwrap(res)));
  }

  reviewDocument(id: string, documentId: string, request: ReviewRequest): Observable<FranchiseOnboarding> {
    return this.send('put', this.url('franchise', id, `kyc-documents/${encodeURIComponent(documentId)}/review`), request);
  }

  saveBankAccount(subject: OnboardingSubject, id: string, request: BankAccountRequest): Observable<FranchiseOnboarding> {
    return this.send('put', this.url(subject, id, 'bank-account'), request);
  }

  reviewBankAccount(id: string, request: ReviewRequest): Observable<FranchiseOnboarding> {
    return this.send('put', this.url('franchise', id, 'bank-account/review'), request);
  }

  saveAgreement(id: string, request: AgreementRequest): Observable<FranchiseOnboarding> {
    return this.send('put', this.url('franchise', id, 'agreement'), request);
  }

  activate(id: string): Observable<FranchiseOnboarding> {
    return this.send('put', this.url('franchise', id, 'activation'), {});
  }

  private url(subject: OnboardingSubject, id: string, path: string): string {
    const owner = encodeURIComponent(id);
    return subject === 'myFranchise'
      ? `${environment.franchiseApiUrl}/mine/${owner}/${path}`
      : `${environment.franchiseApiUrl}/${owner}/${path}`;
  }

  private send(method: 'post' | 'put', url: string, payload: unknown): Observable<FranchiseOnboarding> {
    const body: UamCommonRequest = { requestObject: payload, doesInputHaveEncryptedData: false, success: false, pageNo: 0, pageSize: 0, useOrOperator: false };
    return this.http.request<UamCommonResponse<FranchiseOnboarding>>(method.toUpperCase(), url, { body }).pipe(map((res) => this.unwrap(res)));
  }

  private unwrap<T>(res: UamCommonResponse<T>): T {
    if (!res.success) {
      const fallback = this.translate.instant('shared.errors.requestFailed');
      throw { error: { detail: res.message || fallback, message: res.message || fallback } };
    }
    return res.responseObject as T;
  }
}
