import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of } from 'rxjs';
import { vi } from 'vitest';
import { ApplicationReadinessDetail, ReadinessStep } from '../application-readiness.models';
import { GoLiveGovernanceService } from '../go-live-governance.service';
import { ReadinessNavigationService } from '../readiness-navigation.service';
import { GoLiveSteps } from './go-live-steps';

const step = (over: Partial<ReadinessStep>): ReadinessStep => ({
  id: 'WALLET_APP_REGISTERED', phase: 'BUSINESS', order: 10, service: 'wallet', severity: 'BLOCK', status: 'PASS',
  messageKey: 'readiness.wallet.appRegistered.pass', fixPath: null, locked: false, ...over,
});
const DETAIL: ApplicationReadinessDetail = {
  applicationCode: 'APP',
  applicationName: 'App',
  steps: [
    step({ id: 'UAM_APPLICATION_ACTIVE', service: 'uam', phase: 'ACCESS', messageKey: 'readiness.uam.applicationActive.pass' }),
    step({ status: 'FAIL', messageKey: 'readiness.wallet.appRegistered.fail', fixPath: '/wallet/admin/apps?application=APP' }),
    step({ id: 'DMS_DOCUMENT_TYPES_CONFIGURED', service: 'dms', severity: 'WARN', status: 'UNKNOWN', messageKey: 'readiness.dms.documentTypesConfigured.unknown' }),
    step({ id: 'GO_LIVE_REVIEW', service: 'uam', phase: 'GO_LIVE', status: 'FAIL', messageKey: 'readiness.uam.goLiveReview.fail' }),
  ],
};

describe('GoLiveSteps', () => {
  let fixture: ComponentFixture<GoLiveSteps>;
  let granted: Set<string>;
  const governance = { steps: vi.fn() };
  const navigation = { canOpen: vi.fn() };
  const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => [codes].flat().some((c) => granted.has(c)) };

  function create(): GoLiveSteps {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [GoLiveSteps],
      imports: [RouterModule, HqSharedLibModule, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: GoLiveGovernanceService, useValue: governance },
        { provide: ReadinessNavigationService, useValue: navigation },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    TestBed.inject(TranslateService).setTranslation('en', { readiness: { wallet: { appRegistered: { fail: 'The app is not registered in the wallet' } } } });
    TestBed.inject(TranslateService).use('en');
    fixture = TestBed.createComponent(GoLiveSteps);
    fixture.componentRef.setInput('code', 'APP');
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement;
  const item = (id: string): HTMLElement | null => el().querySelector(`[data-step="${id}"]`);

  beforeEach(() => {
    vi.clearAllMocks();
    granted = new Set(['APPLICATION_READINESS_READ']);
    navigation.canOpen.mockReturnValue(true);
    governance.steps.mockReturnValue(of({ state: 'ready', data: DETAIL }));
  });

  it('makes no request and shows nothing without APPLICATION_READINESS_READ', () => {
    granted = new Set();
    const list = create();

    expect(list.state).toBe('off');
    expect(governance.steps).not.toHaveBeenCalled();
    expect(el().querySelector('.gls')).toBeNull();
  });

  it('lists the steps with a done, not done or could-not-be-checked mark, without the summary step', () => {
    create();

    expect(item('UAM_APPLICATION_ACTIVE')?.textContent).toContain('readiness.governance.steps.done');
    expect(item('WALLET_APP_REGISTERED')?.textContent).toContain('readiness.governance.steps.notDone');
    expect(item('WALLET_APP_REGISTERED')?.textContent).toContain('The app is not registered in the wallet');
    expect(item('DMS_DOCUMENT_TYPES_CONFIGURED')?.textContent).toContain('readiness.governance.steps.unknown');
    expect(item('GO_LIVE_REVIEW')).toBeNull();
  });

  it('labels required and optional steps and shows a Fix link only where the step has one', () => {
    create();

    expect(item('WALLET_APP_REGISTERED')?.textContent).toContain('readiness.governance.steps.required');
    expect(item('DMS_DOCUMENT_TYPES_CONFIGURED')?.textContent).toContain('readiness.governance.steps.optional');
    const fix = item('WALLET_APP_REGISTERED')?.querySelector('a.gls__fix') as HTMLAnchorElement;
    expect(fix.getAttribute('href')).toBe('/wallet/admin/apps?application=APP');
    expect(item('UAM_APPLICATION_ACTIVE')?.querySelector('a.gls__fix')).toBeNull();
  });

  it('shows no count, progress bar, status chip, next-step banner or Required/Recommended toggle', () => {
    create();

    const text = el().textContent ?? '';
    expect(text).not.toMatch(/\d+ of \d+/);
    expect(el().querySelector('[role="progressbar"], app-readiness-status-chip, input[type="checkbox"], select')).toBeNull();
    expect(text).not.toContain('nextStep');
  });

  it('says so when there is nothing to show, and offers a retry when the steps cannot be loaded', () => {
    governance.steps.mockReturnValueOnce(of({ state: 'ready', data: { ...DETAIL, steps: [] } }));
    const list = create();
    expect(list.state).toBe('empty');
    expect(el().textContent).toContain('readiness.governance.steps.empty');

    governance.steps.mockReturnValueOnce(of({ state: 'unavailable', data: null }));
    list.load();
    expect(list.state).toBe('unavailable');

    governance.steps.mockReturnValueOnce(of({ state: 'forbidden', data: null }));
    list.load();
    expect(list.state).toBe('off');
  });

  it('tracks steps by id and stops reading when destroyed', () => {
    const list = create();
    expect(list.trackStep(0, list.steps[0])).toBe('UAM_APPLICATION_ACTIVE');
    fixture.destroy();
    expect(() => list.ngOnDestroy()).not.toThrow();
  });
});
