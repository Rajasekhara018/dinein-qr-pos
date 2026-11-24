import { ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService, HqTimezoneService, HqStatusTone } from 'hq-shared-lib';
import { watchColumnsUntil } from '../shared/table-columns.helpers';
import { Observable, Subject, of, takeUntil } from 'rxjs';
import { OnboardingSchemaService } from '../shared/services/onboarding-schema-service';
import { AppDialogService } from '../shared/services/app-dialog-service';
import { NotificationService } from '../shared/services/notification-service';
import { PendingChangesAware } from '../shared/guards/pending-changes.guard';
import { FieldDialog, FieldDialogData } from './field-dialog/field-dialog';
import { PreviewDialog, PreviewDialogData } from './preview-dialog/preview-dialog';
import {
  DATE_LOCALE_FALLBACK,
  FALLBACK_FIELD_TYPE_ICON,
  FIELD_TYPE_ICONS,
  IDENTIFIER_PATTERN,
  MAX_FIELD_PATTERN_LENGTH,
  REQUIRES_REVIEW_PRIVILEGES,
  SCHEMA_VERSION_COLUMNS,
  STATUS_BADGE_TONE,
  STEP_KEY_PREFIX,
  TYPES_WITH_DEFAULT_DEFINITION,
} from './onboarding-forms.constants';
import {
  FieldType,
  NewTypeFormValue,
  OnboardingSchemaDefinition,
  OnboardingSchemaResponse,
  OnboardingType,
  SchemaField,
  SchemaStep,
  SchemaStatus,
} from './onboarding-forms.models';
import { cloneSteps, errorMessage, formatDateTime } from './onboarding-forms.utils';
import { schemaRuleProblems } from './field-rules.helpers';

/** Admin schema builder: onboarding types, their schema versions, and the step/field editor. */
@Component({
  selector: 'app-onboarding-forms',
  standalone: false,
  templateUrl: './onboarding-forms.html',
  styleUrl: './onboarding-forms.scss',
})
export class OnboardingForms implements OnInit, OnDestroy, PendingChangesAware {
  private readonly hqTimezone = inject(HqTimezoneService);
  readonly statusBadgeTone = STATUS_BADGE_TONE;

  /** Badge tone of a schema version status (the table cell gives the row untyped). */
  statusTone(status: string): HqStatusTone {
    return this.statusBadgeTone[status as SchemaStatus] ?? 'neutral';
  }

  types: OnboardingType[] = [];
  isLoadingTypes = false;

  selectedType: OnboardingType | null = null;
  schemaVersions: OnboardingSchemaResponse[] = [];
  versionColumns: HqCommonTableColumn<OnboardingSchemaResponse>[] = [];
  isLoadingVersions = false;

  showNewTypeForm = false;
  newTypeForm: FormGroup;
  isCreatingType = false;

  // Builder state
  builderOpen = false;
  editingVersion: OnboardingSchemaResponse | null = null;
  builderSteps: SchemaStep[] = [];
  isSavingDraft = false;
  isPublishing = false;
  isLoadingDefaults = false;
  isCopyingFromType = false;
  copySourceCode = '';

  /** JSON snapshot of `builderSteps` taken when the builder was opened (newDraft/editDraft) --
   *  compared against the live steps to drive the "Unsaved changes" pill and discard prompts. */
  private builderSnapshot = '';
  private stepKeySeq = 0;
  private readonly destroy$ = new Subject<void>();

  /** True when the user may flip a type between manual review and auto-approve (the badge is read-only otherwise). */
  get canToggleRequiresReview(): boolean {
    return this.access.can(REQUIRES_REVIEW_PRIVILEGES);
  }

  constructor(
    private api: OnboardingSchemaService,
    private appDialog: AppDialogService,
    private notify: NotificationService,
    private translate: TranslateService,
    private fb: FormBuilder,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
  ) {
    this.newTypeForm = this.fb.group({
      code: ['', [Validators.required, Validators.pattern(IDENTIFIER_PATTERN)]],
      displayName: ['', [Validators.required]],
      requiresReview: [true],
    });
  }

  ngOnInit(): void {
    watchColumnsUntil(this.translate, SCHEMA_VERSION_COLUMNS, (columns) => (this.versionColumns = columns), this.destroy$);
    this.loadTypes();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return this.translate.instant(key, params);
  }

