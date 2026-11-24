import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiService } from '../shared/services/api-service';
import { WorkspaceService } from './workspace.service';

describe('WorkspaceService', () => {
  const api = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() };
  let service: WorkspaceService;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    service = TestBed.inject(WorkspaceService);
  });

  it('lists with repeated kinds, the filters and the paging in the query string', () => {
    api.get.mockReturnValue(of({ content: [{ id: 'a' }], totalElements: 1, totalPages: 1, size: 20, number: 0 }));
    let page: unknown;

    service.list({ kinds: ['NOTE', 'TODO'], status: 'OPEN', scope: 'MINE', refType: 'P', refId: 'p 1' }, 2, 10).subscribe((p) => (page = p));

    expect(api.get).toHaveBeenCalledWith('/v1/config/workspace/items?kind=NOTE&kind=TODO&status=OPEN&scope=MINE&refType=P&refId=p+1&page=2&size=10');
    expect(page).toMatchObject({ totalElements: 1, number: 0 });
  });

  it('can ask for only the high-priority open items the user is involved in', () => {
    api.get.mockReturnValue(of(undefined));

    service.list({ kinds: ['TODO', 'TASK'], status: 'OPEN', priority: 'HIGH', scope: 'INVOLVED' }).subscribe();

    expect(api.get).toHaveBeenCalledWith('/v1/config/workspace/items?kind=TODO&kind=TASK&status=OPEN&priority=HIGH&scope=INVOLVED&page=0&size=20');
  });

  it('clamps the page and the size and fills in a missing answer', () => {
    api.get.mockReturnValue(of(undefined));
    let page: unknown;

    service.list({}, -3, 1000).subscribe((p) => (page = p));

    expect(api.get).toHaveBeenCalledWith('/v1/config/workspace/items?page=0&size=50');
    expect(page).toEqual({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 0 });
  });

  it('creates, edits, changes the status and deletes through the item paths', () => {
    api.post.mockReturnValue(of({ id: 'n' }));
    api.put.mockReturnValue(of({ id: 'n' }));
    api.delete.mockReturnValue(of(undefined));

    service.create({ kind: 'NOTE', title: 't' }).subscribe();
    service.update('a/b', { title: 'x' }).subscribe();
    service.setStatus('a/b', 'DONE').subscribe();
    service.remove('a/b').subscribe();

    expect(api.post).toHaveBeenCalledWith('/v1/config/workspace/items', { kind: 'NOTE', title: 't' });
    expect(api.put).toHaveBeenNthCalledWith(1, '/v1/config/workspace/items/a%2Fb', { title: 'x' });
    expect(api.put).toHaveBeenNthCalledWith(2, '/v1/config/workspace/items/a%2Fb/status', { status: 'DONE' });
    expect(api.delete).toHaveBeenCalledWith('/v1/config/workspace/items/a%2Fb');
  });

  it('reads the summary', () => {
    api.get.mockReturnValue(of({ openTodos: 1, openAssigned: 2, overdue: 3, remindersDue: 4 }));
    let summary: unknown;

    service.summary().subscribe((s) => (summary = s));

    expect(api.get).toHaveBeenCalledWith('/v1/config/workspace/summary');
    expect(summary).toEqual({ openTodos: 1, openAssigned: 2, overdue: 3, remindersDue: 4 });
  });
});
