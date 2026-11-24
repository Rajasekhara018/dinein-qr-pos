import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqDocument, HqPrivilegeAccessService, HqTimezoneService } from 'hq-shared-lib';
import { catchError, defer, forkJoin, map, Observable, of, Subject, Subscription, switchMap, takeUntil, takeWhile, timer } from 'rxjs';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import {
  DEPARTMENT_IMPORT_UPLOAD_INPUT_ID, IMPORT_DMS_CATEGORY, IMPORT_DOWNLOADS, IMPORT_FACT_MAX_LADDERS, IMPORT_FACT_PRIVILEGES, IMPORT_FILE_ACCEPT,
  IMPORT_MAX_SIZE_MB, IMPORT_MODES, IMPORT_NEEDS_LADDERS, IMPORT_NO_LADDER_CODE, IMPORT_NO_LADDER_TEXT, IMPORT_PANEL_ID, IMPORT_POLL_INTERVAL_MS,
  IMPORT_PREVIOUS_STEP, IMPORT_PRIVILEGES_BY_MODE, IMPORT_SETTLED_STATUSES, IMPORT_STEP_ICONS, IMPORT_STEP_KEY, IMPORT_STEP_QUERY_PARAM,
  IMPORT_STEP_SESSION_KEY, IMPORT_TAB_ID_PREFIX, IMPORT_TEXT_PREFIX, IMPORT_UNKNOWN_DEPARTMENT_CODE, IMPORT_UPLOAD_INPUT_ID,
  LADDER_IMPORT_UPLOAD_INPUT_ID, ORG_DEPARTMENT_IMPORT_PRIVILEGES, ORG_LADDER_IMPORT_PRIVILEGES, ORG_PAGE_SIZE, OrgImportPrivileges,
} from '../org-structure.constants';
import { pickLatestImport } from './org-import-loaded.helpers';
import {
  buildImportCsv, classifyUploadProblem, deriveStepState, firstIncompleteStep, importErrorParams, nextImportMode, parseImportError,
} from '../org-structure.helpers';
import {
  OrgDepartmentImportErrorRow, OrgImportBatch, OrgImportErrorRow, OrgImportFacts, OrgImportMode, OrgImportStep, OrgLadder, OrgLadderImportErrorRow,
} from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

/** One rejected row as shown in the report, its errors already turned into readable text. Each kind of row carries its own fields (employee, department, ladder and position). */
interface ErrorReportRow {
  rowNo: number;
  employeeCode?: string;
  fullName?: string;
  code?: string;
  name?: string;
  parentCode?: string;
  ladder?: string;
  position?: string;
  rank?: string;
  managerial?: string;
  problems: string;
}

/** The calls of one import flow (employees or departments): the screen works the same way with either. */
interface ImportFlow {
  create(documentId: string): Observable<OrgImportBatch>;
  recent(limit: number): Observable<OrgImportBatch[]>;
  batch(id: string): Observable<OrgImportBatch>;
  errors(id: string, afterRow: number, limit: number): Observable<ErrorRowFromServer[]>;
  confirm(id: string): Observable<OrgImportBatch>;
  cancel(id: string): Observable<void>;
}

type ErrorRowFromServer = OrgImportErrorRow | OrgDepartmentImportErrorRow | OrgLadderImportErrorRow;

function isDepartmentRow(row: ErrorRowFromServer): row is OrgDepartmentImportErrorRow {
  return 'code' in row;
}

function isLadderRow(row: ErrorRowFromServer): row is OrgLadderImportErrorRow {
  return 'position' in row;
}

/**
 * Bulk setup of the organization in three steps (ladders and job titles, then departments, then employees): upload a CSV, read the validation
 * report, then confirm to apply it. Nothing changes in the live organization until the import is confirmed, and an applying import is followed
 * until it settles. The stepper shows how far each step is; the screen opens on the first step not done (or the one asked for in ?step=), and
 * a step shows only to people who may import it. No step is locked: the data of a later step may already be there.
 */
