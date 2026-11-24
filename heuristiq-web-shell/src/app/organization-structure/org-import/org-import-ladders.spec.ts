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
import {
  IMPORT_POLL_INTERVAL_MS, IMPORT_SAMPLE_ROWS, IMPORT_TEMPLATE_HEADER, LADDER_IMPORT_HEADER, LADDER_IMPORT_SAMPLE_FILE_NAME, LADDER_IMPORT_SAMPLE_ROWS,
  ORG_DEPARTMENT_IMPORT_PRIVILEGES, ORG_IMPORT_PRIVILEGES, ORG_LADDER_IMPORT_PRIVILEGES, ORG_PAGE_SIZE,
} from '../org-structure.constants';
import { buildImportCsv } from '../org-structure.helpers';
import { OrgDepartmentImportErrorRow, OrgImportBatch, OrgImportErrorRow, OrgLadderImportErrorRow } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { OrgImport } from './org-import';

const batch = (over: Partial<OrgImportBatch> = {}): OrgImportBatch =>
  ({ id: 'l1', status: 'VALIDATED', fileName: 'ladders.csv', totalRows: 5, validRows: 4, errorRows: 1, createdCount: 0, updatedCount: 0, message: null, createdBy: 'u', createdAt: '2026-01-01T00:00:00Z', appliedAt: null, ...over });
const ladderError = (no: number, errors: string[] = ['RANK_INVALID'], over: Partial<OrgLadderImportErrorRow> = {}): OrgLadderImportErrorRow =>
  ({ rowNo: no, ladder: 'Standard', position: 'Lead', rank: 'x', managerial: 'maybe', errors, ...over });
const employeeError = (no: number, errors: string[]): OrgImportErrorRow => ({ rowNo: no, employeeCode: 'E' + no, fullName: 'Person ' + no, errors });
const deptError = (no: number, errors: string[]): OrgDepartmentImportErrorRow =>
  ({ rowNo: no, code: 'D' + no, name: 'Dept ' + no, parentCode: null, unitType: null, ladder: 'Gold', errors });

