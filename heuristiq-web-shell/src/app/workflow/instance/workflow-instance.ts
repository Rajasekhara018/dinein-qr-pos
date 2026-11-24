import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { watchColumns } from '../../shared/table-columns.helpers';
import { WorkflowService, workflowErrorMessage } from '../workflow-service';
import { WorkflowInstance, WorkflowVariableRow } from '../workflow.models';
import { WORKFLOW_VARIABLE_COLUMNS } from './workflow-instance.constants';
import { CORE_VARIABLES, WORKFLOW_ROUTE } from '../workflow.constants';
import { userIdsOf, variableText } from '../history/workflow-history.helpers';
import { WorkflowPeopleService } from '../history/workflow-people.service';

/**
 * Looks up a process instance by id or reference: running or finished, its history (app-workflow-timeline) and its variables,
 * people shown by name. Route: instances[/:id].
 */
@Component({
  selector: 'app-workflow-instance',
  standalone: false,
  templateUrl: './workflow-instance.html',
  styleUrl: './workflow-instance.scss',
})
export class WorkflowInstanceView implements OnInit, OnDestroy {
  lookupId = '';
  instance: WorkflowInstance | null = null;
  rows: WorkflowVariableRow[] = [];
  columns: HqCommonTableColumn<WorkflowVariableRow>[] = [];
  private columnsSub?: Subscription;
  /** Bumped by Refresh so the timeline reads the history again. */
  refreshCount = 0;
  isLoading = false;
  errorText = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private workflow: WorkflowService,
    private translate: TranslateService,
    private people: WorkflowPeopleService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, WORKFLOW_VARIABLE_COLUMNS, (columns) => (this.columns = columns));
    this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      this.lookupId = id ?? '';
      this.instance = null;
      this.rows = [];
      this.errorText = '';
      if (id) this.load(id);
    });
  }

  ngOnDestroy(): void {
    this.columnsSub?.unsubscribe();
  }

  /** DRV-2026-000042 style: letters, a year, a number. Anything else is taken as an instance id. */
  private static readonly REFERENCE = /^[A-Za-z]{2,}-\d{4}-\d+$/;

  find(): void {
    const id = this.lookupId.trim();
    if (!id) return;
    if (WorkflowInstanceView.REFERENCE.test(id)) {
      this.isLoading = true;
      this.errorText = '';
      this.workflow.instanceIdByReference(id)
        .pipe(finalize(() => { this.isLoading = false; this.cdr.detectChanges(); }))
        .subscribe({
          next: (instanceId) => void this.router.navigate([WORKFLOW_ROUTE, 'instances', instanceId]),
          error: (err) => {
            this.errorText = err?.status === 404
              ? this.translate.instant('workflow.instance.referenceNotFound')
              : workflowErrorMessage(err) ?? this.translate.instant('workflow.instance.loadFailed');
          },
        });
      return;
    }
    // Routing updates the URL (so the lookup is shareable / survives Back) and triggers the load.
    void this.router.navigate([WORKFLOW_ROUTE, 'instances', id]);
  }

  reload(): void {
    if (!this.instance) return;
    this.load(this.instance.instanceId);
    this.refreshCount++;
  }

  private load(id: string): void {
    this.isLoading = true;
    this.errorText = '';
    this.workflow.instance(id)
      .pipe(finalize(() => { this.isLoading = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: (instance) => {
          this.instance = instance;
          const vars = instance.variables ?? {};
          this.rows = this.toRows(vars, {});
          this.people.names(userIdsOf(vars)).subscribe((names) => {
            this.rows = this.toRows(vars, names);
            this.cdr.detectChanges();
          });
        },
        error: (err) => {
          this.instance = null;
          this.rows = [];
          this.errorText = err?.status === 404
            ? this.translate.instant('workflow.instance.notFound')
            : workflowErrorMessage(err) ?? this.translate.instant('workflow.instance.loadFailed');
        },
      });
  }

  /** Core variables first, then the rest alphabetically; people by name, objects as JSON. */
  private toRows(vars: Record<string, unknown>, names: Record<string, string>): WorkflowVariableRow[] {
    return Object.keys(vars)
      .sort((a, b) => Number(CORE_VARIABLES.includes(b)) - Number(CORE_VARIABLES.includes(a)) || a.localeCompare(b))
      .map((key) => ({ key, value: variableText(key, vars[key], names) }));
  }
}