@Component({
  selector: 'app-org-import',
  standalone: false,
  templateUrl: './org-import.html',
  styleUrl: './org-import.scss',
})
export class OrgImport implements OnInit, OnDestroy {
  private readonly hqTimezone = inject(HqTimezoneService);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });
  private readonly access = inject(HqPrivilegeAccessService);
  /** The organization whose structure this screen shows (the session's). */
  readonly organization = inject(OrganizationContextService);

  readonly departmentPrivileges = ORG_DEPARTMENT_IMPORT_PRIVILEGES;
  readonly ladderPrivileges = ORG_LADDER_IMPORT_PRIVILEGES;
  readonly fileAccept = IMPORT_FILE_ACCEPT;
  readonly dmsCategory = IMPORT_DMS_CATEGORY;
  readonly maxSizeMb = IMPORT_MAX_SIZE_MB;
  readonly uploadInputId = IMPORT_UPLOAD_INPUT_ID;
  readonly departmentUploadInputId = DEPARTMENT_IMPORT_UPLOAD_INPUT_ID;
  readonly ladderUploadInputId = LADDER_IMPORT_UPLOAD_INPUT_ID;
  readonly modes = IMPORT_MODES;
  readonly panelId = IMPORT_PANEL_ID;

  mode: OrgImportMode = 'EMPLOYEES';
  /** The three setup steps with their state; the stepper shows the visible ones. */
  steps: OrgImportStep[] = [];
  readonly stepIcons = IMPORT_STEP_ICONS;
  readonly stepCount = IMPORT_MODES.length;
  /** Bumped whenever what is loaded may have changed; the "Loaded now" section of the open step re-reads when it changes. */
  loadedRefresh = 0;
  recent: OrgImportBatch[] = [];
  selected: OrgImportBatch | null = null;
  errorRows: ErrorReportRow[] = [];
  hasMoreErrors = false;
  /** True once a rejected employee row said its department does not exist: the hint to import the departments first is shown. */
  sawUnknownDepartment = false;
  /** True once a rejected employee row said its department has no position ladder: the hint to give the departments a ladder is shown. */
  sawNoLadder = false;
  /** True once a rejected row (employee: unknown position; department: unknown ladder) shows the ladders and job titles are not loaded: the hint to import them first is shown. */
  sawLaddersNeeded = false;
  busy = false;
  isLoading = false;

  recentColumns: HqCommonTableColumn<OrgImportBatch>[] = [];
  errorColumns: HqCommonTableColumn<ErrorReportRow>[] = [];

  private lastErrorRow = 0;
  private polling?: Subscription;
  private stepRecents: Partial<Record<OrgImportMode, OrgImportBatch[] | null>> = {};
  /** Why the first read of a step's recent imports failed (reported only for the step that opens). */
  private recentErrors: Partial<Record<OrgImportMode, { error?: { detail?: string } }>> = {};
  private stepFacts: OrgImportFacts = { ladders: null, positions: null, topDepartments: null, employees: null };
  private readonly destroy$ = new Subject<void>();

  constructor(
    private api: OrgStructureService,
    private cdr: ChangeDetectorRef,
    private notify: NotificationService,
    private dialogs: AppDialogService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    this.buildColumns();
    this.isLoading = true;
    this.loadSteps(true);
  }

  ngOnDestroy(): void {
    this.polling?.unsubscribe();
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isDepartments(): boolean {
    return this.mode === 'DEPARTMENTS';
  }

  get isLadders(): boolean {
    return this.mode === 'LADDERS';
  }

  get isEmployees(): boolean {
    return this.mode === 'EMPLOYEES';
  }

  /** The privileges of the open tab (the template guards its upload, confirm and cancel with these). */
  get privileges(): OrgImportPrivileges {
    return IMPORT_PRIVILEGES_BY_MODE[this.mode];
  }

  /** The tabs this person may open: employees always, the others only with the right to import them. */
  get visibleModes(): readonly OrgImportMode[] {
    return this.modes.filter((mode) => mode === 'EMPLOYEES' || this.access.can(IMPORT_PRIVILEGES_BY_MODE[mode].create));
  }

  /** The translation key of a text of the open tab (employee texts under orgStructure.import, the others under .departments and .ladders). */
  k(suffix: string): string {
    return IMPORT_TEXT_PREFIX[this.mode] + suffix;
  }

  /** The id of a step's button (the panel is labelled by the open one). */
  tabId(mode: OrgImportMode): string {
    return IMPORT_TAB_ID_PREFIX + mode;
  }

  /** The steps this person may open, in setup order. */
  get visibleSteps(): OrgImportStep[] {
    const visible = this.visibleModes;
    return this.steps.filter((step) => visible.includes(step.mode));
  }

  /** The most recent import of the open step that was applied, is being applied or waits for a decision: the "Latest import" card. */
  get latestImport(): OrgImportBatch | null {
    return pickLatestImport(this.recent);
  }

  /** The privilege that lets someone read the rejected rows of the open step (employee imports have none of their own: the import right itself). */
  get rejectedPrivilege(): string {
    return this.privileges.errorList ?? this.privileges.create;
  }

  /** Opens a batch's report, with its rejected rows. */
  showRejected(batch: OrgImportBatch): void {
    this.open(batch);
  }

  /** Cancels a preview that was never confirmed (after the usual question). */
  cancelPreview(batch: OrgImportBatch): void {
    this.open(batch);
    this.cancel();
  }

  formatWhen(value: string): string {
    return this.hqTimezone.formatLocale(value, 'datetime');
  }

  /** The number of a step (1 to 3). */
  stepNumber(mode: OrgImportMode): number {
    return IMPORT_MODES.indexOf(mode) + 1;
  }

  stepKey(mode: OrgImportMode): string {
    return IMPORT_STEP_KEY[mode];
  }

  /** The step the open one builds on, when it has one. */
  get previousStep(): OrgImportStep | null {
    const previous = IMPORT_PREVIOUS_STEP[this.mode];
    return previous ? this.steps.find((step) => step.mode === previous) ?? null : null;
  }

  /** A gentle warning on steps 2 and 3: the step before has not been started (nothing is locked, the data may already be there). */
  get previousNotStarted(): boolean {
    const previous = this.previousStep;
    return !!previous && previous.known && previous.state === 'NOT_STARTED';
  }

  /** After a confirm: the step to go on with (the next one this person may open), or null on the last step or before a confirm. */
  get nextStep(): OrgImportMode | null {
    const status = this.selected?.status;
    if (status !== 'APPLYING' && status !== 'APPLIED') return null;
    const here = IMPORT_MODES.indexOf(this.mode);
    return this.visibleModes.find((mode) => IMPORT_MODES.indexOf(mode) > here) ?? null;
  }

  statusLabel(status: string): string {
    return this.translate.instant('orgStructure.import.status.' + status);
  }

  get canConfirm(): boolean {
    return !!this.selected && this.selected.validRows > 0 && (this.selected.status === 'VALIDATED' || this.selected.status === 'FAILED');
  }

  get canCancel(): boolean {
    return this.selected?.status === 'VALIDATED';
  }

  /** Employee tab: the file named departments that do not exist yet, so the next step is to import them. */
  get showUnknownDepartmentHint(): boolean {
    return this.isEmployees && this.sawUnknownDepartment && (this.selected?.errorRows ?? 0) > 0;
  }

  /** Employee tab: the file named departments that have no position ladder, so employees cannot be placed in them. */
  get showNoLadderHint(): boolean {
    return this.isEmployees && this.sawNoLadder && (this.selected?.errorRows ?? 0) > 0;
  }

  /** Department tab: how many departments of the open report have no position ladder (0 when it says nothing). */
  get withoutLadderCount(): number {
    return this.isDepartments ? Math.max(0, this.selected?.withoutLadder ?? 0) : 0;
  }

  /** The text of the note about departments without a ladder: one form for a single department, one for any other number. */
  get withoutLadderKey(): string {
    return 'orgStructure.import.departments.' + (this.withoutLadderCount === 1 ? 'withoutLadderOne' : 'withoutLadder');
  }

  /** The ladders and job titles are not loaded yet (an employee row named an unknown position, or a department row an unknown ladder): the next step is the Ladders tab. */
  get showLaddersHint(): boolean {
    return !this.isLadders && this.sawLaddersNeeded && (this.selected?.errorRows ?? 0) > 0;
  }

  /** Employee tab: how many departments got their head from the open report (0 when it says nothing). */
  get headsSetCount(): number {
    return this.isEmployees ? Math.max(0, this.selected?.headsSet ?? 0) : 0;
  }

  /** The text of the note about heads: one form for a single department, one for any other number. */
  get headsSetKey(): string {
    return 'orgStructure.import.' + (this.headsSetCount === 1 ? 'headsSetOne' : 'headsSet');
  }

  /** Ladder tab: how many new ladders the open report created (0 when it says nothing). */
  get laddersCreatedCount(): number {
    return this.isLadders ? Math.max(0, this.selected?.laddersCreated ?? 0) : 0;
  }

  /** Ladder and department tabs: the report has rejected rows but this person may not read them. */
  get errorsNotAllowed(): boolean {
    return !this.isEmployees && (this.selected?.errorRows ?? 0) > 0 && !this.access.can(this.privileges.errorList ?? '');
  }

  /** Opens a tab: the report, the rejected rows and the list of recent imports are those of the new tab. */
  setMode(mode: OrgImportMode): void {
    if (mode === this.mode || !this.visibleModes.includes(mode)) return;
    this.polling?.unsubscribe();
    this.mode = mode;
    this.selected = null;
    this.errorRows = [];
    this.lastErrorRow = 0;
    this.hasMoreErrors = false;
    this.sawUnknownDepartment = false;
    this.sawNoLadder = false;
    this.sawLaddersNeeded = false;
    this.busy = false;
    this.recent = [];
    this.buildColumns();
    this.loadRecent();
    this.refreshFacts();
    this.rememberStep(mode);
    this.cdr.detectChanges();
  }

  /** Arrow keys, Home and End move between the steps (and focus the one that opens). */
  onTabKeydown(event: KeyboardEvent): void {
    const next = nextImportMode(this.visibleModes, this.mode, event.key);
    if (next === this.mode) return;
    event.preventDefault();
    this.setMode(next);
    document.getElementById(this.tabId(next))?.focus();
  }

  /** Downloads the header line of the open step's file, built here in the browser. */
  downloadTemplate(): void {
    const download = IMPORT_DOWNLOADS[this.mode];
    this.saveCsv(buildImportCsv(download.header, []), download.templateFileName);
  }

  /** Downloads a filled example of the open step's file (the same content as docs/samples), built here in the browser. */
  downloadSample(): void {
    const download = IMPORT_DOWNLOADS[this.mode];
    this.saveCsv(buildImportCsv(download.header, download.sampleRows), download.sampleFileName);
  }

  /** The file is now stored in the document store; ask the server to read, validate and stage it. */
  onUploaded(document: HqDocument): void {
    this.busy = true;
    this.flow().create(document.id).subscribe({
      next: (batch) => {
        this.busy = false;
        this.open(batch);
        this.loadRecent();
      },
      error: (err) => this.fail(err, this.k('messages.uploadFailed')),
    });
  }

  /** The file chooser refused the file (too large, not a CSV) or the document store did not take it: say so in the language of the screen. */
  onUploadRejected(message: string): void {
    this.notify.error(this.translate.instant('orgStructure.import.departments.upload.' + classifyUploadProblem(message), { max: this.maxSizeMb }));
  }

  open(batch: OrgImportBatch): void {
    this.selected = batch;
    this.errorRows = [];
    this.lastErrorRow = 0;
    this.hasMoreErrors = false;
    this.sawUnknownDepartment = false;
    this.sawNoLadder = false;
    this.sawLaddersNeeded = false;
    this.loadMoreErrors();
    this.follow(batch);
    this.cdr.detectChanges();
  }

  loadMoreErrors(): void {
    if (!this.selected || this.selected.errorRows === 0 || this.errorsNotAllowed) return;
    this.flow().errors(this.selected.id, this.lastErrorRow, ORG_PAGE_SIZE).subscribe({
      next: (rows) => {
        this.errorRows = [...this.errorRows, ...rows.map((row) => this.toReportRow(row))];
        this.lastErrorRow = rows.length ? rows[rows.length - 1].rowNo : this.lastErrorRow;
        this.hasMoreErrors = rows.length === ORG_PAGE_SIZE;
        this.cdr.detectChanges();
      },
      error: (err) => this.fail(err, this.k('messages.loadFailed')),
    });
  }

  confirm(): void {
    const batch = this.selected;
    if (!batch) return;
    this.dialogs.confirm({
      title: this.translate.instant(this.k('confirmDialog.title')),
      message: this.translate.instant(this.k('confirmDialog.message'), { valid: batch.validRows, rejected: batch.errorRows }),
      confirmText: this.translate.instant('orgStructure.import.confirm'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.busy = true;
      this.flow().confirm(batch.id).subscribe({
        next: (updated) => {
          this.busy = false;
          this.selected = updated;
          this.follow(updated);
          this.notify.success(this.translate.instant(this.k('messages.started')));
          this.loadRecent();
          this.refreshFacts();
          this.cdr.detectChanges();
        },
        error: (err) => this.fail(err, this.k('messages.confirmFailed')),
      });
    });
  }

  cancel(): void {
    const batch = this.selected;
    if (!batch) return;
    this.dialogs.confirm({
      title: this.translate.instant(this.k('cancelDialog.title')),
      message: this.translate.instant(this.k('cancelDialog.message')),
      variant: 'danger',
      confirmText: this.translate.instant('orgStructure.import.cancel'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.flow().cancel(batch.id).subscribe({
        next: () => {
          this.notify.success(this.translate.instant(this.k('messages.cancelled')));
          this.refresh(batch.id);
          this.loadRecent();
          this.refreshFacts();
        },
        error: (err) => this.fail(err, this.k('messages.cancelFailed')),
      });
    });
  }

  /** The calls of the open tab. */
  private flow(): ImportFlow {
    return this.flowFor(this.mode);
  }

  private flowFor(mode: OrgImportMode): ImportFlow {
    if (mode === 'LADDERS') {
      return {
        create: (id) => this.api.createLadderImport(id),
        recent: (limit) => this.api.recentLadderImports(limit),
        batch: (id) => this.api.ladderImportBatch(id),
        errors: (id, after, limit) => this.api.ladderImportErrors(id, after, limit),
        confirm: (id) => this.api.confirmLadderImport(id),
        cancel: (id) => this.api.cancelLadderImport(id),
      };
    }
    if (mode === 'DEPARTMENTS') {
      return {
        create: (id) => this.api.createDepartmentImport(id),
        recent: (limit) => this.api.recentDepartmentImports(limit),
        batch: (id) => this.api.departmentImportBatch(id),
        errors: (id, after, limit) => this.api.departmentImportErrors(id, after, limit),
        confirm: (id) => this.api.confirmDepartmentImport(id),
        cancel: (id) => this.api.cancelDepartmentImport(id),
      };
    }
    return {
      create: (id) => this.api.createImport(id),
      recent: (limit) => this.api.recentImports(limit),
      batch: (id) => this.api.importBatch(id),
      errors: (id, after, limit) => this.api.importErrors(id, after, limit),
      confirm: (id) => this.api.confirmImport(id),
      cancel: (id) => this.api.cancelImport(id),
    };
  }

  private buildColumns(): void {
    const t = (key: string) => this.translate.instant(key);
    this.recentColumns = [
      { key: 'fileName', label: t('orgStructure.import.columns.file') },
      { key: 'status', label: t('orgStructure.import.columns.status'), render: (row) => this.statusLabel(row.status) },
      { key: 'totalRows', label: t('orgStructure.import.columns.rows') },
      { key: 'errorRows', label: t('orgStructure.import.columns.rejected') },
      { key: 'createdAt', label: t('orgStructure.import.columns.uploaded'), render: (row) => this.hqTimezone.formatLocale(row.createdAt, 'datetime') },
    ];
    const ladderColumns = [
      { key: 'rowNo', label: t('orgStructure.import.columns.row') },
      { key: 'ladder', label: t('orgStructure.import.ladders.columns.ladder') },
      { key: 'position', label: t('orgStructure.import.ladders.columns.position') },
      { key: 'rank', label: t('orgStructure.import.ladders.columns.rank') },
      { key: 'managerial', label: t('orgStructure.import.ladders.columns.managerial') },
      { key: 'problems', label: t('orgStructure.import.columns.problems') },
    ];
    this.errorColumns = this.isLadders ? ladderColumns : this.isDepartments
      ? [
        { key: 'rowNo', label: t('orgStructure.import.columns.row') },
        { key: 'code', label: t('orgStructure.chart.columns.code') },
        { key: 'name', label: t('orgStructure.chart.columns.name') },
        { key: 'parentCode', label: t('orgStructure.import.departments.columns.parentCode') },
        { key: 'ladder', label: t('orgStructure.import.departments.columns.ladder') },
        { key: 'problems', label: t('orgStructure.import.columns.problems') },
      ]
      : [
        { key: 'rowNo', label: t('orgStructure.import.columns.row') },
        { key: 'employeeCode', label: t('orgStructure.chart.columns.code') },
        { key: 'fullName', label: t('orgStructure.chart.columns.name') },
        { key: 'problems', label: t('orgStructure.import.columns.problems') },
      ];
  }

  private loadRecent(): void {
    if (!this.isEmployees && !this.access.can(this.privileges.list ?? '')) {
      this.recent = [];
      this.isLoading = false;
      return;
    }
    const tab = this.mode;
    this.isLoading = true;
    this.flow().recent(ORG_PAGE_SIZE).subscribe({
      next: (batches) => {
        if (tab !== this.mode) return;
        this.recent = batches;
        this.stepRecents[tab] = batches;
        this.buildSteps();
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => { this.isLoading = false; this.fail(err, this.k('messages.loadFailed')); },
    });
  }

  /**
   * Reads what each step needs to show its state: the recent imports of every kind this person may list, and the facts about the live organization
   * they may read. Nothing here ever raises an error: what cannot be read is simply left out. With `open`, the screen then opens on a step.
   */
  private loadSteps(open: boolean): void {
    this.recentErrors = {};
    const recents$ = forkJoin(IMPORT_MODES.map((mode) => this.readRecent(mode)));
    forkJoin([recents$, this.readFacts()]).subscribe(([recents, facts]) => {
      IMPORT_MODES.forEach((mode, i) => { this.stepRecents[mode] = recents[i]; });
      this.stepFacts = facts;
      this.buildSteps();
      if (open) this.openInitialStep();
      this.cdr.detectChanges();
    });
  }

  /** Re-reads the facts about the live organization after an import changed it (the open step's own list is re-read by its caller). */
  private refreshFacts(): void {
    this.loadedRefresh++;
    this.readFacts().subscribe((facts) => {
      this.stepFacts = facts;
      this.buildSteps();
      this.cdr.detectChanges();
    });
  }

  private readRecent(mode: OrgImportMode): Observable<OrgImportBatch[] | null> {
    if (mode !== 'EMPLOYEES' && !this.access.can(IMPORT_PRIVILEGES_BY_MODE[mode].list ?? '')) return of(null);
    return defer(() => this.flowFor(mode).recent(ORG_PAGE_SIZE)).pipe(catchError((err) => { this.recentErrors[mode] = err; return of(null); }));
  }

  private readFacts(): Observable<OrgImportFacts> {
    const ladders$ = !this.access.can(IMPORT_FACT_PRIVILEGES.ladders) ? of(null) : defer(() => this.api.ladders()).pipe(
      switchMap((ladders) => this.countPositions(ladders).pipe(map((positions) => ({ ladders: ladders.length, positions })))),
      catchError(() => of(null)),
    );
    const units$ = !this.access.can(IMPORT_FACT_PRIVILEGES.units) ? of(null) : defer(() => this.api.children()).pipe(
      // headcount of a top-level department includes its sub-departments, so the sum is everyone placed in the organization
      map((units) => ({ topDepartments: units.length, employees: units.reduce((sum, unit) => sum + (unit.headcount ?? 0), 0) })),
      catchError(() => of(null)),
    );
    return forkJoin([ladders$, units$]).pipe(map(([l, u]) => ({
      ladders: l?.ladders ?? null, positions: l?.positions ?? null, topDepartments: u?.topDepartments ?? null, employees: u?.employees ?? null,
    })));
  }

  private countPositions(ladders: OrgLadder[]): Observable<number | null> {
    if (!this.access.can(IMPORT_FACT_PRIVILEGES.positions) || ladders.length > IMPORT_FACT_MAX_LADDERS) return of(null);
    if (ladders.length === 0) return of(0);
    return forkJoin(ladders.map((ladder) => this.api.positions(ladder.id).pipe(map((positions) => positions.length)))).pipe(
      map((counts) => counts.reduce((sum, count) => sum + count, 0)),
      catchError(() => of(null)),
    );
  }

  /** The state and facts of the three steps from what was read. */
  private buildSteps(): void {
    const f = this.stepFacts;
    const data: Record<OrgImportMode, { count: number | null; facts: { key: string; count: number | null }[] }> = {
      LADDERS: { count: f.ladders, facts: [{ key: 'ladders', count: f.ladders }, { key: 'positions', count: f.positions }] },
      DEPARTMENTS: { count: f.topDepartments, facts: [{ key: 'departments', count: f.topDepartments }] },
      EMPLOYEES: { count: f.employees, facts: [{ key: 'employees', count: f.employees }] },
    };
    this.steps = IMPORT_MODES.map((mode, i) => {
      const recent = this.stepRecents[mode] ?? null;
      const { count, facts } = data[mode];
      const hasData = count === null ? null : count > 0;
      return {
        mode,
        number: i + 1,
        state: deriveStepState(recent, hasData),
        known: recent !== null || hasData !== null,
        facts: hasData ? facts.filter((fact): fact is { key: string; count: number } => fact.count !== null) : [],
      };
    });
  }

  /** Opens the step asked for in the address, else the one chosen earlier in this session, else the first not done. */
  private openInitialStep(): void {
    const visible = this.visibleModes;
    const byKey = (key: string | null | undefined) => IMPORT_MODES.find((mode) => IMPORT_STEP_KEY[mode] === key);
    const asked = [byKey(this.route?.snapshot.queryParamMap.get(IMPORT_STEP_QUERY_PARAM)), byKey(this.readRememberedStep())]
      .find((mode) => !!mode && visible.includes(mode));
    this.mode = asked ?? firstIncompleteStep(visible, (mode) => this.steps.find((step) => step.mode === mode)?.state ?? 'NOT_STARTED');
    this.buildColumns();
    const read = this.stepRecents[this.mode];
    if (read) {
      this.recent = read;
      this.isLoading = false;
    } else if (this.recentErrors[this.mode]) {
      this.recent = [];
      this.isLoading = false;
      this.fail(this.recentErrors[this.mode] ?? {}, this.k('messages.loadFailed'));
    } else {
      this.recent = [];
      this.isLoading = false;
    }
  }

  private readRememberedStep(): string | null {
    try { return sessionStorage.getItem(IMPORT_STEP_SESSION_KEY); } catch { return null; }
  }

  /** Keeps the user's choice for this session and in the address (so the step can be linked to), without adding a history entry. */
  private rememberStep(mode: OrgImportMode): void {
    try { sessionStorage.setItem(IMPORT_STEP_SESSION_KEY, IMPORT_STEP_KEY[mode]); } catch { /* storage is optional */ }
    void this.router?.navigate([], { relativeTo: this.route, queryParams: { [IMPORT_STEP_QUERY_PARAM]: IMPORT_STEP_KEY[mode] }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  private refresh(id: string): void {
    this.flow().batch(id).subscribe({
      next: (batch) => { this.selected = batch; this.cdr.detectChanges(); },
      error: (err) => this.fail(err, this.k('messages.loadFailed')),
    });
  }

  /** While a batch is being applied in the background, re-read it until it settles (only for a person who may read it). */
  private follow(batch: OrgImportBatch): void {
    this.polling?.unsubscribe();
    if (IMPORT_SETTLED_STATUSES.has(batch.status)) return;
    if (!this.isEmployees && !this.access.can(this.privileges.read ?? '')) return;
    const flow = this.flow();
    this.polling = timer(IMPORT_POLL_INTERVAL_MS, IMPORT_POLL_INTERVAL_MS).pipe(
      switchMap(() => flow.batch(batch.id)),
      takeWhile((latest) => !IMPORT_SETTLED_STATUSES.has(latest.status), true),
      takeUntil(this.destroy$),
    ).subscribe({
      next: (latest) => {
        this.selected = latest;
        if (IMPORT_SETTLED_STATUSES.has(latest.status)) {
          this.loadRecent();
          this.refreshFacts();
        }
        this.cdr.detectChanges();
      },
      error: (err) => this.fail(err, this.k('messages.loadFailed')),
    });
  }

  private toReportRow(row: ErrorRowFromServer): ErrorReportRow {
    const problems = this.problemsText(row.errors);
    if (isLadderRow(row)) {
      return { rowNo: row.rowNo, ladder: row.ladder ?? '', position: row.position ?? '', rank: row.rank == null ? '' : String(row.rank), managerial: row.managerial ?? '', problems };
    }
    if (isDepartmentRow(row)) {
      return { rowNo: row.rowNo, code: row.code ?? '', name: row.name ?? '', parentCode: row.parentCode ?? '', ladder: row.ladder ?? '', problems };
    }
    return { rowNo: row.rowNo, employeeCode: row.employeeCode ?? '', fullName: row.fullName ?? '', problems };
  }

  /** The errors of a row as readable text: each code translated (with its detail), or its raw text when no translation exists. */
  private problemsText(errors: readonly string[]): string {
    const prefix = this.k('errors.');
    const needsLadders = IMPORT_NEEDS_LADDERS[this.mode];
    return errors.map((raw) => {
      const { code, detail } = parseImportError(raw);
      if (needsLadders && (needsLadders.codes.includes(code) || needsLadders.texts.some((text) => raw.toLowerCase().includes(text)))) this.sawLaddersNeeded = true;
      if (this.isEmployees && code === IMPORT_UNKNOWN_DEPARTMENT_CODE) this.sawUnknownDepartment = true;
      if (this.isEmployees && (code === IMPORT_NO_LADDER_CODE || raw.toLowerCase().includes(IMPORT_NO_LADDER_TEXT))) this.sawNoLadder = true;
      const key = prefix + code;
      const text = this.translate.instant(key, importErrorParams(code, detail));
      return text === key ? raw : text;
    }).join('; ');
  }

  private saveCsv(text: string, fileName: string): void {
    const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  }

  private fail(err: { error?: { detail?: string } }, fallbackKey: string): void {
    this.busy = false;
    this.notify.error(apiErrorMessage(err, this.translate.instant(fallbackKey)));
    this.cdr.detectChanges();
  }
}
