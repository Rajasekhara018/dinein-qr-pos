import { DEPARTMENT_PREFIX } from '../workflow.constants';
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { HqCommonTableColumn, HqFileDrop } from 'hq-shared-lib';
import { watchColumns } from '../../shared/table-columns.helpers';
import { WORKFLOW_TASK_COLUMNS } from './workflow-tasks.constants';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs/operators';
import { AuthService } from '../../shared/services/auth-service';
import { NotificationService } from '../../shared/services/notification-service';
import { WorkflowService, workflowErrorMessage } from '../workflow-service';
import { TaskView, WorkflowTask } from '../workflow.models';
import { MAX_ATTACHMENT_BYTES, DEFAULT_TASK_VIEW, TASK_VIEW_KEY_PREFIX, TASK_VIEWS, TASKS_PAGE_SIZE, WORKFLOW_ROUTE } from '../workflow.constants';
import { CompleteTaskDialog, CompleteTaskDialogData, CompleteTaskDialogResult } from './complete-task-dialog/complete-task-dialog';
import { AddCommentDialog, AddCommentDialogData } from './add-comment-dialog/add-comment-dialog';
import { ReassignDialog, ReassignDialogData } from './reassign-dialog/reassign-dialog';

/** Open tasks of every process instance, with the Complete action. */
@Component({
  selector: 'app-workflow-tasks',
  standalone: false,
  templateUrl: './workflow-tasks.html',
  styleUrl: './workflow-tasks.scss',
})
export class WorkflowTasks implements OnInit, OnDestroy {
  tasks: WorkflowTask[] = [];
  columns: HqCommonTableColumn<WorkflowTask>[] = [];
  private columnsSub?: Subscription;
  page = 0;
  total = 0;
  readonly pageSize = TASKS_PAGE_SIZE;
  isLoading = false;
  hasError = false;
  completingId: string | null = null;
  claimingId: string | null = null;

  readonly views = TASK_VIEWS;
  readonly viewKeyPrefix = TASK_VIEW_KEY_PREFIX;
  view: TaskView = DEFAULT_TASK_VIEW;

  processKey = '';
  subjectType = '';
  q = '';
  step = '';
  createdFrom = '';
  createdTo = '';

