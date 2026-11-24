import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HQ_CONFIG, HqDocumentService, HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import {
  DEPARTMENT_IMPORT_HEADER, DEPARTMENT_IMPORT_SAMPLE_FILE_NAME, DEPARTMENT_IMPORT_SAMPLE_ROWS, IMPORT_POLL_INTERVAL_MS, ORG_DEPARTMENT_IMPORT_PRIVILEGES,
  ORG_IMPORT_PRIVILEGES, ORG_PAGE_SIZE,
} from '../org-structure.constants';
import { buildImportCsv } from '../org-structure.helpers';
import { OrgDepartmentImportErrorRow, OrgImportBatch, OrgImportErrorRow } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { OrgImport } from './org-import';

const batch = (over: Partial<OrgImportBatch> = {}): OrgImportBatch =>
  ({ id: 'd1', status: 'VALIDATED', fileName: 'departments.csv', totalRows: 5, validRows: 4, errorRows: 1, createdCount: 0, updatedCount: 0, message: null, createdBy: 'u', createdAt: '2026-01-01T00:00:00Z', appliedAt: null, ...over });
const deptError = (no: number, errors: string[] = ['PARENT_UNKNOWN:HQ'], over: Partial<OrgDepartmentImportErrorRow> = {}): OrgDepartmentImportErrorRow =>
  ({ rowNo: no, code: 'D' + no, name: 'Dept ' + no, parentCode: 'HQ', unitType: null, ladder: null, errors, ...over });
const employeeError = (no: number, errors: string[]): OrgImportErrorRow => ({ rowNo: no, employeeCode: 'E' + no, fullName: 'Person ' + no, errors });

