import { Component, HostListener, Inject, OnDestroy, OnInit } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { Subject, filter, takeUntil } from 'rxjs';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { FIELD_TYPES, IDENTIFIER_PATTERN, MAX_FIELD_PATTERN_LENGTH } from '../onboarding-forms.constants';
import { FieldFormValue, FieldType, SchemaField } from '../onboarding-forms.models';
import { DOCUMENT_TYPE_OPTIONS_PAGE_SIZE, DocumentTypeChoice, NUMERIC_TYPES, TEXT_LIKE_TYPES } from './field-dialog.constants';
import { DocumentTypesService } from '../../document-management/document-types.service';
import { localizedName } from '../../document-management/document-type.helpers';
import { fieldRulesValidator, uniqueFieldKeyValidator } from '../field-rules.helpers';

export interface FieldDialogData {
  field: SchemaField | null;
  /** Keys of the other fields of the form: a field key must be unique across the whole form. */
  takenKeys?: string[];
}

@Component({
  standalone: false,
  selector: 'app-field-dialog',
  templateUrl: './field-dialog.html',
  styleUrl: './field-dialog.scss',
})
export class FieldDialog implements OnInit, OnDestroy {
  form!: FormGroup;
  isEdit = false;
  errorMessage = '';
  /** Form state as first opened -- drives the "Unsaved changes" pill / discard prompt. */
  private formSnapshot = '';
  private confirmingClose = false;
  private readonly destroy$ = new Subject<void>();

  readonly fieldTypes = FIELD_TYPES;
  readonly maxPatternLength = MAX_FIELD_PATTERN_LENGTH;
  /** Active document types a DOCUMENT field can name; empty when they could not be read (the category is then typed by hand). */
  documentTypes: DocumentTypeChoice[] = [];
  documentTypesFailed = false;

  constructor(
    private fb: FormBuilder,
    public dialogRef: MatDialogRef<FieldDialog>,
    private appDialog: AppDialogService,
    private translate: TranslateService,
    private documentTypesApi: DocumentTypesService,
    @Inject(MAT_DIALOG_DATA) public data: FieldDialogData,
  ) {}

