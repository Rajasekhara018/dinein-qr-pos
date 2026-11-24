import { ChangeDetectorRef, Component, HostListener, Inject, OnDestroy, OnInit, Optional } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { Observable, Subscription } from 'rxjs';
import { watchColumns } from '../../../shared/table-columns.helpers';
import { UamService } from '../../../shared/services/uam-service';
import { NotificationService } from '../../../shared/services/notification-service';
import { AppDialogService } from '../../../shared/services/app-dialog-service';
import { PendingChangesAware } from '../../../shared/guards/pending-changes.guard';
import { Permission, UamRole, UpsertRoleRequest } from '../../../shared/models/uam.models';
import { TranslateService } from '@ngx-translate/core';
import {
  DEFAULT_PERMISSION_PAGE_SIZE,
  MODULE_OPTIONS,
  PERMISSION_FETCH_MAX_PAGES,
  PERMISSION_COLUMNS,
  PERMISSION_FETCH_SIZE,
  PERMISSION_PAGE_SIZES,
  PERMISSION_PAGER_WINDOW,
  RESOURCE_OPTIONS,
  ROLE_CREATE_PRIVILEGES,
  ROLE_UPDATE_PRIVILEGES,
} from './role-form-dialog.constants';
import { apiErrorMessage } from '../../../shared/utils/api-error.helpers';
import { OrganizationContextService } from '../../../shared/services/organization-context-service';
import { cachedPermissionCatalogue, rememberPermissionCatalogue } from './permission-catalogue.cache';
import { ROLE_FORM_GUIDE } from '../../admin-form-guides.constants';
import { HTTP_CONFLICT, ROLE_CODE_MAX_LENGTH, ROLE_CODE_PATTERN, ROLE_CODE_TAKEN_KEY, RULE_ERROR_KEYS } from '../../access-management-rules.constants';

export interface RoleFormDialogData {
  mode: 'create' | 'edit';
  value: UamRole | null;
}

@Component({
  selector: 'app-role-form-dialog',
  standalone: false,
  templateUrl: './role-form-dialog.html',
  styleUrl: './role-form-dialog.scss',
})
export class RoleFormDialog implements OnInit, OnDestroy, PendingChangesAware {
  /** Columns of the permission picker (headings follow the language). */
  permissionColumns: HqCommonTableColumn<Permission>[] = [];
  private columnsSub?: Subscription;
  moduleOptions = MODULE_OPTIONS;
  resourceOptions = RESOURCE_OPTIONS;
  readonly codeMaxLength = ROLE_CODE_MAX_LENGTH;
  /** The help beside the form on the role page: what a role is, the access levels and good practice. */
  readonly guide = ROLE_FORM_GUIDE;

  /** Either the injected MAT_DIALOG_DATA (dialog mode) or built from the route (page mode). */
  data: RoleFormDialogData;
  /** True when activated as a routed page rather than opened via MatDialog.open(). */
  readonly isPageMode: boolean;
  isLoadingRole = false;
  isSaving = false;
  private editId: string | null = null;
  private savedSnapshot = '';

  code = '';
  name = '';
  description = '';
  systemRole = false;
  /** The code (upper-case) the server refused as already used, so the message stays under the code until it changes. */
  takenCode: string | null = null;
  selectedCodes = new Set<string>();
  /** False shows the plain-language picker (areas and features); true shows every technical permission code. */
  showAdvanced = false;

  allPermissions: Permission[] = [];
  /** allPermissions after the module / resource / search filters; the table pages over this. */
  filteredPermissions: Permission[] = [];
  isLoadingPermissions = false;

  // Permissions table pagination (client-side over the filtered rows).
  readonly permPageSizes = PERMISSION_PAGE_SIZES;
  permPage = 0;
  permPageSize = DEFAULT_PERMISSION_PAGE_SIZE;

  moduleDraft = '';
  resourceDraft = '';
  searchDraft = '';
  appliedModule = '';
  appliedResource = '';
  appliedSearch = '';

