import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { catchError, forkJoin, of } from 'rxjs';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import {
  ChartPerson, DraftNode, DraftRow, buildDraftTree, chartPeople, flattenTree, makeUnitCode, takenCodes, undoChangeIds,
} from '../org-draft-tree.helpers';
import {
  CHART_HEIGHT_PX, CHART_MAX_LEVELS, CHART_MAX_PEOPLE_UNITS, CHART_PEOPLE_PER_UNIT, ORG_LOGIN_PRIVILEGES, ORG_PRIVILEGES, ORG_ROLES_DIALOG_TITLE_ID,
  ORG_ROLES_DIALOG_WIDTH, PAYLOAD_LABELS_KEY,
  TREE_INDENT_REM, TREE_ROOT_KEY,
} from '../org-structure.constants';
import { OrgChange, OrgPerson, OrgPosition, OrgUnit } from '../org-structure.models';
import { matchPositionForMove } from '../org-structure.helpers';
import { OrgStructureService } from '../org-structure.service';
import { OrgDeptRolesDialog, OrgDeptRolesDialogData } from '../org-dept-roles-dialog/org-dept-roles-dialog';
import { ChartNodeData, toChartNodes } from '../org-chart-view/org-chart-view.helpers';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

/** What the inline editor under a department is doing; `unitId` is empty for "add a top-level department". */
interface Editing {
  mode: 'add' | 'rename' | 'move' | 'person';
  unitId: string;
}

/**
 * The departments of the organization as a tree you edit in place: add a department (or a sub-department),
 * rename, move or archive it, and see exactly what the organization will look like once the draft is published.
 * Every action is saved to the open draft as one change; nothing goes live until the draft is published.
 */
@Component({
  selector: 'app-org-dept-tree',
  standalone: false,
  templateUrl: './org-dept-tree.html',
  styleUrl: './org-dept-tree.scss',
})
export class OrgDeptTree implements OnInit, OnChanges {
  @Input({ required: true }) draftId!: string;
  /** The draft's changes; the tree is these laid over the live departments. */
  @Input() changes: OrgChange[] = [];
  @Output() changesChange = new EventEmitter<OrgChange[]>();

  readonly privileges = ORG_PRIVILEGES;
  readonly loginPrivileges = ORG_LOGIN_PRIVILEGES;
  readonly topLevel = TREE_ROOT_KEY;
  readonly indentRem = TREE_INDENT_REM;
  readonly chartHeight = CHART_HEIGHT_PX;

  /** The same departments as an indented list (to edit) or as a drawn chart (to see the result). */
  view: 'list' | 'chart' = 'list';
  rows: DraftRow[] = [];
  isLoading = false;
  hasError = false;
  busy = false;

  editing: Editing | null = null;
  nameDraft = '';
  /** The department chosen in the move box; empty until one is picked. */
  moveTargetId = '';

  /** Live departments fetched so far, by parent. */
  private readonly live = new Map<string, OrgUnit[]>();
  private readonly expanded = new Set<string>();
  /** People already in each department, fetched when the chart is opened. */
  private readonly livePeople = new Map<string, OrgPerson[]>();
  private chartPeopleByUnit = new Map<string, ChartPerson[]>();
  /** Departments whose people panel is open in the list. */
  private readonly peopleOpen = new Set<string>();
  /** The boxes the chart draws; rebuilt with the tree. */
  chartNodes: ChartNodeData[] = [];
  private roots: DraftNode[] = [];

  constructor(
    private api: OrgStructureService,
    private cdr: ChangeDetectorRef,
    private notify: NotificationService,
    private dialogs: AppDialogService,
    private translate: TranslateService,
    private matDialog: MatDialog,
  ) {}

  /** The Roles action is for departments that exist live: one added in the draft has no id the rules could point at yet, and an archived one takes no more roles. */
  canManageRoles(node: DraftNode): boolean {
    return node.state === 'live' || node.state === 'renamed' || node.state === 'moved';
  }

  /** Opens the dialog with the roles given to this department; focus goes back to the button that opened it. */
  openRoles(node: DraftNode, event: Event): void {
    const trigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    this.matDialog
      .open<OrgDeptRolesDialog, OrgDeptRolesDialogData, boolean>(OrgDeptRolesDialog, {
        data: { unitId: node.id, unitName: node.name, headcount: node.headcount },
        width: ORG_ROLES_DIALOG_WIDTH,
        maxWidth: '94vw',
        ariaLabelledBy: ORG_ROLES_DIALOG_TITLE_ID,
        restoreFocus: true,
      })
      .afterClosed()
      .subscribe(() => trigger?.focus());
  }

