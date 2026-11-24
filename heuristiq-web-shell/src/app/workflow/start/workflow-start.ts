import { ChangeDetectorRef, Component } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs/operators';
import { NotificationService } from '../../shared/services/notification-service';
import { WorkflowService, parseVariablesJson, workflowErrorMessage } from '../workflow-service';
import { DEFAULT_PROCESS_KEY, WORKFLOW_ROUTE } from '../workflow.constants';

/** Starts an instance of a deployed process (generic-review by default). */
@Component({
  selector: 'app-workflow-start',
  standalone: false,
  templateUrl: './workflow-start.html',
  styleUrl: './workflow-start.scss',
})
export class WorkflowStart {
  processKey = DEFAULT_PROCESS_KEY;
  subjectType = '';
  subjectId = '';
  callbackUrl = '';
  variablesText = '';

  submitted = false;
  invalidJson = false;
  isSaving = false;

  constructor(
    private workflow: WorkflowService,
    private router: Router,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  get isValid(): boolean {
    return !!(this.processKey.trim() && this.subjectType.trim() && this.subjectId.trim() && this.callbackUrl.trim());
  }

  submit(): void {
    this.submitted = true;
    const parsed = parseVariablesJson(this.variablesText);
    this.invalidJson = !parsed.ok;
    if (!this.isValid || !parsed.ok || this.isSaving) return;

    this.isSaving = true;
    this.workflow.startInstance(this.processKey.trim(), {
      subjectType: this.subjectType.trim(),
      subjectId: this.subjectId.trim(),
      callbackUrl: this.callbackUrl.trim(),
      variables: parsed.value,
    })
      .pipe(finalize(() => { this.isSaving = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: (instanceId) => {
          this.notify.success(this.translate.instant('workflow.start.started', { id: instanceId }));
          void this.router.navigate([WORKFLOW_ROUTE, 'instances', instanceId]);
        },
        error: (err) => this.notify.error(workflowErrorMessage(err) ?? this.translate.instant('workflow.start.failed')),
      });
  }
}
