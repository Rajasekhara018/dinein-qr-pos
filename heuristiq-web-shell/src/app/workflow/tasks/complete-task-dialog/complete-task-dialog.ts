import { Component, Inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  CHANGE_REQUEST_VARIABLE, CORE_VARIABLES, DECISIONS, DECISION_KEY_PREFIX, DECISION_VARIABLE, ONBOARDING_SUBJECT_TYPE,
} from '../../workflow.constants';
import { ChangeRequestDetail, WorkflowTask } from '../../workflow.models';
import { DeepDiffRow, deepDiff } from '../../change-diff.util';
import { WorkflowService, parseVariablesJson } from '../../workflow-service';
import { OnboardingAdminService } from '../../../shared/services/onboarding-admin-service';
import { OnboardingDocumentRef, OnboardingSubmission } from '../../../onboarded-users/onboarded-users.models';
import { displayValue, formatBytes } from '../../../onboarded-users/onboarded-users.utils';

export interface CompleteTaskDialogData {
  task: WorkflowTask;
}

/** What the dialog closes with: the variables to send and the optional note that goes with the decision. */
export interface CompleteTaskDialogResult {
  variables: Record<string, unknown>;
  comment: string;
}

/** Answers shown before the reviewer expands the list. */
const ANSWER_PREVIEW_ROWS = 8;
/** How long a document's temporary address stays valid after it was opened in a new tab. */
const DOCUMENT_URL_LIFETIME_MS = 60_000;

/**
 * Completes a task: an optional decision (the variable the bundled generic-review process branches
 * on), an optional comment for the history, plus any extra variables as a JSON object. Closes with
 * the variables and comment to send, or null.
 */
@Component({
  selector: 'app-complete-task-dialog',
  standalone: false,
  templateUrl: './complete-task-dialog.html',
  styleUrl: './complete-task-dialog.scss',
})
export class CompleteTaskDialog implements OnInit {
  readonly decisions = DECISIONS;
  readonly decisionKeyPrefix = DECISION_KEY_PREFIX;

  decision = '';
  comment = '';
  variablesText = '';
  invalidJson = false;

  /** What the reviewer is being asked to approve; the section above the decision depends on it. */
  reviewKind: 'CHANGE' | 'ONBOARDING' | 'NONE' = 'NONE';
  change: ChangeRequestDetail | null = null;
  changeLoading = false;
  changeFailed = false;

  /** The onboarding submission under review: what the applicant filled in and the documents they uploaded. */
  submission: OnboardingSubmission | null = null;
  submissionRows: Array<{ field: string; value: string }> = [];
  submissionLoading = false;
  submissionFailed = false;
  showAllAnswers = false;
  openingDocument: string | null = null;
  documentFailed = false;

  constructor(
    private ref: MatDialogRef<CompleteTaskDialog, CompleteTaskDialogResult | null>,
    @Inject(MAT_DIALOG_DATA) public data: CompleteTaskDialogData,
    private workflow: WorkflowService,
    private router: Router,
    private onboarding: OnboardingAdminService,
  ) {}

  ngOnInit(): void {
    const vars = this.data.task.variables ?? {};
    if (typeof vars[CHANGE_REQUEST_VARIABLE] === 'string' && vars[CHANGE_REQUEST_VARIABLE]) {
      this.reviewKind = 'CHANGE';
      this.loadChange(vars[CHANGE_REQUEST_VARIABLE] as string);
    } else if (vars['subjectType'] === ONBOARDING_SUBJECT_TYPE && typeof vars['subjectId'] === 'string') {
      this.reviewKind = 'ONBOARDING';
      this.loadSubmission(vars['subjectId'] as string);
    }
  }

  private loadChange(id: string): void {
    this.changeLoading = true;
    this.changeFailed = false;
    this.workflow.changeRequestDetail(id).subscribe({
      next: (detail) => { this.change = detail; this.changeLoading = false; },
      error: () => { this.changeFailed = true; this.changeLoading = false; },
    });
  }

  private loadSubmission(id: string): void {
    this.submissionLoading = true;
    this.submissionFailed = false;
    this.onboarding.get(id).subscribe({
      next: (submission) => {
        this.submission = submission;
        this.submissionRows = Object.entries(submission.answers ?? {}).map(([field, value]) => ({
          field: this.humanise(field),
          value: displayValue(value),
        }));
        this.submissionLoading = false;
      },
      error: () => { this.submissionFailed = true; this.submissionLoading = false; },
    });
  }

