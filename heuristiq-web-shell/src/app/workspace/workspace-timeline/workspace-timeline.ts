import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, SimpleChanges, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';

import { AuthService } from '../../shared/services/auth-service';
import { NotificationService } from '../../shared/services/notification-service';
import { TIMELINE_STATUS_KEYS as STATUS_KEYS } from './workspace-timeline.constants';
import { WORKSPACE_MAX_COMMENT, WORKSPACE_PRIORITY_LABEL_KEYS, WORKSPACE_PRIVILEGES } from '../workspace.constants';
import { parseActivity } from '../workspace.helpers';
import { TimelineEntry, WorkspaceItem, WorkspacePriority } from '../workspace.models';
import { WorkspaceService } from '../workspace.service';

/**
 * The comments and the history of one team task or ticket, oldest first, with a box to add a comment. The history (created,
 * assigned, moved, re-prioritized, re-scheduled, edited) is written by the server; a comment tells the item's owner and
 * assignee. Reads and writes go through the workspace API, so the organization and the user are the session's.
 */
@Component({
  selector: 'app-workspace-timeline',
  standalone: false,
  templateUrl: './workspace-timeline.html',
  styleUrl: './workspace-timeline.scss',
})
export class WorkspaceTimeline implements OnChanges, OnDestroy {
  private readonly svc = inject(WorkspaceService);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  /** The task or ticket whose timeline is shown. */
  @Input() itemId: string | null = null;
  /** Bump to read the timeline again (after the item itself changed). */
  @Input() refreshKey = 0;

  readonly privileges = WORKSPACE_PRIVILEGES;
  readonly maxComment = WORKSPACE_MAX_COMMENT;
  entries: TimelineEntry[] = [];
  loading = false;
  loadFailed = false;
  draft = '';
  posting = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['itemId'] || changes['refreshKey']) this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    const id = this.itemId;
    if (!id) {
      this.entries = [];
      return;
    }
    this.loading = true;
    this.loadFailed = false;
    this.svc.timeline(id).pipe(finalize(() => { this.loading = false; this.cdr.markForCheck(); }), takeUntil(this.destroy$)).subscribe({
      next: (rows) => { this.entries = rows.map((row) => this.entryOf(row)).reverse(); },
      error: () => { this.loadFailed = true; },
    });
  }

  post(): void {
    const id = this.itemId;
    const text = this.draft.trim();
    if (!id || !text || this.posting) return;
    this.posting = true;
    this.svc.comment(id, text).pipe(finalize(() => { this.posting = false; this.cdr.markForCheck(); }), takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.draft = '';
        this.load();
      },
      error: () => this.notify.error(this.translate.instant('workspace.timeline.postFailed')),
    });
  }

  trackEntry(_: number, entry: TimelineEntry): string {
    return entry.id;
  }

  private entryOf(row: WorkspaceItem): TimelineEntry {
    const mine = !!this.auth.currentUser && row.ownerId === this.auth.currentUser.id;
    const author = mine ? this.translate.instant('workspace.timeline.you') : row.ownerName || this.translate.instant('workspace.badge.colleague');
    if (row.kind !== 'ACTIVITY') {
      return { id: row.id, kind: 'comment', author, mine, at: row.createdAt, text: row.body ?? '', key: '', params: {} };
    }
    const { code, values } = parseActivity(row.body);
    return { id: row.id, kind: 'activity', author, mine, at: row.createdAt, text: '', key: `workspace.timeline.activity.${code}`, params: this.paramsOf(code, values) };
  }

  /** The values of a history line in words: statuses and priorities translated, a due time formatted. */
  private paramsOf(code: string, values: string[]): Record<string, string> {
    const word = (keys: Readonly<Record<string, string>>, v: string | undefined): string => {
      const key = v ? keys[v] : undefined;
      return key ? this.translate.instant(key) : v ?? '';
    };
    switch (code) {
      case 'STATUS':
        return { from: word(STATUS_KEYS, values[0]), to: word(STATUS_KEYS, values[1]) };
      case 'PRIORITY':
        return {
          from: word(WORKSPACE_PRIORITY_LABEL_KEYS, values[0] as WorkspacePriority),
          to: word(WORKSPACE_PRIORITY_LABEL_KEYS, values[1] as WorkspacePriority),
        };
      case 'ASSIGNED':
        return { name: values[0] || this.translate.instant('workspace.desk.unassigned') };
      case 'DUE': {
        const date = values[0] ? new Date(values[0]) : null;
        return { date: date && !Number.isNaN(date.getTime()) ? date.toLocaleString() : this.translate.instant('workspace.quickDue.none') };
      }
      default:
        return {};
    }
  }
}