  ngOnInit(): void {
    const field = this.data?.field;
    this.isEdit = !!field;

    this.form = this.fb.group({
      key: [field?.key ?? '', [Validators.required, Validators.pattern(IDENTIFIER_PATTERN), uniqueFieldKeyValidator(this.data?.takenKeys ?? [])]],
      label: [field?.label ?? '', [Validators.required]],
      type: [field?.type ?? 'TEXT', [Validators.required]],
      required: [field?.required ?? false],
      documentCategory: [field?.documentCategory ?? ''],
      documentType: [field?.documentType ?? ''],
      options: this.fb.array((field?.options ?? []).map((o) => new FormControl(o, [Validators.required]))),
      pattern: [field?.pattern ?? ''],
      patternErrorMessage: [field?.patternErrorMessage ?? ''],
      minValue: [field?.minValue ?? null],
      maxValue: [field?.maxValue ?? null],
      minLength: [field?.minLength ?? null],
      maxLength: [field?.maxLength ?? null],
      dateRange: [field?.dateRange ?? ''],
      minAgeYears: [field?.minAgeYears ?? null],
      maxAgeYears: [field?.maxAgeYears ?? null],
    }, { validators: [fieldRulesValidator()] });
    this.formSnapshot = JSON.stringify(this.form.getRawValue());
    this.loadDocumentTypes();
    this.form.get('documentType')!.valueChanges.pipe(takeUntil(this.destroy$)).subscribe((code: string) => this.applyDocumentType(code));

    // Route Esc / backdrop clicks through cancel() so they get the same discard prompt as Cancel/X.
    this.dialogRef.disableClose = true;
    this.dialogRef.backdropClick().pipe(takeUntil(this.destroy$)).subscribe(() => this.cancel());
    this.dialogRef.keydownEvents()
      .pipe(filter((e) => e.key === 'Escape'), takeUntil(this.destroy$))
      .subscribe(() => this.cancel());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get hasChanges(): boolean {
    return !!this.form && JSON.stringify(this.form.getRawValue()) !== this.formSnapshot;
  }

  @HostListener('window:beforeunload', ['$event'])
  beforeUnload(event: BeforeUnloadEvent): void {
    if (!this.hasChanges) return;
    event.preventDefault();
    event.returnValue = '';
  }

  get optionsArray(): FormArray {
    return this.form.get('options') as FormArray;
  }

  private get selectedType(): FieldType {
    return this.form?.get('type')?.value as FieldType;
  }

  get isSelectType(): boolean {
    return this.selectedType === 'SELECT';
  }

  get isDocumentType(): boolean {
    return this.selectedType === 'DOCUMENT';
  }

  /** A field that names a document type takes that type's category (DMS refuses any other), so it is not typed by hand. */
  get categoryFromType(): boolean {
    return !!this.form?.get('documentType')?.value;
  }

  /** Reads the active document types once; a failure leaves the category to be typed by hand. */
  private loadDocumentTypes(): void {
    const lang = this.translate.currentLang || 'en';
    this.documentTypesApi.list({ category: '', flow: '', active: 'true', page: 0, size: DOCUMENT_TYPE_OPTIONS_PAGE_SIZE })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (page) => {
          this.documentTypes = (page?.content ?? [])
            .map((t) => ({ code: t.code, category: t.category, label: `${localizedName(t.names, lang, t.code)} (${t.code})` }))
            .sort((a, b) => a.label.localeCompare(b.label));
          this.applyDocumentType(this.form.get('documentType')!.value);
        },
        error: () => { this.documentTypesFailed = true; },
      });
  }

  /** Choosing a type sets the field's category to the type's own; clearing it lets the category be typed again. */
  private applyDocumentType(code: string | null): void {
    const category = this.form.get('documentCategory')!;
    const type = code ? this.documentTypes.find((t) => t.code === code) : undefined;
    if (type) {
      category.setValue(type.category, { emitEvent: false });
      category.disable({ emitEvent: false });
    } else if (category.disabled) {
      category.enable({ emitEvent: false });
    }
  }

  get isNumericType(): boolean {
    return NUMERIC_TYPES.includes(this.selectedType);
  }

  /** DATE fields get the date rules (which dates, minimum and maximum age). */
  get isDateType(): boolean {
    return this.selectedType === 'DATE';
  }

  get isTextLikeType(): boolean {
    return TEXT_LIKE_TYPES.includes(this.selectedType);
  }

  hasError(field: string, error: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl && ctrl.touched && ctrl.hasError(error));
  }

  /** A rule of the whole field is broken (pattern, length range, value range); shown at once, as the user types. */
  hasRuleError(code: string): boolean {
    return this.form.hasError(code);
  }

  /** The one message the key box shows (required, pattern, then duplicate), or null. */
  get keyError(): string | null {
    if (this.hasError('key', 'required')) return this.translate.instant('onboardingForms.fieldDialog.keyRequired');
    if (this.hasError('key', 'pattern')) return this.translate.instant('onboardingForms.fieldDialog.keyPattern');
    if (this.form.get('key')?.hasError('keyDuplicate')) {
      return this.translate.instant('onboardingForms.rules.keyDuplicate', { field: this.form.get('key')?.value });
    }
    return null;
  }

  /** The one message the pattern box shows, or null. */
  get patternError(): string | null {
    const field = this.form.get('key')?.value;
    if (this.hasRuleError('patternInvalid')) return this.translate.instant('onboardingForms.rules.patternInvalid', { field });
    if (this.hasRuleError('patternTooLong')) {
      return this.translate.instant('onboardingForms.rules.patternTooLong', { field, max: this.maxPatternLength });
    }
    return null;
  }

  /** The message under a min/max pair (length or value range), or null. */
  rangeError(codes: readonly string[]): string | null {
    const code = codes.find((c) => this.hasRuleError(c));
    return code ? this.translate.instant('onboardingForms.rules.' + code, { field: this.form.get('key')?.value }) : null;
  }

  addOption(): void {
    this.optionsArray.push(new FormControl('', [Validators.required]));
  }

  removeOption(index: number): void {
    this.optionsArray.removeAt(index);
  }

  get canSubmit(): boolean {
    if (this.form.invalid) return false;
    if (this.isSelectType && this.optionsArray.length === 0) return false;
    return true;
  }

  cancel(): void {
    if (!this.hasChanges) {
      this.dialogRef.close(null);
      return;
    }
    if (this.confirmingClose) return;
    this.confirmingClose = true;
    this.appDialog.confirm({
      title: this.translate.instant('onboardingForms.fieldDialog.discard.title'),
      message: this.translate.instant('onboardingForms.fieldDialog.discard.message'),
      icon: 'bi-exclamation-triangle',
      variant: 'warning',
      confirmText: this.translate.instant('onboardingForms.dialogs.discard.confirm'),
      cancelText: this.translate.instant('onboardingForms.dialogs.discard.cancel'),
    }).pipe(takeUntil(this.destroy$)).subscribe((confirmed) => {
      this.confirmingClose = false;
      if (confirmed) this.dialogRef.close(null);
    });
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.isSelectType && this.optionsArray.length === 0) {
      this.errorMessage = this.translate.instant('onboardingForms.fieldDialog.optionsRequired');
      return;
    }
    if (!this.canSubmit) return;

    this.errorMessage = '';
    const value = this.form.value as FieldFormValue;
    const result: SchemaField = {
      key: value.key,
      label: value.label,
      type: value.type,
      required: !!value.required,
    };
    if (value.type === 'SELECT') {
      result.options = value.options.filter((o) => !!o?.trim());
    }
    const raw = this.form.getRawValue() as FieldFormValue;
    const category = raw.documentCategory?.trim();
    const documentType = raw.documentType?.trim();
    if (value.type === 'DOCUMENT' && category) {
      result.documentCategory = category;
    }
    if (value.type === 'DOCUMENT' && documentType) {
      result.documentType = documentType;
    }
    if (this.isTextLikeType) {
      const pattern = value.pattern?.trim();
      const patternMessage = value.patternErrorMessage?.trim();
      if (pattern) result.pattern = pattern;
      if (patternMessage) result.patternErrorMessage = patternMessage;
      if (this.hasNumber(value.minLength)) result.minLength = Number(value.minLength);
      if (this.hasNumber(value.maxLength)) result.maxLength = Number(value.maxLength);
    }
    if (this.isNumericType) {
      if (this.hasNumber(value.minValue)) result.minValue = Number(value.minValue);
      if (this.hasNumber(value.maxValue)) result.maxValue = Number(value.maxValue);
    }
    if (this.isDateType) {
      if (value.dateRange) result.dateRange = value.dateRange;
      if (this.hasNumber(value.minAgeYears)) result.minAgeYears = Number(value.minAgeYears);
      if (this.hasNumber(value.maxAgeYears)) result.maxAgeYears = Number(value.maxAgeYears);
    }
    this.dialogRef.close(result);
  }

  private hasNumber(value: number | string | null): boolean {
    return value !== null && value !== '';
  }
}
