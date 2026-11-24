import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import ar from '../../../assets/i18n/ar.json';
import en from '../../../assets/i18n/en.json';
import hi from '../../../assets/i18n/hi.json';
import kn from '../../../assets/i18n/kn.json';
import ml from '../../../assets/i18n/ml.json';
import ta from '../../../assets/i18n/ta.json';
import te from '../../../assets/i18n/te.json';
import { NotificationService } from '../../shared/services/notification-service';
import { SetupChecklistScreen } from './setup-checklist';
import { SETUP_CHECKLIST_PRIVILEGES } from './setup-checklist.constants';
import { missingCount, setupItemState } from './setup-checklist.helpers';
import { SetupChecklist } from './setup-checklist.models';
import { SetupChecklistApi } from './setup-checklist.service';

describe('setup checklist helpers', () => {
  it('reads true as set up, false as missing and null as not needed', () => {
    expect(setupItemState(true)).toBe('done');
    expect(setupItemState(false)).toBe('missing');
    expect(setupItemState(null)).toBe('notNeeded');
    expect(setupItemState(undefined)).toBe('notNeeded');
    expect(missingCount([true, false, null, false])).toBe(2);
  });
});

describe('setup checklist translations', () => {
  type Tree = { [key: string]: string | Tree };
  const leaves = (tree: Tree, prefix = ''): Record<string, string> => Object.entries(tree).reduce((out, [key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof value === 'string' ? { ...out, [path]: value } : { ...out, ...leaves(value, path) };
  }, {} as Record<string, string>);
  const languages: Record<string, Tree> = { en, hi, ta, te, kn, ml, ar } as unknown as Record<string, Tree>;
  const block = (lang: string) => leaves((languages[lang]['accessManagement'] as Tree)['setupChecklist'] as Tree);
  const params = (text: string) => [...text.matchAll(/\{\{\s*(\w+)\s*\}\}/g)].map((m) => m[1]).sort().join(',');

  it('has the same keys and {{params}} in all seven languages, the menu label too, and no English copied', () => {
    const english = block('en');
    for (const lang of Object.keys(languages)) {
      const other = block(lang);
      expect(Object.keys(other).sort(), lang).toEqual(Object.keys(english).sort());
      for (const [key, text] of Object.entries(other)) {
        expect(params(text), `${lang} ${key}`).toBe(params(english[key]));
        if (lang !== 'en') {
          expect(text, `${lang} ${key}`).not.toBe(english[key]);
        }
      }
      expect(((languages[lang]['nav'] as Tree)['items'] as Tree)['setupChecklist'], lang).toBeTruthy();
    }
  });
});

describe('SetupChecklistScreen', () => {
  const api = { checklist: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  let granted: Set<string>;
  const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => [codes].flat().some((c) => granted.has(c)) };

  const franchiseRun: SetupChecklist = {
    operatingModel: 'FRANCHISE', franchisesUsed: true, placesLoaded: false, complete: false,
    applications: [
      { applicationCode: 'RIDE', commissionPlan: true, packageTemplate: false, franchisePlan: true, complete: false },
      { applicationCode: 'MATRI_APP', commissionPlan: false, packageTemplate: false, franchisePlan: false, complete: false },
    ],
  };
  const companyRun: SetupChecklist = {
    operatingModel: 'DIRECT', franchisesUsed: false, placesLoaded: false, complete: true,
    applications: [{ applicationCode: 'RIDE', commissionPlan: true, packageTemplate: null, franchisePlan: null, complete: true }],
  };

  function create(): ComponentFixture<SetupChecklistScreen> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [SetupChecklistScreen],
      imports: [TranslateModule.forRoot(), HqSharedLibModule, RouterModule],
      providers: [
        provideRouter([]),
        { provide: SetupChecklistApi, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(SetupChecklistScreen);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = new Set([SETUP_CHECKLIST_PRIVILEGES.read]);
  });

  it('loads the checklist and counts what is still missing, places included when franchises are used', () => {
    api.checklist.mockReturnValue(of(franchiseRun));
    const fixture = create();
    const screen = fixture.componentInstance;

    expect(api.checklist).toHaveBeenCalledTimes(1);
    expect(screen.missing(franchiseRun)).toBe(5);
    expect(screen.placesValue(franchiseRun)).toBe(false);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(el.querySelectorAll('.setup-state--missing').length).toBe(5);
    expect(el.textContent).toContain('MATRI_APP');
  });

  it('shows package templates and franchise plans as not needed for a company-run organization', () => {
    api.checklist.mockReturnValue(of(companyRun));
    const fixture = create();
    const screen = fixture.componentInstance;

    expect(screen.missing(companyRun)).toBe(0);
    expect(screen.placesValue(companyRun)).toBeNull();
    expect(screen.stateLabelKey(null)).toBe('accessManagement.setupChecklist.state.notNeeded');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.setup-state--notNeeded').length).toBe(3);
    expect(screen.modelLabel('DIRECT')).toBe('accessManagement.organizations.operatingModel.models.DIRECT');
    expect(screen.modelLabel('NEW_MODEL')).toBe('NEW_MODEL');
  });

  it('reports a failure and reloads on request', () => {
    api.checklist.mockReturnValue(throwError(() => ({ status: 503 })));
    const fixture = create();
    const screen = fixture.componentInstance;

    expect(screen.hasError).toBe(true);
    expect(screen.checklist).toBeNull();
    expect(notify.error).toHaveBeenCalled();

    api.checklist.mockReturnValue(of(companyRun));
    screen.load();
    expect(screen.hasError).toBe(false);
    expect(screen.checklist?.complete).toBe(true);
  });

  it('lists franchises without a place with a "set place" action, and cities without a place, for those allowed to fix them', () => {
    const withGaps: SetupChecklist = {
      ...franchiseRun,
      applicationsWithoutPlace: [
        { franchiseId: 'f-1', franchiseCode: 'RBM-00001', franchiseStatus: 'ACTIVE', applicationCode: 'BESTRIDE_RIDER_APP', locationBased: true },
        { franchiseId: 'f-2', franchiseCode: 'RBM-00002', franchiseStatus: 'ACTIVE', applicationCode: 'MATRI_APP', locationBased: false },
      ],
      citiesWithoutPlace: [{ cityCode: 'GNT', cityName: 'Guntur', locationId: null }, { cityCode: 'VJW', cityName: 'Vijayawada', locationId: 'gone' }],
    };
    granted = new Set([SETUP_CHECKLIST_PRIVILEGES.read, SETUP_CHECKLIST_PRIVILEGES.setPlace, SETUP_CHECKLIST_PRIVILEGES.cities]);
    api.checklist.mockReturnValue(of(withGaps));
    const fixture = create();
    const screen = fixture.componentInstance;
    const el = fixture.nativeElement as HTMLElement;

    expect(screen.placeless(withGaps)).toHaveLength(2);
    expect(screen.setPlaceLink(withGaps.applicationsWithoutPlace![0])).toEqual(['/access-management', 'franchises', 'f-1', 'edit']);
    expect(el.textContent).toContain('RBM-00001');
    expect(el.textContent).toContain('accessManagement.setupChecklist.placeless.noSales');
    expect(el.textContent).toContain('accessManagement.setupChecklist.placeless.optional');
    expect(el.textContent).toContain('accessManagement.setupChecklist.cities.noPlace');
    expect(el.textContent).toContain('accessManagement.setupChecklist.cities.badPlace');
    const links = Array.from(el.querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(links).toContain('/access-management/franchises/f-1/edit');
    expect(links).toContain('/admin/config-settings/cities');
  });

  it('hides the "set place" and cities actions from someone who may only read the checklist', () => {
    api.checklist.mockReturnValue(of({
      ...franchiseRun,
      applicationsWithoutPlace: [{ franchiseId: 'f-1', franchiseCode: 'RBM-00001', franchiseStatus: 'ACTIVE', applicationCode: 'BESTRIDE_RIDER_APP', locationBased: true }],
      citiesWithoutPlace: [{ cityCode: 'GNT', cityName: 'Guntur', locationId: null }],
    }));
    const el = create().nativeElement as HTMLElement;

    expect(el.textContent).toContain('RBM-00001');
    const links = Array.from(el.querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(links).not.toContain('/access-management/franchises/f-1/edit');
    expect(links).not.toContain('/admin/config-settings/cities');
  });

  it('treats an answer without the place lists (an older service) as having none', () => {
    api.checklist.mockReturnValue(of(companyRun));
    const screen = create().componentInstance;

    expect(screen.placeless(companyRun)).toEqual([]);
    expect(screen.citiesWithoutPlace(companyRun)).toEqual([]);
  });

  it('asks for nothing and shows nothing without the privilege', () => {
    granted = new Set();
    const fixture = create();

    expect(api.checklist).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).querySelector('.table-surface')).toBeNull();
  });
});