describe('OrgImport: departments', () => {
  const api = {
    recentImports: vi.fn((): Observable<OrgImportBatch[]> => of([])),
    createImport: vi.fn((): Observable<OrgImportBatch> => of(batch({ id: 'e1' }))),
    importBatch: vi.fn((): Observable<OrgImportBatch> => of(batch({ id: 'e1' }))),
    importErrors: vi.fn((): Observable<OrgImportErrorRow[]> => of([])),
    confirmImport: vi.fn((): Observable<OrgImportBatch> => of(batch({ id: 'e1', status: 'APPLYING' }))),
    cancelImport: vi.fn((): Observable<void> => of(undefined)),
    recentDepartmentImports: vi.fn((): Observable<OrgImportBatch[]> => of([batch()])),
    createDepartmentImport: vi.fn((): Observable<OrgImportBatch> => of(batch())),
    departmentImportBatch: vi.fn((): Observable<OrgImportBatch> => of(batch())),
    departmentImportErrors: vi.fn((): Observable<OrgDepartmentImportErrorRow[]> => of([deptError(2), deptError(3, ['MYSTERY'])])),
    confirmDepartmentImport: vi.fn((): Observable<OrgImportBatch> => of(batch({ status: 'APPLYING' }))),
    cancelDepartmentImport: vi.fn((): Observable<void> => of(undefined)),
    recentLadderImports: vi.fn((): Observable<OrgImportBatch[]> => of([])),
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
    translate.setTranslation('en', {
      orgStructure: { import: {
        status: { VALIDATED: 'Validated' },
        errors: { DEPARTMENT_UNKNOWN: 'Unknown department {{detail}}' },
        departments: { errors: { PARENT_UNKNOWN: 'Unknown parent {{detail}}', CODE_DUPLICATE: 'Code already used on row {{detail}}', LADDER_UNKNOWN: 'Unknown ladder {{detail}}', PARENT_REJECTED: 'Parent {{code}} was rejected (row {{row}})', TOO_DEEP: 'Too deep (up to {{detail}} levels)' } },
      } },
    });
    translate.use('en');
    const fixture = TestBed.createComponent(OrgImport);
    fixture.detectChanges();
    return fixture;
  }

  const refresh = (fixture: ComponentFixture<OrgImport>) => { fixture.changeDetectorRef.markForCheck(); fixture.detectChanges(); };
  const buttons = (fixture: ComponentFixture<OrgImport>) => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).map((b) => b.textContent?.trim());

  beforeEach(() => {
    routeStep = 'employees';
    sessionStorage.clear();
    vi.clearAllMocks();
    vi.useFakeTimers();
    granted = null;
    dialogs.confirm.mockImplementation(() => of(true));
  });
  afterEach(() => vi.useRealTimers());

  describe('the tabs', () => {
    it('offers Ladders, Departments and Employees to someone who may import them all, Employees being the one open', () => {
      const fixture = create();
      const el: HTMLElement = fixture.nativeElement;
      const tabs = Array.from(el.querySelectorAll('nav.import-stepper button'));
      expect(el.querySelector('nav.import-stepper')?.getAttribute('aria-label')).toBe('orgStructure.import.steps.label');
      expect(tabs.map((t) => t.id)).toEqual(['org-import-step-LADDERS', 'org-import-step-DEPARTMENTS', 'org-import-step-EMPLOYEES']);
      expect(tabs.map((t) => t.getAttribute('aria-current'))).toEqual([null, null, 'step']);
      expect(el.querySelector('[role="region"]')?.getAttribute('aria-labelledby')).toBe('org-import-step-EMPLOYEES');
      expect(tabs.every((t) => t.getAttribute('aria-controls') === 'org-import-panel')).toBe(true);
    });

    it('shows no tabs at all to someone who may not import departments, and refuses to open the department tab', () => {
      granted = new Set([ORG_IMPORT_PRIVILEGES.create]);
      const fixture = create();
      expect((fixture.nativeElement as HTMLElement).querySelector('nav.import-stepper')).toBeNull();
      expect(fixture.componentInstance.visibleModes).toEqual(['EMPLOYEES']);
      fixture.componentInstance.setMode('DEPARTMENTS');
      expect(fixture.componentInstance.mode).toBe('EMPLOYEES');
      expect(api.recentDepartmentImports).not.toHaveBeenCalled();
    });

    it('opens the department tab: its own list, columns, texts, privileges and downloads', () => {
      const fixture = create();
      const c = fixture.componentInstance;
      c.setMode('DEPARTMENTS');
      refresh(fixture);
      expect(c.mode).toBe('DEPARTMENTS');
      expect(c.isDepartments).toBe(true);
      expect(api.recentDepartmentImports).toHaveBeenCalledWith(ORG_PAGE_SIZE);
      expect(c.recent).toHaveLength(1);
      expect(c.errorColumns.map((x) => x.key)).toEqual(['rowNo', 'code', 'name', 'parentCode', 'ladder', 'problems']);
      expect(c.privileges).toBe(ORG_DEPARTMENT_IMPORT_PRIVILEGES);
      expect(c.k('title')).toBe('orgStructure.import.departments.title');
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[role="region"]')?.getAttribute('aria-labelledby')).toBe('org-import-step-DEPARTMENTS');
      expect(el.querySelector('[data-testid="download-sample"]')).not.toBeNull();
      expect(el.querySelector('[data-testid="download-template"]')).not.toBeNull();
      expect(el.querySelector('#org-department-import-file, hq-doc-upload')).not.toBeNull();
    });

    it('goes back to the employee tab with a clean report, no polling left running', () => {
      const fixture = create();
      const c = fixture.componentInstance;
      c.setMode('DEPARTMENTS');
      c.open(batch({ status: 'APPLYING', errorRows: 0 }));
      c.setMode('EMPLOYEES');
      expect(c.selected).toBeNull();
      expect(c.errorRows).toEqual([]);
      expect(c.k('title')).toBe('orgStructure.import.title');
      expect(c.errorColumns.map((x) => x.key)).toEqual(['rowNo', 'employeeCode', 'fullName', 'problems']);
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS * 3);
      expect(api.departmentImportBatch).not.toHaveBeenCalled();
      c.setMode('EMPLOYEES');
      expect(api.recentImports).toHaveBeenCalledTimes(2); // the initial load and the one of the switch back, none for the no-op
    });

    it('does not list recent imports to someone without the list privilege', () => {
      granted = new Set([ORG_DEPARTMENT_IMPORT_PRIVILEGES.create, ORG_IMPORT_PRIVILEGES.create]);
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      expect(api.recentDepartmentImports).not.toHaveBeenCalled();
      expect(c.recent).toEqual([]);
      expect(c.isLoading).toBe(false);
    });

    it('ignores a recent list that arrives after the tab was left', () => {
      const fixture = create();
      const c = fixture.componentInstance;
      let late: (value: OrgImportBatch[]) => void = () => undefined;
      api.recentDepartmentImports.mockReturnValueOnce(new Observable<OrgImportBatch[]>((s) => { late = (v) => { s.next(v); s.complete(); }; }));
      c.setMode('DEPARTMENTS');
      c.setMode('EMPLOYEES');
      late([batch({ id: 'late' })]);
      expect(c.recent.map((b) => b.id)).not.toContain('late');
    });
  });

  describe('keyboard on the tabs', () => {
    const key = (name: string) => { const e = new KeyboardEvent('keydown', { key: name, cancelable: true }); return e; };

    it('moves between the tabs with the arrow keys, wrapping round, and focuses the one that opens', () => {
      const fixture = create();
      const c = fixture.componentInstance;
      const right = key('ArrowRight');
      c.onTabKeydown(right);
      refresh(fixture);
      expect(right.defaultPrevented).toBe(true);
      expect(c.mode).toBe('LADDERS');
      expect(document.activeElement?.id).toBe('org-import-step-LADDERS');
      c.onTabKeydown(key('ArrowRight'));
      expect(c.mode).toBe('DEPARTMENTS');
      c.onTabKeydown(key('ArrowRight'));
      expect(c.mode).toBe('EMPLOYEES');
      c.onTabKeydown(key('ArrowLeft'));
      expect(c.mode).toBe('DEPARTMENTS');
      c.onTabKeydown(key('Home'));
      expect(c.mode).toBe('LADDERS');
      c.onTabKeydown(key('End'));
      expect(c.mode).toBe('EMPLOYEES');
    });

    it('leaves every other key alone, and a lone tab has nowhere to go', () => {
      const c = create().componentInstance;
      const other = key('a');
      c.onTabKeydown(other);
      expect(other.defaultPrevented).toBe(false);
      expect(c.mode).toBe('EMPLOYEES');
      granted = new Set();
      const lone = create().componentInstance;
      const arrow = key('ArrowRight');
      lone.onTabKeydown(arrow);
      expect(arrow.defaultPrevented).toBe(false);
      expect(lone.mode).toBe('EMPLOYEES');
    });
  });

  describe('uploading a department file', () => {
    it('stages the document through the department import, opens the report and reads the rejected rows as text', () => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      c.onUploaded({ id: 'doc9' } as never);
      expect(api.createDepartmentImport).toHaveBeenCalledWith('doc9');
      expect(api.createImport).not.toHaveBeenCalled();
      expect(c.selected?.id).toBe('d1');
      expect(api.departmentImportErrors).toHaveBeenCalledWith('d1', 0, ORG_PAGE_SIZE);
      expect(c.errorRows).toEqual([
        { rowNo: 2, code: 'D2', name: 'Dept 2', parentCode: 'HQ', ladder: '', problems: 'Unknown parent HQ' },
        { rowNo: 3, code: 'D3', name: 'Dept 3', parentCode: 'HQ', ladder: '', problems: 'MYSTERY' },
      ]);
      expect(c.busy).toBe(false);
      expect(api.recentDepartmentImports).toHaveBeenCalledTimes(3);
    });

    it('shows blanks for a row with no code, name or parent, and translates a duplicate with the row it points to', () => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      api.departmentImportErrors.mockReturnValueOnce(of([deptError(4, ['CODE_DUPLICATE:2'], { code: null, name: null, parentCode: null, ladder: null })]));
      c.open(batch());
      expect(c.errorRows[0]).toEqual({ rowNo: 4, code: '', name: '', parentCode: '', ladder: '', problems: 'Code already used on row 2' });
    });

    it('reads a rejected parent as its code and row, and a depth limit as its number', () => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      api.departmentImportErrors.mockReturnValueOnce(of([deptError(2, ['PARENT_REJECTED:EXEC:1', 'TOO_DEEP:12'])]));
      c.open(batch());
      expect(c.errorRows[0].problems).toBe('Parent EXEC was rejected (row 1); Too deep (up to 12 levels)');
    });

    it('shows the ladder a row asked for and says when it is not one of the organization ladders', () => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      api.departmentImportErrors.mockReturnValueOnce(of([deptError(5, ['LADDER_UNKNOWN:Gold'], { ladder: 'Gold' })]));
      c.open(batch());
      expect(c.errorRows[0]).toEqual({ rowNo: 5, code: 'D5', name: 'Dept 5', parentCode: 'HQ', ladder: 'Gold', problems: 'Unknown ladder Gold' });
    });

    it('says in the language of the screen that the upload failed', () => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      api.createDepartmentImport.mockReturnValueOnce(throwError(() => ({})));
      c.onUploaded({ id: 'doc9' } as never);
      expect(notify.error).toHaveBeenCalledWith('orgStructure.import.departments.messages.uploadFailed');
      expect(c.busy).toBe(false);
      api.createDepartmentImport.mockReturnValueOnce(throwError(() => ({ error: { detail: 'The file must be a CSV (.csv)' } })));
      c.onUploaded({ id: 'doc9' } as never);
      expect(notify.error).toHaveBeenLastCalledWith('The file must be a CSV (.csv)');
    });

    it.each([
      ['File exceeds the 9.9 MB limit.', 'tooLarge'],
      ['Please upload CSV, up to 10 MB files.', 'notCsv'],
      ['File is too small (minimum 50 bytes).', 'tooSmall'],
      ['Upload failed.', 'failed'],
      ['The document store is down', 'failed'],
    ])('turns the chooser refusal "%s" into the translated message %s', (message, kind) => {
      const fixture = create();
      fixture.componentInstance.setMode('DEPARTMENTS');
      refresh(fixture);
      fixture.debugElement.query(By.css('hq-doc-upload')).triggerEventHandler('rejected', message);
      expect(notify.error).toHaveBeenCalledWith('orgStructure.import.departments.upload.' + kind);
    });
  });

  describe('the rejected rows', () => {
    it('pages through them after the last row seen', () => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      api.departmentImportErrors.mockReturnValueOnce(of(Array.from({ length: ORG_PAGE_SIZE }, (_, i) => deptError(i + 1))));
      c.open(batch({ errorRows: 60 }));
      expect(c.hasMoreErrors).toBe(true);
      api.departmentImportErrors.mockReturnValueOnce(of([deptError(99)]));
      c.loadMoreErrors();
      expect(api.departmentImportErrors).toHaveBeenLastCalledWith('d1', ORG_PAGE_SIZE, ORG_PAGE_SIZE);
      expect(c.errorRows).toHaveLength(ORG_PAGE_SIZE + 1);
      expect(c.hasMoreErrors).toBe(false);
    });

    it('does not ask for them when there are none or when the person may not read them, and says so', () => {
      granted = new Set([ORG_DEPARTMENT_IMPORT_PRIVILEGES.create, ORG_DEPARTMENT_IMPORT_PRIVILEGES.confirm]);
      const fixture = create();
      const c = fixture.componentInstance;
      c.setMode('DEPARTMENTS');
      c.open(batch({ errorRows: 0 }));
      expect(api.departmentImportErrors).not.toHaveBeenCalled();
      expect(c.errorsNotAllowed).toBe(false);
      c.open(batch({ errorRows: 3 }));
      refresh(fixture);
      expect(api.departmentImportErrors).not.toHaveBeenCalled();
      expect(c.errorsNotAllowed).toBe(true);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="errors-not-allowed"]')?.textContent).toContain('orgStructure.import.departments.noErrorAccess');
    });

    it('shows a row exactly as text, never as markup', () => {
      const fixture = create();
      const c = fixture.componentInstance;
      c.setMode('DEPARTMENTS');
      api.departmentImportErrors.mockReturnValueOnce(of([deptError(2, ['<img src=x onerror=alert(1)>'], { name: '<script>alert(1)</script>' })]));
      c.open(batch());
      refresh(fixture);
      const el: HTMLElement = fixture.nativeElement;
      expect(c.errorRows[0].name).toBe('<script>alert(1)</script>');
      expect(el.querySelector('img')).toBeNull();
      expect(el.querySelector('script')).toBeNull();
    });

    it('reports when they cannot be read', () => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      api.departmentImportErrors.mockReturnValueOnce(throwError(() => ({})));
      c.open(batch());
      expect(notify.error).toHaveBeenCalledWith('orgStructure.import.departments.messages.loadFailed');
    });
  });

  describe('confirming and cancelling', () => {
    it('applies the valid departments after confirmation and follows the import until it is applied', () => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      c.selected = batch({ errorRows: 0 });
      api.departmentImportBatch.mockReturnValueOnce(of(batch({ status: 'APPLYING' }))).mockReturnValueOnce(of(batch({ status: 'APPLIED', createdCount: 3, updatedCount: 1 })));
      c.confirm();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'orgStructure.import.departments.confirmDialog.title', message: 'orgStructure.import.departments.confirmDialog.message' }));
      expect(api.confirmDepartmentImport).toHaveBeenCalledWith('d1');
      expect(api.confirmImport).not.toHaveBeenCalled();
      expect(notify.success).toHaveBeenCalledWith('orgStructure.import.departments.messages.started');
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS);
      expect(c.selected?.status).toBe('APPLYING');
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS);
      expect(c.selected?.status).toBe('APPLIED');
      expect(c.selected?.createdCount).toBe(3);
      expect(api.recentDepartmentImports).toHaveBeenCalledTimes(4);
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS * 3);
      expect(api.departmentImportBatch).toHaveBeenCalledTimes(2);
    });

    it.each(['FAILED', 'CANCELLED', 'APPLIED'] as const)('stops following the moment the import is %s', (status) => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      api.departmentImportBatch.mockReturnValueOnce(of(batch({ status })));
      c.open(batch({ status: 'APPLYING', errorRows: 0 }));
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS);
      expect(c.selected?.status).toBe(status);
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS * 4);
      expect(api.departmentImportBatch).toHaveBeenCalledTimes(1);
    });

    it('does not poll for someone who may not read the import, and reports a failed poll otherwise', () => {
      granted = new Set([ORG_DEPARTMENT_IMPORT_PRIVILEGES.create, ORG_DEPARTMENT_IMPORT_PRIVILEGES.list]);
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      c.open(batch({ status: 'APPLYING', errorRows: 0 }));
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS * 2);
      expect(api.departmentImportBatch).not.toHaveBeenCalled();
      granted = null;
      c.open(batch({ status: 'APPLYING', errorRows: 0 }));
      api.departmentImportBatch.mockReturnValueOnce(throwError(() => ({})));
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS);
      expect(notify.error).toHaveBeenCalledWith('orgStructure.import.departments.messages.loadFailed');
    });

    it('does nothing when declined, reports a failed confirm, and stops polling when the screen goes', () => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      c.selected = batch();
      dialogs.confirm.mockImplementation(() => of(false));
      c.confirm();
      expect(api.confirmDepartmentImport).not.toHaveBeenCalled();
      dialogs.confirm.mockImplementation(() => of(true));
      api.confirmDepartmentImport.mockReturnValueOnce(throwError(() => ({})));
      c.confirm();
      expect(notify.error).toHaveBeenCalledWith('orgStructure.import.departments.messages.confirmFailed');
      c.open(batch({ status: 'APPLYING', errorRows: 0 }));
      c.ngOnDestroy();
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS * 2);
      expect(api.departmentImportBatch).not.toHaveBeenCalled();
    });

    it('cancels a validated import through the department import and reloads it', () => {
      const c = create().componentInstance;
      c.setMode('DEPARTMENTS');
      c.selected = batch();
      api.departmentImportBatch.mockReturnValueOnce(of(batch({ status: 'CANCELLED' })));
      c.cancel();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger', title: 'orgStructure.import.departments.cancelDialog.title' }));
      expect(api.cancelDepartmentImport).toHaveBeenCalledWith('d1');
      expect(api.cancelImport).not.toHaveBeenCalled();
      expect(notify.success).toHaveBeenCalledWith('orgStructure.import.departments.messages.cancelled');
      expect(c.selected?.status).toBe('CANCELLED');
      api.cancelDepartmentImport.mockReturnValueOnce(throwError(() => ({})));
      c.cancel();
      expect(notify.error).toHaveBeenCalledWith('orgStructure.import.departments.messages.cancelFailed');
    });
  });

  describe('the privileges of the department tab', () => {
    it('shows upload, confirm and cancel to someone who holds all of them', () => {
      const fixture = create();
      fixture.componentInstance.setMode('DEPARTMENTS');
      fixture.componentInstance.selected = batch();
      refresh(fixture);
      expect((fixture.nativeElement as HTMLElement).querySelector('hq-doc-upload')).not.toBeNull();
      expect(buttons(fixture)).toContain('orgStructure.import.confirm');
      expect(buttons(fixture)).toContain('orgStructure.import.cancel');
    });

    it('lets each department privilege unlock only its own action, and the employee privileges unlock none of them', () => {
      granted = new Set([ORG_DEPARTMENT_IMPORT_PRIVILEGES.create, ORG_DEPARTMENT_IMPORT_PRIVILEGES.confirm]);
      let fixture = create();
      fixture.componentInstance.setMode('DEPARTMENTS');
      fixture.componentInstance.selected = batch();
      refresh(fixture);
      expect(buttons(fixture)).toContain('orgStructure.import.confirm');
      expect(buttons(fixture)).not.toContain('orgStructure.import.cancel');

      granted = new Set([ORG_DEPARTMENT_IMPORT_PRIVILEGES.create, ORG_IMPORT_PRIVILEGES.confirm, ORG_IMPORT_PRIVILEGES.cancel]);
      fixture = create();
      fixture.componentInstance.setMode('DEPARTMENTS');
      fixture.componentInstance.selected = batch();
      refresh(fixture);
      expect(buttons(fixture)).not.toContain('orgStructure.import.confirm');
      expect(buttons(fixture)).not.toContain('orgStructure.import.cancel');
    });

    it('announces the status of the report politely', () => {
      const fixture = create();
      fixture.componentInstance.selected = batch();
      refresh(fixture);
      const status = (fixture.nativeElement as HTMLElement).querySelector('.status');
      expect(status?.getAttribute('role')).toBe('status');
      expect(status?.getAttribute('aria-live')).toBe('polite');
    });
  });

  describe('the hint to import the departments first', () => {
    const open = (fixture: ComponentFixture<OrgImport>, rows: OrgImportErrorRow[]) => {
      api.importErrors.mockReturnValueOnce(of(rows));
      fixture.componentInstance.open(batch({ id: 'e1', errorRows: rows.length }));
      refresh(fixture);
    };
    const hint = (fixture: ComponentFixture<OrgImport>) => (fixture.nativeElement as HTMLElement).querySelector('[data-testid="unknown-department-hint"]');

    it('appears when an employee row names a department that does not exist, and its button switches to the Departments tab', () => {
      const fixture = create();
      open(fixture, [employeeError(1, ['EMAIL_INVALID']), employeeError(2, ['DEPARTMENT_UNKNOWN:EXEC'])]);
      expect(fixture.componentInstance.showUnknownDepartmentHint).toBe(true);
      expect(hint(fixture)?.textContent).toContain('orgStructure.import.unknownDepartmentHint.text');
      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="hint-open-departments"]')?.click();
      refresh(fixture);
      expect(fixture.componentInstance.mode).toBe('DEPARTMENTS');
      expect(api.recentDepartmentImports).toHaveBeenCalled();
      expect(hint(fixture)).toBeNull();
    });

    it('stays away when no row has that problem, and when there are no rejected rows', () => {
      const fixture = create();
      open(fixture, [employeeError(1, ['EMAIL_INVALID'])]);
      expect(hint(fixture)).toBeNull();
      fixture.componentInstance.sawUnknownDepartment = true;
      fixture.componentInstance.selected = batch({ id: 'e1', errorRows: 0 });
      refresh(fixture);
      expect(fixture.componentInstance.showUnknownDepartmentHint).toBe(false);
    });

    it('is forgotten when another file is opened, and never shown on the department tab', () => {
      const fixture = create();
      open(fixture, [employeeError(1, ['DEPARTMENT_UNKNOWN:EXEC'])]);
      api.importErrors.mockReturnValueOnce(of([employeeError(1, ['EMAIL_INVALID'])]));
      fixture.componentInstance.open(batch({ id: 'e2', errorRows: 1 }));
      expect(fixture.componentInstance.sawUnknownDepartment).toBe(false);
      fixture.componentInstance.sawUnknownDepartment = true;
      fixture.componentInstance.setMode('DEPARTMENTS');
      fixture.componentInstance.selected = batch();
      expect(fixture.componentInstance.showUnknownDepartmentHint).toBe(false);
    });

    it('is shown without its button to someone who may not import departments', () => {
      granted = new Set([ORG_IMPORT_PRIVILEGES.create]);
      const fixture = create();
      open(fixture, [employeeError(1, ['DEPARTMENT_UNKNOWN:EXEC'])]);
      expect(hint(fixture)).not.toBeNull();
      expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="hint-open-departments"]')).toBeNull();
    });
  });

  describe('the sample file', () => {
    it('is built in the browser: the header and a top-level, a child and a grandchild department, with no server call', () => {
      const c = create().componentInstance;
      const created: Blob[] = [];
      URL.createObjectURL = vi.fn((b: Blob) => { created.push(b); return 'blob:x'; });
      URL.revokeObjectURL = vi.fn();
      const names: string[] = [];
      const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { names.push(this.download); });
      c.setMode('DEPARTMENTS');
      c.downloadSample();
      expect(names).toEqual([DEPARTMENT_IMPORT_SAMPLE_FILE_NAME]);
      expect(created[0].type).toContain('text/csv');
      expect(created[0].size).toBe(buildImportCsv(DEPARTMENT_IMPORT_HEADER, DEPARTMENT_IMPORT_SAMPLE_ROWS).length);
      expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1);
      expect(api.createDepartmentImport).not.toHaveBeenCalled();
      click.mockRestore();
    });

    it('has the department columns and a tree of one root and its descendants, every row on the Standard ladder', () => {
      expect(DEPARTMENT_IMPORT_HEADER).toEqual(['code', 'name', 'parent_code', 'unit_type', 'ladder']);
      expect(DEPARTMENT_IMPORT_SAMPLE_ROWS).toHaveLength(6);
      const codes = new Set(DEPARTMENT_IMPORT_SAMPLE_ROWS.map((r) => r[0]));
      expect(codes.size).toBe(6);
      expect(DEPARTMENT_IMPORT_SAMPLE_ROWS.filter((r) => r[2] === '').map((r) => r[0])).toEqual(['EXEC']);
      DEPARTMENT_IMPORT_SAMPLE_ROWS.filter((r) => r[2] !== '').forEach((r) => expect(codes.has(r[2]), r[0]).toBe(true));
      expect(DEPARTMENT_IMPORT_SAMPLE_ROWS.every((r) => r.length === DEPARTMENT_IMPORT_HEADER.length)).toBe(true);
      expect(DEPARTMENT_IMPORT_SAMPLE_ROWS.map((r) => r[4])).toEqual(Array(6).fill('Standard'));
    });
  });

  describe('departments without a position ladder', () => {
    const note = (fixture: ComponentFixture<OrgImport>) => (fixture.nativeElement as HTMLElement).querySelector('[data-testid="without-ladder-note"]');
    const showBatch = (withoutLadder: number | null | undefined, mode: 'DEPARTMENTS' | 'EMPLOYEES' = 'DEPARTMENTS') => {
      const fixture = create();
      if (mode === 'DEPARTMENTS') fixture.componentInstance.setMode('DEPARTMENTS');
      fixture.componentInstance.selected = batch({ errorRows: 0, withoutLadder });
      refresh(fixture);
      return fixture;
    };

    it('tells the report how many departments have no ladder, with a form for one and a form for several', () => {
      const several = showBatch(3);
      expect(several.componentInstance.withoutLadderCount).toBe(3);
      expect(several.componentInstance.withoutLadderKey).toBe('orgStructure.import.departments.withoutLadder');
      expect(note(several)?.textContent).toContain('orgStructure.import.departments.withoutLadder');
      const one = showBatch(1);
      expect(one.componentInstance.withoutLadderKey).toBe('orgStructure.import.departments.withoutLadderOne');
      expect(note(one)?.textContent).toContain('orgStructure.import.departments.withoutLadderOne');
    });

    it('stays silent when every department has a ladder, when the report says nothing, and on the employee tab', () => {
      expect(note(showBatch(0))).toBeNull();
      expect(note(showBatch(null))).toBeNull();
      expect(note(showBatch(undefined))).toBeNull();
      expect(note(showBatch(-2))).toBeNull();
      expect(note(showBatch(4, 'EMPLOYEES'))).toBeNull();
    });

    it('passes the number to the translated text', () => {
      const fixture = create();
      fixture.componentInstance.setMode('DEPARTMENTS');
      const translate = TestBed.inject(TranslateService);
      translate.setTranslation('en', { orgStructure: { import: { departments: { withoutLadder: '{{count}} departments have no ladder', withoutLadderOne: '1 department has no ladder' } } } }, true);
      fixture.componentInstance.selected = batch({ errorRows: 0, withoutLadder: 7 });
      refresh(fixture);
      expect(note(fixture)?.textContent).toContain('7 departments have no ladder');
      fixture.componentInstance.selected = batch({ errorRows: 0, withoutLadder: 1 });
      refresh(fixture);
      expect(note(fixture)?.textContent).toContain('1 department has no ladder');
    });
  });

  describe('the hint to give the departments a position ladder', () => {
    const openEmployees = (fixture: ComponentFixture<OrgImport>, rows: OrgImportErrorRow[]) => {
      api.importErrors.mockReturnValueOnce(of(rows));
      fixture.componentInstance.open(batch({ id: 'e1', errorRows: rows.length }));
      refresh(fixture);
    };
    const hint = (fixture: ComponentFixture<OrgImport>) => (fixture.nativeElement as HTMLElement).querySelector('[data-testid="no-ladder-hint"]');

    it('appears for the code the server gives, and its button opens the Departments tab', () => {
      const fixture = create();
      openEmployees(fixture, [employeeError(1, ['DEPARTMENT_HAS_NO_LADDER:TECH'])]);
      expect(fixture.componentInstance.sawNoLadder).toBe(true);
      expect(fixture.componentInstance.showNoLadderHint).toBe(true);
      expect(hint(fixture)?.textContent).toContain('orgStructure.import.ladderHint.text');
      expect(fixture.componentInstance.showUnknownDepartmentHint).toBe(false);
      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="ladder-hint-open-departments"]')?.click();
      refresh(fixture);
      expect(fixture.componentInstance.mode).toBe('DEPARTMENTS');
      expect(hint(fixture)).toBeNull();
    });

    it('appears too when only the English text of the problem comes back, in any capitals', () => {
      const fixture = create();
      openEmployees(fixture, [employeeError(1, ['Department TECH has no position ladder'])]);
      expect(fixture.componentInstance.showNoLadderHint).toBe(true);
      const other = create();
      openEmployees(other, [employeeError(2, ['DEPARTMENT TECH HAS NO POSITION LADDER'])]);
      expect(other.componentInstance.showNoLadderHint).toBe(true);
    });

    it('can appear together with the hint about unknown departments', () => {
      const fixture = create();
      openEmployees(fixture, [employeeError(1, ['DEPARTMENT_UNKNOWN:EXEC']), employeeError(2, ['DEPARTMENT_HAS_NO_LADDER:TECH'])]);
      expect(fixture.componentInstance.showUnknownDepartmentHint).toBe(true);
      expect(fixture.componentInstance.showNoLadderHint).toBe(true);
    });

    it('stays away without that problem, without rejected rows, on the department tab, and after another file is opened', () => {
      const fixture = create();
      openEmployees(fixture, [employeeError(1, ['EMAIL_INVALID'])]);
      expect(hint(fixture)).toBeNull();
      fixture.componentInstance.sawNoLadder = true;
      fixture.componentInstance.selected = batch({ id: 'e1', errorRows: 0 });
      expect(fixture.componentInstance.showNoLadderHint).toBe(false);
      api.importErrors.mockReturnValueOnce(of([employeeError(1, ['EMAIL_INVALID'])]));
      fixture.componentInstance.open(batch({ id: 'e2', errorRows: 1 }));
      expect(fixture.componentInstance.sawNoLadder).toBe(false);
      fixture.componentInstance.sawNoLadder = true;
      fixture.componentInstance.setMode('DEPARTMENTS');
      fixture.componentInstance.selected = batch();
      expect(fixture.componentInstance.sawNoLadder).toBe(false);
      expect(fixture.componentInstance.showNoLadderHint).toBe(false);
    });

    it('is shown without its button to someone who may not import departments', () => {
      granted = new Set([ORG_IMPORT_PRIVILEGES.create]);
      const fixture = create();
      openEmployees(fixture, [employeeError(1, ['DEPARTMENT_HAS_NO_LADDER:TECH'])]);
      expect(hint(fixture)).not.toBeNull();
      expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="ladder-hint-open-departments"]')).toBeNull();
    });
  });
});
