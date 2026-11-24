import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../shared/services/auth-service';
import { NotificationService } from '../../shared/services/notification-service';
import { WorkspaceItem } from '../workspace.models';
import { WorkspaceService } from '../workspace.service';
import { WORKSPACE_PRIVILEGES } from '../workspace.constants';
import { RecordNotes } from './record-notes';

const note = (over: Partial<WorkspaceItem> = {}): WorkspaceItem => ({
  id: 'n1', kind: 'RECORD_NOTE', title: null, body: 'called the member', ownerId: 'alice', assigneeId: null, visibility: 'SHARED',
  status: 'OPEN', priority: 'NORMAL', dueAt: null, remindAt: null, refType: 'ONBOARDED_USER', refId: 's1',
  createdAt: '2026-10-24T10:00:00Z', updatedAt: '2026-10-24T10:00:00Z', completedAt: null, ...over,
});

describe('RecordNotes', () => {
  const svc = { list: vi.fn(), create: vi.fn(), remove: vi.fn() };
  const notify = { error: vi.fn() };
  let granted: Set<string> | null = null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((x) => granted?.has(x)),
  };
  let fixture: ComponentFixture<RecordNotes>;
  let c: RecordNotes;

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    svc.list.mockReturnValue(of({ content: [note()], totalElements: 1, totalPages: 1, size: 20, number: 0 }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [RecordNotes, TranslateModule.forRoot()],
      providers: [
        { provide: WorkspaceService, useValue: svc },
        { provide: AuthService, useValue: { currentUser: { id: 'alice' } } },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    fixture = TestBed.createComponent(RecordNotes);
    c = fixture.componentInstance;
    c.refType = 'ONBOARDED_USER';
    c.refId = 's1';
  });

  it('loads the notes of the record it is given, and nothing before it has one', () => {
    c.refId = '';
    c.ngOnChanges();
    expect(svc.list).not.toHaveBeenCalled();

    c.refId = 's1';
    c.ngOnChanges();

    expect(svc.list).toHaveBeenCalledWith({ kinds: ['RECORD_NOTE'], refType: 'ONBOARDED_USER', refId: 's1' });
    expect(c.notes).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('shows a load error without breaking the screen', () => {
    svc.list.mockReturnValue(throwError(() => new Error('down')));

    c.ngOnChanges();

    expect(c.loadError).toBe(true);
    expect(c.notes).toEqual([]);
  });

  it('adds a note on this record, clears the box and reloads; an empty box adds nothing', () => {
    svc.create.mockReturnValue(of(note()));
    c.add();
    expect(svc.create).not.toHaveBeenCalled();

    c.text = 'spoke to the member';
    c.add();

    expect(svc.create).toHaveBeenCalledWith({ kind: 'RECORD_NOTE', body: 'spoke to the member', refType: 'ONBOARDED_USER', refId: 's1' });
    expect(c.text).toBe('');
    expect(svc.list).toHaveBeenCalledTimes(1);
  });

  it('reports a failed add and keeps the text', () => {
    svc.create.mockReturnValue(throwError(() => new Error('x')));
    c.text = 'keep me';

    c.add();

    expect(notify.error).toHaveBeenCalledWith('workspace.messages.saveFailed');
    expect(c.text).toBe('keep me');
    expect(c.saving).toBe(false);
  });

  it('deletes a note and reloads, and reports a failure', () => {
    svc.remove.mockReturnValueOnce(of(undefined));
    c.remove(note());
    expect(svc.remove).toHaveBeenCalledWith('n1');
    expect(svc.list).toHaveBeenCalledTimes(1);

    svc.remove.mockReturnValueOnce(throwError(() => new Error('x')));
    c.remove(note());
    expect(notify.error).toHaveBeenCalledWith('workspace.messages.saveFailed');
  });

  it('lets only the author delete', () => {
    expect(c.isMine(note())).toBe(true);
    expect(c.isMine(note({ ownerId: 'bob' }))).toBe(false);
    expect(c.trackNote(0, note({ id: 'k' }))).toBe('k');
  });

  describe('rendering', () => {
    const el = (): HTMLElement => fixture.nativeElement as HTMLElement;

    it('shows the notes, the add form, and the delete button on my own notes only', () => {
      svc.list.mockReturnValue(of({ content: [note(), note({ id: 'n2', ownerId: 'bob' })], totalElements: 2, totalPages: 1, size: 20, number: 0 }));

      c.ngOnChanges();
      fixture.detectChanges();

      expect(el().querySelector('.rn__form')).not.toBeNull();
      expect(el().querySelectorAll('.rn__note')).toHaveLength(2);
      expect(el().querySelectorAll('.rn__delete')).toHaveLength(1);
    });

    it('shows the empty message when there are no notes', () => {
      svc.list.mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 }));

      c.ngOnChanges();
      fixture.detectChanges();

      expect(el().querySelector('.rn__state')?.textContent).toContain('workspace.recordNotes.empty');
    });

    it('hides the add form without the create privilege', () => {
      granted = new Set([WORKSPACE_PRIVILEGES.list]);

      c.ngOnChanges();
      fixture.detectChanges();

      expect(el().querySelector('.rn__form')).toBeNull();
      expect(el().querySelector('.rn__list')).not.toBeNull();
    });

    it('shows nothing without the list privilege', () => {
      granted = new Set();

      c.ngOnChanges();
      fixture.detectChanges();

      expect(el().querySelector('.rn__list')).toBeNull();
      expect(el().querySelector('.rn__form')).toBeNull();
    });
  });
});