  ngOnInit(): void {
    this.isLoading = true;
    this.api.children().subscribe({
      next: (units) => {
        this.live.set(TREE_ROOT_KEY, units);
        this.isLoading = false;
        this.rebuild();
      },
      error: () => {
        this.isLoading = false;
        this.hasError = true;
        this.cdr.detectChanges();
      },
    });
  }

  ngOnChanges(): void {
    this.rebuild();
  }

  /**
   * Where a department can be moved to: every department loaded so far (including ones created in the draft),
   * leaving out itself, what is under it and archived ones. A plain list, so nothing pops over the page.
   */
  moveOptions(node: DraftNode): { id: string; label: string }[] {
    const options: { id: string; label: string }[] = [];
    const walk = (list: DraftNode[], depth: number) => {
      for (const candidate of list) {
        if (candidate.id === node.id || candidate.state === 'archived') continue;
        options.push({ id: candidate.id, label: `${'  '.repeat(depth)}${depth ? '└ ' : ''}${candidate.name}` });
        walk(candidate.children, depth + 1);
      }
    };
    walk(this.roots, 0);
    return options;
  }

  // ---- people in a department ----------------------------------------------------------------

  isPeopleOpen(unitId: string): boolean {
    return this.peopleOpen.has(unitId);
  }

  /** Who the department will have once the draft is published, the head first (the head is marked). */
  peopleOf(node: DraftNode): ChartPerson[] {
    return this.chartPeopleByUnit.get(node.id) ?? [];
  }

  /** Opens or closes the department's people, fetching who is already in it the first time. */
  togglePeople(node: DraftNode): void {
    if (this.peopleOpen.delete(node.id)) {
      this.cdr.detectChanges();
      return;
    }
    this.peopleOpen.add(node.id);
    if (node.state === 'new' || this.livePeople.has(node.id)) {
      this.rebuild();
      return;
    }
    this.api.people(node.id, null, CHART_PEOPLE_PER_UNIT).pipe(catchError(() => of([] as OrgPerson[]))).subscribe((people) => {
      this.livePeople.set(node.id, people);
      this.rebuild();
    });
  }

  // ---- list / chart ------------------------------------------------------------------------

  /** The chart shows everything, so every level that has not been fetched yet is loaded first. */
  showView(view: 'list' | 'chart'): void {
    if (view === 'list') {
      this.view = 'list';
      return;
    }
    this.isLoading = true;
    this.loadRemainingLevels(CHART_MAX_LEVELS);
  }

  /** Fetches who is already in each department (only the ones that exist live), then shows the chart. */
  private loadChartPeople(): void {
    const unitIds: string[] = [];
    const walk = (list: DraftNode[]) => list.forEach((node) => {
      if (node.state !== 'new' && !this.livePeople.has(node.id)) unitIds.push(node.id);
      walk(node.children);
    });
    walk(this.roots);
    const asked = unitIds.slice(0, CHART_MAX_PEOPLE_UNITS);
    const lookups = asked.map((id) => this.api.people(id, null, CHART_PEOPLE_PER_UNIT).pipe(catchError(() => of([] as OrgPerson[]))));
    (lookups.length ? forkJoin(lookups) : of([] as OrgPerson[][])).subscribe((lists) => {
      lists.forEach((people, i) => this.livePeople.set(asked[i], people));
      this.isLoading = false;
      this.view = 'chart';
      this.rebuild();
    });
  }

  private loadRemainingLevels(levelsLeft: number): void {
    const pending: string[] = [];
    const walk = (list: DraftNode[]) => list.forEach((node) => {
      if (node.needsLoad) pending.push(node.id);
      walk(node.children);
    });
    walk(this.roots);
    if (!pending.length || levelsLeft <= 0) {
      this.loadChartPeople();
      return;
    }
    forkJoin(pending.map((id) => this.api.children(id))).subscribe({
      next: (lists) => {
        pending.forEach((id, i) => this.live.set(id, lists[i]));
        this.roots = buildDraftTree(this.live, this.changes);
        this.loadRemainingLevels(levelsLeft - 1);
      },
      error: (err) => {
        this.isLoading = false;
        this.fail(err, 'orgStructure.tree.loadFailed');
      },
    });
  }

  // ---- expanding ---------------------------------------------------------------------------

