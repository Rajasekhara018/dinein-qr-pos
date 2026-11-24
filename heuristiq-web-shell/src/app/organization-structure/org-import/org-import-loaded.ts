import { ChangeDetectorRef, Component, inject, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { catchError, defer, forkJoin, map, Observable, of, Subscription, switchMap } from 'rxjs';
import { watchColumns } from '../../shared/table-columns.helpers';
import {
  IMPORT_FACT_PRIVILEGES, IMPORT_LOADED_LEVEL_PAGE, IMPORT_LOADED_MAX_CALLS, IMPORT_LOADED_MAX_LADDERS, ORG_CHART_ROUTE,
} from '../org-structure.constants';
import { ORG_LOADED_EMPLOYEE_COLUMNS, ORG_LOADED_POSITION_COLUMNS, ORG_LOADED_TREE_COLUMNS } from './org-import-loaded.constants';
import { OrgImportMode, OrgLadder, OrgPosition, OrgUnit } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import {
  flattenLevel, loadedUnits, newLevel, OrgDepartmentSummary, OrgEmployeeSummary, OrgUnitLevel, OrgUnitNode, OrgUnitRow, sortPositionsByRank,
  summarizeDepartments, summarizeEmployees,
} from './org-import-loaded.helpers';

/** A ladder with its job titles, most senior first (null titles: this person may not read them). */
export interface OrgLoadedLadder {
  ladder: OrgLadder;
  positions: OrgPosition[] | null;
}

type LoadState = 'loading' | 'ready' | 'error';

/**
 * "Loaded now" of one import step, read-only: the ladders with their job titles (step 1), the department tree (step 2) or the people per
 * department (step 3). It reads only what this person may read and is hidden quietly when they may read none of it. It re-reads whenever the
 * step changes or `refresh` changes (the import screen bumps it after each confirm, cancel and finished import).
 */
@Component({
  selector: 'app-org-import-loaded',
  standalone: false,
  templateUrl: './org-import-loaded.html',
  styleUrl: './org-import-loaded.scss',
})
export class OrgImportLoaded implements OnChanges, OnDestroy {
  private readonly api = inject(OrgStructureService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly translate = inject(TranslateService);

  @Input() mode: OrgImportMode = 'EMPLOYEES';
  /** Changes whenever what is loaded may have changed. */
  @Input() refresh = 0;

  readonly chartRoute = ORG_CHART_ROUTE;
  readonly unitPrivilege = IMPORT_FACT_PRIVILEGES.units;

  expanded = true;
  state: LoadState = 'loading';
  ladders: OrgLoadedLadder[] = [];
  positionsReadable = true;
  root: OrgUnitLevel = { nodes: [], shown: 0 };
  rows: OrgUnitRow[] = [];
  ladderNames = new Map<string, string>();
  /** Employee step: how many of the departments could not be read within the call budget. */
  budgetExceeded = false;
  employeeShown = IMPORT_LOADED_LEVEL_PAGE;

  positionColumns: HqCommonTableColumn<OrgPosition>[] = [];
  treeColumns: HqCommonTableColumn<OrgUnitRow>[] = [];
  employeeColumns: HqCommonTableColumn<OrgUnit>[] = [];

  /** A tree row that is not a department (loading / failed / show more) is drawn as one full-width strip instead of cells. */
  readonly isStripRow = (row: OrgUnitRow): boolean => row.kind !== 'unit';
  /** The cell row of a strip is hidden, so only its full-width strip shows. */
  readonly treeRowClass = (row: OrgUnitRow): string | null => (row.kind === 'unit' ? null : 'loaded__strip-host');

  private loading?: Subscription;
  private readonly columnSubs: Subscription[] = [
    watchColumns(this.translate, ORG_LOADED_POSITION_COLUMNS, (columns) => (this.positionColumns = columns)),
    watchColumns(this.translate, ORG_LOADED_TREE_COLUMNS, (columns) => (this.treeColumns = columns)),
    watchColumns(this.translate, ORG_LOADED_EMPLOYEE_COLUMNS, (columns) => (this.employeeColumns = columns)),
  ];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['mode'] || changes['refresh']) this.load();
  }

  ngOnDestroy(): void {
    this.loading?.unsubscribe();
    this.columnSubs.forEach((sub) => sub.unsubscribe());
  }

  /** The department node of a tree row, for the cell templates (null for a strip row). */
  nodeOf(row: OrgUnitRow): OrgUnitNode | null {
    return row.kind === 'unit' ? row.node : null;
  }

  get headingId(): string {
    return 'org-import-loaded-heading-' + this.mode;
  }

  get bodyId(): string {
    return 'org-import-loaded-body-' + this.mode;
  }

  /** Whether this person may read what the step shows; when not, the whole section stays out of the page. */
  get visible(): boolean {
    return this.access.can(this.mode === 'LADDERS' ? IMPORT_FACT_PRIVILEGES.ladders : IMPORT_FACT_PRIVILEGES.units);
  }

  get isLadders(): boolean {
    return this.mode === 'LADDERS';
  }

  get isDepartments(): boolean {
    return this.mode === 'DEPARTMENTS';
  }

  get isEmployees(): boolean {
    return this.mode === 'EMPLOYEES';
  }

  get positionTotal(): number {
    return this.ladders.reduce((sum, item) => sum + (item.positions?.length ?? 0), 0);
  }

  get departmentSummary(): OrgDepartmentSummary {
    return summarizeDepartments(this.root);
  }

  get employeeSummary(): OrgEmployeeSummary {
    const summary = summarizeEmployees(this.root);
    return { ...summary, atLeast: summary.atLeast || this.budgetExceeded };
  }

  /** Employee step: every department read, parents first, as far as the page size allows. */
  get employeeUnits(): OrgUnit[] {
    return loadedUnits(this.root);
  }

  get employeeRows(): OrgUnit[] {
    return this.employeeUnits.slice(0, this.employeeShown);
  }

  toggleSection(): void {
    this.expanded = !this.expanded;
  }

  /** The name of a department's ladder, or null when it has none. A ladder this person may not read shows as unknown (an empty string). */
  ladderName(unit: OrgUnit): string | null {
    return unit.ladderId ? this.ladderNames.get(unit.ladderId) ?? '' : null;
  }

  /** Opens or closes a department; its sub-departments are read the first time it opens. */
  toggleNode(node: OrgUnitNode): void {
    node.open = !node.open;
    if (node.open && !node.level && !node.loading) this.readChildren(node);
    this.rebuild();
  }

  showMore(level: OrgUnitLevel): void {
    level.shown += IMPORT_LOADED_LEVEL_PAGE;
    this.rebuild();
  }

  showMoreEmployees(): void {
    this.employeeShown += IMPORT_LOADED_LEVEL_PAGE;
  }

  retry(): void {
    this.load();
  }

  private rebuild(): void {
    this.rows = flattenLevel(this.root);
    this.cdr.detectChanges();
  }

  private readChildren(node: OrgUnitNode): void {
    node.loading = true;
    node.failed = false;
    this.api.children(node.unit.id).subscribe({
      next: (units) => {
        node.level = newLevel(units, node.depth + 1, IMPORT_LOADED_LEVEL_PAGE);
        node.loading = false;
        this.rebuild();
      },
      error: () => {
        node.loading = false;
        node.failed = true;
        this.rebuild();
      },
    });
  }

  private load(): void {
    this.loading?.unsubscribe();
    if (!this.visible) {
      this.state = 'ready';
      return;
    }
    this.state = 'loading';
    this.budgetExceeded = false;
    this.employeeShown = IMPORT_LOADED_LEVEL_PAGE;
    const read$ = this.isLadders ? this.readLadders() : this.isDepartments ? this.readDepartments() : this.readEmployees();
    this.loading = read$.subscribe({
      next: () => { this.state = 'ready'; this.cdr.detectChanges(); },
      error: () => { this.state = 'error'; this.cdr.detectChanges(); },
    });
  }

  private readLadders(): Observable<void> {
    this.positionsReadable = this.access.can(IMPORT_FACT_PRIVILEGES.positions);
    return defer(() => this.api.ladders()).pipe(
      switchMap((ladders) => {
        const shown = ladders.slice(0, IMPORT_LOADED_MAX_LADDERS);
        if (!this.positionsReadable || shown.length === 0) return of(shown.map((ladder): OrgLoadedLadder => ({ ladder, positions: null })));
        return forkJoin(shown.map((ladder) => this.api.positions(ladder.id).pipe(
          map((positions): OrgLoadedLadder => ({ ladder, positions: sortPositionsByRank(positions) })),
        )));
      }),
      map((items) => { this.ladders = items; }),
    );
  }

  private readDepartments(): Observable<void> {
    const ladders$: Observable<OrgLadder[]> = this.access.can(IMPORT_FACT_PRIVILEGES.ladders)
      ? defer(() => this.api.ladders()).pipe(catchError(() => of([])))
      : of([]);
    return forkJoin([defer(() => this.api.children()), ladders$]).pipe(
      map(([units, ladders]) => {
        this.ladderNames = new Map(ladders.map((ladder) => [ladder.id, ladder.name]));
        this.root = newLevel(units, 0, IMPORT_LOADED_LEVEL_PAGE);
        this.rows = flattenLevel(this.root);
      }),
    );
  }

  /** The employee step lists every department, so it reads level by level until everything is read or the call budget is used up. */
  private readEmployees(): Observable<void> {
    return defer(() => this.api.children()).pipe(
      switchMap((units) => {
        this.root = newLevel(units, 0, units.length);
        return this.readLevels(this.root.nodes, IMPORT_LOADED_MAX_CALLS);
      }),
    );
  }

  private readLevels(queue: OrgUnitNode[], budget: number): Observable<void> {
    const withChildren = queue.filter((node) => node.unit.childCount > 0);
    if (withChildren.length === 0) return of(undefined);
    if (budget <= 0) { this.budgetExceeded = true; return of(undefined); }
    const batch = withChildren.slice(0, budget);
    if (withChildren.length > batch.length) this.budgetExceeded = true;
    return forkJoin(batch.map((node) => this.api.children(node.unit.id).pipe(
      map((units) => { node.level = newLevel(units, node.depth + 1, units.length); return node.level.nodes; }),
      catchError(() => { node.failed = true; this.budgetExceeded = true; return of([] as OrgUnitNode[]); }),
    ))).pipe(
      switchMap((levels) => this.readLevels(levels.flat(), budget - batch.length)),
    );
  }
}
