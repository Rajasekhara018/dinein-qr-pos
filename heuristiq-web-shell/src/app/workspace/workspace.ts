import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Subject, forkJoin } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize, switchMap, takeUntil } from 'rxjs/operators';

import { AppDialogService } from '../shared/services/app-dialog-service';
import { AuthService } from '../shared/services/auth-service';
import { NotificationService } from '../shared/services/notification-service';
import {
  DUE_GROUPS,
  HANDOVER_TICKET_LIMIT,
  QUICK_DUES,
  TASK_WHO,
  TASK_WHO_FILTERS,
  WORKSPACE_GROUPED_PAGE_SIZE,
  WORKSPACE_MAX_BODY,
  WORKSPACE_MAX_TITLE,
  WORKSPACE_MESSAGE_KEYS,
  WORKSPACE_PAGE_SIZE,
  WORKSPACE_PRIORITIES,
  WORKSPACE_PRIORITY_LABEL_KEYS,
  WORKSPACE_PRIVILEGES,
  WORKSPACE_SEARCH_DEBOUNCE_MS,
  WORKSPACE_SHIFTS,
  WORKSPACE_TABS,
  WorkspaceTab,
} from './workspace.constants';
import {
  canEditItem,
  canTickItem,
  currentShift,
  dueGroup,
  dueState,
  fromLocalInput,
  groupOrder,
  openFirst,
  pinnedFirst,
  plainLabels,
  quickDueValue,
  ticketLabel,
  ticketNumbersOf,
  toLocalInput,
} from './workspace.helpers';
import { parseLabels, queueOrder, ticketKey } from './service-desk/service-desk.helpers';
import {
  DueGroup,
  DueState,
  QuickDue,
  TaskWho,
  WorkspaceDraft,
  WorkspaceItem,
  WorkspaceListFilter,
  WorkspacePerson,
  WorkspacePriority,
  WorkspaceShift,
} from './workspace.models';
import { WorkspacePeopleService } from './workspace-people.service';
import { WorkspaceService } from './workspace.service';

/** What the add / edit form holds, as the inputs give it (the due date and reminder are datetime-local text). */
interface WorkspaceForm {
  title: string;
  body: string;
  assignee: WorkspacePerson | null;
  priority: WorkspacePriority;
  due: string;
  remind: string;
  /** Labels of a note or handover note, typed as one text ("billing, vip"). */
  labels: string;
  pinned: boolean;
  shift: WorkspaceShift;
  /** The tickets a handover note hands over, by number. */
  tickets: number[];
}

const emptyForm = (): WorkspaceForm => ({
  title: '', body: '', assignee: null, priority: 'NORMAL', due: '', remind: '', labels: '', pinned: false, shift: currentShift(new Date()), tickets: [],
});

/** One group of a grouped to-do or task list. */
interface ItemGroup {
  key: DueGroup;
  items: WorkspaceItem[];
}

/**
 * The operations workspace: the service desk, personal notes and to-dos, team tasks and shift-handover notes in one screen.
 * To-dos and team tasks are grouped by when they are due (overdue, today, this week, later, no date, done), searched, filtered
 * by priority (and, for tasks, by whose they are), ticked off one by one or several at once; a task can be assigned to a
 * colleague through the people picker or turned into a service-desk ticket. The organization and the user are the session's
 * (the server takes them from the token); who may edit, tick off or delete an item is decided by the server too.
 */