describe('OrgImport: ladders and positions', () => {
  const api = {
    recentImports: vi.fn((): Observable<OrgImportBatch[]> => of([])),
    createImport: vi.fn((): Observable<OrgImportBatch> => of(batch({ id: 'e1' }))),
    importBatch: vi.fn((): Observable<OrgImportBatch> => of(batch({ id: 'e1' }))),
    importErrors: vi.fn((): Observable<OrgImportErrorRow[]> => of([])),
    confirmImport: vi.fn((): Observable<OrgImportBatch> => of(batch({ id: 'e1', status: 'APPLYING' }))),
    cancelImport: vi.fn((): Observable<void> => of(undefined)),
    recentDepartmentImports: vi.fn((): Observable<OrgImportBatch[]> => of([])),
    departmentImportErrors: vi.fn((): Observable<OrgDepartmentImportErrorRow[]> => of([])),
    createDepartmentImport: vi.fn((): Observable<OrgImportBatch> => of(batch())),
    departmentImportBatch: vi.fn((): Observable<OrgImportBatch> => of(batch())),
    recentLadderImports: vi.fn((): Observable<OrgImportBatch[]> => of([batch()])),
    createLadderImport: vi.fn((): Observable<OrgImportBatch> => of(batch())),
    ladderImportBatch: vi.fn((): Observable<OrgImportBatch> => of(batch())),
    ladderImportErrors: vi.fn((): Observable<OrgLadderImportErrorRow[]> => of([ladderError(2), ladderError(3, ['MYSTERY'])])),
    confirmLadderImport: vi.fn((): Observable<OrgImportBatch> => of(batch({ status: 'APPLYING' }))),
    cancelLadderImport: vi.fn((): Observable<void> => of(undefined)),
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
        errors: {
          DEPARTMENT_UNKNOWN: 'Unknown department {{detail}}',
          HEAD_DUPLICATE: 'Department {{code}} already has a head on row {{row}}',
          POSITION_NOT_IN_LADDER: 'Position {{detail}} is not in the ladder',
        },
        ladders: { errors: { RANK_INVALID: 'Rank must be 1 to 99', DUPLICATE_ROW: 'Already on row {{detail}}' } },
        departments: { errors: { LADDER_UNKNOWN: 'Unknown ladder {{detail}}' } },
        headsSet: '{{count}} heads set',
        headsSetOne: '1 head set',
      } },
    });
    translate.use('en');
    const fixture = TestBed.createComponent(OrgImport);
    fixture.detectChanges();
    return fixture;
  }

  const refresh = (fixture: ComponentFixture<OrgImport>) => { fixture.changeDetectorRef.markForCheck(); fixture.detectChanges(); };
  const el = (fixture: ComponentFixture<OrgImport>) => fixture.nativeElement as HTMLElement;
  const testId = (fixture: ComponentFixture<OrgImport>, id: string) => el(fixture).querySelector(`[data-testid="${id}"]`);

  beforeEach(() => {
    routeStep = 'employees';
    sessionStorage.clear();
    vi.clearAllMocks();
    vi.useFakeTimers();
    granted = null;
    dialogs.confirm.mockImplementation(() => of(true));
  });
  afterEach(() => vi.useRealTimers());

  describe('the tab', () => {
    it('comes first in the tab order and is offered only to someone who may import ladders', () => {
      const all = create();
      expect(Array.from(el(all).querySelectorAll('nav.import-stepper button')).map((t) => t.id)).toEqual(['org-import-step-LADDERS', 'org-import-step-DEPARTMENTS', 'org-import-step-EMPLOYEES']);
      expect(all.componentInstance.visibleModes).toEqual(['LADDERS', 'DEPARTMENTS', 'EMPLOYEES']);

      granted = new Set([ORG_IMPORT_PRIVILEGES.create, ORG_DEPARTMENT_IMPORT_PRIVILEGES.create]);
      const noLadders = create();
      expect(noLadders.componentInstance.visibleModes).toEqual(['DEPARTMENTS', 'EMPLOYEES']);
      expect(testId(noLadders, 'import-step-ladders')).toBeNull();

      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create]);
      const onlyLadders = create();
      expect(onlyLadders.componentInstance.visibleModes).toEqual(['LADDERS', 'EMPLOYEES']);
      expect(testId(onlyLadders, 'import-step-ladders')).not.toBeNull();
      expect(testId(onlyLadders, 'import-step-departments')).toBeNull();
    });

    it('refuses to open the ladder tab without the right to import ladders', () => {
      granted = new Set([ORG_IMPORT_PRIVILEGES.create]);
      const c = create().componentInstance;
      c.setMode('LADDERS');
      expect(c.mode).toBe('EMPLOYEES');
      expect(api.recentLadderImports).not.toHaveBeenCalled();
    });

    it('opens with its own list, columns, texts, privileges and sample download', () => {
      const fixture = create();
      const c = fixture.componentInstance;
      c.setMode('LADDERS');
      refresh(fixture);
      expect(c.isLadders).toBe(true);
      expect(c.isDepartments).toBe(false);
      expect(api.recentLadderImports).toHaveBeenCalledWith(ORG_PAGE_SIZE);
      expect(c.recent).toHaveLength(1);
      expect(c.errorColumns.map((x) => x.key)).toEqual(['rowNo', 'ladder', 'position', 'rank', 'managerial', 'problems']);
      expect(c.privileges).toBe(ORG_LADDER_IMPORT_PRIVILEGES);
      expect(c.k('title')).toBe('orgStructure.import.ladders.title');
      expect(el(fixture).querySelector('[role="region"]')?.getAttribute('aria-labelledby')).toBe('org-import-step-LADDERS');
      expect(testId(fixture, 'download-sample')).not.toBeNull();
      expect(testId(fixture, 'download-template')).not.toBeNull();
    });

    it('does not read the list of someone who may not list ladder imports', () => {
      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create]);
      const c = create().componentInstance;
      c.setMode('LADDERS');
      expect(api.recentLadderImports).not.toHaveBeenCalled();
      expect(c.recent).toEqual([]);
      expect(c.isLoading).toBe(false);
    });
  });

  describe('uploading a ladders file', () => {
    it('stages the document through the ladder import and reads the rejected rows as text, rank and all', () => {
      const c = create().componentInstance;
      c.setMode('LADDERS');
      api.ladderImportErrors.mockReturnValueOnce(of([
        ladderError(2, ['RANK_INVALID', 'DUPLICATE_ROW:1']),
        ladderError(3, ['MYSTERY'], { ladder: null, position: null, rank: null, managerial: null }),
        ladderError(4, ['RANK_INVALID'], { rank: '7' }),
      ]));
      c.onUploaded({ id: 'doc1' } as never);
      expect(api.createLadderImport).toHaveBeenCalledWith('doc1');
      expect(api.createImport).not.toHaveBeenCalled();
      expect(api.ladderImportErrors).toHaveBeenCalledWith('l1', 0, ORG_PAGE_SIZE);
      expect(c.errorRows).toEqual([
        { rowNo: 2, ladder: 'Standard', position: 'Lead', rank: 'x', managerial: 'maybe', problems: 'Rank must be 1 to 99; Already on row 1' },
        { rowNo: 3, ladder: '', position: '', rank: '', managerial: '', problems: 'MYSTERY' },
        { rowNo: 4, ladder: 'Standard', position: 'Lead', rank: '7', managerial: 'maybe', problems: 'Rank must be 1 to 99' },
      ]);
    });

    it('shows how many ladders the file created, only on this tab', () => {
      const fixture = create();
      const c = fixture.componentInstance;
      c.setMode('LADDERS');
      c.selected = batch({ errorRows: 0, laddersCreated: 2 });
      refresh(fixture);
      expect(c.laddersCreatedCount).toBe(2);
      expect(testId(fixture, 'ladders-created')?.textContent).toBe('2');
      c.selected = batch({ errorRows: 0, laddersCreated: null });
      expect(c.laddersCreatedCount).toBe(0);
      c.selected = batch({ errorRows: 0, laddersCreated: -3 });
      expect(c.laddersCreatedCount).toBe(0);
      c.setMode('EMPLOYEES');
      c.selected = batch({ errorRows: 0, laddersCreated: 2 });
      expect(c.laddersCreatedCount).toBe(0);
    });

    it('hides the rejected rows from someone who may not list them and says so', () => {
      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create, ORG_LADDER_IMPORT_PRIVILEGES.list]);
      const fixture = create();
      fixture.componentInstance.setMode('LADDERS');
      fixture.componentInstance.open(batch());
      refresh(fixture);
      expect(fixture.componentInstance.errorsNotAllowed).toBe(true);
      expect(api.ladderImportErrors).not.toHaveBeenCalled();
      expect(testId(fixture, 'errors-not-allowed')?.textContent).toContain('orgStructure.import.ladders.noErrorAccess');
    });

    it('confirms and cancels through the ladder import and follows an applying batch until it settles', () => {
      const c = create().componentInstance;
      c.setMode('LADDERS');
      c.open(batch());
      c.confirm();
      expect(dialogs.confirm).toHaveBeenCalledTimes(1);
      expect(api.confirmLadderImport).toHaveBeenCalledWith('l1');
      expect(c.selected?.status).toBe('APPLYING');
      api.ladderImportBatch.mockReturnValueOnce(of(batch({ status: 'APPLIED', laddersCreated: 1 })));
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS);
      expect(c.selected?.status).toBe('APPLIED');
      c.selected = batch();
      c.cancel();
      expect(api.cancelLadderImport).toHaveBeenCalledWith('l1');
      expect(api.confirmImport).not.toHaveBeenCalled();
      expect(api.cancelImport).not.toHaveBeenCalled();
    });

    it('does not poll a batch the person may not read', () => {
      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create, ORG_LADDER_IMPORT_PRIVILEGES.list, ORG_LADDER_IMPORT_PRIVILEGES.confirm]);
      const c = create().componentInstance;
      c.setMode('LADDERS');
      c.open(batch({ status: 'APPLYING', errorRows: 0 }));
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS * 2);
      expect(api.ladderImportBatch).not.toHaveBeenCalled();
    });
  });

  describe('the sample file', () => {
    it('is built in the browser with the header and the job titles of one ladder, no server call', () => {
      const c = create().componentInstance;
      const created: Blob[] = [];
      URL.createObjectURL = vi.fn((b: Blob) => { created.push(b); return 'blob:x'; });
      URL.revokeObjectURL = vi.fn();
      const names: string[] = [];
      const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { names.push(this.download); });
      c.setMode('LADDERS');
      c.downloadSample();
      expect(names).toEqual([LADDER_IMPORT_SAMPLE_FILE_NAME]);
      expect(created[0].size).toBe(buildImportCsv(LADDER_IMPORT_HEADER, LADDER_IMPORT_SAMPLE_ROWS).length);
      expect(api.createLadderImport).not.toHaveBeenCalled();
      click.mockRestore();
    });

    it('has the same content as docs/samples/org-ladders-import-sample.csv', () => {
      expect(buildImportCsv(LADDER_IMPORT_HEADER, LADDER_IMPORT_SAMPLE_ROWS).split(/\r?\n/).filter(Boolean)).toEqual([
        'ladder,position,rank,managerial', 'Standard,Head,3,yes', 'Standard,Lead,2,yes', 'Standard,Member,1,no',
      ]);
    });
  });

  describe('the employee sample', () => {
    it('has the is_head column, with the head of each department marked yes', () => {
      expect(IMPORT_TEMPLATE_HEADER.join(',')).toBe('employee_code,full_name,department_code,position_name,email,phone,manager_employee_code,joined_on,is_head');
      const column = IMPORT_TEMPLATE_HEADER.indexOf('is_head');
      const heads = IMPORT_SAMPLE_ROWS.filter((row) => row[column] === 'yes');
      expect(heads.length).toBeGreaterThan(0);
      expect(heads.every((row) => row[3] === 'Head' || row[3] === 'Lead')).toBe(true);
      expect(new Set(heads.map((row) => row[2])).size).toBe(heads.length);
      IMPORT_SAMPLE_ROWS.forEach((row) => expect(['yes', ''], row[0]).toContain(row[column]));
    });
  });

  describe('the heads note on the employee report', () => {
    const note = (fixture: ComponentFixture<OrgImport>) => testId(fixture, 'heads-set-note');
    const show = (headsSet: number | null | undefined, mode: 'EMPLOYEES' | 'DEPARTMENTS' = 'EMPLOYEES') => {
      const fixture = create();
      if (mode !== 'EMPLOYEES') fixture.componentInstance.setMode(mode);
      fixture.componentInstance.selected = batch({ errorRows: 0, headsSet });
      refresh(fixture);
      return fixture;
    };

    it('says how many departments got a head, with a form for one and a form for several', () => {
      const several = show(4);
      expect(several.componentInstance.headsSetKey).toBe('orgStructure.import.headsSet');
      expect(note(several)?.textContent).toContain('4 heads set');
      const one = show(1);
      expect(one.componentInstance.headsSetKey).toBe('orgStructure.import.headsSetOne');
      expect(note(one)?.textContent).toContain('1 head set');
    });

    it('stays silent for none, for a report that says nothing, and on the other tabs', () => {
      expect(note(show(0))).toBeNull();
      expect(note(show(null))).toBeNull();
      expect(note(show(undefined))).toBeNull();
      expect(note(show(-1))).toBeNull();
      expect(note(show(3, 'DEPARTMENTS'))).toBeNull();
    });

    it('reads a second head for a department as its code and the row of the first', () => {
      const fixture = create();
      api.importErrors.mockReturnValueOnce(of([employeeError(5, ['HEAD_DUPLICATE:TECH:2'])]));
      fixture.componentInstance.open(batch({ id: 'e1' }));
      expect(fixture.componentInstance.errorRows[0].problems).toBe('Department TECH already has a head on row 2');
    });
  });

  describe('the hint to load the ladders and job titles first', () => {
    const hint = (fixture: ComponentFixture<OrgImport>) => testId(fixture, 'ladders-hint');
    const openEmployees = (fixture: ComponentFixture<OrgImport>, errors: string[]) => {
      api.importErrors.mockReturnValueOnce(of([employeeError(1, errors)]));
      fixture.componentInstance.open(batch({ id: 'e1' }));
      refresh(fixture);
    };
    const openDepartments = (fixture: ComponentFixture<OrgImport>, errors: string[]) => {
      fixture.componentInstance.setMode('DEPARTMENTS');
      api.departmentImportErrors.mockReturnValueOnce(of([deptError(1, errors)]));
      fixture.componentInstance.open(batch());
      refresh(fixture);
    };

    it.each(['POSITION_REQUIRED', 'POSITION_NOT_IN_LADDER:Senior'])('appears for an employee row rejected with %s', (error) => {
      const fixture = create();
      openEmployees(fixture, [error]);
      expect(fixture.componentInstance.showLaddersHint).toBe(true);
      expect(hint(fixture)?.textContent).toContain('orgStructure.import.laddersHint.text');
    });

    it('appears for an employee row whose English text says the position is unknown or not in the ladder, in any capitals', () => {
      const first = create();
      openEmployees(first, ['Unknown position Senior']);
      expect(first.componentInstance.showLaddersHint).toBe(true);
      const second = create();
      openEmployees(second, ['POSITION SENIOR IS NOT IN THE LADDER']);
      expect(second.componentInstance.showLaddersHint).toBe(true);
    });

    it('appears for a department row rejected with an unknown ladder, by code or by text', () => {
      const byCode = create();
      openDepartments(byCode, ['LADDER_UNKNOWN:Gold']);
      expect(byCode.componentInstance.showLaddersHint).toBe(true);
      expect(hint(byCode)).not.toBeNull();
      const byText = create();
      openDepartments(byText, ['Unknown position ladder Gold']);
      expect(byText.componentInstance.showLaddersHint).toBe(true);
    });

    it('its button opens the Ladders tab, where the hint is gone', () => {
      const fixture = create();
      openEmployees(fixture, ['POSITION_REQUIRED']);
      testId(fixture, 'ladders-hint-open')?.dispatchEvent(new Event('click'));
      refresh(fixture);
      expect(fixture.componentInstance.mode).toBe('LADDERS');
      expect(fixture.componentInstance.showLaddersHint).toBe(false);
      expect(hint(fixture)).toBeNull();
    });

    it('is shown without its button to someone who may not import ladders', () => {
      granted = new Set([ORG_IMPORT_PRIVILEGES.create, ORG_DEPARTMENT_IMPORT_PRIVILEGES.create]);
      const fixture = create();
      openEmployees(fixture, ['POSITION_REQUIRED']);
      expect(hint(fixture)).not.toBeNull();
      expect(testId(fixture, 'ladders-hint-open')).toBeNull();
    });

    it('stays away for other problems, without rejected rows, and after another file is opened or the tab changes', () => {
      const fixture = create();
      openEmployees(fixture, ['EMAIL_INVALID']);
      expect(hint(fixture)).toBeNull();
      fixture.componentInstance.sawLaddersNeeded = true;
      fixture.componentInstance.selected = batch({ errorRows: 0 });
      expect(fixture.componentInstance.showLaddersHint).toBe(false);
      fixture.componentInstance.selected = batch({ errorRows: 1 });
      expect(fixture.componentInstance.showLaddersHint).toBe(true);
      api.importErrors.mockReturnValueOnce(of([employeeError(1, ['EMAIL_INVALID'])]));
      fixture.componentInstance.open(batch({ id: 'e2' }));
      expect(fixture.componentInstance.sawLaddersNeeded).toBe(false);
      fixture.componentInstance.sawLaddersNeeded = true;
      fixture.componentInstance.setMode('DEPARTMENTS');
      expect(fixture.componentInstance.sawLaddersNeeded).toBe(false);
    });

    it('is never shown on the Ladders tab itself, whatever its rows say', () => {
      const fixture = create();
      fixture.componentInstance.setMode('LADDERS');
      api.ladderImportErrors.mockReturnValueOnce(of([ladderError(1, ['POSITION_REQUIRED'])]));
      fixture.componentInstance.open(batch());
      expect(fixture.componentInstance.showLaddersHint).toBe(false);
    });
  });
});
