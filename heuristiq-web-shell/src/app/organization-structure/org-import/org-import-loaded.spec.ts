import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Observable, of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { IMPORT_FACT_PRIVILEGES, IMPORT_LOADED_LEVEL_PAGE, IMPORT_LOADED_MAX_CALLS, IMPORT_LOADED_MAX_LADDERS } from '../org-structure.constants';
import { OrgImportMode, OrgLadder, OrgPosition, OrgUnit } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { OrgImportLoaded } from './org-import-loaded';

const ladder = (id: string, name = 'Ladder ' + id): OrgLadder => ({ id, name });
const position = (name: string, rank: number, managerial = false): OrgPosition => ({ id: name, ladderId: 'l1', name, rank, managerial });
const unit = (code: string, over: Partial<OrgUnit> = {}): OrgUnit =>
  ({ id: code, parentId: null, code, name: code + ' name', unitType: 'TEAM', ladderId: 'l1', headEmployeeId: null, headName: null, status: 'ACTIVE', childCount: 0, headcount: 0, ...over });

describe('OrgImportLoaded: what is loaded now', () => {
  const api = {
    ladders: vi.fn((): Observable<OrgLadder[]> => of([])),
    positions: vi.fn((_id: string): Observable<OrgPosition[]> => of([])),
    children: vi.fn((_parentId?: string): Observable<OrgUnit[]> => of([])),
  };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(mode: OrgImportMode, refresh = 0): ComponentFixture<OrgImportLoaded> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrgImportLoaded],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: OrgStructureService, useValue: api },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(OrgImportLoaded);
    fixture.componentRef.setInput('mode', mode);
    fixture.componentRef.setInput('refresh', refresh);
    fixture.detectChanges();
    return fixture;
  }

  const el = (f: ComponentFixture<OrgImportLoaded>) => f.nativeElement as HTMLElement;
  const q = (f: ComponentFixture<OrgImportLoaded>, id: string) => el(f).querySelector(`[data-testid="${id}"]`);
  const all = (f: ComponentFixture<OrgImportLoaded>, id: string) => Array.from(el(f).querySelectorAll(`[data-testid="${id}"]`));
  const refresh = (f: ComponentFixture<OrgImportLoaded>) => { f.changeDetectorRef.markForCheck(); f.detectChanges(); };
  const click = (f: ComponentFixture<OrgImportLoaded>, target: Element | null) => { target?.dispatchEvent(new Event('click')); refresh(f); };

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    api.ladders.mockImplementation(() => of([]));
    api.positions.mockImplementation(() => of([]));
    api.children.mockImplementation(() => of([]));
  });

  describe('step 1: ladders and job titles', () => {
    it('lists each ladder with its job titles, most senior first, and counts them', () => {
      api.ladders.mockImplementation(() => of([ladder('l1', 'Standard'), ladder('l2', 'Sales')]));
      api.positions.mockImplementation((id: string) => of(id === 'l1' ? [position('Member', 1), position('Head', 3, true), position('Lead', 2, true)] : [position('Rep', 1)]));
      const f = create('LADDERS');
      expect(f.componentInstance.ladders.map((l) => l.positions?.map((p) => p.name))).toEqual([['Head', 'Lead', 'Member'], ['Rep']]);
      expect(q(f, 'loaded-summary')?.textContent).toContain('orgStructure.import.loaded.tiles.positions');
      expect(f.componentInstance.positionTotal).toBe(4);
      const card = q(f, 'loaded-ladder-l1');
      expect(card?.textContent).toContain('Standard');
      const cells = Array.from(card?.querySelectorAll('tbody tr') ?? []).map((tr) => Array.from(tr.querySelectorAll('td')).map((td) => td.textContent?.trim()));
      expect(cells[0]).toEqual(['Head', '3', 'orgStructure.import.loaded.yes']);
      expect(cells[2]).toEqual(['Member', '1', 'orgStructure.import.loaded.no']);
      expect(card?.querySelectorAll('th[scope="col"]')).toHaveLength(3);
    });

    it('guides the person when nothing is loaded yet', () => {
      const f = create('LADDERS');
      expect(q(f, 'loaded-empty')?.textContent).toContain('orgStructure.import.loaded.empty.ladders');
      expect(q(f, 'loaded-summary')).toBeNull();
    });

    it('shows the ladders without their job titles to someone who may not read job titles, and makes no call for them', () => {
      granted = new Set([IMPORT_FACT_PRIVILEGES.ladders]);
      api.ladders.mockImplementation(() => of([ladder('l1')]));
      const f = create('LADDERS');
      expect(api.positions).not.toHaveBeenCalled();
      expect(f.componentInstance.positionsReadable).toBe(false);
      expect(q(f, 'loaded-summary')?.textContent).not.toContain('orgStructure.import.loaded.tiles.positions');
      expect(el(f).querySelector('table')).toBeNull();
    });

    it('stays out of the page, with no call, for someone who may not read ladders', () => {
      granted = new Set();
      const f = create('LADDERS');
      expect(q(f, 'loaded-LADDERS')).toBeNull();
      expect(f.componentInstance.visible).toBe(false);
      expect(api.ladders).not.toHaveBeenCalled();
    });

    it('reads job titles for no more ladders than the limit', () => {
      api.ladders.mockImplementation(() => of(Array.from({ length: IMPORT_LOADED_MAX_LADDERS + 5 }, (_, i) => ladder('x' + i))));
      create('LADDERS');
      expect(api.positions).toHaveBeenCalledTimes(IMPORT_LOADED_MAX_LADDERS);
    });

    it('says so inline, without a toast, when the ladders cannot be read, and tries again on request', () => {
      api.ladders.mockImplementationOnce(() => throwError(() => new Error('down')));
      const f = create('LADDERS');
      expect(f.componentInstance.state).toBe('error');
      expect(q(f, 'loaded-error')?.getAttribute('role')).toBe('alert');
      click(f, q(f, 'loaded-retry'));
      expect(f.componentInstance.state).toBe('ready');
      expect(q(f, 'loaded-error')).toBeNull();
    });

    it('also reports a failed read of job titles inline', () => {
      api.ladders.mockImplementation(() => of([ladder('l1')]));
      api.positions.mockImplementation(() => throwError(() => new Error('down')));
      expect(create('LADDERS').componentInstance.state).toBe('error');
    });
  });

  describe('step 2: the departments', () => {
    it('shows a loading status while the first read is pending', () => {
      const pending = new Subject<OrgUnit[]>();
      api.children.mockImplementation(() => pending);
      const f = create('DEPARTMENTS');
      const status = q(f, 'loaded-loading');
      expect(status?.getAttribute('role')).toBe('status');
      expect(status?.getAttribute('aria-live')).toBe('polite');
      pending.next([unit('HQ')]);
      pending.complete();
      refresh(f);
      expect(q(f, 'loaded-loading')).toBeNull();
      expect(q(f, 'loaded-unit-HQ')).not.toBeNull();
    });

    it('shows code, name, type, the ladder by name, head and headcount, with a summary', () => {
      api.ladders.mockImplementation(() => of([ladder('l1', 'Standard')]));
      api.children.mockImplementation(() => of([unit('HQ', { headName: 'Rajesh', headcount: 9 })]));
      const f = create('DEPARTMENTS');
      const cells = Array.from(q(f, 'loaded-unit-HQ')?.querySelectorAll('td') ?? []).map((td) => td.textContent?.trim());
      expect(cells).toEqual(['HQ', 'HQ name', 'TEAM', 'Standard', 'Rajesh', '9']);
      expect(q(f, 'loaded-summary')?.textContent).toContain('orgStructure.import.loaded.tiles.departments');
      expect(q(f, 'loaded-summary')?.textContent).not.toContain('atLeastValue');
      expect(q(f, 'loaded-open-chart')).not.toBeNull();
      expect(f.componentInstance.departmentSummary).toEqual({ departments: 1, withoutLadder: 0, atLeast: false });
    });

    it('warns, in words and with an icon, about a department that has no ladder, and counts it', () => {
      api.ladders.mockImplementation(() => of([ladder('l1')]));
      api.children.mockImplementation(() => of([unit('HQ'), unit('OPS', { ladderId: null })]));
      const f = create('DEPARTMENTS');
      const warnings = all(f, 'loaded-no-ladder');
      expect(warnings).toHaveLength(1);
      expect(warnings[0].textContent).toContain('orgStructure.import.loaded.noLadder');
      expect(warnings[0].querySelector('i')?.getAttribute('aria-hidden')).toBe('true');
      expect(q(f, 'loaded-unit-OPS')?.contains(warnings[0])).toBe(true);
      expect(f.componentInstance.departmentSummary.withoutLadder).toBe(1);
    });

    it('does not claim a missing ladder when the ladders may not be read: the cell is just empty', () => {
      granted = new Set([IMPORT_FACT_PRIVILEGES.units]);
      api.children.mockImplementation(() => of([unit('HQ')]));
      const f = create('DEPARTMENTS');
      expect(api.ladders).not.toHaveBeenCalled();
      expect(all(f, 'loaded-no-ladder')).toHaveLength(0);
      expect(f.componentInstance.ladderName(unit('HQ'))).toBe('');
    });

    it('survives a failed read of the ladder names', () => {
      api.ladders.mockImplementation(() => throwError(() => new Error('down')));
      api.children.mockImplementation(() => of([unit('HQ')]));
      expect(create('DEPARTMENTS').componentInstance.state).toBe('ready');
    });

    it('reads the departments below one only when it is opened, once, and shows them indented', () => {
      api.children.mockImplementation((parent?: string) => of(parent === 'HQ' ? [unit('TECH', { parentId: 'HQ' })] : [unit('HQ', { childCount: 1 })]));
      const f = create('DEPARTMENTS');
      expect(api.children).toHaveBeenCalledTimes(1);
      const expander = q(f, 'loaded-expander');
      expect(expander?.getAttribute('aria-expanded')).toBe('false');
      expect(expander?.getAttribute('aria-label')).toContain('orgStructure.import.loaded.expand');
      click(f, expander);
      expect(api.children).toHaveBeenCalledWith('HQ');
      expect(q(f, 'loaded-unit-TECH')).not.toBeNull();
      expect(all(f, 'loaded-expander')[0].getAttribute('aria-expanded')).toBe('true');
      const rootIndent = (q(f, 'loaded-unit-HQ')?.querySelector('td') as HTMLElement).style.paddingInlineStart;
      const childIndent = (q(f, 'loaded-unit-TECH')?.querySelector('td') as HTMLElement).style.paddingInlineStart;
      expect(parseFloat(childIndent)).toBeGreaterThan(parseFloat(rootIndent));
      click(f, q(f, 'loaded-expander'));
      expect(q(f, 'loaded-unit-TECH')).toBeNull();
      click(f, q(f, 'loaded-expander'));
      expect(q(f, 'loaded-unit-TECH')).not.toBeNull();
      expect(api.children).toHaveBeenCalledTimes(2);
    });

    it('says "at least" in the summary while departments below are unread, and exactly once they are read', () => {
      api.children.mockImplementation((parent?: string) => of(parent === 'HQ' ? [unit('TECH')] : [unit('HQ', { childCount: 1 })]));
      const f = create('DEPARTMENTS');
      expect(q(f, 'loaded-summary')?.textContent).toContain('orgStructure.import.loaded.atLeastValue');
      click(f, q(f, 'loaded-expander'));
      expect(q(f, 'loaded-summary')?.textContent).not.toContain('atLeastValue');
      expect(f.componentInstance.departmentSummary.departments).toBe(2);
    });

    it('shows a department that has a child count but cannot be expanded further as a plain row', () => {
      api.children.mockImplementation(() => of([unit('HQ')]));
      expect(all(create('DEPARTMENTS'), 'loaded-expander')).toHaveLength(0);
    });

    it('says inline that the departments under one could not be read, and reads them again on request', () => {
      let fail = true;
      api.children.mockImplementation((parent?: string) => (parent === 'HQ' ? (fail ? throwError(() => new Error('down')) : of([unit('TECH')])) : of([unit('HQ', { childCount: 1 })])));
      const f = create('DEPARTMENTS');
      click(f, q(f, 'loaded-expander'));
      expect(q(f, 'loaded-children-error')?.getAttribute('role')).toBe('alert');
      fail = false;
      click(f, el(f).querySelector('[data-testid="loaded-children-error"] button'));
      expect(q(f, 'loaded-children-error')).toBeNull();
      expect(q(f, 'loaded-unit-TECH')).not.toBeNull();
    });

    it('shows a page of departments per level, then the rest behind "Show more"', () => {
      api.children.mockImplementation(() => of(Array.from({ length: IMPORT_LOADED_LEVEL_PAGE + 50 }, (_, i) => unit('D' + String(i).padStart(3, '0')))));
      const f = create('DEPARTMENTS');
      expect(el(f).querySelectorAll('[data-testid^="loaded-unit-"]')).toHaveLength(IMPORT_LOADED_LEVEL_PAGE);
      expect(q(f, 'loaded-summary')?.textContent).toContain('orgStructure.import.loaded.atLeastValue');
      const more = q(f, 'loaded-show-more');
      expect(more?.textContent).toContain('orgStructure.import.loaded.showMore');
      click(f, more);
      expect(el(f).querySelectorAll('[data-testid^="loaded-unit-"]')).toHaveLength(IMPORT_LOADED_LEVEL_PAGE + 50);
      expect(q(f, 'loaded-show-more')).toBeNull();
      expect(q(f, 'loaded-summary')?.textContent).not.toContain('atLeastValue');
    });

    it('guides the person when there are no departments, and stays out of the page without the right to list them', () => {
      expect(q(create('DEPARTMENTS'), 'loaded-empty')?.textContent).toContain('orgStructure.import.loaded.empty.departments');
      granted = new Set([IMPORT_FACT_PRIVILEGES.ladders]);
      vi.clearAllMocks();
      const f = create('DEPARTMENTS');
      expect(q(f, 'loaded-DEPARTMENTS')).toBeNull();
      expect(api.children).not.toHaveBeenCalled();
    });

    it('reports a failed first read inline', () => {
      api.children.mockImplementation(() => throwError(() => new Error('down')));
      const f = create('DEPARTMENTS');
      expect(q(f, 'loaded-error')).not.toBeNull();
      expect(q(f, 'loaded-tree')).toBeNull();
    });
  });

  describe('step 3: the people', () => {
    it('sums the people, the departments that hold them and the heads, reading every level', () => {
      api.children.mockImplementation((parent?: string) => {
        if (!parent) return of([unit('HQ', { childCount: 2, headcount: 12, headEmployeeId: 'e1', headName: 'Rajesh' })]);
        return of([unit('TECH', { headcount: 7, headEmployeeId: 'e2', headName: 'Priya' }), unit('HR', { headcount: 5 })]);
      });
      const f = create('EMPLOYEES');
      expect(f.componentInstance.employeeSummary).toEqual({ employees: 12, departments: 3, heads: 2, atLeast: false });
      expect(q(f, 'loaded-summary')?.textContent).toContain('orgStructure.import.loaded.tiles.departmentsWithPeople');
      const rows = Array.from(q(f, 'loaded-people')?.querySelectorAll('tbody tr') ?? []).map((tr) => Array.from(tr.querySelectorAll('td')).map((td) => td.textContent?.trim()));
      expect(rows).toEqual([['HQ name (HQ)', 'Rajesh', '12'], ['TECH name (TECH)', 'Priya', '7'], ['HR name (HR)', '', '5']]);
    });

    it('guides the person when nobody is placed yet', () => {
      api.children.mockImplementation(() => of([unit('HQ')]));
      expect(q(create('EMPLOYEES'), 'loaded-empty')?.textContent).toContain('orgStructure.import.loaded.empty.employees');
    });

    it('says "at least" when it had to stop reading, within its call budget, and when one level cannot be read', () => {
      api.children.mockImplementation((parent?: string) => {
        if (!parent) return of(Array.from({ length: IMPORT_LOADED_MAX_CALLS + 3 }, (_, i) => unit('T' + i, { childCount: 1, headcount: 1 })));
        return of([]);
      });
      const f = create('EMPLOYEES');
      expect(api.children).toHaveBeenCalledTimes(1 + IMPORT_LOADED_MAX_CALLS);
      expect(f.componentInstance.employeeSummary.atLeast).toBe(true);
      expect(q(f, 'loaded-summary')?.textContent).toContain('orgStructure.import.loaded.atLeastValue');

      api.children.mockImplementation((parent?: string) => (parent ? throwError(() => new Error('down')) : of([unit('HQ', { childCount: 1, headcount: 3 })])));
      expect(create('EMPLOYEES').componentInstance.employeeSummary.atLeast).toBe(true);
    });

    it('pages a long list of departments behind "Show more"', () => {
      api.children.mockImplementation(() => of(Array.from({ length: IMPORT_LOADED_LEVEL_PAGE + 20 }, (_, i) => unit('D' + i, { headcount: 1 }))));
      const f = create('EMPLOYEES');
      expect(f.componentInstance.employeeRows).toHaveLength(IMPORT_LOADED_LEVEL_PAGE);
      click(f, q(f, 'loaded-show-more'));
      expect(f.componentInstance.employeeRows).toHaveLength(IMPORT_LOADED_LEVEL_PAGE + 20);
      expect(q(f, 'loaded-show-more')).toBeNull();
    });

    it('reports a failed first read inline', () => {
      api.children.mockImplementation(() => throwError(() => new Error('down')));
      expect(create('EMPLOYEES').componentInstance.state).toBe('error');
    });
  });

  describe('every section', () => {
    it('collapses and opens from its heading button, which says which with aria-expanded', () => {
      api.ladders.mockImplementation(() => of([ladder('l1')]));
      const f = create('LADDERS');
      const toggle = q(f, 'loaded-toggle');
      expect(toggle?.getAttribute('aria-expanded')).toBe('true');
      expect(toggle?.getAttribute('aria-controls')).toBe(f.componentInstance.bodyId);
      expect(el(f).querySelector('#' + f.componentInstance.bodyId)).not.toBeNull();
      click(f, toggle);
      expect(q(f, 'loaded-toggle')?.getAttribute('aria-expanded')).toBe('false');
      expect(el(f).querySelector('#' + f.componentInstance.bodyId)).toBeNull();
      expect(el(f).querySelector('section')?.getAttribute('aria-labelledby')).toBe(f.componentInstance.headingId);
    });

    it('reads again when its refresh signal changes or the step changes, and not otherwise', () => {
      const f = create('LADDERS');
      expect(api.ladders).toHaveBeenCalledTimes(1);
      f.componentRef.setInput('refresh', 1);
      f.detectChanges();
      expect(api.ladders).toHaveBeenCalledTimes(2);
      f.detectChanges();
      expect(api.ladders).toHaveBeenCalledTimes(2);
      api.ladders.mockImplementation(() => of([ladder('l9', 'Late')]));
      f.componentRef.setInput('refresh', 2);
      f.detectChanges();
      expect(q(f, 'loaded-ladder-l9')).not.toBeNull();
      f.componentRef.setInput('mode', 'DEPARTMENTS');
      f.detectChanges();
      expect(api.children).toHaveBeenCalled();
    });

    it('drops an unfinished read when a newer one starts, and when the screen is left', () => {
      const first = new Subject<OrgLadder[]>();
      api.ladders.mockImplementationOnce(() => first);
      const f = create('LADDERS');
      api.ladders.mockImplementation(() => of([ladder('l2', 'New')]));
      f.componentRef.setInput('refresh', 1);
      f.detectChanges();
      first.next([ladder('l1', 'Old')]);
      expect(f.componentInstance.ladders.map((l) => l.ladder.name)).toEqual(['New']);
      f.destroy();
    });
  });
});