@Component({
  selector: 'app-workspace',
  standalone: false,
  templateUrl: './workspace.html',
  styleUrl: './workspace.scss',
})
export class Workspace implements OnInit, OnDestroy {
  private readonly svc = inject(WorkspaceService);
  private readonly people = inject(WorkspacePeopleService);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly dialog = inject(AppDialogService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();
  private readonly search$ = new Subject<string>();

  readonly tabs = WORKSPACE_TABS;
  readonly privileges = WORKSPACE_PRIVILEGES;
  readonly priorities = WORKSPACE_PRIORITIES;
  readonly priorityKeys = WORKSPACE_PRIORITY_LABEL_KEYS;
  readonly quickDues = QUICK_DUES;
  readonly taskWho = TASK_WHO;
  readonly shifts = WORKSPACE_SHIFTS;
  readonly ticketKey = ticketKey;
  /** The tickets that still need work, urgent and overdue first, offered to a handover note. */
  openTickets: WorkspaceItem[] = [];
  readonly maxTitle = WORKSPACE_MAX_TITLE;
  readonly maxBody = WORKSPACE_MAX_BODY;

  active: WorkspaceTab = WORKSPACE_TABS[0];
  items: WorkspaceItem[] = [];
  groups: ItemGroup[] = [];
  total = 0;
  page = 0;
  loading = false;
  loadError = false;
  saving = false;
  editingId: string | null = null;
  form: WorkspaceForm = emptyForm();

  /** List filters of to-dos and tasks. */
  search = '';
  priorityFilter: WorkspacePriority | '' = '';
  who: TaskWho = 'all';
  showDone = false;
  /** Ids ticked for a bulk action. */
  selected = new Set<string>();
  /** Groups the user folded away. */
  folded = new Set<DueGroup>();
  /** Tasks whose comments and history are open. */
  expanded = new Set<string>();

  get userId(): string | null {
    return this.auth.currentUser?.id ?? null;
  }

  get hasMore(): boolean {
    return this.items.length < this.total;
  }

  /** To-dos and tasks are shown grouped by when they are due; notes and handover notes as a plain list. */
  get grouped(): boolean {
    return this.active.status;
  }

  /** The form needs a title where the tab has one, and text where it has none (handover notes). */
  get canSubmit(): boolean {
    const text = this.active.titled ? this.form.title : this.form.body;
    return !this.saving && text.trim().length > 0;
  }

  get filtered(): boolean {
    return !!(this.search.trim() || this.priorityFilter || this.who !== 'all' || this.showDone);
  }

  ngOnInit(): void {
    this.search$.pipe(debounceTime(WORKSPACE_SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntil(this.destroy$)).subscribe(() => this.reload());
    this.reload();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  selectTab(tab: WorkspaceTab): void {
    if (tab === this.active) return;
    this.active = tab;
    this.cancelEdit();
    this.resetFilters();
    this.reload();
    if (tab.key === 'handover') this.loadOpenTickets();
  }

  // ── list ─────────────────────────────────────────────────────────────────

  reload(): void {
    // The service desk tab loads its own queues.
    if (this.active.key === 'desk') return;
    this.page = 0;
    this.selected.clear();
    this.load(false);
  }

  loadMore(): void {
    this.page += 1;
    this.load(true);
  }

  onSearch(text: string): void {
    this.search = text ?? '';
    this.search$.next(this.search.trim());
  }

  setWho(who: TaskWho): void {
    if (who === this.who) return;
    this.who = who;
    this.reload();
  }

  toggleShowDone(): void {
    this.showDone = !this.showDone;
    this.reload();
  }

  clearFilters(): void {
    this.resetFilters();
    this.reload();
  }

  private resetFilters(): void {
    this.search = '';
    this.priorityFilter = '';
    this.who = 'all';
    this.showDone = false;
    this.selected.clear();
  }

  private load(append: boolean): void {
    this.loading = true;
    this.loadError = false;
    const size = this.grouped ? WORKSPACE_GROUPED_PAGE_SIZE : WORKSPACE_PAGE_SIZE;
    this.svc
      .list(this.listFilter(), this.page, size)
      .pipe(finalize(() => { this.loading = false; this.cdr.markForCheck(); }), takeUntil(this.destroy$))
      .subscribe({
        next: (res) => {
          this.total = res.totalElements;
          const all = append ? [...this.items, ...res.content] : res.content;
          this.items = this.active.kind === 'NOTE' ? pinnedFirst(all) : openFirst(all);
          this.groups = this.groupsOf(this.items);
        },
        error: () => {
          this.loadError = true;
          this.notify.error(this.translate.instant(WORKSPACE_MESSAGE_KEYS.loadFailed));
        },
      });
  }

  /** The request of the active tab and its filters: done items only when asked for. */
  private listFilter(): WorkspaceListFilter {
    const tab = this.active;
    const who = tab.assignee ? TASK_WHO_FILTERS[this.who] : { scope: tab.scope };
    const filter: WorkspaceListFilter = { kinds: [tab.kind], scope: who.scope };
    if ('unassigned' in who && who.unassigned) filter.unassigned = true;
    if (tab.status && !this.showDone) filter.activeOnly = true;
    if (tab.priority && this.priorityFilter) filter.priority = this.priorityFilter;
    const text = this.search.trim();
    if (text) filter.q = text;
    return filter;
  }

  private groupsOf(items: readonly WorkspaceItem[]): ItemGroup[] {
    if (!this.grouped) return [];
    const now = new Date();
    return DUE_GROUPS
      .map((key) => ({ key, items: items.filter((item) => dueGroup(item, now) === key).sort(groupOrder) }))
      .filter((group) => group.items.length > 0);
  }

  toggleThread(item: WorkspaceItem): void {
    if (this.expanded.has(item.id)) this.expanded.delete(item.id);
    else this.expanded.add(item.id);
  }

  toggleFold(group: DueGroup): void {
    if (this.folded.has(group)) this.folded.delete(group);
    else this.folded.add(group);
  }

  // ── form ─────────────────────────────────────────────────────────────────

  /** Creates the item, or saves the one being edited. */
  submit(): void {
    if (!this.canSubmit) return;
    const draft = this.toDraft();
    this.saving = true;
    const request = this.editingId ? this.svc.update(this.editingId, draft) : this.svc.create(draft);
    request.pipe(finalize(() => { this.saving = false; this.cdr.markForCheck(); })).subscribe({
      next: () => {
        this.notify.success(this.translate.instant(WORKSPACE_MESSAGE_KEYS.saved));
        this.cancelEdit();
        this.reload();
      },
      error: (err: unknown) => this.notify.error(this.errorText(err, WORKSPACE_MESSAGE_KEYS.saveFailed)),
    });
  }

  edit(item: WorkspaceItem): void {
    this.editingId = item.id;
    this.form = {
      title: item.title ?? '',
      body: item.body ?? '',
      assignee: item.assigneeId ? { id: item.assigneeId, name: item.assigneeName || item.assigneeId } : null,
      priority: item.priority,
      due: toLocalInput(item.dueAt),
      remind: toLocalInput(item.remindAt),
      labels: plainLabels(item.labels).join(', '),
      pinned: !!item.pinned,
      shift: item.shift ?? currentShift(new Date()),
      tickets: ticketNumbersOf(item.labels),
    };
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form = emptyForm();
  }

  setQuickDue(choice: QuickDue): void {
    this.form.due = quickDueValue(choice, new Date());
  }

  /** Opens the people picker; the chosen colleague becomes the task's assignee. */
  pickAssignee(): void {
    this.people.pick().subscribe((person) => {
      if (person) {
        this.form.assignee = person;
        this.cdr.markForCheck();
      }
    });
  }

  /** Assigns the task to the signed-in user, under their own name (what colleagues will see), else their email. */
  assignFormToMe(): void {
    const me = this.auth.currentUser;
    if (me) this.form.assignee = { id: me.id, name: me.name?.trim() || me.email || me.id };
  }

  clearAssignee(): void {
    this.form.assignee = null;
  }

  // ── actions on items ─────────────────────────────────────────────────────

  /** Ticks an open item off, or re-opens a finished one. */
  toggleDone(item: WorkspaceItem): void {
    this.svc.setStatus(item.id, item.status === 'DONE' ? 'OPEN' : 'DONE').subscribe({
      next: () => this.reload(),
      error: (err: unknown) => this.notify.error(this.errorText(err, WORKSPACE_MESSAGE_KEYS.saveFailed)),
    });
  }

  toggleSelected(item: WorkspaceItem): void {
    if (this.selected.has(item.id)) this.selected.delete(item.id);
    else this.selected.add(item.id);
  }

  /** The ticked items that can still be ticked off by this user. */
  get selectedOpen(): WorkspaceItem[] {
    return this.items.filter((item) => this.selected.has(item.id) && item.status !== 'DONE' && this.canTick(item));
  }

  /** Ticks off every ticked item at once. */
  completeSelected(): void {
    const items = this.selectedOpen;
    if (!items.length) return;
    forkJoin(items.map((item) => this.svc.setStatus(item.id, 'DONE'))).subscribe({
      next: () => {
        this.notify.success(this.translate.instant('workspace.bulk.done', { count: items.length }));
        this.reload();
      },
      error: (err: unknown) => {
        this.notify.error(this.errorText(err, WORKSPACE_MESSAGE_KEYS.saveFailed));
        this.reload();
      },
    });
  }

  clearSelection(): void {
    this.selected.clear();
  }

  /**
   * Turns a team task into a service-desk ticket (same title, description, priority, due time and assignee) and ticks the task
   * off, so the work goes on in the desk's queues.
   */
  convertToTicket(item: WorkspaceItem): void {
    this.dialog.confirm({
      title: this.translate.instant('workspace.convert.title'),
      message: this.translate.instant('workspace.convert.message', { title: item.title ?? '' }),
      icon: 'bi-ticket-perforated',
      confirmText: this.translate.instant('workspace.convert.confirm'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.svc.create({
        kind: 'TICKET', title: item.title, body: item.body, priority: item.priority, dueAt: item.dueAt,
        assigneeId: item.assigneeId, assigneeName: item.assigneeName, category: 'REQUEST',
      }).pipe(switchMap((ticket) => this.svc.setStatus(item.id, 'DONE').pipe(switchMap(() => [ticket])))).subscribe({
        next: (ticket) => {
          this.notify.success(this.translate.instant('workspace.convert.done', { key: `OPS-${ticket.ticketNumber ?? ''}` }));
          this.reload();
        },
        error: (err: unknown) => this.notify.error(this.errorText(err, WORKSPACE_MESSAGE_KEYS.saveFailed)),
      });
    });
  }

  remove(item: WorkspaceItem): void {
    this.dialog
      .confirm({
        title: this.translate.instant(WORKSPACE_MESSAGE_KEYS.deleteTitle),
        message: this.translate.instant(WORKSPACE_MESSAGE_KEYS.deleteMessage),
        icon: 'bi-trash',
        variant: 'warning',
        confirmText: this.translate.instant(WORKSPACE_MESSAGE_KEYS.deleteConfirm),
      })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.svc.remove(item.id).subscribe({
          next: () => {
            this.notify.success(this.translate.instant(WORKSPACE_MESSAGE_KEYS.deleted));
            if (this.editingId === item.id) this.cancelEdit();
            this.reload();
          },
          error: (err: unknown) => this.notify.error(this.errorText(err, WORKSPACE_MESSAGE_KEYS.saveFailed)),
        });
      });
  }

  // ── notes ────────────────────────────────────────────────────────────────

  /** Pins a note to the top of the owner's notes, or unpins it. */
  togglePin(item: WorkspaceItem): void {
    this.svc.update(item.id, { ...this.draftOf(item), pinned: !item.pinned }).subscribe({
      next: () => this.reload(),
      error: (err: unknown) => this.notify.error(this.errorText(err, WORKSPACE_MESSAGE_KEYS.saveFailed)),
    });
  }

  /** Makes a to-do (or a service-desk ticket) of a note; the note stays as it is. */
  noteTo(item: WorkspaceItem, kind: 'TODO' | 'TICKET'): void {
    this.svc.create({ kind, title: item.title, body: item.body, priority: 'NORMAL', category: kind === 'TICKET' ? 'REQUEST' : null })
      .subscribe({
        next: (created) => this.notify.success(kind === 'TICKET'
          ? this.translate.instant('workspace.convert.ticketCreated', { key: ticketKey(created) })
          : this.translate.instant('workspace.convert.todoCreated')),
        error: (err: unknown) => this.notify.error(this.errorText(err, WORKSPACE_MESSAGE_KEYS.saveFailed)),
      });
  }

  // ── handover ─────────────────────────────────────────────────────────────

  /** The tickets that still need work, offered to attach to a handover note (urgent and overdue first). */
  private loadOpenTickets(): void {
    this.svc.list({ kinds: ['TICKET'], scope: 'VISIBLE', activeOnly: true }, 0, HANDOVER_TICKET_LIMIT)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (page) => { this.openTickets = [...page.content].sort(queueOrder); this.cdr.markForCheck(); },
        error: () => { this.openTickets = []; },
      });
  }

