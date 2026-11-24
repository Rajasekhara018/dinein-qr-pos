import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import {
  CreateOnboardingTypeRequest,
  OnboardingEnvelope,
  OnboardingSchemaDefinition,
  OnboardingSchemaResponse,
  OnboardingType,
} from '../../onboarding-forms/onboarding-forms.models';

/**
 * Admin schema-builder access to Rider_OnboardingService (AdminOnboardingTypeController /
 * AdminOnboardingSchemaController) via the gateway's rider-onboarding route. Responses use the
 * service's own {status, data, message} envelope -- not UAM's {success, responseObject}.
 */
@Injectable({ providedIn: 'root' })
export class OnboardingSchemaService {
  private readonly base = `${environment.riderOnboardingApiUrl}/admin/onboarding-types`;

  constructor(private http: HttpClient, private translate: TranslateService) {}

  listTypes(): Observable<OnboardingType[]> {
    return this.http.get<OnboardingEnvelope<OnboardingType[]>>(this.base).pipe(map((res) => this.unwrap(res)));
  }

  createType(body: CreateOnboardingTypeRequest): Observable<OnboardingType> {
    return this.http.post<OnboardingEnvelope<OnboardingType>>(this.base, body).pipe(map((res) => this.unwrap(res)));
  }

  setRequiresReview(typeCode: string, value: boolean): Observable<OnboardingType> {
    const params = new HttpParams().set('value', value);
    return this.http
      .patch<OnboardingEnvelope<OnboardingType>>(`${this.typeUrl(typeCode)}/requires-review`, {}, { params })
      .pipe(map((res) => this.unwrap(res)));
  }

  listSchemas(typeCode: string): Observable<OnboardingSchemaResponse[]> {
    return this.http
      .get<OnboardingEnvelope<OnboardingSchemaResponse[]>>(`${this.typeUrl(typeCode)}/schemas`)
      .pipe(map((res) => this.unwrap(res)));
  }

  /** Newest version first. */
  listSchemasNewestFirst(typeCode: string): Observable<OnboardingSchemaResponse[]> {
    return this.listSchemas(typeCode).pipe(
      map((versions) => (versions ?? []).slice().sort((a, b) => b.version - a.version)),
    );
  }

  defaultSchema(typeCode: string): Observable<OnboardingSchemaDefinition> {
    return this.http
      .get<OnboardingEnvelope<OnboardingSchemaDefinition>>(`${this.typeUrl(typeCode)}/schemas/defaults`)
      .pipe(map((res) => this.unwrap(res)));
  }

  createDraft(typeCode: string, definition: OnboardingSchemaDefinition): Observable<OnboardingSchemaResponse> {
    return this.http
      .post<OnboardingEnvelope<OnboardingSchemaResponse>>(`${this.typeUrl(typeCode)}/schemas`, definition)
      .pipe(map((res) => this.unwrap(res)));
  }

  updateDraft(typeCode: string, version: number, definition: OnboardingSchemaDefinition): Observable<OnboardingSchemaResponse> {
    return this.http
      .put<OnboardingEnvelope<OnboardingSchemaResponse>>(`${this.typeUrl(typeCode)}/schemas/${version}`, definition)
      .pipe(map((res) => this.unwrap(res)));
  }

  publish(typeCode: string, version: number): Observable<OnboardingSchemaResponse> {
    return this.http
      .post<OnboardingEnvelope<OnboardingSchemaResponse>>(`${this.typeUrl(typeCode)}/schemas/${version}/publish`, {})
      .pipe(map((res) => this.unwrap(res)));
  }

  republish(typeCode: string, version: number): Observable<OnboardingSchemaResponse> {
    return this.http
      .post<OnboardingEnvelope<OnboardingSchemaResponse>>(`${this.typeUrl(typeCode)}/schemas/${version}/republish`, {})
      .pipe(map((res) => this.unwrap(res)));
  }

  private typeUrl(typeCode: string): string {
    return `${this.base}/${encodeURIComponent(typeCode)}`;
  }

  private unwrap<T>(res: OnboardingEnvelope<T>): T {
    if (!res.status) {
      const message = res.message || this.translate.instant('shared.errors.requestFailed');
      throw { error: { detail: message, message } };
    }
    return res.data as T;
  }
}
