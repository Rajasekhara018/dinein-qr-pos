import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService, HqStatCardItem, HqStatusTone } from 'hq-shared-lib';
import { Observable, Subject, of } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize, switchMap, takeUntil } from 'rxjs/operators';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { AuthService } from '../../shared/services/auth-service';
import { NotificationService } from '../../shared/services/notification-service';
import { TICKET_PRIORITIES, WORKSPACE_MAX_BODY, WORKSPACE_MAX_TITLE, WORKSPACE_PRIORITY_LABEL_KEYS, WORKSPACE_PRIVILEGES } from '../workspace.constants';
import { fromLocalInput, toLocalInput } from '../workspace.helpers';
import {
  WorkspaceCategory,
  WorkspaceDraft,
  WorkspaceItem,
  WorkspaceListFilter,
  WorkspacePriority,
  WorkspaceStatus,
  WorkspaceSummary,
} from '../workspace.models';
import { WorkspaceService } from '../workspace.service';
import { WorkspacePeopleService } from '../workspace-people.service';
import {
  BOARD_COLUMNS,
  DESK_BOARD_SIZE,
  DESK_I18N,
  DESK_PAGE_SIZE,
  DESK_SEARCH_DEBOUNCE_MS,
  DESK_STATS,
  DESK_VIEWS,
  DeskView,
  DeskViewDef,
  SLA_TONES,
  SlaState,
  TICKET_ACTION_LABEL_KEYS,
  TICKET_CATEGORIES,
  TICKET_CATEGORY_ICONS,
  TICKET_PRIORITY_TONES,
  TICKET_QUEUES,
  TICKET_RECORD_TYPES,
  TICKET_STATUS_LABEL_KEYS,
  TICKET_STATUS_TONES,
} from './service-desk.constants';
import { isActive, nextStatuses, parseLabels, queueOrder, slaState, spanOf, ticketKey } from './service-desk.helpers';

/** What the ticket form holds, as its inputs give it (the due time is datetime-local text, labels are typed as one text). */
interface TicketForm {
  title: string;
  body: string;
  category: WorkspaceCategory;
  priority: WorkspacePriority;
  queue: string;
  assigneeId: string;
  /** How the assignee is shown (the name chosen in the people picker). */
  assigneeName: string;
  labels: string;
  due: string;
  refType: string;
  refId: string;
}

const emptyTicketForm = (): TicketForm => ({
  title: '', body: '', category: 'REQUEST', priority: 'NORMAL', queue: '', assigneeId: '', assigneeName: '', labels: '', due: '', refType: '', refId: '',
});

/**
 * The operations service desk: numbered tickets (incidents, requests, problems, changes, questions) worked by the team through
 * queues (mine, unassigned, the team's, raised by me, resolved), as a list or a board whose cards are dragged between statuses.
 * A ticket has a priority that sets its due time, a queue, labels, an assignee and the record it is about; resolving asks what
 * was done. The organization and the user are the session's; the server decides who may do what, the buttons only follow it.
 */