  constructor(
    private workflow: WorkflowService,
    private dialog: MatDialog,
    private router: Router,
    private notify: NotificationService,
    private auth: AuthService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, WORKFLOW_TASK_COLUMNS, (columns) => (this.columns = columns));
    this.load();
  }

  ngOnDestroy(): void {
    this.columnsSub?.unsubscribe();
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    this.workflow.tasks(this.processKey.trim(), this.subjectType.trim(), this.view, this.page, this.pageSize, {
      q: this.q.trim(), step: this.step.trim(), createdFrom: this.createdFrom, createdTo: this.createdTo,
    })
      .pipe(finalize(() => { this.isLoading = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: ({ items, total }) => {
          // The last page can vanish when tasks are completed: step back to the new last page.
          if (items.length === 0 && this.page > 0 && total > 0) {
            this.page = Math.max(Math.ceil(total / this.pageSize) - 1, 0);
            this.load();
            return;
          }
          this.tasks = items;
          this.total = total;
        },
        error: () => { this.tasks = []; this.total = 0; this.hasError = true; },
      });
  }

  /** Filters change what the list holds, so they start again from the first page. */
  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  get totalPages(): number {
    return Math.max(Math.ceil(this.total / this.pageSize), 1);
  }

  goToPage(delta: number): void {
    const next = this.page + delta;
    if (next < 0 || next >= this.totalPages) return;
    this.page = next;
    this.load();
  }

  clearFilters(): void {
    this.processKey = '';
    this.subjectType = '';
    this.q = '';
    this.step = '';
    this.createdFrom = '';
    this.createdTo = '';
    this.view = DEFAULT_TASK_VIEW;
    this.applyFilters();
  }

  /** What the task is about: the subject the instance was started for. */
  subjectOf(task: WorkflowTask): string {
    const type = task.variables?.['subjectType'];
    const id = task.variables?.['subjectId'];
    return [type, id].filter((v) => v !== undefined && v !== null && v !== '').join(' · ') || '—';
  }

  /** Who has the task: the assignee, or nobody yet. */
  isMine(task: WorkflowTask): boolean {
    return !!task.assignee && task.assignee === this.auth.currentUser?.id;
  }

  /** A task you took from an offered pool can be given back to it. */
  canRelease(task: WorkflowTask): boolean {
    return this.isMine(task) && ((task.candidateUsers?.length ?? 0) > 0 || (task.candidateGroups?.length ?? 0) > 0);
  }

  /** Whoever has the task can hand it on, and so can a super admin. */
  canReassign(task: WorkflowTask): boolean {
    return !!task.assignee && (this.isMine(task) || (this.auth.currentUser?.role ?? '') === 'SUPER_ADMIN');
  }

  release(task: WorkflowTask): void {
    this.claimingId = task.taskId;
    this.workflow.releaseTask(task.taskId)
      .pipe(finalize(() => { this.claimingId = null; this.cdr.detectChanges(); }))
      .subscribe({
        next: () => {
          this.notify.success(this.translate.instant('workflow.tasks.released'));
          this.load();
        },
        error: (err) => this.notify.error(workflowErrorMessage(err) ?? this.translate.instant('workflow.tasks.releaseFailed')),
      });
  }

  openReassign(task: WorkflowTask): void {
    const data: ReassignDialogData = { task };
    this.dialog.open(ReassignDialog, { data, width: '760px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((userId: string | null | undefined) => {
        if (!userId) return;
        this.claimingId = task.taskId;
        this.workflow.reassignTask(task.taskId, userId)
          .pipe(finalize(() => { this.claimingId = null; this.cdr.detectChanges(); }))
          .subscribe({
            next: () => {
              this.notify.success(this.translate.instant('workflow.tasks.reassigned'));
              this.load();
            },
            error: (err) => this.notify.error(workflowErrorMessage(err) ?? this.translate.instant('workflow.tasks.reassignFailed')),
          });
      });
  }

  /** The users and roles an unassigned task is offered to, for the Assigned-to column. */
  offeredUsers(task: WorkflowTask): string {
    return (task.candidateUsers ?? []).join(', ');
  }

  offeredRoles(task: WorkflowTask): string {
    return (task.candidateGroups ?? []).filter((g) => !g.startsWith(DEPARTMENT_PREFIX)).join(', ');
  }

  /** The departments a step is offered to, by code: the engine stores {@code dept:<organization>:<code>}. */
  offeredDepartments(task: WorkflowTask): string {
    return (task.candidateGroups ?? []).filter((g) => g.startsWith(DEPARTMENT_PREFIX))
      .map((g) => g.slice(g.lastIndexOf(':') + 1)).join(', ');
  }

  claim(task: WorkflowTask): void {
    this.claimingId = task.taskId;
    this.workflow.claimTask(task.taskId)
      .pipe(finalize(() => { this.claimingId = null; this.cdr.detectChanges(); }))
      .subscribe({
        next: () => {
          this.notify.success(this.translate.instant('workflow.tasks.claimed'));
          this.load();
        },
        error: (err) => this.notify.error(workflowErrorMessage(err) ?? this.translate.instant('workflow.tasks.claimFailed')),
      });
  }

  openComplete(task: WorkflowTask): void {
    const data: CompleteTaskDialogData = { task };
    this.dialog.open(CompleteTaskDialog, { data, width: '760px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result: CompleteTaskDialogResult | null | undefined) => {
        if (result) this.complete(task, result);
      });
  }

  /** The task a file is being attached to while the file picker is open. */
  private attachTarget: WorkflowTask | null = null;

  /** Remembers the row and opens the file picker (a hidden hq-file-drop) for its attachment. */
  pickAttachment(task: WorkflowTask, picker: Pick<HqFileDrop, 'choose'>): void {
    this.attachTarget = task;
    picker.choose();
  }

  /** The attachment picked for the remembered row: checked for size and uploaded. */
  onAttachmentChosen(files: File[]): void {
    const file = files[0];
    const task = this.attachTarget;
    this.attachTarget = null;
    if (!file || !task) return;
    if (file.size > MAX_ATTACHMENT_BYTES) {
      this.notify.error(this.translate.instant('workflow.tasks.attachTooLarge'));
      return;
    }
    this.claimingId = task.taskId;
    this.workflow.uploadAttachment(task.taskId, file)
      .pipe(finalize(() => { this.claimingId = null; this.cdr.detectChanges(); }))
      .subscribe({
        next: () => this.notify.success(this.translate.instant('workflow.tasks.attached')),
        error: (err) => this.notify.error(workflowErrorMessage(err) ?? this.translate.instant('workflow.tasks.attachFailed')),
      });
  }

  openComment(task: WorkflowTask): void {
    const data: AddCommentDialogData = { task };
    this.dialog.open(AddCommentDialog, { data, width: '760px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((text: string | null | undefined) => {
        if (!text) return;
        this.workflow.addComment(task.taskId, text).subscribe({
          next: () => this.notify.success(this.translate.instant('workflow.tasks.commentAdded')),
          error: (err) => this.notify.error(workflowErrorMessage(err) ?? this.translate.instant('workflow.tasks.commentFailed')),
        });
      });
  }

  private complete(task: WorkflowTask, result: CompleteTaskDialogResult): void {
    this.completingId = task.taskId;
    this.workflow.completeTask(task.taskId, result.variables, result.comment)
      .pipe(finalize(() => { this.completingId = null; this.cdr.detectChanges(); }))
      .subscribe({
        next: () => {
          this.notify.success(this.translate.instant('workflow.tasks.completed'));
          this.load();
        },
        error: (err) => this.notify.error(workflowErrorMessage(err) ?? this.translate.instant('workflow.tasks.completeFailed')),
      });
  }

  viewInstance(task: WorkflowTask): void {
    void this.router.navigate([WORKFLOW_ROUTE, 'instances', task.processInstanceId]);
  }

  trackByTask(_: number, task: WorkflowTask): string {
    return task.taskId;
  }
}
