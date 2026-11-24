import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HQ_CONFIG, HqDocumentService, HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, Observable, of } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { ORG_IMPORT_PRIVILEGES, ORG_LADDER_IMPORT_PRIVILEGES } from '../org-structure.constants';
import { OrgImportBatch, OrgLadderImportErrorRow } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { OrgImport } from './org-import';

const batch = (over: Partial<OrgImportBatch> = {}): OrgImportBatch =>
  ({ id: 'b1', status: 'APPLIED', fileName: 'ladders.csv', totalRows: 5, validRows: 4, errorRows: 1, createdCount: 3, updatedCount: 1, message: null, createdBy: 'asha', createdAt: '2026-01-01T00:00:00Z', appliedAt: null, ...over });

describe('OrgImport: the latest import card and the loaded section', () => {
  const api = {
    recentImports: vi.fn((): Observable<OrgImportBatch[]> => of([])),
    importErrors: vi.fn(() => of([])), importBatch: vi.fn(), cancelImport: vi.fn((): Observable<void> => of(undefined)),
    confirmImport: vi.fn((): Observable<OrgImportBatch> => of(batch({ id: 'e1', status: 'APPLYING' }))),
    recentDepartmentImports: vi.fn((): Observable<OrgImportBatch[]> => of([])),
    recentLadderImports: vi.fn((): Observable<OrgImportBatch[]> => of([])),
    ladderImportErrors: vi.fn((): Observable<OrgLadderImportErrorRow[]> => of([{ rowNo: 2, ladder: 'S', position: 'P', rank: 'x', managerial: null, errors: ['RANK_INVALID'] }])),
    ladderImportBatch: vi.fn((): Observable<OrgImportBatch> => of(batch())),
    cancelLadderImport: vi.fn((): Observable<void> => of(undefined)),
    ladders: vi.fn(() => of([])), positions: vi.fn(() => of([])), children: vi.fn(() => of([])),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  let granted: Set<string> | null;
  let routeStep: string | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(): ComponentFixture<OrgImport> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrgImport],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: OrgStructureService, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: OrganizationContextService, useValue: { currentName$: of('Acme') } },
        { provide: HqTimezoneService, useValue: { formatLocale: (v: string) => 'local:' + v } },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: HqDocumentService, useValue: {} },
        { provide: HQ_CONFIG, useValue: {} },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => routeStep } } } },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {});
    translate.use('en');
    const fixture = TestBed.createComponent(OrgImport);
    fixture.detectChanges();
    return fixture;
  }

  const el = (f: ComponentFixture<OrgImport>) => f.nativeElement as HTMLElement;
  const q = (f: ComponentFixture<OrgImport>, id: string) => el(f).querySelector(`[data-testid="${id}"]`);
  const refresh = (f: ComponentFixture<OrgImport>) => { f.changeDetectorRef.markForCheck(); f.detectChanges(); };

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    routeStep = 'ladders';
    sessionStorage.clear();
    dialogs.confirm.mockImplementation(() => of(true));
    api.recentLadderImports.mockImplementation(() => of([]));
    api.recentImports.mockImplementation(() => of([]));
  });

  describe('the latest import card', () => {
    it('is absent when no import was applied, is being applied or waits for a decision', () => {
      api.recentLadderImports.mockImplementation(() => of([batch({ status: 'FAILED' }), batch({ status: 'CANCELLED' })]));
      const f = create();
      expect(q(f, 'latest-import')).toBeNull();
      expect(f.componentInstance.latestImport).toBeNull();
    });

    it('shows file, who and when, and every count, for an applied ladders file, with the ladders created', () => {
      api.recentLadderImports.mockImplementation(() => of([batch({ laddersCreated: 2, errorRows: 0 })]));
      const f = create();
      refresh(f);
      const card = q(f, 'latest-import');
      expect(card?.textContent).toContain('orgStructure.import.latest.title');
      expect(card?.textContent).toContain('orgStructure.import.latest.by');
      expect(card?.querySelectorAll('dl.summary > div').length).toBe(6);
      expect(q(f, 'latest-ladders-created')?.textContent).toBe('2');
      expect(q(f, 'latest-heads-set')).toBeNull();
      expect(q(f, 'latest-stale')).toBeNull();
      expect(q(f, 'latest-show-rejected')).toBeNull();
      expect(q(f, 'latest-cancel-preview')).toBeNull();
    });

    it('shows the heads set for an employee file, and not the ladders created', () => {
      routeStep = 'employees';
      api.recentImports.mockImplementation(() => of([batch({ headsSet: 4, laddersCreated: 9, errorRows: 0 })]));
      const f = create();
      refresh(f);
      expect(q(f, 'latest-heads-set')?.textContent).toBe('4');
      expect(q(f, 'latest-ladders-created')).toBeNull();
    });

    it('leaves out a count the batch does not carry', () => {
      api.recentLadderImports.mockImplementation(() => of([batch({ errorRows: 0 })]));
      const f = create();
      refresh(f);
      expect(q(f, 'latest-ladders-created')).toBeNull();
    });

    it('is the newest of the applied, applying and waiting files', () => {
      api.recentLadderImports.mockImplementation(() => of([
        batch({ id: 'old', createdAt: '2025-01-01T00:00:00Z' }), batch({ id: 'new', status: 'APPLYING', createdAt: '2026-02-01T00:00:00Z' }), batch({ id: 'bad', status: 'FAILED', createdAt: '2026-03-01T00:00:00Z' }),
      ]));
      const f = create();
      expect(f.componentInstance.latestImport?.id).toBe('new');
    });

    it('opens the report with its rejected rows from "Show rejected rows" when the file had some', () => {
      api.recentLadderImports.mockImplementation(() => of([batch()]));
      const f = create();
      refresh(f);
      q(f, 'latest-show-rejected')?.dispatchEvent(new Event('click'));
      expect(f.componentInstance.selected?.id).toBe('b1');
      expect(api.ladderImportErrors).toHaveBeenCalled();
      expect(f.componentInstance.errorRows).toHaveLength(1);
    });

    it('hides "Show rejected rows" from someone who may not read them', () => {
      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create, ORG_LADDER_IMPORT_PRIVILEGES.list, ORG_IMPORT_PRIVILEGES.create]);
      api.recentLadderImports.mockImplementation(() => of([batch()]));
      const f = create();
      refresh(f);
      expect(q(f, 'latest-import')).not.toBeNull();
      expect(q(f, 'latest-show-rejected')).toBeNull();
    });

    it('offers to cancel a preview that was never confirmed, after the usual question', () => {
      api.recentLadderImports.mockImplementation(() => of([batch({ status: 'VALIDATED' })]));
      const f = create();
      refresh(f);
      expect(q(f, 'latest-stale')?.textContent).toContain('orgStructure.import.latest.waiting');
      q(f, 'latest-cancel-preview')?.dispatchEvent(new Event('click'));
      expect(dialogs.confirm).toHaveBeenCalledTimes(1);
      expect(api.cancelLadderImport).toHaveBeenCalledWith('b1');
    });

    it('does not cancel when the question is answered no', () => {
      dialogs.confirm.mockImplementation(() => of(false));
      api.recentLadderImports.mockImplementation(() => of([batch({ status: 'VALIDATED' })]));
      const f = create();
      refresh(f);
      q(f, 'latest-cancel-preview')?.dispatchEvent(new Event('click'));
      expect(api.cancelLadderImport).not.toHaveBeenCalled();
    });

    it('hides the cancel action from someone who may not cancel, and for a file that is not a preview', () => {
      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create, ORG_LADDER_IMPORT_PRIVILEGES.list, ORG_IMPORT_PRIVILEGES.create]);
      api.recentLadderImports.mockImplementation(() => of([batch({ status: 'VALIDATED' })]));
      const hidden = create();
      refresh(hidden);
      expect(q(hidden, 'latest-stale')).not.toBeNull();
      expect(q(hidden, 'latest-cancel-preview')).toBeNull();
      granted = null;
      api.recentLadderImports.mockImplementation(() => of([batch({ status: 'APPLIED' })]));
      const applied = create();
      refresh(applied);
      expect(q(applied, 'latest-cancel-preview')).toBeNull();
    });
  });

  describe('keeping "Loaded now" up to date', () => {
    it('is told to read again after a confirm, after a cancel, when a poll sees a file finish, and when a step opens', () => {
      vi.useFakeTimers();
      routeStep = 'employees';
      const f = create();
      const start = f.componentInstance.loadedRefresh;
      f.componentInstance.setMode('LADDERS');
      expect(f.componentInstance.loadedRefresh).toBeGreaterThan(start);
      const afterOpen = f.componentInstance.loadedRefresh;
      f.componentInstance.setMode('EMPLOYEES');
      f.componentInstance.open(batch({ id: 'e1', status: 'VALIDATED', errorRows: 0 }));
      f.componentInstance.confirm();
      expect(f.componentInstance.loadedRefresh).toBeGreaterThan(afterOpen + 1);
      const afterConfirm = f.componentInstance.loadedRefresh;
      api.importBatch.mockImplementation(() => of(batch({ id: 'e1', status: 'APPLIED' })));
      vi.advanceTimersByTime(3000);
      expect(f.componentInstance.loadedRefresh).toBeGreaterThan(afterConfirm);
      const afterPoll = f.componentInstance.loadedRefresh;
      f.componentInstance.selected = batch({ id: 'e2', status: 'VALIDATED' });
      f.componentInstance.cancel();
      expect(f.componentInstance.loadedRefresh).toBeGreaterThan(afterPoll);
      vi.useRealTimers();
    });
  });
});