  constructor(
    @Optional() private dialogRef: MatDialogRef<RoleFormDialog> | null,
    private uam: UamService,
    private cdr: ChangeDetectorRef,
    private route: ActivatedRoute,
    private router: Router,
    private notify: NotificationService,
    private appDialog: AppDialogService,
    private translate: TranslateService,
    private organizationContext: OrganizationContextService,
    @Optional() @Inject(MAT_DIALOG_DATA) dialogData: RoleFormDialogData | null,
  ) {
    this.isPageMode = !dialogData;
    if (dialogData) {
      this.data = dialogData;
    } else {
      this.editId = this.route.snapshot.paramMap.get('id');
      this.data = { mode: this.editId ? 'edit' : 'create', value: null };
    }
    this.applyValue(this.data.value);
  }

  private applyValue(value: UamRole | null): void {
    if (value) {
      this.code = value.code ?? '';
      this.name = value.name ?? '';
      this.description = value.description ?? '';
      this.systemRole = !!value.systemRole;
      this.selectedCodes = new Set(value.permissions ?? []);
    }
    this.savedSnapshot = this.currentSnapshot();
  }

  /** Privileges needed to save this form: create or update, depending on the mode (a stable array, so bindings do not churn). */
  get savePrivileges(): readonly string[] {
    return this.data.mode === 'create' ? ROLE_CREATE_PRIVILEGES : ROLE_UPDATE_PRIVILEGES;
  }

  get title(): string {
    return this.translate.instant(this.data.mode === 'create' ? 'accessManagement.roles.form.createNewRole' : 'accessManagement.roles.form.updateRole');
  }