  isTicketPicked(ticket: WorkspaceItem): boolean {
    return !!ticket.ticketNumber && this.form.tickets.includes(ticket.ticketNumber);
  }

  toggleTicket(ticket: WorkspaceItem): void {
    const n = ticket.ticketNumber;
    if (!n) return;
    this.form.tickets = this.form.tickets.includes(n) ? this.form.tickets.filter((t) => t !== n) : [...this.form.tickets, n];
  }

  /** Marks a colleague's handover note read; the writer sees who of the next shift has read it. */
  acknowledge(item: WorkspaceItem): void {
    const me = this.auth.currentUser;
    this.svc.acknowledge(item.id, me ? me.name?.trim() || me.email || me.id : null).subscribe({
      next: () => {
        this.notify.success(this.translate.instant('workspace.handover.acknowledged'));
        this.reload();
      },
      error: (err: unknown) => this.notify.error(this.errorText(err, WORKSPACE_MESSAGE_KEYS.saveFailed)),
    });
  }

  hasRead(item: WorkspaceItem): boolean {
    return (item.acknowledgements ?? []).some((ack) => ack.userId === this.userId);
  }

  /** "Read by Ravi, Anita" for a handover note. */
  readBy(item: WorkspaceItem): string {
    return (item.acknowledgements ?? []).map((ack) => ack.userName || this.translate.instant('workspace.badge.colleague')).join(', ');
  }

