import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { Subject, takeUntil } from 'rxjs';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamRole } from '../../shared/models/uam.models';
import {
  LOGIN_RUN_PAGE_SIZE, ORG_LOGIN_PRIVILEGES, ORG_PAGE_SIZE,
} from '../org-structure.constants';
import { parseImportError } from '../org-structure.helpers';
import { OrgLoginCandidate, OrgLoginResult, OrgLoginRun, OrgRoleRule, PickerItem } from '../org-structure.models';
import { OrgRoleRulesService } from '../org-role-rules.service';
import { OrgStructureService } from '../org-structure.service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

/** A candidate as the preview table shows it, with its roles and status already turned into text. */
interface CandidateRow {
  employeeCode: string;
  fullName: string;
  email: string;
  roles: string;
  status: string;
}

/** A person who was not given a login in the last run, with the reason in words. */
interface ProblemRow {
  employeeCode: string;
  outcome: string;
  reason: string;
}

/**
 * Role-mapping rules and login creation. A rule says which role goes with a department and/or position; logins are
 * created for employees from those rules after a preview. Roles are never picked per person, and the server
 * recomputes them when it creates a login.
 */
@Component({
  selector: 'app-org-logins',
  standalone: false,
  templateUrl: './org-logins.html',
  styleUrl: './org-logins.scss',
})
export class OrgLogins implements OnInit, OnDestroy {
  readonly privileges = ORG_LOGIN_PRIVILEGES;
  /** The rules and roles come from the same place as in the Roles dialog of the Org builder. */
  private readonly roleSource = inject(OrgRoleRulesService);

  rules: OrgRoleRule[] = [];
  roles: UamRole[] = [];
  ruleColumns: HqCommonTableColumn<OrgRoleRule>[] = [];

  newRuleUnit: PickerItem | null = null;
  newRulePosition: PickerItem | null = null;
  newRuleRole = '';

  filterUnit: PickerItem | null = null;
  candidates: OrgLoginCandidate[] = [];
  candidateRows: CandidateRow[] = [];
  candidateColumns: HqCommonTableColumn<CandidateRow>[] = [];
  hasMoreCandidates = false;
  loadingCandidates = false;
  /** False until the first preview has loaded, so an untouched screen asks for a preview instead of claiming nobody needs a login. */
  previewed = false;

  busy = false;
  running = false;
  totals: { created: number; skipped: number; failed: number } | null = null;
  problems: ProblemRow[] = [];
  problemColumns: HqCommonTableColumn<ProblemRow>[] = [];

  private cancelRequested = false;
  private readonly destroy$ = new Subject<void>();

  constructor(
    private api: OrgStructureService,
    private cdr: ChangeDetectorRef,
    private notify: NotificationService,
    private dialogs: AppDialogService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    const t = (key: string) => this.translate.instant(key);
    this.ruleColumns = [
      { key: 'unitName', label: t('orgStructure.logins.columns.department'), render: (row) => row.unitName ?? t('orgStructure.logins.anyDepartment') },
      { key: 'positionName', label: t('orgStructure.logins.columns.position'), render: (row) => row.positionName ?? t('orgStructure.logins.anyPosition') },
      { key: 'roleCode', label: t('orgStructure.logins.columns.role') },
    ];
    this.candidateColumns = [
      { key: 'employeeCode', label: t('orgStructure.chart.columns.code') },
      { key: 'fullName', label: t('orgStructure.chart.columns.name') },
      { key: 'email', label: t('orgStructure.chart.columns.email') },
      { key: 'roles', label: t('orgStructure.logins.columns.roles') },
      { key: 'status', label: t('orgStructure.logins.columns.status') },
    ];
    this.problemColumns = [
      { key: 'employeeCode', label: t('orgStructure.chart.columns.code') },
      { key: 'outcome', label: t('orgStructure.logins.columns.status') },
      { key: 'reason', label: t('orgStructure.logins.columns.reason') },
    ];
    this.loadRules();
    this.loadRoles();
  }

