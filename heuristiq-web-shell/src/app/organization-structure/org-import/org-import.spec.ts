import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HQ_CONFIG, HqDocumentService, HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { IMPORT_POLL_INTERVAL_MS, IMPORT_TEMPLATE_FILE_NAME, IMPORT_SAMPLE_FILE_NAME, ORG_IMPORT_PRIVILEGES, ORG_PAGE_SIZE } from '../org-structure.constants';
import { OrgImportBatch, OrgImportErrorRow } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { OrgImport } from './org-import';

const batch = (over: Partial<OrgImportBatch> = {}): OrgImportBatch =>
  ({ id: 'b1', status: 'VALIDATED', fileName: 'staff.csv', totalRows: 10, validRows: 8, errorRows: 2, createdCount: 0, updatedCount: 0, message: null, createdBy: 'u', createdAt: '2026-01-01T00:00:00Z', appliedAt: null, ...over });
const errorRow = (no: number, errors: string[] = ['EMAIL_INVALID']): OrgImportErrorRow => ({ rowNo: no, employeeCode: 'E' + no, fullName: null, errors });

describe('OrgImport', () => {
  const api = {
    recentImports: vi.fn((): Observable<OrgImportBatch[]> => of([batch()])),
    createImport: vi.fn((): Observable<OrgImportBatch> => of(batch())),
    importBatch: vi.fn((): Observable<OrgImportBatch> => of(batch())),
    importErrors: vi.fn((): Observable<OrgImportErrorRow[]> => of([errorRow(3), errorRow(4, ['DEPARTMENT_UNKNOWN:HR', 'MYSTERY'])])),
    confirmImport: vi.fn((): Observable<OrgImportBatch> => of(batch({ status: 'APPLYING' }))),
    cancelImport: vi.fn((): Observable<void> => of(undefined)),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  let granted: Set<string> | null;
  /** The ?step= of the address; these specs open on the employee step unless a test says otherwise. */
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
    translate.setTranslation('en', { orgStructure: { import: { status: { VALIDATED: 'Validated' }, errors: { EMAIL_INVALID: 'Bad email', DEPARTMENT_UNKNOWN: 'Unknown department {{detail}}' } } } });
    translate.use('en');
    const fixture = TestBed.createComponent(OrgImport);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    routeStep = 'employees';
    sessionStorage.clear();
    vi.clearAllMocks();
    vi.useFakeTimers();
    granted = null;
    dialogs.confirm.mockImplementation(() => of(true));
  });
  afterEach(() => vi.useRealTimers());

  it('loads recent imports and defines the report columns', () => {
    const c = create().componentInstance;
    expect(api.recentImports).toHaveBeenCalledWith(ORG_PAGE_SIZE);
    expect(c.recent).toHaveLength(1);
    expect(c.isLoading).toBe(false);
    const [, status, , , uploaded] = c.recentColumns;
    expect(status.render?.(c.recent[0])).toBe('Validated');
    expect(uploaded.render?.(c.recent[0])).toBe('local:2026-01-01T00:00:00Z');
    expect(c.errorColumns.map((x) => x.key)).toEqual(['rowNo', 'employeeCode', 'fullName', 'problems']);
  });

  it('reports a failed load of recent imports', () => {
    api.recentImports.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Nope' } })));
    const c = create().componentInstance;
    expect(notify.error).toHaveBeenCalledWith('Nope');
    expect(c.isLoading).toBe(false);
  });

  it('works out when the open batch may be confirmed or cancelled', () => {
    const c = create().componentInstance;
    expect(c.canConfirm).toBe(false);
    expect(c.canCancel).toBe(false);
    c.selected = batch();
    expect([c.canConfirm, c.canCancel]).toEqual([true, true]);
    c.selected = batch({ status: 'FAILED' });
    expect([c.canConfirm, c.canCancel]).toEqual([true, false]);
    c.selected = batch({ validRows: 0 });
    expect(c.canConfirm).toBe(false);
    c.selected = batch({ status: 'APPLIED' });
    expect([c.canConfirm, c.canCancel]).toEqual([false, false]);
  });

  describe('opening a batch', () => {
    it('reads the rejected rows with translated problems, falling back to the raw text', () => {
      const c = create().componentInstance;
      c.open(batch());
      expect(api.importErrors).toHaveBeenCalledWith('b1', 0, ORG_PAGE_SIZE);
      expect(c.errorRows).toEqual([
        { rowNo: 3, employeeCode: 'E3', fullName: '', problems: 'Bad email' },
        { rowNo: 4, employeeCode: 'E4', fullName: '', problems: 'Unknown department HR; MYSTERY' },
      ]);
      expect(c.hasMoreErrors).toBe(false);
    });

    it('skips the error lookup for a clean batch', () => {
      const c = create().componentInstance;
      c.open(batch({ errorRows: 0 }));
      expect(api.importErrors).not.toHaveBeenCalled();
      c.selected = null;
      c.loadMoreErrors();
      expect(api.importErrors).not.toHaveBeenCalled();
    });

    it('pages through errors after the last row seen', () => {
      const c = create().componentInstance;
      api.importErrors.mockReturnValueOnce(of(Array.from({ length: ORG_PAGE_SIZE }, (_, i) => errorRow(i + 1))));
      c.open(batch());
      expect(c.hasMoreErrors).toBe(true);
      api.importErrors.mockReturnValueOnce(of([errorRow(99)]));
      c.loadMoreErrors();
      expect(api.importErrors).toHaveBeenLastCalledWith('b1', ORG_PAGE_SIZE, ORG_PAGE_SIZE);
      expect(c.errorRows).toHaveLength(ORG_PAGE_SIZE + 1);
      expect(c.hasMoreErrors).toBe(false);
      api.importErrors.mockReturnValueOnce(of([]));
      c.loadMoreErrors();
      expect(api.importErrors).toHaveBeenLastCalledWith('b1', 99, ORG_PAGE_SIZE);
    });

    it('reports when the errors cannot be read', () => {
      const c = create().componentInstance;
      api.importErrors.mockReturnValueOnce(throwError(() => ({})));
      c.open(batch());
      expect(notify.error).toHaveBeenCalledWith('orgStructure.import.messages.loadFailed');
    });
  });

  describe('uploading', () => {
    it('stages the stored document, opens its report and refreshes the list', () => {
      const c = create().componentInstance;
      c.onUploaded({ id: 'doc1' } as never);
      expect(api.createImport).toHaveBeenCalledWith('doc1');
      expect(c.selected?.id).toBe('b1');
      expect(c.busy).toBe(false);
      expect(api.recentImports).toHaveBeenCalledTimes(2);
    });

    it('reports a rejected upload', () => {
      const c = create().componentInstance;
      api.createImport.mockReturnValueOnce(throwError(() => ({})));
      c.onUploaded({ id: 'doc1' } as never);
      expect(notify.error).toHaveBeenCalledWith('orgStructure.import.messages.uploadFailed');
      expect(c.busy).toBe(false);
    });
  });

  describe('confirming', () => {
    it('applies the import after confirmation and follows it until it settles', () => {
      const c = create().componentInstance;
      c.selected = batch({ errorRows: 0 });
      api.importBatch.mockReturnValueOnce(of(batch({ status: 'APPLYING' }))).mockReturnValueOnce(of(batch({ status: 'APPLIED', createdCount: 8 })));
      c.confirm();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ message: 'orgStructure.import.confirmDialog.message' }));
      expect(api.confirmImport).toHaveBeenCalledWith('b1');
      expect(c.selected?.status).toBe('APPLYING');
      expect(notify.success).toHaveBeenCalledWith('orgStructure.import.messages.started');
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS);
      expect(c.selected?.status).toBe('APPLYING');
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS);
      expect(c.selected?.status).toBe('APPLIED');
      expect(c.selected?.createdCount).toBe(8);
      expect(api.recentImports).toHaveBeenCalledTimes(3); // refreshed once it settled
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS * 3);
      expect(api.importBatch).toHaveBeenCalledTimes(2); // polling stopped
    });

    it('does nothing when declined or when no batch is open', () => {
      const c = create().componentInstance;
      c.confirm();
      expect(dialogs.confirm).not.toHaveBeenCalled();
      c.selected = batch();
      dialogs.confirm.mockImplementation(() => of(false));
      c.confirm();
      expect(api.confirmImport).not.toHaveBeenCalled();
    });

    it('reports a failed confirm', () => {
      const c = create().componentInstance;
      c.selected = batch();
      api.confirmImport.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Already applied' } })));
      c.confirm();
      expect(notify.error).toHaveBeenCalledWith('Already applied');
      expect(c.busy).toBe(false);
    });

    it('reports a failure while following the import', () => {
      const c = create().componentInstance;
      c.open(batch({ status: 'APPLYING', errorRows: 0 }));
      api.importBatch.mockReturnValueOnce(throwError(() => ({})));
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS);
      expect(notify.error).toHaveBeenCalledWith('orgStructure.import.messages.loadFailed');
    });

    it('stops following when the screen is destroyed', () => {
      const c = create().componentInstance;
      c.open(batch({ status: 'APPLYING', errorRows: 0 }));
      c.ngOnDestroy();
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS * 2);
      expect(api.importBatch).not.toHaveBeenCalled();
    });
  });

  describe('cancelling', () => {
    it('cancels after a danger confirmation and reloads the batch', () => {
      const c = create().componentInstance;
      c.selected = batch();
      api.importBatch.mockReturnValueOnce(of(batch({ status: 'CANCELLED' })));
      c.cancel();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger' }));
      expect(api.cancelImport).toHaveBeenCalledWith('b1');
      expect(notify.success).toHaveBeenCalledWith('orgStructure.import.messages.cancelled');
      expect(c.selected?.status).toBe('CANCELLED');
    });

    it('does nothing when declined, with no batch, and reports failures', () => {
      const c = create().componentInstance;
      c.cancel();
      c.selected = batch();
      dialogs.confirm.mockImplementation(() => of(false));
      c.cancel();
      expect(api.cancelImport).not.toHaveBeenCalled();
      dialogs.confirm.mockImplementation(() => of(true));
      api.cancelImport.mockReturnValueOnce(throwError(() => ({})));
      c.cancel();
      expect(notify.error).toHaveBeenCalledWith('orgStructure.import.messages.cancelFailed');
      api.cancelImport.mockReturnValueOnce(of(undefined));
      api.importBatch.mockReturnValueOnce(throwError(() => ({})));
      c.cancel();
      expect(notify.error).toHaveBeenLastCalledWith('orgStructure.import.messages.loadFailed');
    });
  });

  describe('downloads', () => {
    it('saves the template and the sample as CSV files', () => {
      const c = create().componentInstance;
      const created: Blob[] = [];
      URL.createObjectURL = vi.fn((b: Blob) => { created.push(b); return 'blob:x'; });
      URL.revokeObjectURL = vi.fn();
      const names: string[] = [];
      const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { names.push(this.download); });
      c.downloadTemplate();
      c.downloadSample();
      expect(names).toEqual([IMPORT_TEMPLATE_FILE_NAME, IMPORT_SAMPLE_FILE_NAME]);
      expect(created.every((b) => b.type.startsWith('text/csv'))).toBe(true);
      expect(created[1].size).toBeGreaterThan(created[0].size);
      expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
      click.mockRestore();
    });
  });

  describe('privileges', () => {
    const texts = (el: HTMLElement) => Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim());

    it('shows upload, confirm and cancel to a full operator', () => {
      const fixture = create();
      fixture.componentInstance.selected = batch();
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('hq-doc-upload')).not.toBeNull();
      expect(texts(el)).toContain('orgStructure.import.confirm');
      expect(texts(el)).toContain('orgStructure.import.cancel');
      expect(el.querySelector('.summary')?.textContent).toContain('10');
    });

    it('hides upload, confirm and cancel from someone who can only view', () => {
      granted = new Set();
      const fixture = create();
      fixture.componentInstance.selected = batch();
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('hq-doc-upload')).toBeNull();
      expect(texts(el)).not.toContain('orgStructure.import.confirm');
      expect(texts(el)).not.toContain('orgStructure.import.cancel');
      expect(texts(el).some((t) => t?.includes('orgStructure.import.downloadTemplate'))).toBe(true);
    });

    it('lets each privilege unlock only its own action', () => {
      granted = new Set([ORG_IMPORT_PRIVILEGES.confirm]);
      const fixture = create();
      fixture.componentInstance.selected = batch();
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      const t = texts(fixture.nativeElement);
      expect(t).toContain('orgStructure.import.confirm');
      expect(t).not.toContain('orgStructure.import.cancel');
    });

    it('shows the applying note and a failure message for the open batch', () => {
      const fixture = create();
      fixture.componentInstance.selected = batch({ status: 'FAILED', message: 'Disk full', errorRows: 0 });
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).querySelector('.failure')?.textContent).toContain('Disk full');
      fixture.componentInstance.selected = batch({ status: 'APPLYING', errorRows: 0 });
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('orgStructure.import.applying');
    });
  });
});
