import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { ORG_PAGE_SIZE } from '../org-structure.constants';
import { OrgPerson, OrgUnit } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { OrgChart } from './org-chart';

const unit = (id: string, over: Partial<OrgUnit> = {}): OrgUnit =>
  ({ id, parentId: null, code: id.toUpperCase(), name: 'Unit ' + id, unitType: null, ladderId: null, headEmployeeId: null, headName: null, headUserId: null, status: 'ACTIVE', childCount: 0, headcount: 3, ...over }) as OrgUnit;
const person = (id: string): OrgPerson =>
  ({ employeeId: id, employeeCode: 'C' + id, fullName: 'Person ' + id, email: null, unitId: 'ops', positionId: 'p', positionName: 'Lead', managerEmployeeId: null });

describe('OrgChart', () => {
  const api = {
    children: vi.fn((parentId?: string): Observable<OrgUnit[]> =>
      of(parentId === 'ops' ? [unit('ops-a', { parentId: 'ops' })] : [unit('ops', { childCount: 1, headEmployeeId: 'e2', headName: 'Boss', headUserId: 'u-boss' }), unit('sales')])),
    people: vi.fn((): Observable<OrgPerson[]> => of([person('e1'), person('e2')])),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(): ComponentFixture<OrgChart> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrgChart],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: OrgStructureService, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: OrganizationContextService, useValue: { currentName$: of('Acme') } },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(OrgChart);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
  });

  it('loads the top-level departments and builds the people columns', () => {
    const chart = create().componentInstance;
    expect(api.children).toHaveBeenCalledWith();
    expect(chart.roots.map((n) => n.unit.id)).toEqual(['ops', 'sales']);
    expect(chart.visible).toHaveLength(2);
    expect(chart.isLoading).toBe(false);
    expect(chart.peopleColumns.map((c) => c.key)).toEqual(['employeeCode', 'fullName', 'role', 'positionName', 'email']);
    expect(chart.trackNode(0, chart.roots[1])).toBe('sales');
  });

  it('shows an error state when the tree cannot load', () => {
    api.children.mockReturnValueOnce(throwError(() => new Error('down')));
    const fixture = create();
    expect(fixture.componentInstance.hasError).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('orgStructure.chart.loadFailed');
  });

  it('says so when the organization has no departments', () => {
    api.children.mockReturnValueOnce(of([]));
    expect(create().nativeElement.textContent).toContain('orgStructure.chart.noUnits');
  });

  it('expands a department by fetching its children once, then toggles from memory', () => {
    const chart = create().componentInstance;
    const ops = chart.roots[0];
    const stop = vi.fn();
    chart.toggle(ops, { stopPropagation: stop } as unknown as Event);
    expect(stop).toHaveBeenCalled();
    expect(api.children).toHaveBeenCalledWith('ops');
    expect(ops.expanded).toBe(true);
    expect(chart.visible.map((n) => [n.unit.id, n.depth])).toEqual([['ops', 0], ['ops-a', 1], ['sales', 0]]);
    chart.toggle(ops);
    expect(chart.visible.map((n) => n.unit.id)).toEqual(['ops', 'sales']);
    chart.toggle(ops);
    expect(ops.expanded).toBe(true);
    expect(api.children).toHaveBeenCalledTimes(2);
  });

  it('reports a failed expansion and leaves the node closed', () => {
    const chart = create().componentInstance;
    api.children.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Slow' } })));
    chart.toggle(chart.roots[0]);
    expect(notify.error).toHaveBeenCalledWith('Slow');
    expect(chart.roots[0].loading).toBe(false);
    expect(chart.roots[0].expanded).toBe(false);
    api.children.mockReturnValueOnce(throwError(() => undefined));
    chart.toggle(chart.roots[0]);
    expect(notify.error).toHaveBeenLastCalledWith('orgStructure.chart.loadFailed');
  });

  it('selecting a department loads its people with the head listed first and marked', () => {
    const chart = create().componentInstance;
    chart.select(chart.roots[0]);
    expect(api.people).toHaveBeenCalledWith('ops', null, ORG_PAGE_SIZE);
    expect(chart.peopleRows.map((r) => [r.employeeId, r.role])).toEqual([['e2', 'orgStructure.chart.headBadge'], ['e1', '']]);
    expect(chart.hasMorePeople).toBe(false);
  });

  it('offers load more when a full page came back and continues after the last person', () => {
    const chart = create().componentInstance;
    api.people.mockReturnValueOnce(of(Array.from({ length: ORG_PAGE_SIZE }, (_, i) => person('p' + i))));
    chart.select(chart.roots[1]);
    expect(chart.hasMorePeople).toBe(true);
    api.people.mockReturnValueOnce(of([person('last')]));
    chart.loadPeople();
    expect(api.people).toHaveBeenLastCalledWith('sales', 'p' + (ORG_PAGE_SIZE - 1), ORG_PAGE_SIZE);
    expect(chart.people).toHaveLength(ORG_PAGE_SIZE + 1);
    expect(chart.hasMorePeople).toBe(false);
  });

  it('does nothing to load people without a selection, and reports a failed load', () => {
    const chart = create().componentInstance;
    chart.loadPeople();
    expect(api.people).not.toHaveBeenCalled();
    api.people.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Nope' } })));
    chart.select(chart.roots[0]);
    expect(notify.error).toHaveBeenCalledWith('Nope');
    expect(chart.peopleLoading).toBe(false);
  });

  describe('rendering', () => {
    it('renders the tree rows and the head of the selected department', () => {
      const fixture = create();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Unit ops');
      expect(el.textContent).toContain('Boss');
      const rows = el.querySelectorAll('.tree__row');
      expect(rows).toHaveLength(2);
      (rows[0] as HTMLElement).click();
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      expect(fixture.componentInstance.selected?.unit.id).toBe('ops');
      expect(el.querySelector('.org-chart__head')?.textContent).toContain('orgStructure.chart.headOf');
    });

    it('shows the head wallet shortcuts only to holders of the wallet privileges', () => {
      granted = new Set(['WALLET_ADMIN_USER_WALLET_READ']);
      const fixture = create();
      fixture.componentInstance.select(fixture.componentInstance.roots[0]);
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.org-chart__head-links a')).map((a) => a.textContent?.trim());
      expect(links).toEqual(['orgStructure.chart.headWallet']);
    });

    it('hides both shortcuts without the privileges, and shows both with them', () => {
      granted = new Set();
      let fixture = create();
      fixture.componentInstance.select(fixture.componentInstance.roots[0]);
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).querySelectorAll('.org-chart__head-links a')).toHaveLength(0);
      granted = new Set(['WALLET_ADMIN_USER_WALLET_READ', 'WALLET_ADMIN_TEAM_MOVEMENT_READ']);
      fixture = create();
      fixture.componentInstance.select(fixture.componentInstance.roots[0]);
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).querySelectorAll('.org-chart__head-links a')).toHaveLength(2);
    });

    it('shows a no-head note for a department without a head', () => {
      const fixture = create();
      fixture.componentInstance.select(fixture.componentInstance.roots[1]);
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('orgStructure.chart.noHead');
    });
  });
});