  retrySubmission(): void {
    const id = this.submissionId;
    if (id) this.loadSubmission(id);
  }

  /** `fullName` -> `Full name`: the answers arrive keyed by field name, not by label. */
  humanise(key: string): string {
    const spaced = key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim().toLowerCase();
    return spaced.charAt(0).toUpperCase() + spaced.slice(1);
  }

  get documents(): OnboardingDocumentRef[] {
    return this.submission?.documents ?? [];
  }

  get visibleRows(): Array<{ field: string; value: string }> {
    return this.showAllAnswers ? this.submissionRows : this.submissionRows.slice(0, ANSWER_PREVIEW_ROWS);
  }

  get hiddenRowCount(): number {
    return Math.max(0, this.submissionRows.length - ANSWER_PREVIEW_ROWS);
  }

  size(bytes: number | null | undefined): string {
    return formatBytes(bytes);
  }

  /**
   * Shows a stored document in a new browser tab. The tab is opened straight from the click (so the browser does not
   * block it) and pointed at the file once it has been fetched.
   */
  openDocument(doc: OnboardingDocumentRef): void {
    const id = this.submissionId;
    if (!id || this.openingDocument) return;
    const tab = window.open('', '_blank');
    this.openingDocument = doc.fieldKey;
    this.documentFailed = false;
    this.onboarding.downloadDocument(id, doc.fieldKey).subscribe({
      next: (blob) => {
        this.openingDocument = null;
        const url = URL.createObjectURL(blob);
        if (tab) tab.location.href = url; else window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), DOCUMENT_URL_LIFETIME_MS);
      },
      error: () => {
        this.openingDocument = null;
        this.documentFailed = true;
        tab?.close();
      },
    });
  }

  retryChange(): void {
    const id = this.changeRequestId;
    if (id) this.loadChange(id);
  }

  get changeRequestId(): string | null {
    const v = (this.data.task.variables ?? {})[CHANGE_REQUEST_VARIABLE];
    return typeof v === 'string' && v ? v : null;
  }

  get submissionId(): string | null {
    const v = (this.data.task.variables ?? {})['subjectId'];
    return typeof v === 'string' && v ? v : null;
  }

  get submissionReference(): string | null {
    const v = (this.data.task.variables ?? {})['reference'];
    return typeof v === 'string' && v ? v : null;
  }

  /** The exact leaves that changed, with nested JSON opened up (computed once per loaded change). */
  private diffSource: ChangeRequestDetail | null = null;
  private diffCache: DeepDiffRow[] = [];

  get diffRows(): DeepDiffRow[] {
    if (this.change !== this.diffSource) {
      this.diffSource = this.change;
      this.diffCache = this.change ? deepDiff(this.change.diff.entries) : [];
    }
    return this.diffCache;
  }

  /** A value as the reviewer reads it: text as is, anything structured as compact JSON, empty as a dash. */
  show(value: unknown): string {
    if (value === undefined || value === null || value === '') return '—';
    return typeof value === 'object' ? JSON.stringify(value) : String(value);
  }

  /** Opens the full change request (AdminWeb) or the submission with its documents in a new tab, so this dialog stays open. */
  openInNewTab(kind: 'CHANGE' | 'ONBOARDING'): void {
    const commands = kind === 'CHANGE'
      ? ['/admin', 'change-requests', this.changeRequestId ?? '']
      : ['/onboarded-users', this.submissionId ?? ''];
    window.open(this.router.serializeUrl(this.router.createUrlTree(commands)), '_blank', 'noopener');
  }

  /** Existing variables of the instance, for reference (the core ones first). */
  get existing(): { key: string; value: string }[] {
    const vars = this.data.task.variables ?? {};
    const keys = Object.keys(vars).sort((a, b) => Number(CORE_VARIABLES.includes(b)) - Number(CORE_VARIABLES.includes(a)) || a.localeCompare(b));
    return keys.map((key) => ({ key, value: typeof vars[key] === 'object' ? JSON.stringify(vars[key]) : String(vars[key]) }));
  }

  submit(): void {
    const parsed = parseVariablesJson(this.variablesText);
    this.invalidJson = !parsed.ok;
    if (!parsed.ok) return;
    const variables: Record<string, unknown> = { ...parsed.value };
    if (this.decision) variables[DECISION_VARIABLE] = this.decision;
    this.ref.close({ variables, comment: this.comment.trim() });
  }

  cancel(): void {
    this.ref.close(null);
  }
}