  toggle(node: DraftNode): void {
    if (this.expanded.has(node.id)) {
      this.expanded.delete(node.id);
      this.rebuild();
      return;
    }
    this.expanded.add(node.id);
    if (!node.needsLoad) {
      this.rebuild();
      return;
    }
    this.api.children(node.id).subscribe({
      next: (units) => {
        this.live.set(node.id, units);
        this.rebuild();
      },
      error: (err) => {
        this.expanded.delete(node.id);
        this.fail(err, 'orgStructure.tree.loadFailed');
      },
    });
  }

  isExpanded(node: DraftNode): boolean {
    return this.expanded.has(node.id);
  }

  canExpand(node: DraftNode): boolean {
    return node.children.length > 0 || node.needsLoad;
  }

  // ---- inline editor -----------------------------------------------------------------------

  startAdd(parentId: string): void {
    this.editing = { mode: 'add', unitId: parentId };
    this.nameDraft = '';
  }

  startRename(node: DraftNode): void {
    this.editing = { mode: 'rename', unitId: node.id };
    this.nameDraft = node.name;
  }

  startPerson(node: DraftNode): void {
    this.editing = { mode: 'person', unitId: node.id };
  }

  /** A person was added (or the editor was closed): the draft's changes may have grown. */
  onChangesFromEditor(changes: OrgChange[]): void {
    this.changesChange.emit(changes);
  }

  startMove(node: DraftNode): void {
    this.editing = { mode: 'move', unitId: node.id };
    this.moveTargetId = '';
  }

  cancelEdit(): void {
    this.editing = null;
    this.nameDraft = '';
    this.moveTargetId = '';
  }

  isEditing(mode: Editing['mode'], unitId: string): boolean {
    return this.editing?.mode === mode && this.editing.unitId === unitId;
  }

  /** The code a new department will get, shown so nothing is created that the person cannot see. */
  get codePreview(): string {
    return this.nameDraft.trim() ? makeUnitCode(this.nameDraft, takenCodes(this.live, this.changes)) : '';
  }

  // ---- actions (each one change in the draft) ------------------------------------------------

  confirmAdd(): void {
    const name = this.nameDraft.trim();
    if (!this.editing || this.editing.mode !== 'add' || !name) return;
    const parentId = this.editing.unitId;
    const parent = this.find(parentId);
    const payload: OrgChange['payload'] = { code: this.codePreview, name };
    if (parentId) payload['parentId'] = parentId;
    payload[PAYLOAD_LABELS_KEY] = parent ? { parentId: parent.name } : {};
    this.send('UNIT_CREATE', payload, () => {
      if (parentId) this.expanded.add(parentId);
    });
  }

  confirmRename(): void {
    const name = this.nameDraft.trim();
    if (!this.editing || this.editing.mode !== 'rename' || !name) return;
    const node = this.find(this.editing.unitId);
    if (!node || name === node.name) {
      this.cancelEdit();
      return;
    }
    this.send('UNIT_RENAME', { id: node.id, name, [PAYLOAD_LABELS_KEY]: { id: node.name } });
  }

  confirmMove(toTopLevel = false): void {
    if (!this.editing || this.editing.mode !== 'move') return;
    const node = this.find(this.editing.unitId);
    const target = toTopLevel ? undefined : this.find(this.moveTargetId);
    if (!node || (!toTopLevel && !target)) return;
    const labels: Record<string, string> = { id: node.name };
    const payload: OrgChange['payload'] = { id: node.id };
    if (target) {
      payload['newParentId'] = target.id;
      labels['newParentId'] = target.name;
    }
    payload[PAYLOAD_LABELS_KEY] = labels;
    this.send('UNIT_MOVE', payload);
  }

  archive(node: DraftNode): void {
    this.dialogs.confirm({
      title: this.translate.instant('orgStructure.tree.archiveDialog.title'),
      message: this.translate.instant('orgStructure.tree.archiveDialog.message', { name: node.name }),
      variant: 'danger',
      confirmText: this.translate.instant('orgStructure.tree.archive'),
    }).subscribe((confirmed) => {
      if (confirmed) this.send('UNIT_ARCHIVE', { id: node.id, [PAYLOAD_LABELS_KEY]: { id: node.name } });
    });
  }

  /** Takes back what is pending on a department (for a new one, it and everything under it). */
  undo(node: DraftNode): void {
    const ids = undoChangeIds(this.changes, node);
    if (!ids.length) return;
    this.busy = true;
    forkJoin(ids.map((id) => this.api.removeChange(this.draftId, id))).subscribe({
      next: () => {
        this.busy = false;
        this.changesChange.emit(this.changes.filter((c) => !ids.includes(c.id)));
      },
      error: (err) => this.fail(err, 'orgStructure.builder.messages.removeFailed'),
    });
  }

