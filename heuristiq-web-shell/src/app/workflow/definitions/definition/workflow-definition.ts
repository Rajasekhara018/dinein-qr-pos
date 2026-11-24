import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs/operators';
import { AppDialogService } from '../../../shared/services/app-dialog-service';
import { AuthService } from '../../../shared/services/auth-service';
import { NotificationService } from '../../../shared/services/notification-service';
import { OrgStructureService } from '../../../organization-structure/org-structure.service';
import { WorkflowService, workflowErrorMessage } from '../../workflow-service';
import { BpmnGraph, DefinitionProposal, DefinitionValidation, MigrationResult, ProcessDefinitionDetail, ProcessDefinitionSummary } from '../../workflow.models';
import { DEFAULT_NOTIFY_CHANNELS, NOTIFY_CHANNEL_KEY_PREFIX, NOTIFY_CHANNELS, NotifyChannel, ASSIGN_MODE_KEY_PREFIX, ASSIGN_MODES, AssignMode, DEFINITION_EDITOR_ROLES, SLA_LIMITS, SLA_MINUTES_PER_UNIT, SLA_UNIT_KEY_PREFIX, SLA_UNITS, SlaUnit, WORKFLOW_ROUTE , DEPARTMENT_PREFIX } from '../../workflow.constants';
import { WORKFLOW_TEMPLATES } from '../workflow-templates';
import { applyNotify, applyReassign, applyReminder, StepDuration, applySla, applyWho, readSteps, StepSla, StepWho } from './bpmn-sla';
import { UamService } from '../../../shared/services/uam-service';
import { UamRole, UamUser } from '../../../shared/models/uam.models';

/**
 * One process definition: its diagram, its BPMN XML, and -- for roles allowed to -- editing the XML,
 * checking it and deploying it as a new version. Deploying changes only instances started later;
 * running instances finish on the version they began with.
 */
@Component({
  selector: 'app-workflow-definition',
  standalone: false,
  templateUrl: './workflow-definition.html',
  styleUrl: './workflow-definition.scss',
})
export class WorkflowDefinition implements OnInit {
  key = '';
  detail: ProcessDefinitionDetail | null = null;
  /** The version being looked at (a number from detail.versions). */
  version: number | null = null;

  xml = '';
  graph: BpmnGraph | null = null;
  validation: DefinitionValidation | null = null;
  /** The diagram shows the edited XML rather than the deployed version. */
  previewing = false;

  isLoading = false;
  loadError = false;
  isBusy = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private workflow: WorkflowService,
    private auth: AuthService,
    private notify: NotificationService,
    private appDialog: AppDialogService,
    private uam: UamService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  /** Changes need a second person's approval (the service's setting), so Deploy becomes "Submit for approval". */
  requireApproval = false;
  proposals: DefinitionProposal[] = [];
  migrationResult: MigrationResult | null = null;

  get myId(): string | null {
    return this.auth.currentUser?.id ?? null;
  }

  private loadSettings(): void {
    this.workflow.definitionSettings().subscribe({
      next: (settings) => { this.requireApproval = settings.requireApproval; this.cdr.detectChanges(); },
      error: () => { this.requireApproval = false; },
    });
  }

  loadProposals(): void {
    if (!this.key || this.isNew || !this.canEdit) return;
    this.workflow.proposals(this.key).subscribe({
      next: (list) => {
        this.proposals = list.slice(0, 10);
        this.loadDeployerNames(list.map((p) => ({ deployedBy: p.proposedBy } as ProcessDefinitionSummary)));
        this.loadDeployerNames(list.filter((p) => p.decidedBy).map((p) => ({ deployedBy: p.decidedBy } as ProcessDefinitionSummary)));
        this.cdr.detectChanges();
      },
      error: () => { this.proposals = []; },
    });
  }

  personName(id: string | null | undefined): string {
    return id ? (this.deployerNames[id] ?? id) : '';
  }

