import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { ORG_PRIVILEGES } from '../org-structure.constants';
import { hasErrors, summarizeChange } from '../org-structure.helpers';
import { OrgChange, OrgDraft, OrgDraftResult, OrgIssue, OrgUnit } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

/**
 * Edits the organization as a draft. Nothing here touches the live organization: changes are
 * collected, previewed (the server applies them and rolls back) and only then published.
 */
@Component({
  selector: 'app-org-builder',
  standalone: false,
  templateUrl: './org-builder.html',
  styleUrl: './org-builder.scss',
})
export class OrgBuilder implements OnInit {
  /** The organization whose structure this screen shows (the session's). */
  readonly organization = inject(OrganizationContextService);

  readonly privileges = ORG_PRIVILEGES;

  draft: OrgDraft | null = null;
  changes: OrgChange[] = [];
  isLoading = false;
  busy = false;
  result: OrgDraftResult | null = null;

  changeColumns: HqCommonTableColumn<OrgChange>[] = [];
  issueColumns: HqCommonTableColumn<OrgIssue>[] = [];
  unitColumns: HqCommonTableColumn<OrgUnit>[] = [];

  constructor(
    private api: OrgStructureService,
    private cdr: ChangeDetectorRef,
    private notify: NotificationService,
    private dialogs: AppDialogService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    const t = (key: string, params?: object) => this.translate.instant(key, params);
    this.changeColumns = [
      { key: 'seq', label: '#' },
      { key: 'type', label: t('orgStructure.builder.changeType'), render: (row) => t('orgStructure.changeTypes.' + row.type) },
      { key: 'payload', label: t('orgStructure.builder.details'), render: (row) => summarizeChange(row) },
    ];
    this.issueColumns = [
      { key: 'severity', label: t('orgStructure.builder.severityLabel'), render: (row) => t('orgStructure.builder.severity.' + row.severity) },
      { key: 'message', label: t('orgStructure.builder.issueMessage') },
    ];
    this.unitColumns = [
      { key: 'name', label: t('orgStructure.fields.name') },
      { key: 'code', label: t('orgStructure.fields.code') },
      { key: 'headcount', label: t('orgStructure.builder.headcountLabel') },
    ];
    this.loadDraft();
  }

  get hasErrorIssues(): boolean {
    return !!this.result && hasErrors(this.result.issues);
  }

  // ---- draft lifecycle ---------------------------------------------------------------------

  loadDraft(): void {
    this.isLoading = true;
    this.api.currentDraft().subscribe({
      next: (draft) => {
        this.draft = draft;
        this.loadChanges();
      },
      error: () => {
        // No open draft yet is a normal state, not an error.
        this.draft = null;
        this.changes = [];
        this.isLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  startDraft(): void {
    this.busy = true;
    this.api.openDraft().subscribe({
      next: (draft) => { this.draft = draft; this.changes = []; this.busy = false; this.cdr.detectChanges(); },
      error: (err) => this.fail(err, 'orgStructure.builder.messages.startFailed'),
    });
  }

  private loadChanges(): void {
    if (!this.draft) return;
    this.api.changes(this.draft.id).subscribe({
      next: (changes) => { this.changes = changes; this.isLoading = false; this.cdr.detectChanges(); },
      error: (err) => { this.isLoading = false; this.fail(err, 'orgStructure.builder.messages.loadFailed'); },
    });
  }

  // ---- the draft's changes -----------------------------------------------------------------

  /** The department tree added or removed changes: the preview no longer matches the draft. */
  onTreeChanged(changes: OrgChange[]): void {
    this.changes = changes;
    this.result = null;
    this.cdr.detectChanges();
  }

  remove(change: OrgChange): void {
    if (!this.draft) return;
    this.api.removeChange(this.draft.id, change.id).subscribe({
      next: () => { this.changes = this.changes.filter((c) => c.id !== change.id); this.result = null; this.cdr.detectChanges(); },
      error: (err) => this.fail(err, 'orgStructure.builder.messages.removeFailed'),
    });
  }

  // ---- preview / publish / discard -----------------------------------------------------------

  preview(): void {
    if (!this.draft) return;
    this.busy = true;
    this.api.preview(this.draft.id).subscribe({
      next: (result) => { this.result = result; this.busy = false; this.cdr.detectChanges(); },
      error: (err) => this.fail(err, 'orgStructure.builder.messages.previewFailed'),
    });
  }

  publish(): void {
    if (!this.draft) return;
    const draftId = this.draft.id;
    this.dialogs.confirm({
      title: this.translate.instant('orgStructure.builder.publishDialog.title'),
      message: this.translate.instant('orgStructure.builder.publishDialog.message'),
      confirmText: this.translate.instant('orgStructure.builder.publish'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.busy = true;
      this.api.publish(draftId).subscribe({
        next: (result) => {
          this.busy = false;
          this.result = result;
          if (result.applied) {
            this.notify.success(this.translate.instant('orgStructure.builder.messages.published'));
            this.draft = null;
            this.changes = [];
          } else {
            this.notify.error(this.blockedMessage(result));
          }
          this.cdr.detectChanges();
        },
        error: (err) => this.fail(err, 'orgStructure.builder.messages.publishFailed'),
      });
    });
  }

  discard(): void {
    if (!this.draft) return;
    const draftId = this.draft.id;
    this.dialogs.confirm({
      title: this.translate.instant('orgStructure.builder.discardDialog.title'),
      message: this.translate.instant('orgStructure.builder.discardDialog.message'),
      variant: 'danger',
      confirmText: this.translate.instant('orgStructure.builder.discard'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.discard(draftId).subscribe({
        next: () => {
          this.notify.success(this.translate.instant('orgStructure.builder.messages.discarded'));
          this.draft = null;
          this.changes = [];
          this.result = null;
          this.cdr.detectChanges();
        },
        error: (err) => this.fail(err, 'orgStructure.builder.messages.discardFailed'),
      });
    });
  }

  /** Puts the organization back as it was before the most recent publish, after a confirmation. */
  rollbackLast(): void {
    this.dialogs.confirm({
      title: this.translate.instant('orgStructure.builder.rollbackDialog.title'),
      message: this.translate.instant('orgStructure.builder.rollbackDialog.message'),
      variant: 'danger',
      confirmText: this.translate.instant('orgStructure.builder.rollback'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.busy = true;
      this.api.rollbackLast().subscribe({
        next: () => {
          this.busy = false;
          this.result = null;
          this.notify.success(this.translate.instant('orgStructure.builder.messages.rolledBack'));
          this.cdr.detectChanges();
        },
        error: (err) => this.fail(err, 'orgStructure.builder.messages.rollbackFailed'),
      });
    });
  }

  /** Why a publish was not applied: how many problems block it and the first one, so the cause is visible without scrolling to the checks. */
  private blockedMessage(result: OrgDraftResult): string {
    const errors = (result.issues ?? []).filter((issue) => issue.severity === 'ERROR');
    const first = errors[0] ?? result.issues?.[0];
    return this.translate.instant('orgStructure.builder.messages.publishBlocked', { count: errors.length || (result.issues?.length ?? 0), message: first?.message ?? '' });
  }

  private fail(err: { error?: { detail?: string } }, fallbackKey: string): void {
    this.busy = false;
    this.notify.error(apiErrorMessage(err, this.translate.instant(fallbackKey)));
    this.cdr.detectChanges();
  }
}
