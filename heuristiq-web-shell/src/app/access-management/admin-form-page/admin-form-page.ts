import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HQ_PHONE_DEFAULT_COUNTRY, HqAppDialogFormValues, HqPrivilegeAccessService, hqReadSetting } from 'hq-shared-lib';
import { Observable, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { AdminFormService } from '../admin-form.service';
import { AdminFormEntity, AdminFormField, AdminFormGuide, AdminFormOption } from '../admin-form.types';
import { buildPayload, copyTextToClipboard, errorDetail, fieldError, initialFieldValue, invalidPhoneField } from '../admin-form.utils';
import {
  ACCESS_MANAGEMENT_BASE_PATH, ADMIN_FORM_NO_OPTIONS, ADMIN_FORM_PAGES, ADMIN_FORM_PAGE_DATA_KEY, AdminFormPageConfig,
  OrganizationAdminResult, organizationAdminPath,
} from './admin-form-page.constants';

/**
 * One plain full-page create / edit form for every config-driven Access Management entity (users, groups,
 * organizations, applications, API clients, security policies, notification templates). It replaces the
 * pop-up form: the fields come from the same {@link AdminFormPageConfig}, and saving goes through
 * {@link AdminFormService}, so the payload and the toasts are unchanged.
 */
@Component({
  selector: 'app-admin-form-page',
  standalone: false,
  templateUrl: './admin-form-page.html',
  styleUrl: './admin-form-page.scss',
})
export class AdminFormPage implements OnInit, PendingChangesAware {
  /**
   * Inside the organization setup stepper: no heading, no Cancel and none of the sections below the form (the stepper has them as
   * steps), the form comes from `embeddedPath` and `embeddedId` instead of the route, and a save stays on the page.
   */
  @Input() embedded = false;
  @Input() embeddedPath = '';
  @Input() embeddedId: string | null = null;
  /** The entity was read (its values, by field key). */
  @Output() readonly entityLoaded = new EventEmitter<HqAppDialogFormValues>();
  /** A save went through (the values saved, by field key). */
  @Output() readonly entitySaved = new EventEmitter<HqAppDialogFormValues>();

  page!: AdminFormPageConfig;
  /** The entity being edited, or null when creating. */
  id: string | null = null;
  fields: readonly AdminFormField[] = [];
  values: HqAppDialogFormValues = {};
  options: Record<string, AdminFormOption[]> = {};
  /** What the user typed in a multi-select's filter box, per field. */
  filters: Record<string, string> = {};
  isLoading = true;
  /** Embedded only: the entity could not be read (a retry is shown instead of the form). */
  loadFailed = false;
  isSaving = false;
  /** The first administrator of a new organization (optional; all three or none). */
  admin = { name: '', mobile: '', email: '' };
  /** Set after a successful save so leaving the page does not ask to discard it. */
  private saved = false;
  private initial = '';
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly defaultPhoneCountrySetting = inject(HQ_PHONE_DEFAULT_COUNTRY, { optional: true });
  /** The app's default phone country now (it may follow the current organization), or none. */
  private get defaultPhoneCountry(): string | undefined {
    return hqReadSetting(this.defaultPhoneCountrySetting, '') || undefined;
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private uam: UamService,
    private adminForm: AdminFormService,
    private notify: NotificationService,
    private dialogs: AppDialogService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const path = this.embedded ? this.embeddedPath : (this.route.snapshot.data[ADMIN_FORM_PAGE_DATA_KEY] as string);
    this.page = ADMIN_FORM_PAGES.find((p) => p.path === path) ?? ADMIN_FORM_PAGES[0];
    this.fields = this.page.config.fields.filter((f) => !f.privileges || this.access.can(f.privileges));
    this.id = this.embedded ? this.embeddedId : this.route.snapshot.paramMap.get('id');
    this.adminForm.loadOptions(this.fields).subscribe((options) => {
      this.options = options;
      this.loadEntity();
    });
  }

  /** The help panel beside the form, when the entity has one (not inside the organization setup stepper). */
  get guide(): AdminFormGuide | null {
    return this.embedded ? null : (this.page.config.guide ?? null);
  }

  get isEdit(): boolean {
    return this.id !== null;
  }

  applyPolicyTemplate(template: Record<string, boolean | number>): void {
    if (this.page.config.resourcePath !== '/security-policies') return;
    const updated = { ...this.values };
    for (const field of this.fields) if (field.key.includes('Policy.')) updated[field.key] = '';
    for (const [key, value] of Object.entries(template)) updated[key] = String(value);
    this.values = updated;
  }

  get entityName(): string {
    return this.translate.instant(this.page.config.entityName);
  }

  get title(): string {
    return this.translate.instant(this.isEdit ? 'shared.adminCrud.editTitle' : 'shared.adminCrud.newTitle', { entity: this.entityName });
  }

  get savePrivileges(): readonly string[] {
    return this.isEdit ? this.page.updatePrivileges : this.page.createPrivileges;
  }

  /** True on the page that creates an organization, which also offers to set up its administrator. */
  get offersAdmin(): boolean {
    return this.page.path === 'organizations' && !this.isEdit;
  }

  private get adminStarted(): boolean {
    return Object.values(this.admin).some((v) => v.trim() !== '');
  }

  private get adminComplete(): boolean {
    return Object.values(this.admin).every((v) => v.trim() !== '');
  }

  get canSubmit(): boolean {
    const adminOk = !this.offersAdmin || !this.adminStarted || this.adminComplete;
    return !this.isLoading && !this.isSaving && adminOk && this.fields.every((f) => (!this.isRequired(f) || this.hasValue(f)) && !fieldError(f, this.values));
  }

  /** The names (translated) of the required fields that are still empty; the setup action bar lists them as what to fill in. */
  missingFields(): string[] {
    if (this.isLoading || this.loadFailed) return [];
    return this.visibleFields.filter((f) => this.isRequired(f) && !this.isReadOnly(f) && !this.hasValue(f)).map((f) => this.translate.instant(f.label));
  }

  /** The field must be filled in: always, or on the create page only. */
  isRequired(field: AdminFormField): boolean {
    return !!field.required || (!!field.requiredOnCreate && !this.isEdit);
  }

  /** What is wrong with the value typed in a field, as a translation key; null when it is fine. */
  errorOf(field: AdminFormField): string | null {
    return fieldError(field, this.values);
  }

  hasValue(field: AdminFormField): boolean {
    const value = this.values[field.key];
    if (Array.isArray(value)) return value.length > 0;
    return typeof value === 'boolean' || String(value ?? '').trim() !== '';
  }

  /** How many characters are typed in a text field, for the "n / max" counter. */
  lengthOf(field: AdminFormField): number {
    return String(this.values[field.key] ?? '').length;
  }

  /** The fields drawn on the page: all of them except those marked hidden, which are still loaded and saved. */
  get visibleFields(): readonly AdminFormField[] {
    return this.fields.filter((f) => !f.hidden);
  }

  // ---- read-only fields and select choices -------------------------------------------------------

  /** True when the field is shown but cannot be changed on this page (its value is still sent). */
  isReadOnly(field: AdminFormField): boolean {
    return field.readOnly === 'always' || (field.readOnly === 'edit' && this.isEdit);
  }

  /** The text a read-only field shows: the chosen option's label, or the raw value. */
  displayOf(field: AdminFormField): string {
    const value = this.values[field.key];
    const option = (this.options[field.key] ?? field.options ?? []).find((o) => o.value === value);
    return option?.label ?? String(value ?? '');
  }

  /** Picking an option that carries values (a catalogue entry) copies them into the other fields. */
  onSelect(field: AdminFormField, value: string): void {
    const option = (this.options[field.key] ?? field.options ?? []).find((o) => o.value === value);
    if (!option?.fill) return;
    for (const [key, filled] of Object.entries(option.fill)) {
      if (this.fields.some((f) => f.key === key)) this.values[key] = filled;
    }
  }

  // ---- information panel and code box -------------------------------------------------------------

  /** The fields that show an information panel while they are editable (the catalogue of application codes). */
  get panelFields(): AdminFormField[] {
    return this.fields.filter((f) => f.optionPanel && !this.isReadOnly(f));
  }

  /** Every choice of the field that carries detail, in the order loaded, for its information panel. */
  panelEntries(field: AdminFormField): AdminFormOption[] {
    return (this.options[field.key] ?? field.options ?? []).filter((o) => !!o.detail);
  }

  /** The picked value of a field that has a code box, or '' when nothing is picked. */
  codeOf(field: AdminFormField): string {
    return String(this.values[field.key] ?? '').trim();
  }

  /** Copies the picked code and tells the user whether it worked. */
  copyCode(field: AdminFormField): void {
    const box = field.codeBox;
    const code = this.codeOf(field);
    if (!box || !code) return;
    void copyTextToClipboard(code).then((ok) => {
      if (ok) this.notify.success(this.translate.instant(box.copied, { code }));
      else this.notify.error(this.translate.instant(box.copyFailed));
    });
  }

  /** The empty-state message of an editable select that loads its choices but has none (the list could not be loaded). */
  noOptionsMessage(field: AdminFormField): string | null {
    if (!field.emptyOptionsMessage || !field.loadOptions || this.isReadOnly(field)) return null;
    return (this.options[field.key] ?? []).length === 0 ? field.emptyOptionsMessage : null;
  }

  /** The message of an editable select whose every choice is disabled (everything is already used). */
  allDisabledMessage(field: AdminFormField): string | null {
    const all = this.options[field.key] ?? field.options ?? [];
    if (!field.allDisabledMessage || this.isReadOnly(field) || all.length === 0) return null;
    return all.every((o) => o.disabled) ? field.allDisabledMessage : null;
  }

  // ---- multi-select helpers --------------------------------------------------------------------

  /** Every choice of a searchable select, unfiltered (the control does its own search). */
  allOptionsOf(field: AdminFormField): readonly AdminFormOption[] {
    return this.options[field.key] ?? field.options ?? ADMIN_FORM_NO_OPTIONS;
  }

  /**
   * Whether a select is drawn as the searchable drop-down (the same size as every other field): always, except when one of its
   * choices can be disabled, which only the native select can show.
   */
  useSearchableSelect(field: AdminFormField): boolean {
    return !!field.searchable || !this.allOptionsOf(field).some((option) => option.disabled);
  }

  optionsOf(field: AdminFormField): AdminFormOption[] {
    const all = this.options[field.key] ?? field.options ?? [];
    const filter = (this.filters[field.key] ?? '').trim().toLowerCase();
    return filter ? all.filter((o) => o.label.toLowerCase().includes(filter)) : all;
  }

  isChecked(field: AdminFormField, value: string): boolean {
    const current = this.values[field.key];
    return Array.isArray(current) && current.includes(value);
  }

  toggle(field: AdminFormField, value: string): void {
    const current = Array.isArray(this.values[field.key]) ? [...(this.values[field.key] as string[])] : [];
    this.values[field.key] = current.includes(value) ? current.filter((v) => v !== value) : [...current, value];
  }

  // ---- load / save -----------------------------------------------------------------------------

  /** Reads the entity again after a failed read (embedded). */
  retryLoad(): void {
    this.loadFailed = false;
    this.isLoading = true;
    this.loadEntity();
  }

  private loadEntity(): void {
    if (!this.id) {
      this.start(null);
      return;
    }
    this.uam.getById<AdminFormEntity>(this.page.config.resourcePath, this.id).subscribe({
      next: (entity) => this.start(entity),
      error: (err: unknown) => {
        this.notify.error(errorDetail(err) ?? this.translate.instant('shared.adminCrud.messages.loadFailed', { entity: this.entityName.toLowerCase() }));
        if (this.embedded) {
          // In the stepper the other steps stay usable: show a retry here instead of leaving, and never offer an empty form to save.
          this.loadFailed = true;
          this.isLoading = false;
          this.cdr.detectChanges();
          return;
        }
        this.saved = true;
        this.back();
      },
    });
  }

  private start(entity: AdminFormEntity | null): void {
    const values: HqAppDialogFormValues = {};
    for (const field of this.fields) values[field.key] = initialFieldValue(field, entity);
    this.values = values;
    this.initial = JSON.stringify(values);
    this.isLoading = false;
    this.entityLoaded.emit(values);
    this.cdr.detectChanges();
  }

  submit(): void {
    if (!this.canSubmit) return;
    if (invalidPhoneField(this.fields, this.values, this.defaultPhoneCountry)) {
      this.notify.error(this.translate.instant('shared.phoneNumberInput.invalid'));
      return;
    }
    this.isSaving = true;
    const wantsAdmin = this.offersAdmin && this.adminStarted;
    this.adminForm.save(this.page.config, this.id, buildPayload(this.fields, this.values),
      wantsAdmin ? (created) => this.createAdmin(created) : undefined).subscribe((ok) => {
      this.isSaving = false;
      if (ok && this.embedded) {
        // The stepper stays on this step: what was saved becomes the new starting point, so the step no longer counts as changed.
        this.initial = JSON.stringify(this.values);
        this.entitySaved.emit(this.values);
      } else if (ok) {
        this.saved = true;
        this.back();
      }
      this.cdr.detectChanges();
    });
  }

  /** Sets up the new organization's administrator and, for a new account, shows the one-time password once. */
  private createAdmin(created: AdminFormEntity | null): Observable<unknown> {
    const tenantId = String(created?.['id'] ?? '');
    if (!tenantId) return of(null);
    const request = { name: this.admin.name.trim(), mobile: this.admin.mobile.trim(), email: this.admin.email.trim() };
    return this.uam.create<typeof request, OrganizationAdminResult>(organizationAdminPath(tenantId), request).pipe(
      switchMap((result) => {
        this.notify.success(this.translate.instant('shared.adminCrud.orgAdmin.created', { email: result.email }));
        if (!result.temporaryPassword) return of(result);
        return this.dialogs.confirm({
          title: this.translate.instant('shared.adminCrud.orgAdmin.credentialsTitle'),
          message: this.translate.instant('shared.adminCrud.orgAdmin.credentialsMessage', { email: result.email, password: result.temporaryPassword }),
          confirmText: this.translate.instant('shared.adminCrud.orgAdmin.credentialsConfirm'),
          cancelText: this.translate.instant('shared.adminCrud.cancel'),
        });
      }),
      catchError((err: unknown) => {
        this.notify.error(errorDetail(err) ?? this.translate.instant('shared.adminCrud.orgAdmin.failed'));
        return of(null);
      }),
    );
  }

  cancel(): void {
    this.back();
  }

  private back(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, this.page.path]);
  }

  // ---- leaving with unsaved changes ------------------------------------------------------------

  hasPendingChanges(): boolean {
    return !this.saved && !this.isLoading && (JSON.stringify(this.values) !== this.initial || this.adminStarted);
  }

  confirmDiscardChanges(): Observable<boolean> {
    if (!this.hasPendingChanges()) return of(true);
    const entity = this.entityName.toLowerCase();
    return this.dialogs.confirm({
      title: this.translate.instant('shared.adminCrud.discard.title', { entity }),
      message: this.translate.instant('shared.adminCrud.discard.message', { entity }),
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
      variant: 'danger',
    });
  }
}