@Component({
  selector: 'app-service-desk',
  standalone: false,
  templateUrl: './service-desk.html',
  styleUrl: './service-desk.scss',
})
export class ServiceDesk implements OnInit, OnDestroy {
  private readonly svc = inject(WorkspaceService);
  private readonly people = inject(WorkspacePeopleService);
  private readonly auth = inject(AuthService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly notify = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly dialogs = inject(AppDialogService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();
  private readonly search$ = new Subject<string>();

  readonly i18n = DESK_I18N;
  readonly privileges = WORKSPACE_PRIVILEGES;
  readonly views = DESK_VIEWS;
  readonly columnsOfBoard = BOARD_COLUMNS;
  readonly priorities = TICKET_PRIORITIES;
  readonly categories = TICKET_CATEGORIES;
  readonly queues = TICKET_QUEUES;
  readonly recordTypes = TICKET_RECORD_TYPES;
  readonly maxTitle = WORKSPACE_MAX_TITLE;
  readonly maxBody = WORKSPACE_MAX_BODY;
  readonly ticketKey = ticketKey;

  view: DeskViewDef = DESK_VIEWS[0];
  layout: 'list' | 'board' = 'list';
  search = '';
  priorityFilter: WorkspacePriority | '' = '';
  categoryFilter: WorkspaceCategory | '' = '';
  queueFilter = '';

  items: WorkspaceItem[] = [];
  total = 0;
  page = 0;
  loading = false;
  loadError = false;
  summary: WorkspaceSummary | null = null;
  stats: HqStatCardItem[] = [];
  columns: HqCommonTableColumn<WorkspaceItem>[] = [];

  /** The ticket open in the drawer (null while creating one or with the drawer closed). */
  selected: WorkspaceItem | null = null;
  drawerOpen = false;
  form: TicketForm = emptyTicketForm();
  saving = false;
  busy = false;
  /** Bumped after every change of the open ticket, so its timeline is read again. */
  timelineKey = 0;

  get userId(): string | null {
    return this.auth.currentUser?.id ?? null;
  }

  /** The drawer's title: "New ticket", or the open ticket's key and title (empty while the drawer is closed). */
  get drawerTitle(): string {
    if (this.creating) return this.translate.instant(`${DESK_I18N}.newTicket`);
    const t = this.selected;
    return t ? `${ticketKey(t)} · ${t.title ?? ''}` : '';
  }

  get creating(): boolean {
    return this.drawerOpen && !this.selected;
  }

  get canEdit(): boolean {
    return this.access.can(this.creating ? this.privileges.create : this.privileges.update);
  }

  get canMove(): boolean {
    return this.access.can(this.privileges.status);
  }

  get hasMore(): boolean {
    return this.layout === 'list' && this.items.length < this.total;
  }

  get canSave(): boolean {
    return !this.saving && this.canEdit && this.form.title.trim().length > 0;
  }

  /** The tickets on screen after the category filter (the API has none for it), in queue order. */
  get shown(): WorkspaceItem[] {
    const rows = this.categoryFilter ? this.items.filter((item) => item.category === this.categoryFilter) : this.items;
    return this.view.key === 'done' ? rows : [...rows].sort(queueOrder);
  }

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`${DESK_I18N}.columns.${key}`);
    this.columns = [
      { key: 'ticketNumber', label: heading('key') },
      { key: 'title', label: heading('title') },
      { key: 'priority', label: heading('priority') },
      { key: 'status', label: heading('status') },
      { key: 'assigneeId', label: heading('assignee') },
      { key: 'queue', label: heading('queue') },
      { key: 'dueAt', label: heading('due') },
    ];
    this.search$.pipe(debounceTime(DESK_SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntil(this.destroy$)).subscribe(() => this.reload());
    this.loadSummary();
    this.reload();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── queues and filters ───────────────────────────────────────────────────

  selectView(key: DeskView): void {
    const next = this.views.find((view) => view.key === key);
    if (!next || next === this.view) return;
    this.view = next;
    this.reload();
  }

  setLayout(layout: 'list' | 'board'): void {
    if (layout === this.layout) return;
    this.layout = layout;
    this.reload();
  }

  onSearch(text: string): void {
    this.search = text ?? '';
    this.search$.next(this.search.trim());
  }

  onFilterChange(): void {
    this.reload();
  }

  clearFilters(): void {
    this.search = '';
    this.priorityFilter = '';
    this.categoryFilter = '';
    this.queueFilter = '';
    this.reload();
  }

  get filtered(): boolean {
    return !!(this.search.trim() || this.priorityFilter || this.categoryFilter || this.queueFilter);
  }

  reload(): void {
    this.page = 0;
    this.load(false);
  }

  loadMore(): void {
    this.page += 1;
    this.load(true);
  }

  private load(append: boolean): void {
    this.loading = true;
    this.loadError = false;
    const board = this.layout === 'board';
    this.svc.list(this.filter(board), this.page, board ? DESK_BOARD_SIZE : DESK_PAGE_SIZE)
      .pipe(finalize(() => { this.loading = false; this.cdr.markForCheck(); }), takeUntil(this.destroy$))
      .subscribe({
        next: (page) => {
          // The board leaves closed tickets out; it shows the work in progress.
          const rows = board ? page.content.filter((item) => item.status !== 'CLOSED') : page.content;
          this.items = append ? [...this.items, ...rows] : rows;
          this.total = page.totalElements;
        },
        error: () => {
          this.loadError = true;
          if (!append) this.items = [];
        },
      });
  }

  /** The list request of the current queue and filters; the board also shows resolved tickets of an active queue. */
  private filter(board: boolean): WorkspaceListFilter {
    const filter: WorkspaceListFilter = { ...this.view.filter };
    if (board && filter.activeOnly) delete filter.activeOnly;
    if (this.priorityFilter) filter.priority = this.priorityFilter;
    if (this.queueFilter) filter.queue = this.queueFilter;
    const text = this.search.trim();
    if (text) filter.q = text;
    return filter;
  }

  private loadSummary(): void {
    if (!this.access.can(this.privileges.summary)) return;
    this.svc.summary().pipe(takeUntil(this.destroy$)).subscribe({
      next: (summary) => {
        this.summary = summary;
        this.stats = DESK_STATS.map((stat) => ({
          label: this.translate.instant(`${DESK_I18N}.stats.${stat.key}`),
          value: summary?.[stat.key] ?? 0,
          icon: stat.icon,
          tone: stat.tone,
        }));
        this.cdr.markForCheck();
      },
      error: () => { this.stats = []; },
    });
  }

  // ── board ────────────────────────────────────────────────────────────────

  columnItems(status: WorkspaceStatus): WorkspaceItem[] {
    return this.shown.filter((item) => item.status === status);
  }

  /** A card was dropped on another column: the ticket moves to that status (resolving asks what was done). */
  drop(event: CdkDragDrop<WorkspaceStatus, WorkspaceStatus, WorkspaceItem>): void {
    const item = event.item.data;
    const target = event.container.data;
    if (!item || !target || item.status === target || !this.canMove) return;
    this.move(item, target);
  }

  // ── one ticket ───────────────────────────────────────────────────────────

  openNew(): void {
    this.selected = null;
    this.form = emptyTicketForm();
    this.drawerOpen = true;
  }

  open(item: WorkspaceItem): void {
    this.selected = item;
    this.form = {
      title: item.title ?? '',
      body: item.body ?? '',
      category: item.category ?? 'REQUEST',
      priority: item.priority,
      queue: item.queue ?? '',
      assigneeId: item.assigneeId ?? '',
      assigneeName: item.assigneeName ?? '',
      labels: (item.labels ?? []).join(', '),
      due: toLocalInput(item.dueAt),
      refType: item.refType ?? '',
      refId: item.refId ?? '',
    };
    this.drawerOpen = true;
  }

  closeDrawer(): void {
    this.drawerOpen = false;
    this.selected = null;
  }

  save(): void {
    if (!this.canSave) return;
    this.saving = true;
    const draft = this.draftOfForm();
    const call: Observable<WorkspaceItem> = this.selected ? this.svc.update(this.selected.id, draft) : this.svc.create({ ...draft, kind: 'TICKET' });
    const creating = !this.selected;
    call.pipe(finalize(() => { this.saving = false; this.cdr.markForCheck(); }), takeUntil(this.destroy$)).subscribe({
      next: (saved) => {
        this.notify.success(this.translate.instant(creating ? `${DESK_I18N}.messages.created` : `${DESK_I18N}.messages.saved`,
          { key: ticketKey(saved) }));
        this.afterChange(saved);
      },
      error: () => this.notify.error(this.translate.instant(`${DESK_I18N}.messages.saveFailed`)),
    });
  }

  /** The caller picks the ticket up: it is assigned to them and, when still open, moves to in progress. */
  assignToMe(item: WorkspaceItem): void {
    const me = this.auth.currentUser;
    if (!me || this.busy) return;
    this.busy = true;
    this.svc.update(item.id, { ...this.draftOf(item), assigneeId: me.id, assigneeName: me.name?.trim() || me.email || me.id }).pipe(
      switchMap((saved) => (saved.status === 'OPEN' && this.canMove ? this.svc.setStatus(saved.id, 'IN_PROGRESS') : of(saved))),
      finalize(() => { this.busy = false; this.cdr.markForCheck(); }),
      takeUntil(this.destroy$),
    ).subscribe({
      next: (saved) => {
        this.notify.success(this.translate.instant(`${DESK_I18N}.messages.assigned`, { key: ticketKey(saved) }));
        this.afterChange(saved);
      },
      error: () => this.notify.error(this.translate.instant(`${DESK_I18N}.messages.saveFailed`)),
    });
  }

  /** Moves a ticket to another status; resolving first asks what was done and keeps it on the ticket. */
  move(item: WorkspaceItem, status: WorkspaceStatus): void {
    if (this.busy) return;
    if (status === 'RESOLVED') {
      this.resolve(item);
      return;
    }
    this.busy = true;
    this.svc.setStatus(item.id, status).pipe(
      finalize(() => { this.busy = false; this.cdr.markForCheck(); }),
      takeUntil(this.destroy$),
    ).subscribe({
      next: (saved) => {
        this.notify.success(this.translate.instant(`${DESK_I18N}.messages.moved`,
          { key: ticketKey(saved), status: this.statusLabel(saved.status) }));
        this.afterChange(saved);
      },
      error: () => this.notify.error(this.translate.instant(`${DESK_I18N}.messages.moveFailed`)),
    });
  }

  private resolve(item: WorkspaceItem): void {
    this.dialogs.prompt({
      title: this.translate.instant(`${DESK_I18N}.resolve.title`, { key: ticketKey(item) }),
      confirmText: this.translate.instant(`${DESK_I18N}.actions.resolve`),
      cancelText: this.translate.instant(`${DESK_I18N}.form.cancel`),
      field: { key: 'resolution', label: this.translate.instant(`${DESK_I18N}.resolve.label`), type: 'textarea', required: true },
    }).subscribe((resolution) => {
      const text = resolution?.trim();
      if (!text) return;
      this.busy = true;
      this.svc.update(item.id, { ...this.draftOf(item), resolution: text }).pipe(
        switchMap((saved) => this.svc.setStatus(saved.id, 'RESOLVED')),
        finalize(() => { this.busy = false; this.cdr.markForCheck(); }),
        takeUntil(this.destroy$),
      ).subscribe({
        next: (saved) => {
          this.notify.success(this.translate.instant(`${DESK_I18N}.messages.resolved`, { key: ticketKey(saved) }));
          this.afterChange(saved);
        },
        error: () => this.notify.error(this.translate.instant(`${DESK_I18N}.messages.moveFailed`)),
      });
    });
  }

  /** The list, the counters and the open drawer follow what the server answered. */
  private afterChange(saved: WorkspaceItem): void {
    this.timelineKey += 1;
    if (this.drawerOpen) {
      if (this.selected) this.open(saved);
      else this.closeDrawer();
    }
    this.loadSummary();
    this.reload();
  }

  private draftOfForm(): WorkspaceDraft {
    const f = this.form;
    return {
      title: f.title.trim(),
      body: f.body.trim() || null,
      category: f.category,
      priority: f.priority,
      queue: f.queue || null,
      assigneeId: f.assigneeId.trim() || null,
      assigneeName: f.assigneeId.trim() ? f.assigneeName || null : null,
      labels: parseLabels(f.labels),
      // Left empty, the server sets the due time from the priority.
      dueAt: fromLocalInput(f.due),
      refType: f.refType || null,
      refId: f.refType ? f.refId.trim() || null : null,
      resolution: this.selected?.resolution ?? null,
    };
  }

  /** The ticket as an edit request, unchanged (the base of assign and resolve). */
  private draftOf(item: WorkspaceItem): WorkspaceDraft {
    return {
      title: item.title, body: item.body, category: item.category ?? 'REQUEST', priority: item.priority, queue: item.queue ?? null,
      assigneeId: item.assigneeId, assigneeName: item.assigneeName ?? null, labels: item.labels ?? [], dueAt: item.dueAt, remindAt: item.remindAt, refType: item.refType,
      refId: item.refId, resolution: item.resolution ?? null,
    };
  }

  /** Opens the people picker; the chosen colleague becomes the ticket's assignee. */
  pickAssignee(): void {
    this.people.pick().subscribe((person) => {
      if (person) {
        this.form.assigneeId = person.id;
        this.form.assigneeName = person.name;
        this.cdr.markForCheck();
      }
    });
  }

  clearAssignee(): void {
    this.form.assigneeId = '';
    this.form.assigneeName = '';
  }

  // ── words and tones ──────────────────────────────────────────────────────

  transitions(item: WorkspaceItem): readonly WorkspaceStatus[] {
    return nextStatuses(item.status);
  }

  statusLabel(status: WorkspaceStatus): string {
    const key = TICKET_STATUS_LABEL_KEYS[status];
    return key ? this.translate.instant(key) : status;
  }

  actionLabelKey(status: WorkspaceStatus): string {
    return TICKET_ACTION_LABEL_KEYS[status] ?? status;
  }

  statusTone(status: WorkspaceStatus): HqStatusTone {
    return TICKET_STATUS_TONES[status] ?? 'neutral';
  }

  priorityLabelKey(priority: WorkspacePriority): string {
    return WORKSPACE_PRIORITY_LABEL_KEYS[priority];
  }

  priorityTone(priority: WorkspacePriority): HqStatusTone {
    return TICKET_PRIORITY_TONES[priority] ?? 'neutral';
  }

  categoryIcon(category: WorkspaceCategory | null | undefined): string {
    return category ? TICKET_CATEGORY_ICONS[category] : 'bi-ticket';
  }

  /** A translated code (category, queue, record type), or the code itself when it has no words. */
  codeLabel(group: 'categories' | 'queues' | 'recordTypes', code: string | null | undefined): string {
    if (!code) return '';
    const key = `${DESK_I18N}.${group}.${code}`;
    const text = this.translate.instant(key);
    return text === key ? code : text;
  }

  assigneeLabel(item: WorkspaceItem): string {
    if (!item.assigneeId) return this.translate.instant(`${DESK_I18N}.unassigned`);
    return item.assigneeId === this.userId ? this.translate.instant(`${DESK_I18N}.me`) : item.assigneeName || item.assigneeId;
  }

  sla(item: WorkspaceItem): SlaState {
    return slaState(item);
  }

  slaTone(item: WorkspaceItem): HqStatusTone {
    return SLA_TONES[this.sla(item)];
  }

  /** "Due in 3 hours", "2 days overdue", "Met" ... for the SLA badge. */
  slaText(item: WorkspaceItem): string {
    const state = this.sla(item);
    if (state === 'none') return this.translate.instant(`${DESK_I18N}.sla.none`);
    if (state === 'met' || state === 'missed') return this.translate.instant(`${DESK_I18N}.sla.${state}`);
    const left = Date.parse(item.dueAt as string) - Date.now();
    const span = spanOf(left);
    const amount = this.translate.instant(`${DESK_I18N}.span.${span.unit}`, { value: span.value });
    return this.translate.instant(state === 'breached' ? `${DESK_I18N}.sla.overdue` : `${DESK_I18N}.sla.dueIn`, { span: amount });
  }

  isActive(item: WorkspaceItem): boolean {
    return isActive(item.status);
  }

  trackItem(_: number, item: WorkspaceItem): string {
    return item.id;
  }
}