  /** Keeps the move drop-down's <option>s from being re-created on every change-detection pass. */
  trackOption(_: number, option: { id: string }): string {
    return option.id;
  }

  trackRow(_: number, row: DraftRow): string {
    return row.node.id;
  }

  // ---- internals ---------------------------------------------------------------------------

  private send(type: OrgChange['type'], payload: OrgChange['payload'], afterSaved?: () => void): void {
    this.busy = true;
    this.api.addChange(this.draftId, type, payload).subscribe({
      next: (change) => {
        this.busy = false;
        afterSaved?.();
        this.cancelEdit();
        this.changesChange.emit([...this.changes, change]);
      },
      error: (err) => this.fail(err, 'orgStructure.builder.messages.addFailed'),
    });
  }

  /** Removes the department's head, so someone else can be chosen (a department has one head). */
  removeHead(node: DraftNode): void {
    this.send('UNIT_CLEAR_HEAD', { unitId: node.id, [PAYLOAD_LABELS_KEY]: { unitId: node.name } });
  }

  /**
   * Makes a person the head of the department. A department has exactly one head, so a current head is cleared
   * first (both steps are draft changes, nothing is live until publish).
   */
  makeHead(node: DraftNode, person: ChartPerson): void {
    const labels = { [PAYLOAD_LABELS_KEY]: { unitId: node.name, employeeId: person.name } };
    const setHead = () => this.send('UNIT_SET_HEAD', { unitId: node.id, employeeId: person.employeeId, ...labels });
    if (!node.headEmployeeId) {
      setHead();
      return;
    }
    this.busy = true;
    this.api.addChange(this.draftId, 'UNIT_CLEAR_HEAD', { unitId: node.id, [PAYLOAD_LABELS_KEY]: { unitId: node.name } }).subscribe({
      next: (cleared) => {
        this.changesChange.emit([...this.changes, cleared]);
        this.busy = false;
        this.api.addChange(this.draftId, 'UNIT_SET_HEAD', { unitId: node.id, employeeId: person.employeeId, ...labels }).subscribe({
          next: (change) => this.changesChange.emit([...this.changes, cleared, change]),
          error: (err) => this.fail(err, 'orgStructure.builder.messages.addFailed'),
        });
      },
      error: (err) => this.fail(err, 'orgStructure.builder.messages.addFailed'),
    });
  }

  /** Takes a person out of the department (a draft change; if they were the head, the head is cleared too on publish). */
  removePerson(node: DraftNode, person: ChartPerson): void {
    const labels = { [PAYLOAD_LABELS_KEY]: { unitId: node.name, employeeId: person.name } };
    if (person.isHead) {
      this.busy = true;
      this.api.addChange(this.draftId, 'UNIT_CLEAR_HEAD', { unitId: node.id, [PAYLOAD_LABELS_KEY]: { unitId: node.name } }).subscribe({
        next: (cleared) => {
          this.busy = false;
          this.changesChange.emit([...this.changes, cleared]);
          this.api.addChange(this.draftId, 'UNASSIGN', { employeeId: person.employeeId, unitId: node.id, ...labels }).subscribe({
            next: (change) => this.changesChange.emit([...this.changes, cleared, change]),
            error: (err) => this.fail(err, 'orgStructure.builder.messages.addFailed'),
          });
        },
        error: (err) => this.fail(err, 'orgStructure.builder.messages.addFailed'),
      });
      return;
    }
    this.send('UNASSIGN', { employeeId: person.employeeId, unitId: node.id, ...labels });
  }

  /** The person being moved and the department they are moved to, while the move picker is open. */
  moving: { unitId: string; employeeId: string } | null = null;
  moveToUnitId = '';
  /** Jobs of the chosen department's ladder, the one the person will hold there, and whether that is still being looked up. */
  moveTargetPositions: OrgPosition[] = [];
  movePositionId = '';
  moveLadderMissing = false;
  moveChecking = false;

  /** Departments a person can be moved to: every department of the draft except the one they are in. */
  moveTargets(node: DraftNode): DraftNode[] {
    return this.rows.map((r) => r.node).filter((n) => n.id !== node.id && n.state !== 'archived');
  }

  isMoving(node: DraftNode, person: ChartPerson): boolean {
    return this.moving?.unitId === node.id && this.moving.employeeId === person.employeeId;
  }

  startMovePerson(node: DraftNode, person: ChartPerson): void {
    this.moving = { unitId: node.id, employeeId: person.employeeId };
    this.moveToUnitId = '';
    this.resetMoveJob();
  }

  cancelMove(): void {
    this.moving = null;
    this.moveToUnitId = '';
    this.resetMoveJob();
  }

