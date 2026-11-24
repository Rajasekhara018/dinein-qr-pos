import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, from, map, switchMap, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import {
  FranchiseRelinkResult,
  OnboardingPage,
  OnboardingResponse,
  OnboardingSubmission,
  OnboardingSubmissionFilters,
  SubmissionProgress,
} from '../../onboarded-users/onboarded-users.models';
import { OnboardingReviewItem, RejectReviewRequest } from '../../onboarding-reviews/onboarding-reviews.models';

/**
 * Admin read access to Rider_OnboardingService (AdminOnboardingSubmissionController) via the
 * gateway's rider-onboarding route. Responses use the service's own {status, data, message}
 * envelope -- not UAM's {success, responseObject}.
 */
@Injectable({ providedIn: 'root' })
export class OnboardingAdminService {
  private readonly base = `${environment.riderOnboardingApiUrl}/admin/onboarding-submissions`;
  private readonly reviewsBase = `${environment.riderOnboardingApiUrl}/admin/onboarding-reviews`;

  constructor(private http: HttpClient, private translate: TranslateService) {}

  list(filters: OnboardingSubmissionFilters, page: number, size: number): Observable<OnboardingPage<OnboardingSubmission>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.query.trim()) params = params.set('q', filters.query.trim());
    return this.http
      .get<OnboardingResponse<OnboardingPage<OnboardingSubmission>>>(this.base, { params })
      .pipe(map((res) => this.unwrap(res)));
  }

  /** Submissions waiting on a human decision (open Rider_WorkflowService review tasks). */
  listOpenReviews(): Observable<OnboardingReviewItem[]> {
    return this.http
      .get<OnboardingResponse<OnboardingReviewItem[]>>(this.reviewsBase)
      .pipe(map((res) => this.unwrap(res)));
  }

  approveReview(submissionId: string): Observable<void> {
    return this.http
      .post<OnboardingResponse<void>>(`${this.reviewsBase}/${encodeURIComponent(submissionId)}/approve`, {})
      .pipe(map((res) => this.unwrap(res)));
  }

  /** Sends a submission under review back to the applicant, with what they must fix. */
  returnReview(submissionId: string, reason: string): Observable<void> {
    const body: RejectReviewRequest = { reason };
    return this.http
      .post<OnboardingResponse<void>>(`${this.reviewsBase}/${encodeURIComponent(submissionId)}/return`, body)
      .pipe(map((res) => this.unwrap(res)));
  }

  rejectReview(submissionId: string, reason: string): Observable<void> {
    const body: RejectReviewRequest = { reason };
    return this.http
      .post<OnboardingResponse<void>>(`${this.reviewsBase}/${encodeURIComponent(submissionId)}/reject`, body)
      .pipe(map((res) => this.unwrap(res)));
  }

  get(id: string): Observable<OnboardingSubmission> {
    return this.http
      .get<OnboardingResponse<OnboardingSubmission>>(`${this.base}/${encodeURIComponent(id)}`)
      .pipe(map((res) => this.unwrap(res)));
  }

  progress(id: string): Observable<SubmissionProgress> {
    return this.http.get<OnboardingResponse<SubmissionProgress>>(`${this.base}/${encodeURIComponent(id)}/progress`)
      .pipe(map((res) => this.unwrap(res)));
  }

  /** Re-runs account/profile setup for an APPROVED or ACCOUNT_CREATION_FAILED submission. */
  retryProvisioning(id: string): Observable<OnboardingSubmission> {
    return this.http
      .post<OnboardingResponse<OnboardingSubmission>>(`${this.base}/${encodeURIComponent(id)}/retry-provisioning`, {})
      .pipe(map((res) => this.unwrap(res)));
  }

  /** Links the driver of an APPROVED driver submission to its franchise again (idempotent). */
  relinkFranchise(id: string): Observable<FranchiseRelinkResult> {
    return this.http
      .post<OnboardingResponse<FranchiseRelinkResult>>(`${this.base}/${encodeURIComponent(id)}/relink-franchise`, {})
      .pipe(map((res) => this.unwrap(res)));
  }

  /** The uploaded file for one DOCUMENT field, streamed from Rider_DMS via the onboarding service. */
  downloadDocument(id: string, fieldKey: string): Observable<Blob> {
    return this.http
      .get(`${this.base}/${encodeURIComponent(id)}/documents/${encodeURIComponent(fieldKey)}`, { responseType: 'blob' })
      .pipe(
        // With responseType 'blob' an error body arrives as a Blob too -- read it so the caller can
        // show the server's own reason ("Document download was rejected: …") instead of a bare 502.
        catchError((err: HttpErrorResponse) =>
          from(this.errorMessageOf(err)).pipe(
            switchMap((message) => throwError(() => ({ error: { detail: message, message } }))),
          ),
        ),
      );
  }

  private async errorMessageOf(err: HttpErrorResponse): Promise<string> {
    const fallback = this.translate.instant('shared.errors.requestFailed');
    try {
      const body = err.error instanceof Blob ? await err.error.text() : '';
      const parsed = body ? (JSON.parse(body) as { message?: string }) : null;
      return parsed?.message || body || `${fallback} (HTTP ${err.status})`;
    } catch {
      return `${fallback} (HTTP ${err.status})`;
    }
  }

  private unwrap<T>(res: OnboardingResponse<T>): T {
    if (!res.status) {
      const message = res.message || this.translate.instant('shared.errors.requestFailed');
      throw { error: { detail: message, message } };
    }
    return res.data as T;
  }
}
