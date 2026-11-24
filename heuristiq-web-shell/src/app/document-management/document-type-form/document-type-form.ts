import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subscription } from 'rxjs';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import {
  buildDocumentTypeSummary,
  emptyDocumentTypeForm,
  exceedsPlatformLimit,
  formFromResponse,
  formatFileSizeMb,
  hasErrors,
  saveErrorKey,
  toRequest,
  validateDocumentTypeForm,
} from '../document-type.helpers';
import {
  DocumentFormatKey,
  DocumentTypeFormErrors,
  DocumentTypeFormModel,
  FileCheckResult,
} from '../document-type.models';
import { DocumentTypesService } from '../document-types.service';
import {
  CHANGE_REQUESTS_ROUTE,
  DOCUMENT_FORMATS,
  DOCUMENT_TYPES_ROUTE,
  DOCUMENT_TYPE_LANGUAGES,
  DOCUMENT_TYPE_LANGUAGE_NAMES,
  DOCUMENT_TYPE_LIMITS,
  NO_VERIFICATION,
  VERIFICATION_METHODS,
  VERIFICATION_METHOD_KEY_PREFIX,
  VERIFICATION_PROVIDERS,
} from '../document-types.constants';

@Component({
  selector: 'app-document-type-form',
  templateUrl: './document-type-form.html',
  styleUrl: './document-type-form.scss',
  standalone: false,
})
export class DocumentTypeFormComponent implements OnInit, OnDestroy, PendingChangesAware {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly svc = inject(DocumentTypesService);
  private readonly translate = inject(TranslateService);
  private readonly notify = inject(NotificationService);
  private readonly appDialog = inject(AppDialogService);
  readonly organizationName$ = inject(OrganizationContextService).currentName$;

  readonly formats = DOCUMENT_FORMATS;
  readonly languages = DOCUMENT_TYPE_LANGUAGES;
  readonly languageNames = DOCUMENT_TYPE_LANGUAGE_NAMES;
  readonly limits = DOCUMENT_TYPE_LIMITS;
  readonly changeRequestsRoute = CHANGE_REQUESTS_ROUTE;
  readonly listRoute = DOCUMENT_TYPES_ROUTE;

  isEdit = false;
  editId: string | null = null;
  loading = false;
  loadFailed = false;
  saving = false;
  submitted = false;
  /** Set after a save was accepted: the change now waits for approval. */
  sentForApproval = false;
  saveErrorMessage = '';

  form: DocumentTypeFormModel = emptyDocumentTypeForm();
  readonly verificationMethods = VERIFICATION_METHODS;
  readonly verificationKeyPrefix = VERIFICATION_METHOD_KEY_PREFIX;
  errors: DocumentTypeFormErrors = {};
  private savedSnapshot = '';
  private subs = new Subscription();