  ngOnDestroy(): void {
    this.cancelRequested = true;
    this.destroy$.next();
    this.destroy$.complete();
  }

  get readyCount(): number {
    return this.candidates.filter((candidate) => candidate.ready).length;
  }

  get canAddRule(): boolean {
    return !!this.newRuleRole && (!!this.newRuleUnit || !!this.newRulePosition);
  }

  /** The position picker offers the positions of the chosen department's ladder. */
  get positionLadderId(): string | null {
    return this.newRuleUnit?.ladderId ?? null;
  }

  // ---- rules -----------------------------------------------------------------------------------

  addRule(): void {
    if (!this.canAddRule) return;
    this.busy = true;
    this.api.createRoleRule(this.newRuleUnit?.id ?? null, this.newRulePosition?.id ?? null, this.newRuleRole).subscribe({
      next: () => {
        this.busy = false;
        this.newRuleUnit = null;
        this.newRulePosition = null;
        this.newRuleRole = '';
        this.notify.success(this.translate.instant('orgStructure.logins.messages.ruleAdded'));
        this.loadRules();
      },
      error: (err) => this.fail(err, 'orgStructure.logins.messages.ruleFailed'),
    });
  }

  deleteRule(rule: OrgRoleRule): void {
    this.dialogs.confirm({
      title: this.translate.instant('orgStructure.logins.deleteRuleDialog.title'),
      message: this.translate.instant('orgStructure.logins.deleteRuleDialog.message', { role: rule.roleCode }),
      variant: 'danger',
      confirmText: this.translate.instant('orgStructure.logins.deleteRule'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.api.deleteRoleRule(rule.id).subscribe({
        next: () => { this.notify.success(this.translate.instant('orgStructure.logins.messages.ruleDeleted')); this.loadRules(); },
        error: (err) => this.fail(err, 'orgStructure.logins.messages.ruleFailed'),
      });
    });
  }

  // ---- preview ---------------------------------------------------------------------------------

  preview(more = false): void {
    const last = more && this.candidates.length ? this.candidates[this.candidates.length - 1].employeeId : null;
    this.loadingCandidates = true;
    this.api.loginCandidates(this.filterUnit?.id ?? null, last, ORG_PAGE_SIZE).subscribe({
      next: (page) => {
        this.candidates = more ? [...this.candidates, ...page] : page;
        this.candidateRows = this.candidates.map((candidate) => this.toRow(candidate));
        this.hasMoreCandidates = page.length === ORG_PAGE_SIZE;
        this.loadingCandidates = false;
        this.previewed = true;
        this.cdr.detectChanges();
      },
      error: (err) => { this.loadingCandidates = false; this.fail(err, 'orgStructure.logins.messages.loadFailed'); },
    });
  }

  // ---- creating logins ---------------------------------------------------------------------

  /** Creates logins for the ready people in the list on screen. */
  createListed(): void {
    const ids = this.candidates.filter((candidate) => candidate.ready).map((candidate) => candidate.employeeId);
    if (ids.length === 0) return;
    this.confirmThen(ids.length, () => {
      this.beginRun();
      this.stepChosen(ids);
    });
  }

  /** Sends the chosen people in small batches (each login costs a password hash), so no single request outlives a proxy timeout. */
  private stepChosen(ids: string[]): void {
    const batch = ids.slice(0, LOGIN_RUN_PAGE_SIZE);
    const rest = ids.slice(LOGIN_RUN_PAGE_SIZE);
    this.api.createLogins({ employeeIds: batch }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (run) => {
        this.absorb(run);
        if (rest.length > 0 && !this.cancelRequested) {
          this.stepChosen(rest);
        } else {
          this.endRun();
          this.preview();
        }
      },
      error: (err) => { this.endRun(); this.fail(err, 'orgStructure.logins.messages.createFailed'); },
    });
  }

