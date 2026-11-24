import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { Subscription } from 'rxjs';
import { watchColumns } from '../../shared/table-columns.helpers';
import {
  WORKFLOW_REPORT_OPEN_COLUMNS, WORKFLOW_REPORT_REVIEWER_COLUMNS, WORKFLOW_REPORT_STEP_COLUMNS,
  WorkflowReportOpenStep, WorkflowReportReviewer, WorkflowReportStep,
} from './workflow-reports.constants';
import { TranslateService } from '@ngx-translate/core';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize, map } from 'rxjs/operators';
import { UamService } from '../../shared/services/uam-service';
import { WorkflowService } from '../workflow-service';
import { WorkflowReport } from '../workflow.models';
import { csvRow, downloadCsv } from './csv';

/** How the approval workflows are doing: volumes, step durations, missed SLAs, reviewer workload, what is waiting. */
@Component({
  selector: 'app-workflow-reports',
  standalone: false,
  templateUrl: './workflow-reports.html',
  styleUrl: './workflow-reports.scss',
})
export class WorkflowReports implements OnInit, OnDestroy {
  stepColumns: HqCommonTableColumn<WorkflowReportStep>[] = [];
  reviewerColumns: HqCommonTableColumn<WorkflowReportReviewer>[] = [];
  openColumns: HqCommonTableColumn<WorkflowReportOpenStep>[] = [];
  private columnSubs: Subscription[] = [];

  from = '';
  to = '';
  processKey = '';

  report: WorkflowReport | null = null;
  names: Record<string, string> = {};
  isLoading = false;
  hasError = false;

  constructor(
    private workflow: WorkflowService,
    private uam: UamService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.columnSubs = [
      watchColumns(this.translate, WORKFLOW_REPORT_STEP_COLUMNS, (columns) => (this.stepColumns = columns)),
      watchColumns(this.translate, WORKFLOW_REPORT_REVIEWER_COLUMNS, (columns) => (this.reviewerColumns = columns)),
      watchColumns(this.translate, WORKFLOW_REPORT_OPEN_COLUMNS, (columns) => (this.openColumns = columns)),
    ];
    // The last 30 days, ending today.
    const today = new Date();
    const start = new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000);
    this.to = this.isoDay(today);
    this.from = this.isoDay(start);
    this.load();
  }

  ngOnDestroy(): void {
    this.columnSubs.forEach((sub) => sub.unsubscribe());
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    this.workflow.report(this.from, this.to, this.processKey.trim())
      .pipe(finalize(() => { this.isLoading = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: (report) => {
          this.report = report;
          this.loadNames(report);
        },
        error: () => { this.report = null; this.hasError = true; },
      });
  }

  private loadNames(report: WorkflowReport): void {
    const ids = Array.from(new Set(report.reviewers.map((r) => r.userId).filter((id): id is string => !!id)));
    if (ids.length === 0) return;
    forkJoin(ids.map((id) => this.uam.getUser(id).pipe(
      map((u) => [id, u.displayName || u.userName || id] as const),
      catchError(() => of([id, id] as const)),
    ))).subscribe((pairs) => {
      this.names = Object.fromEntries(pairs);
      this.cdr.detectChanges();
    });
  }

  person(userId: string | null): string {
    return userId ? (this.names[userId] ?? userId) : this.translate.instant('workflow.reports.unknownPerson');
  }

  /** "1d 2h", "3h 5m", "40m", "<1m": how long a step took or has been waiting. */
  duration(ms: number): string {
    if (!ms || ms < 60_000) return ms > 0 ? '<1' + this.unit('m') : '—';
    const minutes = Math.floor(ms / 60_000);
    const d = Math.floor(minutes / 1440);
    const h = Math.floor((minutes % 1440) / 60);
    const m = minutes % 60;
    if (d > 0) return `${d}${this.unit('d')} ${h}${this.unit('h')}`;
    if (h > 0) return `${h}${this.unit('h')} ${m}${this.unit('m')}`;
    return `${m}${this.unit('m')}`;
  }

  /** The share of SLA-bound tasks that finished late, as a whole percentage; null when none had an SLA. */
  breachRate(withSla: number, breached: number): number | null {
    return withSla > 0 ? Math.round((breached / withSla) * 100) : null;
  }

  /** Saves what is on screen as a CSV file: totals, steps, people and what is waiting, with people shown by name. */
  exportCsv(): void {
    const r = this.report;
    if (!r) return;
    const t = (key: string) => this.translate.instant(key);
    const minutes = (ms: number) => Math.round(ms / 60_000);
    const lines: string[] = [
      csvRow([t('workflow.reports.export.period'), this.from, this.to, this.processKey.trim()]),
      '',
      csvRow([t('workflow.reports.cards.started'), t('workflow.reports.cards.finished'), t('workflow.reports.cards.approved'),
        t('workflow.reports.cards.rejected'), t('workflow.reports.cards.returned'), t('workflow.reports.cards.open'), t('workflow.reports.cards.overdue')]),
      csvRow([r.totals.started, r.totals.finished, r.totals.approved, r.totals.rejected, r.totals.returned, r.totals.openTasks, r.totals.overdueOpenTasks]),
      '',
      csvRow([t('workflow.reports.steps.title')]),
      csvRow([t('workflow.reports.steps.step'), t('workflow.tasks.columns.process'), t('workflow.reports.steps.completed'),
        t('workflow.reports.export.avgMinutes'), t('workflow.reports.export.maxMinutes'), t('workflow.reports.steps.sla'), t('workflow.reports.steps.late')]),
      ...r.steps.map((s) => csvRow([s.taskName || s.taskKey, s.processKey, s.completed, minutes(s.avgDurationMillis), minutes(s.maxDurationMillis), s.withSla, s.breached])),
      '',
      csvRow([t('workflow.reports.people.title')]),
      csvRow([t('workflow.reports.people.person'), t('workflow.reports.steps.completed'), t('workflow.reports.cards.approved'),
        t('workflow.reports.cards.rejected'), t('workflow.reports.export.avgMinutes'), t('workflow.reports.people.late')]),
      ...r.reviewers.map((p) => csvRow([this.person(p.userId), p.completed, p.approved, p.rejected, minutes(p.avgDurationMillis), p.breached])),
      '',
      csvRow([t('workflow.reports.waiting.title')]),
      csvRow([t('workflow.reports.steps.step'), t('workflow.tasks.columns.process'), t('workflow.reports.waiting.open'),
        t('workflow.reports.waiting.overdue'), t('workflow.reports.export.oldestMinutes')]),
      ...r.open.map((o) => csvRow([o.taskName || o.taskKey, o.processKey, o.open, o.overdue, minutes(o.oldestAgeMillis)])),
    ];
    downloadCsv(`workflow-report-${this.from || 'start'}_${this.to || 'today'}.csv`, lines);
  }

  trackByIndex(index: number): number {
    return index;
  }

  private unit(key: string): string {
    return this.translate.instant(`workflow.timeline.units.${key}`);
  }

  private isoDay(date: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }
}
