import { Injectable, Provider, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, catchError, throwError } from 'rxjs';
import { HqDocument, HqDocumentService } from 'hq-shared-lib';
import { ONBOARDING_UPLOAD_KEYS, ONBOARDING_UPLOAD_MAX_SIZE_MB } from './franchise-onboarding-upload.constants';
import { isDmsFileRuleRejection, normalizedOnboardingFile, onboardingUploadProblem } from './franchise-onboarding-upload.helpers';

/** The error shape hq-doc-upload shows (`err.error.message`). */
interface UploadError {
  error: { message: string };
}

/**
 * Uploads of the onboarding pages: checks the file against the shared onboarding rules, sends it with the content type of
 * its extension, and turns a DMS type/size refusal into a translated message naming the allowed types and size.
 * Provided on the page (ONBOARDING_DOCUMENT_PROVIDERS) so every hq-doc-upload of the page uploads through it.
 */
@Injectable()
export class OnboardingUploadService {
  private readonly documents = inject(HqDocumentService, { skipSelf: true });
  private readonly translate = inject(TranslateService);

  upload(file: File, category: string, ownerId: string, checksum?: string, documentType?: string | null): Observable<HqDocument> {
    const problem = onboardingUploadProblem(file);
    if (problem) return throwError(() => this.failure(ONBOARDING_UPLOAD_KEYS[problem], file.name));
    return this.documents.upload(normalizedOnboardingFile(file), category, ownerId, checksum, documentType).pipe(
      catchError((err: unknown) => throwError(() => (isDmsFileRuleRejection(err) ? this.failure(ONBOARDING_UPLOAD_KEYS.rejected, file.name) : err))),
    );
  }

  /** The DMS client for the page: everything as usual, except uploads go through {@link upload}. */
  asDocumentService(): HqDocumentService {
    const scoped = Object.create(this.documents) as HqDocumentService;
    scoped.upload = (file, category, ownerId, checksum, documentType) => this.upload(file, category, ownerId, checksum, documentType);
    return scoped;
  }

  private failure(key: string, name: string): UploadError {
    const types = this.translate.instant(ONBOARDING_UPLOAD_KEYS.hints);
    return { error: { message: this.translate.instant(key, { name, types, max: ONBOARDING_UPLOAD_MAX_SIZE_MB }) } };
  }
}

/** Page providers: hq-doc-upload on the page injects HqDocumentService and gets the onboarding upload rules. */
export const ONBOARDING_DOCUMENT_PROVIDERS: Provider[] = [
  OnboardingUploadService,
  { provide: HqDocumentService, useFactory: (uploads: OnboardingUploadService) => uploads.asDocumentService(), deps: [OnboardingUploadService] },
];
