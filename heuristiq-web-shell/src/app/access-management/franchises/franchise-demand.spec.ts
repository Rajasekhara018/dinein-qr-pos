import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { FranchiseApi } from './franchise-api';
import { FranchiseDemand } from './franchise-demand';
import { demandQueryString, trailUpTo } from './franchise-demand.helpers';
import { PlaceDemand } from './franchise-demand.models';

describe('franchise demand helpers', () => {
  it('builds the summary query, leaving the parent out for the top places', () => {
    expect(demandQueryString({ applicationCode: 'RIDE', page: 0, size: 20 })).toBe('?applicationCode=RIDE&page=0&size=20');
    expect(demandQueryString({ applicationCode: 'RIDE', parentId: 'loc 1', page: 2, size: 50 }))
      .toBe('?applicationCode=RIDE&parentId=loc+1&page=2&size=50');
  });

  it('cuts the drill-down path back to a step, or to the top places', () => {
    const trail = [{ locationId: 's', name: 'State' }, { locationId: 'd', name: 'District' }, { locationId: 't', name: 'Town' }];
    expect(trailUpTo(trail, 0)).toEqual([{ locationId: 's', name: 'State' }]);
    expect(trailUpTo(trail, 1).map((s) => s.locationId)).toEqual(['s', 'd']);
    expect(trailUpTo(trail, -1)).toEqual([]);
  });
});

describe('FranchiseDemand', () => {
  const api = { demandSummary: vi.fn() };
  const uam = { list: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  const state: PlaceDemand = {
    locationId: 'loc-ap', code: 'AP', name: 'Andhra Pradesh', type: 'STATE', total: 5, customers: 3, providers: 1, partners: 1,
    covered: false, coveringFranchiseCode: null,
  };
  const district: PlaceDemand = {
    locationId: 'loc-kr', code: 'AP-KR', name: 'Krishna', type: 'DISTRICT', total: 4, customers: 2, providers: 1, partners: 1,
    covered: true, coveringFranchiseCode: 'RBM-00001',
  };

  function create(): ComponentFixture<FranchiseDemand> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [FranchiseDemand],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: FranchiseApi, useValue: api },
        { provide: UamService, useValue: uam },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(FranchiseDemand);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    uam.list.mockReturnValue(of({ content: [{ code: 'BESTRIDE_RIDER_APP', name: 'BestRide' }, { code: 'MATRIMONY', name: 'Matrimony' }] }));
    api.demandSummary.mockReturnValue(of({ items: [state], page: 0, size: 20, totalElements: 1 }));
  });

  it('opens on the first application and its top places', () => {
    const component = create().componentInstance;

    expect(component.applicationCode).toBe('BESTRIDE_RIDER_APP');
    expect(api.demandSummary).toHaveBeenCalledWith({ applicationCode: 'BESTRIDE_RIDER_APP', parentId: undefined, page: 0, size: 20 });
    expect(component.items).toEqual([state]);
    expect(component.total).toBe(1);
  });

  it('drills into a place and back up the path', () => {
    const component = create().componentInstance;
    api.demandSummary.mockReturnValue(of({ items: [district], page: 0, size: 20, totalElements: 1 }));

    component.drillInto(state);
    expect(api.demandSummary).toHaveBeenLastCalledWith(expect.objectContaining({ parentId: 'loc-ap', page: 0 }));
    expect(component.trail).toEqual([{ locationId: 'loc-ap', name: 'Andhra Pradesh' }]);

    component.goUpTo(-1);
    expect(component.trail).toEqual([]);
    expect(api.demandSummary).toHaveBeenLastCalledWith(expect.objectContaining({ parentId: undefined }));
  });

  it('starts over at the top places when the application changes', () => {
    const component = create().componentInstance;
    component.drillInto(state);
    component.applicationCode = 'MATRIMONY';

    component.onApplicationChange();

    expect(component.trail).toEqual([]);
    expect(api.demandSummary).toHaveBeenLastCalledWith({ applicationCode: 'MATRIMONY', parentId: undefined, page: 0, size: 20 });
  });

  it('pages and resizes', () => {
    const component = create().componentInstance;

    component.goToPage(2);
    expect(api.demandSummary).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }));
    component.onPageSizeChange(50);
    expect(api.demandSummary).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 50 }));
  });

  it('shows the coverage of each place and never who asked', () => {
    api.demandSummary.mockReturnValue(of({ items: [district], page: 0, size: 20, totalElements: 1 }));
    const text: string = create().nativeElement.textContent;

    expect(text).toContain('Krishna');
    expect(text).toContain('accessManagement.franchiseDemand.coveredBy');
    expect(text).not.toContain('userId');
  });

  it('offers no drill-down for the lowest places', () => {
    const component = create().componentInstance;

    expect(component.canDrill(state)).toBe(true);
    expect(component.canDrill({ ...state, type: 'VILLAGE' })).toBe(false);
    expect(component.canDrill({ ...state, type: 'WARD' })).toBe(false);
  });

  it('shows a type with no translation as its code', () => {
    const component = create().componentInstance;

    expect(component.typeLabel('NEW_TYPE')).toBe('NEW_TYPE');
  });

  it('reports a failed load', () => {
    api.demandSummary.mockReturnValue(throwError(() => ({})));

    const component = create().componentInstance;

    expect(component.hasError).toBe(true);
    expect(component.isLoading).toBe(false);
    expect(notify.error).toHaveBeenCalled();
  });

  it('asks nothing without the summary privilege', () => {
    granted = new Set();

    const fixture = create();

    expect(uam.list).not.toHaveBeenCalled();
    expect(api.demandSummary).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.table-surface')).toBeNull();
  });

  it('loads nothing until an application is chosen', () => {
    uam.list.mockReturnValue(of({ content: [] }));
    const component = create().componentInstance;

    component.load();

    expect(api.demandSummary).not.toHaveBeenCalled();
  });
});
