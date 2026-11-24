import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { QA_DASHBOARD_PRIVILEGES, QA_MESSAGE_KEYS, QA_RUNNABLE_ENVIRONMENTS } from '../qa-dashboard.constants';
import { accessMock, QA_SHARED_DECLARATIONS, routerStub, routeStub, testSet } from '../qa-dashboard.fixtures';
import { QaTestSet, QaTestSetInput, QaTriggerResult } from '../qa-dashboard.models';
import { QaDashboardService } from '../qa-dashboard.service';
import { QaTestSetsPage } from './qa-test-sets';

const P = QA_DASHBOARD_PRIVILEGES;
type FormData = { title: string; fields?: Array<{ key: string; value?: unknown; options?: Array<{ value: string }> }> };

describe('QaTestSetsPage', () => {
  const svc = {
    testSets: vi.fn((): Observable<QaTestSet[]> => of([testSet('s1'), testSet('s2', { name: 'Smoke API', description: '' })])),
    createTestSet: vi.fn((_i: QaTestSetInput): Observable<QaTestSet> => of(testSet('new'))),
    updateTestSet: vi.fn((_id: string, _i: QaTestSetInput): Observable<QaTestSet> => of(testSet('s1'))),
    deleteTestSet: vi.fn((_id: string): Observable<void> => of(undefined)),
    runTestSet: vi.fn((_id: string, _e: string): Observable<QaTriggerResult> => of({ release: 'QA_REG_OCT26_R3', queued: true, queueUrl: null })),
    facets: vi.fn(),
  };
  const dialogs = {
    form: vi.fn((_d: FormData): Observable<Record<string, string> | null> => of(null)),
    confirm: vi.fn((_d: unknown): Observable<boolean> => of(true)),
  };
  const notify = { success: vi.fn(), error: vi.fn() };
  let granted: Set<string> | null;
  let route: ReturnType<typeof routeStub>;
  let fixture: ComponentFixture<QaTestSetsPage>;

  function create(params: Record<string, string> = {}): QaTestSetsPage {
    route = routeStub(params);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaTestSetsPage, ...QA_SHARED_DECLARATIONS],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: QaDashboardService, useValue: svc },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
        { provide: ActivatedRoute, useValue: route.route },
        { provide: Router, useValue: routerStub() },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
      ],
    });
    fixture = TestBed.createComponent(QaTestSetsPage);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = (selector: string): HTMLElement | null => el().querySelector(selector);
  const valid = { name: 'Nightly UI', suite: 'REGRESSION', tagExpression: '@regression', description: 'Every night' };

  beforeEach(() => {
    vi.clearAllMocks();
    svc.testSets.mockImplementation(() => of([testSet('s1'), testSet('s2', { name: 'Smoke API', description: '' })]));
    svc.createTestSet.mockImplementation(() => of(testSet('new')));
    svc.updateTestSet.mockImplementation(() => of(testSet('s1')));
    svc.deleteTestSet.mockImplementation(() => of(undefined));
    svc.runTestSet.mockImplementation(() => of({ release: 'QA_REG_OCT26_R3', queued: true, queueUrl: null }));
    dialogs.form.mockImplementation(() => of(null));
    dialogs.confirm.mockImplementation(() => of(true));
    granted = null;
  });

  it('lists the test sets of the workspace', () => {
    create();
    expect(svc.testSets).toHaveBeenCalledWith();
    const rows = el().querySelectorAll('.qa-set-row');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Nightly UI');
    expect(rows[0].textContent).toContain('@regression and not @slow');
    expect(rows[1].querySelectorAll('td')[3].textContent?.trim()).toBe('—');
  });

  it('says there are no test sets', () => {
    svc.testSets.mockImplementation(() => of([]));
    create();
    expect(q('.qa-empty')).not.toBeNull();
    expect(q('.qa-sets')).toBeNull();
  });

  it('shows only the test sets of the open workspace (the API lists them all)', () => {
    svc.testSets.mockImplementation(() => of([testSet('s1'), testSet('s2', { workspace: 'API', name: 'Smoke API' })]));
    const page = create();
    expect(page.testSets.map((s) => s.id)).toEqual(['s1']);
    route.setParams({ workspace: 'API' });
    expect(page.testSets.map((s) => s.id)).toEqual(['s2']);
    expect(svc.testSets).toHaveBeenCalledTimes(2);
  });

  it('shows an error when the list cannot be loaded and a loading message while waiting', () => {
    svc.testSets.mockImplementation(() => throwError(() => new Error('down')));
    const page = create();
    expect(page.hasError).toBe(true);
    expect(page.testSets).toEqual([]);
    expect(q('.qa-state--error')).not.toBeNull();
    svc.testSets.mockImplementation(() => NEVER);
    create();
    expect(q('.qa-state')?.textContent).toContain('qaDashboard.loading');
  });

  describe('create', () => {
    it('opens an empty form with the suites and saves what was entered in this workspace', () => {
      dialogs.form.mockImplementation(() => of({ ...valid, name: '  Nightly UI  ' }));
      create({ workspace: 'API' });
      (q('.qa-create') as HTMLButtonElement).click();
      const data = dialogs.form.mock.calls[0][0];
      expect(data.title).toBe(QA_MESSAGE_KEYS.formCreateTitle);
      expect(data.fields?.map((f) => f.key)).toEqual(['name', 'suite', 'tagExpression', 'description']);
      expect(data.fields?.find((f) => f.key === 'suite')?.options?.map((o) => o.value)).toEqual(['REGRESSION', 'SMOKE', 'OTHER']);
      expect(svc.createTestSet).toHaveBeenCalledWith({ name: 'Nightly UI', workspace: 'API', suite: 'REGRESSION', tagExpression: '@regression', description: 'Every night' });
      expect(notify.success).toHaveBeenCalledWith(QA_MESSAGE_KEYS.setSaved);
      expect(svc.testSets).toHaveBeenCalledTimes(2);
    });

    it('does nothing when the form is cancelled', () => {
      create();
      (q('.qa-create') as HTMLButtonElement).click();
      expect(svc.createTestSet).not.toHaveBeenCalled();
    });

    it('explains a wrong value and opens the form again with what was typed', () => {
      dialogs.form
        .mockImplementationOnce(() => of({ ...valid, tagExpression: 'not a tag' }))
        .mockImplementationOnce(() => of(valid));
      const page = create();
      page.openCreate();
      expect(notify.error).toHaveBeenCalledWith(QA_MESSAGE_KEYS.fieldError);
      expect(dialogs.form).toHaveBeenCalledTimes(2);
      expect(dialogs.form.mock.calls[1][0].fields?.find((f) => f.key === 'tagExpression')?.value).toBe('not a tag');
      expect(svc.createTestSet).toHaveBeenCalledTimes(1);
    });

    it.each([
      ['name', { name: 'ab' }],
      ['suite', { suite: '' }],
      ['description', { description: 'x'.repeat(501) }],
    ])('refuses a wrong %s', (_field, patch) => {
      dialogs.form.mockImplementationOnce(() => of({ ...valid, ...patch })).mockImplementation(() => of(null));
      create().openCreate();
      expect(notify.error).toHaveBeenCalledTimes(1);
      expect(svc.createTestSet).not.toHaveBeenCalled();
    });

    it('reports a failed save', () => {
      dialogs.form.mockImplementation(() => of(valid));
      svc.createTestSet.mockImplementation(() => throwError(() => new Error('down')));
      create().openCreate();
      expect(notify.error).toHaveBeenCalledWith(QA_MESSAGE_KEYS.setSaveFailed);
    });

    it('does not offer or open the form without the create privilege', () => {
      granted = new Set([P.testSetList]);
      const page = create();
      expect(q('.qa-create')).toBeNull();
      page.openCreate();
      expect(dialogs.form).not.toHaveBeenCalled();
    });
  });

  describe('edit', () => {
    it('opens the form with the current values and sends the version it was based on', () => {
      dialogs.form.mockImplementation(() => of({ ...valid, description: 'Changed' }));
      create();
      (q('.qa-edit') as HTMLButtonElement).click();
      const data = dialogs.form.mock.calls[0][0];
      expect(data.title).toBe(QA_MESSAGE_KEYS.formEditTitle);
      expect(data.fields?.find((f) => f.key === 'name')?.value).toBe('Nightly UI');
      expect(svc.updateTestSet).toHaveBeenCalledWith('s1', expect.objectContaining({ description: 'Changed', version: 2, workspace: 'UI' }));
      expect(svc.createTestSet).not.toHaveBeenCalled();
    });

    it('does not offer or open the form without the update privilege', () => {
      granted = new Set([P.testSetList]);
      const page = create();
      expect(q('.qa-edit')).toBeNull();
      page.openEdit(testSet('s1'));
      expect(dialogs.form).not.toHaveBeenCalled();
    });
  });

  describe('delete', () => {
    it('asks first, then deletes and reloads', () => {
      create();
      (q('.qa-delete') as HTMLButtonElement).click();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: QA_MESSAGE_KEYS.setDeleteTitle, variant: 'danger' }));
      expect(svc.deleteTestSet).toHaveBeenCalledWith('s1');
      expect(notify.success).toHaveBeenCalledWith(QA_MESSAGE_KEYS.setDeleted);
      expect(svc.testSets).toHaveBeenCalledTimes(2);
    });

    it('does nothing when the user declines', () => {
      dialogs.confirm.mockImplementation(() => of(false));
      create();
      (q('.qa-delete') as HTMLButtonElement).click();
      expect(svc.deleteTestSet).not.toHaveBeenCalled();
    });

    it('reports a failed delete', () => {
      svc.deleteTestSet.mockImplementation(() => throwError(() => new Error('down')));
      const page = create();
      page.confirmDelete(testSet('s1'));
      expect(notify.error).toHaveBeenCalledWith(QA_MESSAGE_KEYS.setDeleteFailed);
      expect(page.busyId).toBeNull();
    });

    it('does not offer or run it without the delete privilege', () => {
      granted = new Set([P.testSetList]);
      const page = create();
      expect(q('.qa-delete')).toBeNull();
      page.confirmDelete(testSet('s1'));
      expect(dialogs.confirm).not.toHaveBeenCalled();
    });
  });

  describe('run on Jenkins', () => {
    it('asks for an environment that is never live, then starts the run and names the release', () => {
      dialogs.form.mockImplementation(() => of({ environment: 'uat' }));
      create();
      (q('.qa-run') as HTMLButtonElement).click();
      const field = dialogs.form.mock.calls[0][0].fields?.[0];
      expect(field?.options?.map((o) => o.value)).toEqual([...QA_RUNNABLE_ENVIRONMENTS]);
      expect(field?.options?.map((o) => o.value)).not.toContain('live');
      expect(svc.runTestSet).toHaveBeenCalledWith('s1', 'uat');
      expect(notify.success).toHaveBeenCalledWith(QA_MESSAGE_KEYS.runStarted);
    });

    it('does nothing when no environment is chosen', () => {
      dialogs.form.mockImplementation(() => of(null));
      create().openRun(testSet('s1'));
      expect(svc.runTestSet).not.toHaveBeenCalled();
    });

    it('reports a run that could not be started', () => {
      dialogs.form.mockImplementation(() => of({ environment: 'qa' }));
      svc.runTestSet.mockImplementation(() => throwError(() => new Error('down')));
      const page = create();
      page.openRun(testSet('s1'));
      expect(notify.error).toHaveBeenCalledWith(QA_MESSAGE_KEYS.runFailed);
      expect(page.busyId).toBeNull();
    });

    it('does not offer or start a run without the trigger privilege', () => {
      granted = new Set([P.testSetList]);
      const page = create();
      expect(q('.qa-run')).toBeNull();
      page.openRun(testSet('s1'));
      expect(dialogs.form).not.toHaveBeenCalled();
    });
  });

  it('shortens a long description and tracks rows by id', () => {
    const page = create();
    expect(page.summary('x'.repeat(300)).length).toBe(121);
    expect(page.summary(null)).toBe('');
    expect(page.trackBySet(0, testSet('s7'))).toBe('s7');
  });

  it('asks for nothing without the list privilege', () => {
    granted = new Set([P.testSetCreate]);
    create();
    expect(svc.testSets).not.toHaveBeenCalled();
    expect(q('.qa-create')).toBeNull();
  });
});