  ticketsOf(item: WorkspaceItem): number[] {
    return ticketNumbersOf(item.labels);
  }

  labelsOf(item: WorkspaceItem): string[] {
    return plainLabels(item.labels);
  }

  // ── what the template shows ──────────────────────────────────────────────

  isMine(item: WorkspaceItem): boolean {
    return canEditItem(item, this.userId);
  }

  canTick(item: WorkspaceItem): boolean {
    return canTickItem(item, this.userId);
  }

  due(item: WorkspaceItem): DueState {
    return dueState(item, new Date());
  }

  assigneeText(item: WorkspaceItem): string {
    if (!item.assigneeId) return '';
    if (item.assigneeId === this.userId) return this.translate.instant('workspace.badge.assignedToMe');
    return item.assigneeName || this.translate.instant('workspace.badge.assigned');
  }

  priorityKey(priority: WorkspacePriority): string {
    return this.priorityKeys[priority];
  }

  priorityTone(priority: WorkspacePriority): 'danger' | 'warning' | 'info' | 'neutral' {
    return priority === 'URGENT' ? 'danger' : priority === 'HIGH' ? 'warning' : priority === 'LOW' ? 'neutral' : 'info';
  }

  trackItem(_: number, item: WorkspaceItem): string {
    return item.id;
  }

