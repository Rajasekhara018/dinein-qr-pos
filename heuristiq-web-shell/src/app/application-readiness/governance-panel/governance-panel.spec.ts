import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of } from 'rxjs';
import { vi } from 'vitest';
import { GovernanceState } from '../go-live-governance.models';
import { GoLiveGovernanceService } from '../go-live-governance.service';
import { GovernancePanel } from './governance-panel';

const STATE: GovernanceState = {
  applicationCode: 'APP',
  guardEnabled: true,
  override: { by: 'u2', at: '2026-10-25T10:00:00', reason: 'Launch is tomorrow', blockingSteps: ['WALLET_APP_REGISTERED'] },
  alerts: { lastBlockedAt: '2026-10-24T08:00:00', lastRefusedAt: null },
};

describe('GovernancePanel', () => {
  let fixture: ComponentFixture<GovernancePanel>;
  let granted: Set<string>;
  const governance = { state: vi.fn() };
  const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => [codes].flat().some((c) => granted.has(c)) };

  function create(): GovernancePanel {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [GovernancePanel],
      imports: [HqSharedLibModule, TranslateModule.forRoot()],
      providers: [
        { provide: GoLiveGovernanceService, useValue: governance },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    fixture = TestBed.createComponent(GovernancePanel);
    fixture.componentRef.setInput('code', 'APP');
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement;

  beforeEach(() => {
    vi.clearAllMocks();
    granted = new Set(['APPLICATION_READINESS_GOVERNANCE_READ']);
    governance.state.mockReturnValue(of({ state: 'ready', data: STATE }));
  });

  it('makes no request and renders nothing without the governance read privilege', () => {
    granted = new Set();
    const panel = create();
    expect(panel.state).toBe('off');
    expect(governance.state).not.toHaveBeenCalled();
    expect(el().querySelector('.gp')).toBeNull();
  });

  it('shows the last override with who, when and why, and the alert times', () => {
    const panel = create();
    const facts = el().textContent ?? '';
    expect(panel.overriddenBy).toBe('u2');
    expect(facts).toContain('Launch is tomorrow');
    expect(facts).toContain('WALLET_APP_REGISTERED');
    expect(facts).toContain('readiness.governance.panel.never');
  });

  it('has no owner team section, picker, candidates or organization builder link', () => {
    create();
    const text = el().textContent ?? '';
    expect(text).not.toContain('ownerTeam');
    expect(text).not.toContain('openOrgBuilder');
    expect(el().querySelector('input[type="checkbox"]')).toBeNull();
    expect(el().querySelector('.btn-accent')).toBeNull();
  });

  it('says so when there never was an override', () => {
    governance.state.mockReturnValue(of({ state: 'ready', data: { ...STATE, override: null, alerts: null } }));
    create();
    expect(el().textContent).toContain('readiness.governance.panel.noOverride');
  });

  it('offers a retry when the state cannot be loaded, and hides itself on a refusal', () => {
    governance.state.mockReturnValueOnce(of({ state: 'unavailable', data: null }));
    const panel = create();
    expect(panel.state).toBe('unavailable');
    expect(el().querySelector('.gp [role="alert"] button')).not.toBeNull();
    panel.load();
    expect(panel.state).toBe('ready');

    governance.state.mockReturnValueOnce(of({ state: 'forbidden', data: null }));
    panel.load();
    expect(panel.state).toBe('off');
  });

  it('reads again when the refresh key changes', () => {
    const panel = create();
    governance.state.mockClear();
    fixture.componentRef.setInput('refreshKey', 'later');
    fixture.detectChanges();
    expect(governance.state).toHaveBeenCalledTimes(1);
    expect(panel.state).toBe('ready');
  });

  it('notes that the guard is switched off in the environment', () => {
    governance.state.mockReturnValue(of({ state: 'ready', data: { ...STATE, guardEnabled: false } }));
    create();
    expect(el().querySelector('.gp__note')).not.toBeNull();
  });

  it('stops reading when it is destroyed', () => {
    const panel = create();
    fixture.destroy();
    expect(() => panel.ngOnDestroy()).not.toThrow();
  });
});