  private resetMoveJob(): void {
    this.moveTargetPositions = [];
    this.movePositionId = '';
    this.moveLadderMissing = false;
    this.moveChecking = false;
  }

  /**
   * A department only accepts jobs of its own ladder, so when the target is chosen its ladder is read and the person's job is
   * matched to it (the same job, or the one with the same name). When there is no match the person picks one, so the draft can
   * never hold a job that is not in the ladder.
   */
  onMoveTargetChange(person: ChartPerson): void {
    this.resetMoveJob();
    const target = this.find(this.moveToUnitId);
    if (!target) return;
    if (!target.ladderId) {
      this.moveLadderMissing = true;
      return;
    }
    this.moveChecking = true;
    this.api.positions(target.ladderId).pipe(catchError(() => of([] as OrgPosition[]))).subscribe((positions) => {
      this.moveChecking = false;
      this.moveTargetPositions = positions;
      this.movePositionId = matchPositionForMove(positions, person.positionId, person.position)?.id ?? '';
      this.cdr.detectChanges();
    });
  }

  /** A move needs a target, a ladder with a job chosen from it, and no lookup in progress. */
  get canConfirmMove(): boolean {
    return !this.busy && !!this.moveToUnitId && !this.moveChecking && !this.moveLadderMissing && !!this.movePositionId;
  }

  /**
   * Moves a person from this department to another: out of the first (clearing the head if it was them), into the
   * second with the same job. A person is in one department only, so it is always both steps; a job the other
   * department does not have is refused straight away.
   */
  confirmMovePerson(node: DraftNode, person: ChartPerson): void {
    const target = this.find(this.moveToUnitId);
    if (!target || !this.canConfirmMove) return;
    const steps: { type: OrgChange['type']; payload: OrgChange['payload'] }[] = [];
    if (person.isHead) steps.push({ type: 'UNIT_CLEAR_HEAD', payload: { unitId: node.id, [PAYLOAD_LABELS_KEY]: { unitId: node.name } } });
    steps.push({ type: 'UNASSIGN', payload: { employeeId: person.employeeId, unitId: node.id, [PAYLOAD_LABELS_KEY]: { unitId: node.name, employeeId: person.name } } });
    steps.push({ type: 'ASSIGN', payload: {
      employeeId: person.employeeId, unitId: target.id, positionId: this.movePositionId, primary: true,
      [PAYLOAD_LABELS_KEY]: { employeeId: person.name, unitId: target.name, positionId: this.moveTargetPositions.find((p) => p.id === this.movePositionId)?.name ?? person.position },
    } });
    this.busy = true;
    const done: OrgChange[] = [];
    const next = (i: number): void => {
      if (i === steps.length) {
        this.busy = false;
        this.cancelMove();
        return;
      }
      this.api.addChange(this.draftId, steps[i].type, steps[i].payload).subscribe({
        next: (change) => {
          done.push(change);
          this.changesChange.emit([...this.changes, ...done]);
          next(i + 1);
        },
        error: (err) => {
          this.busy = false;
          this.fail(err, 'orgStructure.builder.messages.addFailed');
        },
      });
    };
    next(0);
  }

  private find(id: string): DraftNode | undefined {
    const walk = (list: DraftNode[]): DraftNode | undefined => {
      for (const node of list) {
        if (node.id === id) return node;
        const found = walk(node.children);
        if (found) return found;
      }
      return undefined;
    };
    return walk(this.roots);
  }

  private rebuild(): void {
    this.roots = buildDraftTree(this.live, this.changes);
    this.rows = flattenTree(this.roots, this.expanded);
    this.rebuildChartPeople();
    this.chartNodes = toChartNodes(this.roots, this.chartPeopleByUnit, this.translate.instant('nav.sections.organization'));
    this.cdr.detectChanges();
  }

  /** Who each department will have after publishing: those fetched live, with the draft's changes laid over them. */
  private rebuildChartPeople(): void {
    const byUnit = new Map<string, ChartPerson[]>();
    const walk = (list: DraftNode[]) => list.forEach((node) => {
      byUnit.set(node.id, chartPeople(node.id, this.livePeople.get(node.id) ?? [], this.changes, node.headEmployeeId));
      walk(node.children);
    });
    walk(this.roots);
    this.chartPeopleByUnit = byUnit;
  }

  private fail(err: { error?: { detail?: string } }, fallbackKey: string): void {
    this.busy = false;
    this.notify.error(apiErrorMessage(err, this.translate.instant(fallbackKey)));
    this.cdr.detectChanges();
  }
}
