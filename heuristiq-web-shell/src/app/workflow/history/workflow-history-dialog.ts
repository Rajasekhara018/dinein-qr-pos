import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { WorkflowService } from '../workflow-service';
import { WORKFLOW_ROUTE } from '../workflow.constants';
import { SummaryRow, summaryOf, userIdsOf } from './workflow-history.helpers';
import { WorkflowPeopleService } from './workflow-people.service';

/** What the history dialog is opened with: the request's workflow instance, and a heading (its reference and name). */
export interface WorkflowHistoryDialogData {
  instanceId: string;
  title?: string | null;
  subtitle?: string | null;
}

/**
 * A request's history in a dialog, opened from the onboarding screens: a plain-language summary (reference, type, decision, who
 * decided, by name) and the timeline. Internal values (callback URL, ids) stay on the Workflow page, which "Open full details"
 * goes to.
 */
@Component({
  selector: 'app-workflow-history-dialog',
  standalone: false,
  templateUrl: './workflow-history-dialog.html',
  styleUrl: './workflow-history-dialog.scss',
})
export class WorkflowHistoryDialog implements OnInit {
  readonly data: WorkflowHistoryDialogData = inject(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<WorkflowHistoryDialog>);
  private readonly workflow = inject(WorkflowService);
  private readonly people = inject(WorkflowPeopleService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);

  summary: SummaryRow[] = [];
  active: boolean | null = null;

  ngOnInit(): void {
    this.workflow.instance(this.data.instanceId).subscribe({
      next: (instance) => {
        const vars = instance.variables ?? {};
        this.active = instance.active;
        this.summary = summaryOf(vars, {}, (k) => this.translate.instant(k));
        this.cdr.markForCheck();
        this.people.names(userIdsOf(vars)).subscribe((names) => {
          this.summary = summaryOf(vars, names, (k) => this.translate.instant(k));
          this.cdr.markForCheck();
        });
      },
      // The summary is a bonus: the timeline below still shows (or says why it cannot).
      error: () => undefined,
    });
  }

  openFull(): void {
    this.ref.close();
    void this.router.navigate([WORKFLOW_ROUTE, 'instances', this.data.instanceId]);
  }

  close(): void {
    this.ref.close();
  }
}