  private toDraft(): WorkspaceDraft {
    const tab = this.active;
    return {
      kind: tab.kind,
      title: tab.titled ? this.form.title : null,
      body: this.form.body,
      assigneeId: tab.assignee ? this.form.assignee?.id ?? null : null,
      assigneeName: tab.assignee ? this.form.assignee?.name ?? null : null,
      priority: tab.priority ? this.form.priority : null,
      dueAt: tab.due ? fromLocalInput(this.form.due) : null,
      remindAt: tab.reminder ? fromLocalInput(this.form.remind) : null,
      labels: this.formLabels(),
      pinned: tab.kind === 'NOTE' && this.form.pinned,
      shift: tab.kind === 'HANDOVER' ? this.form.shift : null,
    };
  }

  /** A note's labels, or a handover note's labels with the tickets it hands over ("ops-42"). */
  private formLabels(): string[] {
    const typed = parseLabels(this.form.labels);
    if (this.active.kind !== 'HANDOVER') return this.active.kind === 'NOTE' ? typed : [];
    return [...this.form.tickets.map(ticketLabel), ...typed];
  }

  /** The item as an edit request, unchanged (the base of pin). */
  private draftOf(item: WorkspaceItem): WorkspaceDraft {
    return {
      title: item.title, body: item.body, assigneeId: item.assigneeId, assigneeName: item.assigneeName, priority: item.priority,
      dueAt: item.dueAt, remindAt: item.remindAt, labels: item.labels ?? [], pinned: !!item.pinned, shift: item.shift ?? null,
    };
  }

  /** The server's own message when it sent one, else the generic text of the action. */
  private errorText(err: unknown, fallbackKey: string): string {
    const body = (err as { error?: { message?: string; responseObject?: { message?: string }[] } })?.error;
    const detail = body?.responseObject?.[0]?.message ?? body?.message;
    return detail ?? this.translate.instant(fallbackKey);
  }
}