  // ---- Types ----

  loadTypes(): void {
    this.isLoadingTypes = true;
    this.api.listTypes().pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.types = data ?? [];
        this.isLoadingTypes = false;
        if (this.selectedType) {
          const stillPresent = this.types.find((type) => type.code === this.selectedType!.code);
          if (stillPresent) this.selectedType = stillPresent;
        }
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.isLoadingTypes = false;
        this.notify.error(errorMessage(err, this.t('onboardingForms.toasts.loadTypesFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  toggleNewTypeForm(): void {
    this.showNewTypeForm = !this.showNewTypeForm;
    if (!this.showNewTypeForm) this.newTypeForm.reset({ code: '', displayName: '', requiresReview: true });
  }

  createType(): void {
    if (this.newTypeForm.invalid || this.isCreatingType) {
      this.newTypeForm.markAllAsTouched();
      return;
    }
    this.isCreatingType = true;
    const { code, displayName, requiresReview } = this.newTypeForm.value as NewTypeFormValue;
    this.api
      .createType({ code: code ?? '', displayName: displayName ?? '', requiresReview: !!requiresReview })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.notify.success(this.t('onboardingForms.toasts.typeCreated'));
          this.isCreatingType = false;
          this.showNewTypeForm = false;
          this.newTypeForm.reset({ code: '', displayName: '', requiresReview: true });
          this.loadTypes();
        },
        error: (err: unknown) => {
          this.isCreatingType = false;
          this.notify.error(errorMessage(err, this.t('onboardingForms.toasts.createTypeFailed')));
          this.cdr.detectChanges();
        },
      });
  }

  selectType(type: OnboardingType): void {
    if (this.selectedType?.code === type.code) return;
    this.confirmDiscardChanges().pipe(takeUntil(this.destroy$)).subscribe((confirmed) => {
      if (!confirmed) return;
      this.selectedType = type;
      this.resetBuilder();
      this.loadSchemaVersions(type);
      this.cdr.detectChanges();
    });
  }