  /** Puts a proposed change into the editor (and its diagram) so it can be read before deciding. */
  viewProposal(proposal: DefinitionProposal): void {
    this.workflow.proposal(proposal.id).subscribe({
      next: (full) => {
        this.xml = full.xml ?? '';
        this.validation = null;
        this.preview();
        this.notify.info(this.translate.instant('workflow.definitions.proposals.loaded'));
      },
      error: (err) => this.notify.error(workflowErrorMessage(err) ?? this.translate.instant('workflow.definitions.actionFailed')),
    });
  }

  approveProposal(proposal: DefinitionProposal): void {
    this.appDialog.confirm({
      title: this.translate.instant('workflow.definitions.proposals.approveTitle'),
      message: this.translate.instant('workflow.definitions.proposals.approveMessage', { key: proposal.processKey }),
      icon: 'bi-check2-circle',
      variant: 'warning',
      confirmText: this.translate.instant('workflow.definitions.proposals.approve'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.run(this.workflow.approveProposal(proposal.id), (deployed: ProcessDefinitionSummary) => {
        this.notify.success(this.translate.instant('workflow.definitions.deployed', { key: deployed.key, version: deployed.version }));
        this.load(null);
        this.loadProposals();
      });
    });
  }

  rejectProposal(proposal: DefinitionProposal): void {
    this.appDialog.prompt({
      title: this.translate.instant('workflow.definitions.proposals.rejectTitle'),
      message: this.translate.instant('workflow.definitions.proposals.rejectMessage'),
      icon: 'bi-x-circle',
      variant: 'danger',
      confirmText: this.translate.instant('workflow.definitions.proposals.reject'),
      field: {
        key: 'reason',
        label: this.translate.instant('workflow.definitions.proposals.reasonLabel'),
        placeholder: this.translate.instant('workflow.definitions.proposals.reasonPlaceholder'),
        type: 'textarea',
        required: true,
      },
    }).subscribe((reason) => {
      if (!reason?.trim()) return;
      this.run(this.workflow.rejectProposal(proposal.id, reason.trim()), () => this.loadProposals());
    });
  }

  withdrawProposal(proposal: DefinitionProposal): void {
    this.run(this.workflow.withdrawProposal(proposal.id), () => this.loadProposals());
  }

  /** Checks which running requests could move to this version, then asks before moving them. */
  migrateRunning(): void {
    this.run(this.workflow.migrateRunning(this.key, true), (check: MigrationResult) => {
      this.migrationResult = check;
      if (check.candidates === 0) {
        this.notify.info(this.translate.instant('workflow.definitions.migrate.nothing'));
        return;
      }
      if (check.movable === 0) {
        this.notify.warning(this.translate.instant('workflow.definitions.migrate.noneMovable', { count: check.candidates }));
        return;
      }
      this.appDialog.confirm({
        title: this.translate.instant('workflow.definitions.migrate.title'),
        message: this.translate.instant('workflow.definitions.migrate.confirm',
          { movable: check.movable, candidates: check.candidates, version: check.latestVersion }),
        icon: 'bi-arrow-right-circle',
        variant: 'warning',
        confirmText: this.translate.instant('workflow.definitions.migrate.move'),
      }).subscribe((confirmed) => {
        if (!confirmed) return;
        this.run(this.workflow.migrateRunning(this.key, false), (done: MigrationResult) => {
          this.migrationResult = done;
          this.notify.success(this.translate.instant('workflow.definitions.migrate.done', { count: done.movable, version: done.latestVersion }));
        });
      });
    });
  }

  ngOnInit(): void {
    this.loadSettings();
    this.loadPeople();
    this.route.paramMap.subscribe((params) => {
      this.key = params.get('key') ?? '';
      if (this.key === 'new') {
        this.loadNew();
        return;
      }
      this.isNew = false;
      this.load(null);
    });
  }

  /** A workflow being created from a template: nothing is deployed until Deploy is clicked. */
  isNew = false;

  private loadNew(): void {
    const query = this.route.snapshot.queryParamMap;
    const template = WORKFLOW_TEMPLATES.find((t) => t.id === query.get('template'));
    const key = query.get('key') ?? '';
    if (!template || !key) {
      this.back();
      return;
    }
    const name = query.get('name') || key;
    this.isNew = true;
    this.proposals = [];
    this.migrationResult = null;
    this.version = null;
    this.xml = template.build(key, name);
    this.validation = null;
    this.detail = {
      definition: { key, name, version: 0, deploymentId: '', suspended: false },
      xml: '',
      versions: [],
    };
    this.preview();
  }

  /** Editing, checking and deploying are for the roles the service allows (it enforces this too). */
  get canEdit(): boolean {
    return DEFINITION_EDITOR_ROLES.includes(this.auth.currentUser?.role ?? '');
  }

  readonly slaUnits = SLA_UNITS;
  readonly slaUnitKeyPrefix = SLA_UNIT_KEY_PREFIX;

  /** Each review step with its SLA, read from the XML being edited, in the order the flow reaches them. */
  get steps(): StepSla[] {
    return readSteps(this.xml).map((step) => this.inChosenUnit(step));
  }

  /** The unit the user picked per step: the XML only stores a duration, which would otherwise show as "1 day" for 24 hours. */
  private chosenUnit: Record<string, SlaUnit> = {};

  private inChosenUnit(step: StepSla): StepSla {
    const chosen = this.chosenUnit[step.id];
    if (!chosen || step.custom) return step;
    if (step.amount === null) return { ...step, unit: chosen };
    const minutes = step.amount * SLA_MINUTES_PER_UNIT[step.unit];
    return minutes % SLA_MINUTES_PER_UNIT[chosen] === 0
      ? { ...step, amount: minutes / SLA_MINUTES_PER_UNIT[chosen], unit: chosen }
      : step;
  }

  /** What the user typed for a step while it is outside the unit's allowed range (not written to the XML). */
  slaDraft: Record<string, string> = {};

  get hasSlaErrors(): boolean {
    return Object.keys(this.slaDraft).length > 0;
  }

  limit(unit: SlaUnit): { min: number; max: number } {
    return SLA_LIMITS[unit];
  }

  /** The number to show: the out-of-range entry the user is still correcting, else the saved SLA. */
  displayAmount(step: StepSla): number | string | null {
    return this.slaDraft[step.id] ?? step.amount;
  }

  displayUnit(step: StepSla): SlaUnit {
    return this.slaDraft[step.id] !== undefined ? (this.chosenUnit[step.id] ?? step.unit) : step.unit;
  }

  setSla(step: StepSla, amount: number | string | null, unit: SlaUnit): void {
    this.chosenUnit[step.id] = unit;
    this.validation = null;
    if (amount === null || amount === '') {
      delete this.slaDraft[step.id];
      this.xml = applySla(this.xml, step.id, null, unit);
      return;
    }
    const value = Number(amount);
    const { min, max } = SLA_LIMITS[unit];
    if (!Number.isInteger(value) || value < min || value > max) {
      // Out of range for this unit: keep what was typed so it can be corrected, and do not change the XML.
      this.slaDraft[step.id] = String(amount);
      return;
    }
    delete this.slaDraft[step.id];
    this.xml = applySla(this.xml, step.id, value, unit);
  }

  /** Roles and users a step can be given to, from UAM (the service accepts nothing else). */
  roleOptions: { value: string; label: string }[] = [];
  userOptions: { value: string; label: string }[] = [];
  departmentOptions: { value: string; label: string }[] = [];
  private readonly orgStructure = inject(OrgStructureService);

  /** Smallest repeat interval the service accepts (anything shorter would flood people). */
  private static readonly MIN_REPEAT_MINUTES = 15;

  private reminderKey(step: StepSla, which: StepDuration): string {
    return `${step.id}:${which}`;
  }

  reminderLimit(which: StepDuration, unit: SlaUnit): { min: number; max: number } {
    const base = SLA_LIMITS[unit];
    return which === 'repeatEvery' && unit === 'MINUTES'
      ? { min: WorkflowDefinition.MIN_REPEAT_MINUTES, max: base.max }
      : base;
  }

  /** The unit shown for a step's reminder or repeat interval: the one the user picked, else the saved one. */
  reminderUnit(step: StepSla, which: StepDuration): SlaUnit {
    return this.chosenUnit[this.reminderKey(step, which)] ?? step[which]?.unit ?? 'HOURS';
  }

  /** The number shown: what the user is still correcting, else the saved value in the shown unit. */
  reminderAmount(step: StepSla, which: StepDuration): number | string | null {
    const draft = this.slaDraft[this.reminderKey(step, which)];
    if (draft !== undefined) return draft;
    const saved = step[which];
    if (!saved) return null;
    const unit = this.reminderUnit(step, which);
    const minutes = saved.amount * SLA_MINUTES_PER_UNIT[saved.unit];
    return minutes % SLA_MINUTES_PER_UNIT[unit] === 0 ? minutes / SLA_MINUTES_PER_UNIT[unit] : saved.amount;
  }

  reminderInvalid(step: StepSla, which: StepDuration): boolean {
    return this.slaDraft[this.reminderKey(step, which)] !== undefined;
  }

  setReminder(step: StepSla, which: StepDuration, amount: number | string | null, unit: SlaUnit): void {
    const key = this.reminderKey(step, which);
    this.chosenUnit[key] = unit;
    this.validation = null;
    if (amount === null || amount === '') {
      delete this.slaDraft[key];
      this.xml = applyReminder(this.xml, step.id, which, null, unit);
      return;
    }
    const value = Number(amount);
    const { min, max } = this.reminderLimit(which, unit);
    if (!Number.isInteger(value) || value < min || value > max) {
      this.slaDraft[key] = String(amount);
      return;
    }
    delete this.slaDraft[key];
    this.xml = applyReminder(this.xml, step.id, which, value, unit);
  }

  setReassign(step: StepSla, userId: string): void {
    this.xml = applyReassign(this.xml, step.id, userId || null);
    this.validation = null;
  }

  /** UAM user id -> display name, for the people who deployed versions. */
  deployerNames: Record<string, string> = {};

  private loadDeployerNames(versions: ProcessDefinitionSummary[]): void {
    const ids = Array.from(new Set(versions.flatMap((v) => [v.deployedBy, v.approvedBy]).filter((id): id is string => !!id)))
      .filter((id) => !(id in this.deployerNames));
    ids.forEach((id) => this.uam.getUser(id).subscribe({
      next: (u) => { this.deployerNames[id] = u.displayName || u.userName || id; this.cdr.detectChanges(); },
      error: () => { this.deployerNames[id] = id; },
    }));
  }

  deployer(version: ProcessDefinitionSummary): string {
    return version.deployedBy ? (this.deployerNames[version.deployedBy] ?? version.deployedBy) : this.translate.instant('workflow.definitions.changeLog.bundled');
  }

  readonly notifyChannels = NOTIFY_CHANNELS;
  readonly notifyChannelKeyPrefix = NOTIFY_CHANNEL_KEY_PREFIX;
  readonly defaultNotifyChannels = DEFAULT_NOTIFY_CHANNELS;

  /** What the step notifies on: its own choice, else the service's default. */
  effectiveChannels(step: StepSla): readonly NotifyChannel[] {
    return step.notifyChannels ?? DEFAULT_NOTIFY_CHANNELS;
  }

  channelOn(step: StepSla, channel: NotifyChannel): boolean {
    return this.effectiveChannels(step).includes(channel);
  }

  /** Turns one channel on or off; turning off the last one switches the step's notifications off. */
  toggleChannel(step: StepSla, channel: NotifyChannel): void {
    const current = this.effectiveChannels(step);
    const next = current.includes(channel) ? current.filter((c) => c !== channel) : [...current, channel];
    this.xml = applyNotify(this.xml, step.id, next);
    this.validation = null;
  }

  /** Back to the service's default channels (removes the step's own choice). */
  useDefaultChannels(step: StepSla): void {
    this.xml = applyNotify(this.xml, step.id, null);
    this.validation = null;
  }

  readonly assignModes = ASSIGN_MODES;
  readonly assignModeKeyPrefix = ASSIGN_MODE_KEY_PREFIX;

  /** The kind picked for a step that has nothing chosen yet (the XML cannot hold an empty choice). */
  private chosenMode: Record<string, AssignMode> = {};

  /** A plain value in candidateGroups is a role code; {@code dept:<code>} is a department of the organization. */
  rolesOf(step: StepSla): string[] {
    return step.candidateGroups.filter((g) => !g.startsWith(DEPARTMENT_PREFIX));
  }

  departmentsOf(step: StepSla): string[] {
    return step.candidateGroups.filter((g) => g.startsWith(DEPARTMENT_PREFIX));
  }

  /** A step is for one kind of assignee: what its XML holds, else what the user just picked. */
  mode(step: StepSla): AssignMode {
    if (step.assignee) return 'PERSON';
    if (step.candidateUsers.length > 0) return 'USERS';
    if (this.departmentsOf(step).length > 0) return 'DEPARTMENTS';
    if (this.rolesOf(step).length > 0) return 'ROLES';
    return this.chosenMode[step.id] ?? 'NONE';
  }

  /** Switching kind clears the other kinds, so a step never mixes them. */
  setMode(step: StepSla, mode: AssignMode): void {
    if (this.mode(step) === mode) return;
    let xml = this.xml;
    xml = applyWho(xml, step.id, 'assignee', []);
    xml = applyWho(xml, step.id, 'candidateUsers', []);
    xml = applyWho(xml, step.id, 'candidateGroups', []);
    this.xml = xml;
    this.chosenMode[step.id] = mode;
    this.validation = null;
  }

  private loadPeople(): void {
    this.uam.list<UamRole>('/roles', 0, 200).subscribe({
      next: (page) => {
        this.roleOptions = page.content.map((r) => ({ value: r.code, label: r.name ? `${r.name} (${r.code})` : r.code }));
        this.cdr.detectChanges();
      },
      error: () => { this.roleOptions = []; },
    });
    this.orgStructure.searchUnits('', 200).subscribe({
      next: (units) => {
        this.departmentOptions = units.map((u) => ({ value: DEPARTMENT_PREFIX + u.code, label: `${u.name} (${u.code})` }));
        this.cdr.detectChanges();
      },
      error: () => { this.departmentOptions = []; },
    });
    this.uam.listUsers(0, 200).subscribe({
      next: (page) => {
        this.userOptions = page.content.map((u: UamUser) => ({
          value: u.id,
          label: u.displayName || u.userName + (u.email ? ` (${u.email})` : ''),
        }));
        this.cdr.detectChanges();
      },
      error: () => { this.userOptions = []; },
    });
  }

  /** What to show for a stored id or code: the person's / role's / department's name when it is known. */
  label(options: { value: string; label: string }[], value: string): string {
    return options.find((o) => o.value === value)?.label ?? (value.startsWith(DEPARTMENT_PREFIX) ? value.slice(DEPARTMENT_PREFIX.length) : value);
  }

  /** Options not already chosen for the step. */
  remaining(options: { value: string; label: string }[], chosen: string[]): { value: string; label: string }[] {
    return options.filter((o) => !chosen.includes(o.value));
  }

  setAssignee(step: StepSla, userId: string): void {
    this.xml = applyWho(this.xml, step.id, 'assignee', userId ? [userId] : []);
    this.validation = null;
  }

  addWho(step: StepSla, field: Exclude<StepWho, 'assignee'>, value: string): void {
    if (!value) return;
    this.xml = applyWho(this.xml, step.id, field, [...step[field], value]);
    this.validation = null;
  }

  removeWho(step: StepSla, field: Exclude<StepWho, 'assignee'>, value: string): void {
    this.xml = applyWho(this.xml, step.id, field, step[field].filter((v) => v !== value));
    this.validation = null;
  }

  trackByStep(_: number, step: StepSla): string {
    return step.id;
  }

  get isLatest(): boolean {
    return !!this.detail && (this.isNew || this.version === this.detail.versions[0]?.version);
  }

  get hasChanges(): boolean {
    return !!this.detail && (this.isNew || this.xml !== this.detail.xml);
  }

  back(): void {
    void this.router.navigate([WORKFLOW_ROUTE, 'definitions']);
  }

  selectVersion(version: number): void {
    this.load(version);
  }

  private load(version: number | null): void {
    this.isLoading = true;
    this.loadError = false;
    this.workflow.definition(this.key, version ?? undefined)
      .pipe(finalize(() => { this.isLoading = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: (detail) => {
          this.detail = detail;
          this.version = detail.definition.version;
          this.loadDeployerNames(detail.versions);
          this.migrationResult = null;
          this.loadProposals();
          this.xml = detail.xml;
          this.slaDraft = {};
          this.chosenMode = {};
          this.validation = null;
          this.previewing = false;
          this.loadGraph();
        },
        error: () => { this.detail = null; this.loadError = true; },
      });
  }

  private loadGraph(): void {
    this.workflow.definitionGraph(this.key, this.version ?? undefined).subscribe({
      next: (graph) => { this.graph = graph; this.cdr.detectChanges(); },
      error: () => { this.graph = null; this.cdr.detectChanges(); },
    });
  }

  discard(): void {
    if (!this.detail) return;
    this.slaDraft = {};
    this.chosenMode = {};
    this.xml = this.detail.xml;
    this.validation = null;
    if (this.previewing) {
      this.previewing = false;
      this.loadGraph();
    }
  }

  preview(): void {
    this.run(this.workflow.previewGraph(this.xml), (graph) => { this.graph = graph; this.previewing = true; });
  }

  check(): void {
    if (this.hasSlaErrors) return;
    this.run(this.workflow.validateDefinition(this.xml), (result) => {
      this.validation = result;
      if (result.valid) this.notify.success(this.translate.instant('workflow.definitions.valid'));
    });
  }

  deploy(): void {
    if (this.hasSlaErrors) return;
    if (!this.hasChanges) {
      this.notify.warning(this.translate.instant('workflow.definitions.unchanged'));
      return;
    }
    // The change note is the version's change log entry, so it is asked for with the confirmation.
    this.appDialog.prompt({
      title: this.translate.instant('workflow.definitions.deployConfirmTitle'),
      message: this.translate.instant('workflow.definitions.deployConfirmMessage', { key: this.key }),
      icon: 'bi-cloud-upload',
      variant: 'warning',
      confirmText: this.translate.instant('workflow.definitions.deployConfirm'),
      field: {
        key: 'note',
        label: this.translate.instant('workflow.definitions.changeNote.label'),
        placeholder: this.translate.instant('workflow.definitions.changeNote.placeholder'),
        type: 'textarea',
        required: true,
      },
    }).subscribe((note) => {
      if (!note?.trim()) return;
      if (this.requireApproval) {
        this.run(this.workflow.propose(this.xml, note.trim()), () => {
          this.notify.success(this.translate.instant('workflow.definitions.proposals.submitted'));
          this.loadProposals();
        });
        return;
      }
      this.run(this.workflow.deployDefinition(this.xml, note.trim()), (deployed: ProcessDefinitionSummary) => {
        this.notify.success(this.translate.instant('workflow.definitions.deployed', { key: deployed.key, version: deployed.version }));
        // The key may have changed if the XML's process id was edited: follow it.
        if (deployed.key !== this.key) {
          void this.router.navigate([WORKFLOW_ROUTE, 'definitions', deployed.key]);
        } else {
          this.load(null);
        }
      });
    });
  }

  /** Runs one editor action: busy flag around it, the service's message on failure. */
  private run<T>(call: import('rxjs').Observable<T>, onOk: (value: T) => void): void {
    this.isBusy = true;
    call.pipe(finalize(() => { this.isBusy = false; this.cdr.detectChanges(); })).subscribe({
      next: onOk,
      error: (err) => this.notify.error(workflowErrorMessage(err) ?? this.translate.instant('workflow.definitions.actionFailed')),
    });
  }
}
