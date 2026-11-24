import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ApiService } from '../shared/services/api-service';
import { AuthService } from '../shared/services/auth-service';
import {
  WORKSPACE_ITEMS_PATH,
  WORKSPACE_ITEM_REF_TYPE,
  WORKSPACE_MAX_PAGE_SIZE,
  WORKSPACE_PAGE_SIZE,
  WORKSPACE_SUMMARY_PATH,
} from './workspace.constants';
import {
  WorkspaceDraft,
  WorkspaceItem,
  WorkspaceListFilter,
  WorkspacePage,
  WorkspaceStatus,
  WorkspaceSummary,
} from './workspace.models';

/**
 * Notes, to-dos, tasks, record notes and handover notes through the gateway (Rider_ConfigService, /api/v1/config/workspace).
 * The organization and the user are the session's: the server takes them from the token, so nothing here sends them.
 */
@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  /** One page of the items the user may see, newest first. */
  list(filter: WorkspaceListFilter = {}, page = 0, size = WORKSPACE_PAGE_SIZE): Observable<WorkspacePage> {
    const safeSize = Math.min(Math.max(size, 1), WORKSPACE_MAX_PAGE_SIZE);
    const safePage = Math.max(page, 0);
    return this.api
      .get<Partial<WorkspacePage> | undefined>(`${WORKSPACE_ITEMS_PATH}${this.query(filter, safePage, safeSize)}`)
      .pipe(
        map((res) => ({
          content: res?.content ?? [],
          totalElements: res?.totalElements ?? 0,
          totalPages: res?.totalPages ?? 0,
          size: res?.size ?? safeSize,
          number: res?.number ?? safePage,
        })),
      );
  }

  create(draft: WorkspaceDraft): Observable<WorkspaceItem> {
    return this.api.post<WorkspaceItem>(WORKSPACE_ITEMS_PATH, { ...draft, actorName: this.actorName() });
  }

  update(id: string, draft: WorkspaceDraft): Observable<WorkspaceItem> {
    return this.api.put<WorkspaceItem>(`${WORKSPACE_ITEMS_PATH}/${encodeURIComponent(id)}`, { ...draft, actorName: this.actorName() });
  }

  /** The comments and history of a team task or ticket, newest first. */
  timeline(itemId: string): Observable<WorkspaceItem[]> {
    return this.list({ kinds: ['RECORD_NOTE', 'ACTIVITY'], scope: 'VISIBLE', refType: WORKSPACE_ITEM_REF_TYPE, refId: itemId }, 0, WORKSPACE_MAX_PAGE_SIZE)
      .pipe(map((page) => page.content));
  }

  /** Comments on a team task or ticket (its owner and assignee are told). */
  comment(itemId: string, text: string): Observable<WorkspaceItem> {
    return this.create({ kind: 'RECORD_NOTE', body: text, refType: WORKSPACE_ITEM_REF_TYPE, refId: itemId });
  }

  /** How the signed-in user is shown on what they write: their name, else their email. */
  private actorName(): string | null {
    const me = this.auth.currentUser;
    return me ? me.name?.trim() || me.email || null : null;
  }

  /** Ticks off or re-opens a to-do or task, or moves a ticket to another status. */
  setStatus(id: string, status: WorkspaceStatus): Observable<WorkspaceItem> {
    return this.api.put<WorkspaceItem>(`${WORKSPACE_ITEMS_PATH}/${encodeURIComponent(id)}/status`, { status, actorName: this.actorName() });
  }

  /** Marks a handover note read by the signed-in user, shown under `name` in its read receipts. */
  acknowledge(id: string, name: string | null): Observable<WorkspaceItem> {
    return this.api.put<WorkspaceItem>(`${WORKSPACE_ITEMS_PATH}/${encodeURIComponent(id)}/status`, { status: 'DONE', actorName: name });
  }

  remove(id: string): Observable<void> {
    return this.api.delete<void>(`${WORKSPACE_ITEMS_PATH}/${encodeURIComponent(id)}`);
  }

  /** The counters of the Today page. */
  summary(): Observable<WorkspaceSummary> {
    return this.api.get<WorkspaceSummary>(WORKSPACE_SUMMARY_PATH);
  }

  /** The query string of a list request: repeated `kind`, then the other filters, then the paging. */
  private query(filter: WorkspaceListFilter, page: number, size: number): string {
    const params = new URLSearchParams();
    (filter.kinds ?? []).forEach((kind) => params.append('kind', kind));
    if (filter.status) params.set('status', filter.status);
    if (filter.priority) params.set('priority', filter.priority);
    if (filter.scope) params.set('scope', filter.scope);
    if (filter.refType) params.set('refType', filter.refType);
    if (filter.refId) params.set('refId', filter.refId);
    if (filter.queue) params.set('queue', filter.queue);
    if (filter.unassigned) params.set('unassigned', 'true');
    if (filter.activeOnly) params.set('activeOnly', 'true');
    if (filter.q) params.set('q', filter.q);
    params.set('page', String(page));
    params.set('size', String(size));
    return `?${params.toString()}`;
  }
}
