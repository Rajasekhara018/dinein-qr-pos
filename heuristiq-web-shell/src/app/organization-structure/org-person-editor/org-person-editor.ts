import { ChangeDetectorRef, Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import {
  EMPTY, Observable, catchError, concatMap, from, map, of, switchMap, tap, throwError, toArray,
} from 'rxjs';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { HqPickerPage, HqLookupService } from 'hq-shared-lib';
import { SearchApiService, SearchCondition, UAM_SEARCH_MODELS } from '../../shared/services/search-api-service';
import { UamUser } from '../../shared/models/uam.models';
import { DraftNode } from '../org-draft-tree.helpers';
import {
  EMPLOYEE_LOOKUP_LIMIT, NEW_EMPLOYEE_STATUS, PAYLOAD_LABELS_KEY,
} from '../org-structure.constants';
import { OrgChange, OrgEmployee, OrgLadder, OrgPosition } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

interface Choice {
  id: string;
  label: string;
  rank?: number;
}

/**
 * Adds a person to a department in three plain steps: the ladder (the list of job titles the department uses),
 * the person's job title on it, and the person, picked from the existing users. The ladder and the title can be
 * created on the spot. Each step is saved to the open draft; nothing goes live until it is published.
 */
@Component({
  selector: 'app-org-person-editor',
  standalone: false,
  templateUrl: './org-person-editor.html',
  styleUrl: './org-person-editor.scss',
})
export class OrgPersonEditor implements OnInit {
  @Input({ required: true }) draftId!: string;
  @Input({ required: true }) node!: DraftNode;
  /** The draft's changes, so ladders and job titles created earlier in it can be chosen. */
  @Input() changes: OrgChange[] = [];
  @Output() changesChange = new EventEmitter<OrgChange[]>();
  @Output() closed = new EventEmitter<void>();

  ladderId = '';
  positionId = '';
  newLadderName = '';
  newPositionName = '';
  newPositionRank: number | null = null;
  newPositionManagerial = false;

  /** Everyone chosen in the dialog so far; people from several searches can be added together. */
  selectedUsers: UamUser[] = [];
  /** Make the person being added the head of the department (only offered for one person, and when it has no head). */
  makeHead = false;
  busy = false;

  private liveLadders: OrgLadder[] = [];
  private livePositions: OrgPosition[] = [];
  private readonly organizationContext = inject(OrganizationContextService);
  private readonly searchApi = inject(SearchApiService);
  private readonly lookup = inject(HqLookupService);

  constructor(
    private api: OrgStructureService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {
  }

  ngOnInit(): void {
    this.ladderId = this.node.ladderId ?? '';
    this.api.ladders().pipe(catchError(() => of([] as OrgLadder[]))).subscribe((ladders) => {
      this.liveLadders = ladders;
      this.cdr.detectChanges();
    });
    this.loadPositions();
  }

  // ---- what can be chosen ----------------------------------------------------------------------

  /** Ladders that exist live plus the ones created or copied in this draft. */
  get ladderOptions(): Choice[] {
    const fromDraft = this.changes
      .filter((c) => c.type === 'LADDER_CREATE' || c.type === 'LADDER_COPY')
      .map((c) => ({ id: String(c.payload['id']), label: String(c.payload['name']) }));
    const seen = new Set<string>();
    return [...this.liveLadders.map((l) => ({ id: l.id, label: l.name })), ...fromDraft].filter((l) => !seen.has(l.id) && !!seen.add(l.id));
  }

  /** Job titles of the chosen ladder, live and from this draft, lowest rank first. */
  get positionOptions(): Choice[] {
    const fromDraft = this.changes
      .filter((c) => c.type === 'POSITION_UPSERT' && String(c.payload['ladderId']) === this.ladderId)
      .map((c) => ({ id: String(c.payload['id']), label: String(c.payload['name']), rank: Number(c.payload['rank']) }));
    const seen = new Set<string>();
    return [...this.livePositions.map((p) => ({ id: p.id, label: p.name, rank: p.rank })), ...fromDraft]
      .filter((p) => !seen.has(p.id) && !!seen.add(p.id))
      .sort((a, b) => (a.rank ?? 0) - (b.rank ?? 0));
  }

  /** What is still missing before the person can be added, as a translation key; null when nothing is. */
  get missingKey(): string | null {
    if (!this.ladderId) return 'orgStructure.tree.person.needLadder';
    if (!this.positionId) return 'orgStructure.tree.person.needPosition';
    if (this.selectedUsers.length === 0) return 'orgStructure.tree.person.needPerson';
    return null;
  }

  /**
   * The option lists are rebuilt on every change-detection pass, so without a stable identity the browser's
   * <option> elements are replaced each time, which closes an open drop-down and can drop the selection.
   */
  trackOption(_: number, option: Choice): string {
    return option.id;
  }

  /** A department has one head, so "make head" is only offered while it has none. */
  get headAvailable(): boolean {
    return !this.node.headEmployeeId;
  }

  get canAdd(): boolean {
    return !this.busy && !!this.ladderId && !!this.positionId && this.selectedUsers.length > 0;
  }

  onLadderChosen(): void {
    this.positionId = '';
    this.loadPositions();
  }

  /** Takes a person off the chosen list (the chip's cross). */
  removeUser(user: UamUser): void {
    this.selectedUsers = this.selectedUsers.filter((selected) => selected.id !== user.id);
  }

  trackUser(_: number, user: UamUser): string {
    return user.id;
  }

  displayName(user: UamUser): string {
    const full = user.fullName ? [user.fullName.firstName, user.fullName.middleName, user.fullName.lastName].filter(Boolean).join(' ') : '';
    return user.displayName || full || user.userName;
  }

  // ---- creating a ladder or a job title on the spot ------------------------------------------------

  createLadder(): void {
    const name = this.newLadderName.trim();
    if (!name || this.busy) return;
    this.busy = true;
    this.api.addChange(this.draftId, 'LADDER_CREATE', { name }).subscribe({
      next: (change) => {
        this.busy = false;
        this.newLadderName = '';
        this.emit([...this.changes, change]);
        this.ladderId = String(change.payload['id']);
        this.onLadderChosen();
      },
      error: (err) => this.fail(err),
    });
  }

  createPosition(): void {
    const name = this.newPositionName.trim();
    if (!name || !this.ladderId || this.busy) return;
    const rank = this.newPositionRank ?? Math.max(0, ...this.positionOptions.map((p) => p.rank ?? 0)) + 1;
    this.busy = true;
    this.api.addChange(this.draftId, 'POSITION_UPSERT', {
      ladderId: this.ladderId, name, rank, managerial: this.newPositionManagerial,
    }).subscribe({
      next: (change) => {
        this.busy = false;
        this.newPositionName = '';
        this.newPositionRank = null;
        this.newPositionManagerial = false;
        this.emit([...this.changes, change]);
        this.positionId = String(change.payload['id']);
        this.cdr.detectChanges();
      },
      error: (err) => this.fail(err),
    });
  }

  // ---- adding the person ---------------------------------------------------------------------

  /**
   * Adds every ticked person to this department, one after another. A person who cannot be added (for example someone
   * who already belongs to another department) is reported and skipped; the others are still added.
   */
  add(): void {
    if (!this.canAdd) return;
    const users = this.selectedUsers;
    const added: OrgChange[] = [];
    const failures: string[] = [];
    const keep = (change: OrgChange | null): void => { if (change) added.push(change); };
    const position = this.positionOptions.find((p) => p.id === this.positionId);
    const ladder = this.ladderOptions.find((l) => l.id === this.ladderId);
    const base = this.changes;
    this.busy = true;

    const setLadder$: Observable<OrgChange | null> = this.ladderId === this.node.ladderId
      ? of(null)
      : this.api.addChange(this.draftId, 'UNIT_SET_LADDER', {
        unitId: this.node.id, ladderId: this.ladderId,
        [PAYLOAD_LABELS_KEY]: { unitId: this.node.name, ladderId: ladder?.label ?? '' },
      });

    const addOne = (user: UamUser): Observable<OrgChange> => this.ensureEmployee(user).pipe(
      switchMap((employee) => {
        const other = this.otherDepartmentInDraft([...base, ...added], employee.id);
        return other === null ? of(employee) : throwError(() => ({
          error: { detail: this.translate.instant('orgStructure.tree.person.alreadyAssigned', { person: employee.fullName, department: other }) },
        }));
      }),
      switchMap((employee) => this.api.addChange(this.draftId, 'ASSIGN', {
        employeeId: employee.id, unitId: this.node.id, positionId: this.positionId, primary: true,
        [PAYLOAD_LABELS_KEY]: { employeeId: employee.fullName, unitId: this.node.name, positionId: position?.label ?? '' },
      })),
      tap((change) => keep(change)),
      switchMap((assignment) => this.makeHead && users.length === 1 && this.headAvailable
        ? this.api.addChange(this.draftId, 'UNIT_SET_HEAD', {
          unitId: this.node.id, employeeId: (assignment.payload['employeeId'] as string),
          [PAYLOAD_LABELS_KEY]: { unitId: this.node.name, employeeId: this.displayName(users[0]) },
        }).pipe(tap((change) => keep(change)), map(() => assignment))
        : of(assignment)),
    );

    setLadder$.pipe(
      tap((change) => keep(change)),
      switchMap(() => from(users).pipe(
        concatMap((user) => addOne(user).pipe(
          catchError((err: { error?: { detail?: string } }) => {
            failures.push(`${this.displayName(user)}: ${apiErrorMessage(err, this.translate.instant('orgStructure.tree.person.failed'))}`);
            return EMPTY;
          }),
        )),
        toArray(),
      )),
    ).subscribe({
      next: (assigned) => {
        this.busy = false;
        this.emit([...base, ...added]);
        if (assigned.length) {
          this.notify.success(this.translate.instant('orgStructure.tree.person.addedMany', { count: assigned.length }));
        }
        failures.forEach((message) => this.notify.error(message));
        // Stay open on the same ladder and job title; whoever could not be added stays ticked to try again.
        const failedNames = new Set(failures.map((message) => message.split(':')[0]));
        this.selectedUsers = users.filter((user) => failedNames.has(this.displayName(user)));
        this.cdr.detectChanges();
      },
      error: (err) => {
        // The ladder change itself failed; keep the draft list in step with anything saved before it.
        if (added.length) this.emit([...base, ...added]);
        this.fail(err);
      },
    });
  }

  /** Closes the editor; the people already added stay in the draft. */
  cancel(): void {
    this.closed.emit();
  }

  // ---- internals ---------------------------------------------------------------------------

  private loadPositions(): void {
    this.livePositions = [];
    if (!this.ladderId) {
      this.cdr.detectChanges();
      return;
    }
    // A ladder created in this draft is not on the server yet: it has no live titles, and asking would only 404.
    if (this.isDraftLadder(this.ladderId)) {
      this.cdr.detectChanges();
      return;
    }
    this.api.positions(this.ladderId).pipe(catchError(() => of([] as OrgPosition[]))).subscribe((positions) => {
      this.livePositions = positions;
      this.cdr.detectChanges();
    });
  }

  /**
   * The name of another department this person is already added to in the draft (and not removed from since), or
   * null. A person belongs to one department only; to move them, remove them from the first one before adding them.
   */
  private otherDepartmentInDraft(changes: readonly OrgChange[], employeeId: string): string | null {
    let department: string | null = null;
    let departmentId: string | null = null;
    for (const change of [...changes].sort((a, b) => a.seq - b.seq)) {
      if (String(change.payload['employeeId']) !== employeeId) continue;
      if (change.type === 'ASSIGN') {
        departmentId = String(change.payload['unitId']);
        const labels = change.payload[PAYLOAD_LABELS_KEY];
        department = (labels && typeof labels === 'object' ? (labels as Record<string, string>)['unitId'] : undefined) ?? departmentId;
      } else if (change.type === 'UNASSIGN' && String(change.payload['unitId']) === departmentId) {
        department = null;
        departmentId = null;
      }
    }
    return departmentId !== null && departmentId !== this.node.id ? department : null;
  }

  private isDraftLadder(id: string): boolean {
    return this.changes.some((c) => (c.type === 'LADDER_CREATE' || c.type === 'LADDER_COPY') && String(c.payload['id']) === id);
  }

  /** The people list is made of employees; a user becomes one the first time they are added to a department. */
  private ensureEmployee(user: UamUser): Observable<OrgEmployee> {
    const code = user.employeeCode || user.userName;
    return this.api.searchEmployees(code, EMPLOYEE_LOOKUP_LIMIT).pipe(
      map((found) => found.find((employee) => employee.userId === user.id)),
      switchMap((existing) => existing ? of(existing) : this.api.createEmployee({
        employeeCode: code,
        fullName: this.displayName(user),
        email: user.email,
        status: NEW_EMPLOYEE_STATUS,
        userId: user.id,
      })),
    );
  }

  /**
   * Every user read comes from the search index's getall API, one page at a time, never the whole table: the users of
   * the organization being built (text5 = tenant), narrowed by the typed text on username, email or full name.
   */
  private searchUsers(query: string, page: number, size: number): Observable<HqPickerPage<UamUser>> {
    const tenantId = this.organizationContext.currentTenantId();
    if (!tenantId) return of({ items: [], total: 0 });
    const text = query.trim();
    const any: SearchCondition[] = text
      ? ['text1', 'text2', 'text7'].map((field) => ({ field, operator: 'LIKE' as const, value: text }))
      : [];
    return this.searchApi.getAll('user', [{ field: 'text5', operator: 'EQ', value: tenantId }], any, page, size, 'text1').pipe(
      map((result) => ({ items: result.content.map(UAM_SEARCH_MODELS.users.toRow), total: result.totalElements })),
    );
  }

  /** Opens the paged search dialog for choosing people (any number, across searches); the ticked ones become the selection. */
  browseUsers(): void {
    this.lookup.open<UamUser>({
      title: this.translate.instant('orgStructure.tree.person.browseTitle'),
      subtitle: this.translate.instant('orgStructure.tree.person.browseSubtitle'),
      multiple: true,
      selected: this.selectedUsers,
      idOf: (user) => user.id,
      columns: [
        { key: 'userName', label: this.translate.instant('orgStructure.tree.person.colUserName') },
        { key: 'displayName', label: this.translate.instant('orgStructure.tree.person.colName'), render: (user) => this.displayName(user) },
        { key: 'email', label: this.translate.instant('orgStructure.tree.person.colEmail') },
      ],
      chipLabel: (user) => this.displayName(user),
      fetch: (query, page, size) => this.searchUsers(query, page, size),
    }).subscribe((chosen) => {
      if (chosen) {
        this.selectedUsers = chosen;
        this.cdr.detectChanges();
      }
    });
  }

  private emit(changes: OrgChange[]): void {
    this.changes = changes;
    this.changesChange.emit(changes);
    this.cdr.detectChanges();
  }

  private fail(err: { error?: { detail?: string } }): void {
    this.busy = false;
    this.notify.error(apiErrorMessage(err, this.translate.instant('orgStructure.tree.person.failed')));
    this.cdr.detectChanges();
  }
}