  // "Test a file" panel
  testFile: File | null = null;
  testing = false;
  testResult: FileCheckResult | null = null;
  testError = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.editId = id;
      this.load(id);
    } else {
      this.savedSnapshot = this.snapshot();
    }
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  private load(id: string): void {
    this.loading = true;
    this.loadFailed = false;
    this.subs.add(
      this.svc.getById(id).subscribe({
        next: (res) => {
          this.form = formFromResponse(res);
          this.savedSnapshot = this.snapshot();
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.loadFailed = true;
          this.notify.error(this.translate.instant('documentManagement.editor.messages.loadFailed'));
        },
      }),
    );
  }

  /** The plain-language sentence, rebuilt as the form changes. */
  get summary(): string {
    return buildDocumentTypeSummary(
      {
        allowedFormats: this.form.allowedFormats,
        maxFileSizeMb: this.form.maxFileSizeMb,
        required: this.form.required,
        expiryYears: this.form.expiryYears,
        retentionDays: this.form.retentionDays,
      },
      (key, params) => this.translate.instant(key, params),
    );
  }

  isFormatSelected(key: DocumentFormatKey): boolean {
    return this.form.allowedFormats.includes(key);
  }

  toggleFormat(key: DocumentFormatKey, checked: boolean): void {
    const without = this.form.allowedFormats.filter((k) => k !== key);
    this.form.allowedFormats = checked ? [...without, key] : without;
    if (this.submitted) this.revalidate();
  }

  /** Turning verification on asks for a method; turning it off clears it. */
  onVerificationToggle(on: boolean): void {
    this.form.verificationMethod = on ? (this.form.verificationMethod === NO_VERIFICATION ? VERIFICATION_METHODS[0] : this.form.verificationMethod) : NO_VERIFICATION;
    this.onVerificationMethodChange();
  }

  get availableProviders() { return VERIFICATION_PROVIDERS.filter(provider => provider.methods.includes(this.form.verificationMethod)); }

  onVerificationMethodChange(): void {
    if (!this.availableProviders.some(provider => provider.key === this.form.verificationProvider)) this.form.verificationProvider = null;
    this.onFieldChange();
  }

  onFieldChange(): void {
    if (this.submitted) this.revalidate();
  }

  private revalidate(): void {
    this.errors = validateDocumentTypeForm(this.form, this.isEdit);
  }

  // ── Pending changes ───────────────────────────────────────────────────────

  get hasChanges(): boolean {
    return !this.sentForApproval && this.snapshot() !== this.savedSnapshot;
  }

  private snapshot(): string {
    return JSON.stringify(this.form);
  }

  @HostListener('window:beforeunload', ['$event'])
  beforeUnload(event: BeforeUnloadEvent): void {
    if (!this.hasChanges) return;
    event.preventDefault();
    event.returnValue = '';
  }

  hasPendingChanges(): boolean {
    return this.hasChanges;
  }

  confirmDiscardChanges(): Observable<boolean> {
    return this.appDialog.confirm({
      title: this.translate.instant('documentManagement.editor.discard.title'),
      message: this.translate.instant('documentManagement.editor.discard.message'),
      icon: 'bi-exclamation-triangle',
      variant: 'warning',
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
    });
  }

  // ── Save ──────────────────────────────────────────────────────────────────

  save(): void {
    this.submitted = true;
    this.saveErrorMessage = '';
    this.errors = validateDocumentTypeForm(this.form, this.isEdit);
    if (hasErrors(this.errors)) return;
    this.saving = true;
    const request = toRequest(this.form);
    const call$ = this.isEdit && this.editId ? this.svc.update(this.editId, request) : this.svc.create(request);
    this.subs.add(
      call$.subscribe({
        next: () => {
          this.saving = false;
          this.sentForApproval = true;
          this.notify.success(this.translate.instant('documentManagement.editor.messages.sentForApproval'));
        },
        error: (err: HttpErrorResponse) => {
          this.saving = false;
          this.saveErrorMessage = this.translate.instant(saveErrorKey(err?.status));
          this.notify.error(this.saveErrorMessage);
        },
      }),
    );
  }

  /** Back to the list; asks first when there are unsaved edits. */
  cancel(): void {
    this.router.navigate([DOCUMENT_TYPES_ROUTE]);
  }

  reload(): void {
    if (this.editId) this.load(this.editId);
  }

  // ── Test a file ───────────────────────────────────────────────────────────

  get codeForTest(): string {
    return this.form.code;
  }

  /** A file chosen or dropped on the shared drop area; it is only checked, never stored. */
  onFilesPicked(files: File[]): void {
    this.setTestFile(files[0] ?? null);
  }

  private setTestFile(file: File | null): void {
    this.testResult = null;
    this.testError = '';
    this.testFile = file;
    if (file && exceedsPlatformLimit(file.size)) {
      this.testError = this.translate.instant('documentManagement.test.tooLarge', {
        size: formatFileSizeMb(file.size),
        max: DOCUMENT_TYPE_LIMITS.maxFileSizeMbMax,
      });
      this.testFile = null;
    }
  }

  runTest(): void {
    if (!this.testFile || !this.editId) return;
    this.testing = true;
    this.testResult = null;
    this.testError = '';
    this.subs.add(
      this.svc.checkFile(this.testFile, this.form.code, this.form.category.trim() || undefined).subscribe({
        next: (res) => {
          this.testResult = res;
          this.testing = false;
        },
        error: () => {
          this.testing = false;
          this.testError = this.translate.instant('documentManagement.test.failed');
        },
      }),
    );
  }

  clearTest(): void {
    this.testFile = null;
    this.testResult = null;
    this.testError = '';
  }

  formatSize(file: File): string {
    return formatFileSizeMb(file.size);
  }
}