  loadSchemaVersions(type: OnboardingType): void {
    this.isLoadingVersions = true;
    this.api.listSchemasNewestFirst(type.code).pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.schemaVersions = data;
        this.isLoadingVersions = false;
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.schemaVersions = [];
        this.isLoadingVersions = false;
        this.notify.error(errorMessage(err, this.t('onboardingForms.toasts.loadVersionsFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  toggleRequiresReview(type: OnboardingType, event: Event): void {
    event.stopPropagation();
    const next = !type.requiresReview;
    const prefix = next ? 'onboardingForms.dialogs.requireReview' : 'onboardingForms.dialogs.skipReview';
    this.appDialog.confirm({
      title: this.t(`${prefix}.title`),
      message: this.t(`${prefix}.message`, { name: type.displayName }),
      icon: next ? 'bi-clipboard-check' : 'bi-lightning-charge',
      variant: next ? undefined : 'warning',
      confirmText: this.t(`${prefix}.confirm`),
    }).pipe(takeUntil(this.destroy$)).subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.setRequiresReview(type.code, next).pipe(takeUntil(this.destroy$)).subscribe({
        next: () => {
          this.notify.success(this.t('onboardingForms.toasts.typeUpdated'));
          this.loadTypes();
        },
        error: (err: unknown) => {
          this.notify.error(errorMessage(err, this.t('onboardingForms.toasts.updateTypeFailed')));
          this.cdr.detectChanges();
        },
      });
    });
  }

  // ---- Builder ----

  newDraft(): void {
    if (!this.selectedType) return;
    this.editingVersion = null;
    this.builderSteps = [];
    this.copySourceCode = '';
    this.builderOpen = true;
    this.takeBuilderSnapshot();
  }

  /** Every OTHER onboarding type -- the source list for "Copy from" in the builder. Lets a
   *  freshly-created type (or any existing one) start from another type's step/field tree
   *  instead of being hand-built from scratch. */
  get copySourceTypes(): OnboardingType[] {
    return this.types.filter((type) => type.code !== this.selectedType?.code);
  }

  /** Copies another type's most recent schema version (preferring its currently PUBLISHED one,
   *  falling back to the latest version of any status if it has no published version yet) into
   *  the builder as a starting point. Loads that type's own version list on demand rather than
   *  keeping every type's versions cached, since this only runs while the builder is open. */
  copyFromType(code: string): void {
    this.copySourceCode = code;
    if (!code || this.isCopyingFromType) return;
    this.isCopyingFromType = true;
    this.api.listSchemasNewestFirst(code).pipe(takeUntil(this.destroy$)).subscribe({
      next: (versions) => {
        this.isCopyingFromType = false;
        const source = versions.find((v) => v.status === 'PUBLISHED') ?? versions[0];
        if (!source) {
          this.notify.error(this.t('onboardingForms.toasts.copyNoVersions'));
          this.cdr.detectChanges();
          return;
        }
        this.builderSteps = cloneSteps(source.definition?.steps ?? []);
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.isCopyingFromType = false;
        this.notify.error(errorMessage(err, this.t('onboardingForms.toasts.copyFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  editDraft(version: OnboardingSchemaResponse): void {
    this.editingVersion = version;
    this.builderSteps = cloneSteps(version.definition?.steps ?? []);
    this.copySourceCode = '';
    this.builderOpen = true;
    this.takeBuilderSnapshot();
  }

  /** True while the builder is open and its steps/fields differ from what was loaded into it. */
  get hasChanges(): boolean {
    return this.builderOpen && JSON.stringify(this.builderSteps) !== this.builderSnapshot;
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

  /** Resolves true immediately when there is nothing to lose; otherwise asks before discarding. */
  confirmDiscardChanges(): Observable<boolean> {
    if (!this.hasChanges) return of(true);
    return this.appDialog.confirm({
      title: this.t('onboardingForms.dialogs.discard.title'),
      message: this.t('onboardingForms.dialogs.discard.message'),
      icon: 'bi-exclamation-triangle',
      variant: 'warning',
      confirmText: this.t('onboardingForms.dialogs.discard.confirm'),
      cancelText: this.t('onboardingForms.dialogs.discard.cancel'),
    });
  }

  /** Cancel button: closes immediately when clean, prompts first when there are unsaved edits. */
  closeBuilder(): void {
    if (!this.hasChanges) {
      this.resetBuilder();
      return;
    }
    this.confirmDiscardChanges().pipe(takeUntil(this.destroy$)).subscribe((confirmed) => {
      if (!confirmed) return;
      this.resetBuilder();
      this.cdr.detectChanges();
    });
  }

  private resetBuilder(): void {
    this.builderOpen = false;
    this.editingVersion = null;
    this.builderSteps = [];
    this.copySourceCode = '';
    this.builderSnapshot = '';
  }

  private takeBuilderSnapshot(): void {
    this.builderSnapshot = JSON.stringify(this.builderSteps);
  }

  addStep(): void {
    this.builderSteps = [
      ...this.builderSteps,
      { key: `${STEP_KEY_PREFIX}${++this.stepKeySeq}`, title: this.t('onboardingForms.builder.newStepTitle'), fields: [] },
    ];
  }

  removeStep(index: number): void {
    this.builderSteps = this.builderSteps.filter((_, i) => i !== index);
  }

  setStepTitle(index: number, title: string): void {
    this.builderSteps = this.builderSteps.map((step, i) => (i === index ? { ...step, title } : step));
  }

  setStepKey(index: number, key: string): void {
    this.builderSteps = this.builderSteps.map((step, i) => (i === index ? { ...step, key } : step));
  }

  /** Reads the current value out of a (change) event's input element. */
  inputValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  dropStep(event: CdkDragDrop<SchemaStep[]>): void {
    const steps = [...this.builderSteps];
    moveItemInArray(steps, event.previousIndex, event.currentIndex);
    this.builderSteps = steps;
  }

  dropField(event: CdkDragDrop<SchemaField[]>, stepIndex: number): void {
    const step = this.builderSteps[stepIndex];
    const fields = [...step.fields];
    moveItemInArray(fields, event.previousIndex, event.currentIndex);
    this.builderSteps = this.builderSteps.map((s, i) => (i === stepIndex ? { ...s, fields } : s));
  }

  addField(stepIndex: number): void {
    this.openFieldDialog(stepIndex, null);
  }

  editField(stepIndex: number, fieldIndex: number): void {
    this.openFieldDialog(stepIndex, fieldIndex);
  }

  removeField(stepIndex: number, fieldIndex: number): void {
    this.builderSteps = this.builderSteps.map((step, i) => {
      if (i !== stepIndex) return step;
      return { ...step, fields: step.fields.filter((_, fi) => fi !== fieldIndex) };
    });
  }

  openPreview(): void {
    if (!this.selectedType || this.builderSteps.length === 0) return;
    this.showPreview(this.selectedType.displayName, this.builderSteps);
  }

  /** Previews a specific schema version (any status, including PUBLISHED) straight from the
   *  version-history table -- same read-only mock as the builder's own Preview, just sourced
   *  from that version's saved definition instead of the in-progress builder state. */
  previewVersion(version: OnboardingSchemaResponse): void {
    if (!this.selectedType) return;
    this.showPreview(this.selectedType.displayName, version.definition?.steps ?? []);
  }

  private showPreview(typeLabel: string, steps: SchemaStep[]): void {
    if (steps.length === 0) return;
    this.dialog.open<PreviewDialog, PreviewDialogData>(PreviewDialog, {
      width: '480px',
      maxWidth: '94vw',
      autoFocus: false,
      restoreFocus: true,
      panelClass: 'app-dialog-panel',
      data: { typeLabel, steps: cloneSteps(steps) },
    });
  }

  /** Whether `selectedType` has a built-in default definition (DefaultOnboardingSeeder only
   *  ever seeds CUSTOMER/DRIVER) -- gates showing "Load Defaults" for every other type. */
  get hasDefaultDefinition(): boolean {
    return !!this.selectedType && TYPES_WITH_DEFAULT_DEFINITION.includes(this.selectedType.code);
  }

  /** Pulls the current built-in default step/field tree into the builder as a starting point
   *  for a new draft -- lets an admin re-sync a published schema that predates a later change
   *  to the seeder's default definition, without hand-rebuilding every step/field in the UI. */
  loadDefaults(): void {
    if (!this.selectedType || this.isLoadingDefaults) return;
    this.isLoadingDefaults = true;
    this.api.defaultSchema(this.selectedType.code).pipe(takeUntil(this.destroy$)).subscribe({
      next: (definition) => {
        this.isLoadingDefaults = false;
        this.builderSteps = cloneSteps(definition.steps ?? []);
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.isLoadingDefaults = false;
        this.notify.error(errorMessage(err, this.t('onboardingForms.toasts.loadDefaultsFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  private openFieldDialog(stepIndex: number, fieldIndex: number | null): void {
    const step = this.builderSteps[stepIndex];
    const field = fieldIndex !== null ? step.fields[fieldIndex] : null;
    const ref = this.dialog.open<FieldDialog, FieldDialogData, SchemaField | null>(FieldDialog, {
      width: '520px',
      maxWidth: '94vw',
      autoFocus: true,
      restoreFocus: true,
      panelClass: 'app-dialog-panel',
      data: { field, takenKeys: this.otherFieldKeys(stepIndex, fieldIndex) },
    });
    ref.afterClosed().pipe(takeUntil(this.destroy$)).subscribe((result) => {
      if (!result) return;
      this.builderSteps = this.builderSteps.map((s, i) => {
        if (i !== stepIndex) return s;
        const fields = [...s.fields];
        if (fieldIndex !== null) {
          fields[fieldIndex] = result;
        } else {
          fields.push(result);
        }
        return { ...s, fields };
      });
      this.cdr.detectChanges();
    });
  }

  /** Keys of every field of the form except the one being edited (null = a new field): keys must be unique across the form. */
  private otherFieldKeys(stepIndex: number, fieldIndex: number | null): string[] {
    return this.builderSteps.flatMap((step, si) =>
      step.fields.filter((_, fi) => !(si === stepIndex && fi === fieldIndex)).map((field) => field.key));
  }

  /** Says what is wrong with a form (broken pattern, minimum above maximum, repeated key) and returns true when something is. */
  private rejectBrokenRules(steps: readonly SchemaStep[]): boolean {
    const problems = schemaRuleProblems(steps);
    if (problems.length === 0) return false;
    const text = problems
      .map((problem) => this.t(`onboardingForms.rules.${problem.code}`, { field: problem.field, max: MAX_FIELD_PATTERN_LENGTH }))
      .join(' ');
    this.notify.error(this.t('onboardingForms.rules.cannotSave', { problems: text }));
    return true;
  }

  saveDraft(): void {
    if (!this.selectedType || this.isSavingDraft) return;
    if (this.rejectBrokenRules(this.builderSteps)) return;
    this.isSavingDraft = true;
    const definition: OnboardingSchemaDefinition = { steps: this.builderSteps };
    const typeCode = this.selectedType.code;
    const isUpdate = !!this.editingVersion;

    const request$ = this.editingVersion
      ? this.api.updateDraft(typeCode, this.editingVersion.version, definition)
      : this.api.createDraft(typeCode, definition);

    request$.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success(this.t(isUpdate ? 'onboardingForms.toasts.draftUpdated' : 'onboardingForms.toasts.draftCreated'));
        this.isSavingDraft = false;
        this.resetBuilder();
        this.loadSchemaVersions(this.selectedType!);
      },
      error: (err: unknown) => {
        this.isSavingDraft = false;
        this.notify.error(errorMessage(err, this.t('onboardingForms.toasts.saveDraftFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  /** Rolls back to a previously-published (now ARCHIVED) version -- the currently-published one
   *  (if any) is archived in its place, exactly like a normal publish. No existing submission is
   *  affected either way: each one locked in its schema version when it was created and is never
   *  re-validated against "whatever's currently published" later. */
  republish(version: OnboardingSchemaResponse): void {
    if (!this.selectedType || this.isPublishing) return;
    if (this.rejectBrokenRules(version.definition?.steps ?? [])) return;
    const typeCode = this.selectedType.code;
    this.appDialog.confirm({
      title: this.t('onboardingForms.dialogs.restore.title'),
      message: this.t('onboardingForms.dialogs.restore.message', { version: version.version, name: this.selectedType.displayName }),
      icon: 'bi-arrow-counterclockwise',
      variant: 'warning',
      confirmText: this.t('onboardingForms.dialogs.restore.confirm'),
    }).pipe(takeUntil(this.destroy$)).subscribe((confirmed) => {
      if (!confirmed) return;
      this.isPublishing = true;
      this.api.republish(typeCode, version.version).pipe(takeUntil(this.destroy$)).subscribe({
        next: () => {
          this.notify.success(this.t('onboardingForms.toasts.restored', { version: version.version }));
          this.isPublishing = false;
          this.loadSchemaVersions(this.selectedType!);
        },
        error: (err: unknown) => {
          this.isPublishing = false;
          this.notify.error(errorMessage(err, this.t('onboardingForms.toasts.restoreFailed')));
          this.cdr.detectChanges();
        },
      });
    });
  }

  publish(version: OnboardingSchemaResponse): void {
    if (!this.selectedType || this.isPublishing) return;
    // A draft stored before these checks existed is caught here instead of going live broken.
    if (this.rejectBrokenRules(version.definition?.steps ?? [])) return;
    const typeCode = this.selectedType.code;
    this.appDialog.confirm({
      title: this.t('onboardingForms.dialogs.publish.title'),
      message: this.t('onboardingForms.dialogs.publish.message', { version: version.version, name: this.selectedType.displayName }),
      icon: 'bi-cloud-arrow-up',
      variant: 'warning',
      confirmText: this.t('onboardingForms.dialogs.publish.confirm'),
    }).pipe(takeUntil(this.destroy$)).subscribe((confirmed) => {
      if (!confirmed) return;
      this.isPublishing = true;
      this.api.publish(typeCode, version.version).pipe(takeUntil(this.destroy$)).subscribe({
        next: () => {
          this.notify.success(this.t('onboardingForms.toasts.published'));
          this.isPublishing = false;
          this.loadSchemaVersions(this.selectedType!);
        },
        error: (err: unknown) => {
          this.isPublishing = false;
          this.notify.error(errorMessage(err, this.t('onboardingForms.toasts.publishFailed')));
          this.cdr.detectChanges();
        },
      });
    });
  }

  // ---- Helpers ----

  get totalFieldCount(): number {
    return this.builderSteps.reduce((sum, step) => sum + step.fields.length, 0);
  }

  fieldTypeIcon(type: FieldType): string {
    return FIELD_TYPE_ICONS[type] ?? FALLBACK_FIELD_TYPE_ICON;
  }

  formatDate(iso: string | undefined): string {
    return formatDateTime(iso, this.translate.currentLang || DATE_LOCALE_FALLBACK, this.hqTimezone.effectiveTimezone());
  }
}