  ngOnDestroy(): void {
    this.columnsSub?.unsubscribe();
  }

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, PERMISSION_COLUMNS, (columns) => (this.permissionColumns = columns));
    if (this.isPageMode && this.editId) {
      this.isLoadingRole = true;
      this.uam.getById<UamRole>('/roles', this.editId).subscribe({
        next: (role) => {
          this.data = { ...this.data, value: role };
          this.applyValue(role);
          this.isLoadingRole = false;
          this.cdr.detectChanges();
        },
        error: (err: any) => {
          this.isLoadingRole = false;
          this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.roles.messages.loadFailed')));
          this.cdr.detectChanges();
        },
      });
    }

    const known = cachedPermissionCatalogue();
    if (known) {
      // Read a moment ago for another editor: no need to fetch the ~2,300 rows again.
      this.allPermissions = [...known];
      this.applyPermissionFilters();
      return;
    }
    this.isLoadingPermissions = true;
    this.loadAllPermissions(0, []);
  }

  /**
   * UAM pages the permission catalogue; fetch every page so the table's filters, "select all
   * matching" and the selected count all see the whole catalogue (a single page of 200 used to
   * silently hide the rest).
   */
  private loadAllPermissions(page: number, collected: Permission[]): void {
    this.uam.list<Permission>('/permissions', page, PERMISSION_FETCH_SIZE).subscribe({
      next: (res) => {
        const all = collected.concat(res.content ?? []);
        if (!res.last && page + 1 < (res.totalPages ?? 1) && page + 1 < PERMISSION_FETCH_MAX_PAGES) {
          this.loadAllPermissions(page + 1, all);
          return;
        }
        this.allPermissions = all;
        // Only a catalogue read to its last page is remembered.
        rememberPermissionCatalogue(all);
        this.applyPermissionFilters();
        this.isLoadingPermissions = false;
        this.cdr.detectChanges();
      },
      error: () => {
        // Keep whatever pages did arrive rather than showing an empty catalogue.
        this.allPermissions = collected;
        this.applyPermissionFilters();
        this.isLoadingPermissions = false;
        this.cdr.detectChanges();
      },
    });
  }

  /** The simple picker hands back the whole new selection. */
  onAccessChange(next: Set<string>): void {
    this.selectedCodes = next;
    this.cdr.detectChanges();
  }

  private applyPermissionFilters(): void {
    this.filteredPermissions = this.allPermissions.filter((p) =>
      (!this.appliedModule || p.module === this.appliedModule) &&
      (!this.appliedResource || p.resource === this.appliedResource) &&
      (!this.appliedSearch || p.code.toLowerCase().includes(this.appliedSearch) || (p.displayName ?? '').toLowerCase().includes(this.appliedSearch)));
    this.permPage = 0;
  }

  searchPermissions(): void {
    this.appliedModule = this.moduleDraft;
    this.appliedResource = this.resourceDraft;
    this.appliedSearch = this.searchDraft.trim().toLowerCase();
    this.applyPermissionFilters();
  }

  resetPermissionFilter(): void {
    this.moduleDraft = '';
    this.resourceDraft = '';
    this.searchDraft = '';
    this.appliedModule = '';
    this.appliedResource = '';
    this.appliedSearch = '';
    this.applyPermissionFilters();
  }

  // ── permissions table pagination ───────────────────────────────────────────
  get permTotalPages(): number {
    return Math.max(1, Math.ceil(this.filteredPermissions.length / this.permPageSize));
  }

  get pagedPermissions(): Permission[] {
    const start = this.permPage * this.permPageSize;
    return this.filteredPermissions.slice(start, start + this.permPageSize);
  }

  get permRangeStart(): number {
    return this.filteredPermissions.length === 0 ? 0 : this.permPage * this.permPageSize + 1;
  }

  get permRangeEnd(): number {
    return Math.min((this.permPage + 1) * this.permPageSize, this.filteredPermissions.length);
  }

  /** Page numbers (1-based) around the current page. */
  get permPageNumbers(): number[] {
    const current = this.permPage + 1;
    const from = Math.max(1, current - PERMISSION_PAGER_WINDOW);
    const to = Math.min(this.permTotalPages, current + PERMISSION_PAGER_WINDOW);
    const numbers: number[] = [];
    for (let n = from; n <= to; n++) numbers.push(n);
    return numbers;
  }

  goToPermPage(page: number): void {
    this.permPage = Math.min(Math.max(0, page), this.permTotalPages - 1);
  }

  onPermPageSizeChange(size: number): void {
    this.permPageSize = size;
    this.permPage = 0;
  }

  isSelected(permission: Permission): boolean {
    return this.selectedCodes.has(permission.code);
  }

  toggle(permission: Permission): void {
    if (this.selectedCodes.has(permission.code)) {
      this.selectedCodes.delete(permission.code);
    } else {
      this.selectedCodes.add(permission.code);
    }
  }

  /** Header checkbox: every row on the CURRENT page is selected. */
  get isAllPageSelected(): boolean {
    const rows = this.pagedPermissions;
    return rows.length > 0 && rows.every((p) => this.selectedCodes.has(p.code));
  }

  toggleAllOnPage(): void {
    const rows = this.pagedPermissions;
    if (this.isAllPageSelected) {
      rows.forEach((p) => this.selectedCodes.delete(p.code));
    } else {
      rows.forEach((p) => this.selectedCodes.add(p.code));
    }
  }

  /** Every row matching the filters, across all pages, is selected. */
  get isAllFilteredSelected(): boolean {
    const rows = this.filteredPermissions;
    return rows.length > 0 && rows.every((p) => this.selectedCodes.has(p.code));
  }

  toggleAllFiltered(): void {
    const rows = this.filteredPermissions;
    if (this.isAllFilteredSelected) {
      rows.forEach((p) => this.selectedCodes.delete(p.code));
    } else {
      rows.forEach((p) => this.selectedCodes.add(p.code));
    }
  }

  /**
   * Whether the caller may make a platform-wide (system) role: only an organization-wide caller, the one the sign-in token marks with the
   * orgWide claim. Everyone else never sees the control, and the server ignores the flag from them as well.
   */
  get canSetSystemRole(): boolean {
    return this.organizationContext.canSwitch;
  }

  /**
   * The translation key of what is wrong with the code typed, or null. An existing role keeps its code as it is (older codes may not
   * follow today's pattern), so the rule applies to a new code and to a changed one.
   */
  get codeError(): string | null {
    const typed = this.code.trim();
    if (this.takenCode !== null && typed.toUpperCase() === this.takenCode) return ROLE_CODE_TAKEN_KEY;
    if (typed === '' || (this.data.mode === 'edit' && typed === (this.data.value?.code ?? '').trim())) return null;
    return ROLE_CODE_PATTERN.test(typed) ? null : RULE_ERROR_KEYS.roleCode;
  }

  get canSubmit(): boolean {
    const typed = this.code.trim();
    return typed.length > 0 && !this.codeError;
  }

  // ── pending-changes (page mode) ─────────────────────────────────────────────
  private currentSnapshot(): string {
    return JSON.stringify({
      code: this.code,
      name: this.name,
      description: this.description,
      systemRole: this.systemRole,
      // Sets don't JSON.stringify meaningfully -- compare as a sorted array instead.
      selectedCodes: Array.from(this.selectedCodes).sort(),
    });
  }

  get hasChanges(): boolean {
    return this.isPageMode && !this.isLoadingRole && this.currentSnapshot() !== this.savedSnapshot;
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
      title: this.translate.instant('accessManagement.roles.form.discardDialog.title'),
      message: this.translate.instant('accessManagement.roles.form.discardDialog.message'),
      icon: 'bi-exclamation-triangle',
      variant: 'warning',
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
    });
  }

  /** Back to the list: `roles/new` -> `..`, `roles/:id/edit` -> `../..` (relative nav is segment-based). */
  private navigateBack(): void {
    this.router.navigate([this.editId ? '../..' : '..'], { relativeTo: this.route });
  }

  cancel(): void {
    if (this.dialogRef) {
      this.dialogRef.close(null);
      return;
    }
    // Page mode: mirror template-form's cancel() -- prompt here directly when dirty. Once the user
    // confirms, savedSnapshot is reset so the CanDeactivate guard doesn't ask a second time.
    if (this.hasChanges) {
      this.confirmDiscardChanges().subscribe((confirmed) => {
        if (!confirmed) return;
        this.savedSnapshot = this.currentSnapshot();
        this.navigateBack();
      });
      return;
    }
    this.navigateBack();
  }

  submit(): void {
    if (!this.canSubmit || this.isLoadingRole || this.isSaving) return;
    const payload: UpsertRoleRequest = {
      code: this.code.trim(),
      name: this.name.trim() || null,
      description: this.description.trim() || null,
      permissions: Array.from(this.selectedCodes),
      systemRole: this.systemRole,
    };
    if (this.dialogRef) {
      this.dialogRef.close(payload);
      return;
    }

    const call: Observable<unknown> = this.editId
      ? this.uam.update<UpsertRoleRequest, UamRole>('/roles', this.editId, payload)
      : this.uam.create<UpsertRoleRequest, UamRole>('/roles', payload);

    this.isSaving = true;
    call.subscribe({
      next: () => {
        this.isSaving = false;
        this.savedSnapshot = this.currentSnapshot();
        this.notify.success(this.translate.instant(this.editId ? 'accessManagement.roles.messages.updated' : 'accessManagement.roles.messages.created'));
        this.navigateBack();
      },
      error: (err: any) => {
        this.isSaving = false;
        if (err?.status === HTTP_CONFLICT && !this.editId) {
          this.takenCode = this.code.trim().toUpperCase();
        }
        this.notify.error(apiErrorMessage(err, this.translate.instant('shared.adminCrud.messages.saveFailed')));
        this.cdr.detectChanges();
      },
    });
  }
}