  /** Creates logins for everyone ready in the chosen department (or the whole organization), page after page. */
  createAll(): void {
    this.confirmThen(null, () => {
      this.beginRun();
      this.step(null);
    });
  }

  cancelRun(): void {
    this.cancelRequested = true;
  }

  private step(afterId: string | null): void {
    const request = { unitId: this.filterUnit?.id, afterId: afterId ?? undefined, limit: LOGIN_RUN_PAGE_SIZE };
    this.api.createLogins(request).pipe(takeUntil(this.destroy$)).subscribe({
      next: (run) => {
        this.absorb(run);
        if (run.nextAfterEmployeeId && !this.cancelRequested) {
          this.step(run.nextAfterEmployeeId);
        } else {
          this.endRun();
          this.preview();
        }
      },
      error: (err) => { this.endRun(); this.fail(err, 'orgStructure.logins.messages.createFailed'); },
    });
  }

  private confirmThen(count: number | null, action: () => void): void {
    this.dialogs.confirm({
      title: this.translate.instant('orgStructure.logins.confirmDialog.title'),
      message: this.translate.instant(count === null ? 'orgStructure.logins.confirmDialog.messageAll'
        : 'orgStructure.logins.confirmDialog.message', { count }),
      confirmText: this.translate.instant('orgStructure.logins.createConfirm'),
    }).subscribe((confirmed) => { if (confirmed) action(); });
  }

  private beginRun(): void {
    this.running = true;
    this.cancelRequested = false;
    this.totals = { created: 0, skipped: 0, failed: 0 };
    this.problems = [];
    this.cdr.detectChanges();
  }

  private endRun(): void {
    this.running = false;
    this.cdr.detectChanges();
  }

  private absorb(run: OrgLoginRun): void {
    if (!this.totals) return;
    this.totals = {
      created: this.totals.created + run.created,
      skipped: this.totals.skipped + run.skipped,
      failed: this.totals.failed + run.failed,
    };
    this.problems = [...this.problems, ...run.results.filter((result) => result.outcome !== 'CREATED').map((result) => this.toProblem(result))];
    this.cdr.detectChanges();
  }

  // ---- loading ---------------------------------------------------------------------------------

  private loadRules(): void {
    this.roleSource.rules().subscribe({
      next: (rules) => { this.rules = rules; this.cdr.detectChanges(); },
      error: (err) => this.fail(err, 'orgStructure.logins.messages.loadFailed'),
    });
  }

  private loadRoles(): void {
    this.roleSource.roles().subscribe({
      next: (roles) => { this.roles = roles; this.cdr.detectChanges(); },
      error: () => { /* the dropdown stays empty; rules can still be listed */ },
    });
  }

  // ---- text ------------------------------------------------------------------------------------

  private toRow(candidate: OrgLoginCandidate): CandidateRow {
    return {
      employeeCode: candidate.employeeCode,
      fullName: candidate.fullName,
      email: candidate.email ?? '',
      roles: candidate.roles.join(', '),
      status: candidate.ready ? this.translate.instant('orgStructure.logins.ready') : candidate.issues.map((issue) => this.issueText(issue)).join('; '),
    };
  }

  private toProblem(result: OrgLoginResult): ProblemRow {
    return {
      employeeCode: result.employeeCode,
      outcome: this.translate.instant('orgStructure.logins.outcome.' + result.outcome),
      reason: result.code ? this.issueText(result.code) : '',
    };
  }

  private issueText(raw: string): string {
    const { code, detail } = parseImportError(raw);
    const key = 'orgStructure.logins.issues.' + code;
    const text = this.translate.instant(key, { detail });
    return text === key ? raw : text;
  }

  private fail(err: { error?: { detail?: string } }, fallbackKey: string): void {
    this.busy = false;
    this.notify.error(apiErrorMessage(err, this.translate.instant(fallbackKey)));
    this.cdr.detectChanges();
  }
}
