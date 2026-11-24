import { Router } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HqApplicationsService, HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { FranchiseApi } from './franchise-api';
import { Franchise, FranchiseAgent } from './franchise.models';
import { MyFranchises } from './my-franchises';

const MATRI = 'PASUPU_KUMKUMA_MATRIMONY_APP';
const RIDE = 'BESTRIDE_RIDER_APP';

const franchise = (id: string, code: string, matriCount: number, rideCount: number): Franchise => ({
  unitId: id, code, name: code, ownerUserId: 'o1', status: 'ACTIVE', totalAgentLimit: 50, totalAgentCount: matriCount + rideCount,
  applications: [
    { applicationCode: MATRI, agentLimit: 20, agentCount: matriCount, fee: { code: 'INR', value: 100000 } },
    { applicationCode: RIDE, agentLimit: 30, agentCount: rideCount, fee: { code: 'INR', value: 200000 } },
  ],
});
const agent: FranchiseAgent = { employeeId: 'e1', userId: 'au', fullName: 'Sita', email: 'sita@example.com', phone: null, applicationCode: MATRI };

const api = { mine: vi.fn(), agents: vi.fn(), addAgent: vi.fn(), requestMoreAgents: vi.fn(), removeAgent: vi.fn() };
const dialogs = { confirm: vi.fn() };
const notify = { success: vi.fn(), error: vi.fn() };
const applicationsStub = { load: () => of([]), nameOf: (code: string) => code };
let granted: Set<string> | null = null;
const access = {
  changes$: EMPTY,
  can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
};

function create(): { fixture: ComponentFixture<MyFranchises>; component: MyFranchises } {
  const fixture = TestBed.createComponent(MyFranchises);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

describe('MyFranchises', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    api.mine.mockReturnValue(of([franchise('u1', 'RBM-00001', 1, 2)]));
    api.agents.mockReturnValue(of([agent]));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [MyFranchises],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: FranchiseApi, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: HqPrivilegeAccessService, useValue: access }, { provide: HqApplicationsService, useValue: applicationsStub },
      ],
    });
  });

  it('loads the owner\'s franchises, selects the first and loads its agents', () => {
    const { component } = create();

    expect(component.franchises).toHaveLength(1);
    expect(component.selected?.code).toBe('RBM-00001');
    expect(api.agents).toHaveBeenCalledWith('u1');
    expect(component.agents).toEqual([agent]);
  });

  it('does not load or show agents for a person without the agent-list privilege', () => {
    granted = new Set(['FRANCHISE_MINE_LIST']);

    const { component, fixture } = create();

    expect(component.selected?.code).toBe('RBM-00001');
    expect(api.agents).not.toHaveBeenCalled();
    expect(notify.error).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).not.toContain('accessManagement.franchises.noAgents');
  });

  it('selects nothing and loads no agents when the person owns no franchise', () => {
    api.mine.mockReturnValue(of([]));

    const { component, fixture } = create();

    expect(component.selected).toBeNull();
    expect(api.agents).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('accessManagement.franchises.noFranchises');
  });

  it('reports a failure to load the franchises', () => {
    api.mine.mockReturnValue(throwError(() => new Error('down')));

    const { component } = create();

    expect(component.hasError).toBe(true);
    expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errorMessage');
  });

  it('reports a failure to load the agents', () => {
    api.agents.mockReturnValue(throwError(() => new Error('down')));

    create();

    expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.agentsUnavailable');
  });

  it('switches between the owner\'s franchises and loads the chosen one\'s agents', () => {
    api.mine.mockReturnValue(of([franchise('u1', 'RBM-00001', 1, 2), franchise('u2', 'RBM-00002', 0, 0)]));
    const { component } = create();

    component.select(component.franchises[1]);

    expect(component.selected?.unitId).toBe('u2');
    expect(api.agents).toHaveBeenLastCalledWith('u2');
  });

  it('offers only the applications that still have a place free', () => {
    api.mine.mockReturnValue(of([franchise('u1', 'RBM-00001', 20, 2)]));

    const { component } = create();

    expect(component.openApplications).toEqual([]);
  });

  describe('adding an agent', () => {
    it('adds the agent for the chosen application and reloads the franchise', () => {
      api.addAgent.mockReturnValue(of(agent));
      const { component } = create();
      component.agent = { applicationCode: MATRI, fullName: ' Sita ', email: 'sita@example.com', phone: '' };

      component.addAgent();

      expect(api.addAgent).toHaveBeenCalledWith('u1', { applicationCode: MATRI, fullName: 'Sita', email: 'sita@example.com' });
      expect(notify.success).toHaveBeenCalledWith('accessManagement.franchises.messages.agentAdded');
      expect(api.mine).toHaveBeenCalledTimes(2);
      expect(component.selected?.unitId).toBe('u1');
    });

    it('sends nothing and says why when the form is incomplete', () => {
      const { component } = create();

      component.addAgent();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.agentApplication');
      component.agent = { applicationCode: MATRI, fullName: 'Sita', email: 'nope', phone: '' };
      component.addAgent();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.agent');
      expect(api.addAgent).not.toHaveBeenCalled();
    });

    it('does not add the agent when the phone number is not a valid number', () => {
      const { component } = create();
      component.agent = { applicationCode: MATRI, fullName: 'Sita', email: 'sita@example.com', phone: '+91123' };

      component.addAgent(true);

      expect(api.addAgent).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.phone');
    });

    it('does nothing without a selected franchise', () => {
      api.mine.mockReturnValue(of([]));
      const { component } = create();

      component.addAgent();

      expect(api.addAgent).not.toHaveBeenCalled();
    });

    it('shows the reason UAM gave when the limit is reached', () => {
      api.addAgent.mockReturnValue(throwError(() => ({ error: { detail: 'This franchise already has its limit of 20 agents' } })));
      const { component } = create();
      component.agent = { applicationCode: MATRI, fullName: 'Sita', email: 'sita@example.com', phone: '' };

      component.addAgent();

      expect(notify.error).toHaveBeenCalledWith('This franchise already has its limit of 20 agents');
      expect(component.saving).toBe(false);
    });

    it('falls back to a general message when UAM gives no reason', () => {
      api.addAgent.mockReturnValue(throwError(() => ({})));
      const { component } = create();
      component.agent = { applicationCode: MATRI, fullName: 'Sita', email: 'sita@example.com', phone: '' };

      component.addAgent();

      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.messages.agentFailed');
    });
  });

  describe('asking for more agents', () => {
    const fullFranchise = () => franchise('u1', 'RBM-00001', 20, 2);

    it('lists the applications whose places are all used, and opens the request form for one', () => {
      api.mine.mockReturnValue(of([fullFranchise()]));
      const { component } = create();

      expect(component.fullApplications.map((a) => a.applicationCode)).toEqual([MATRI]);
      component.startRequest(component.fullApplications[0]);
      expect(component.requestFor).toBe(MATRI);
      expect(component.requestAgents).toBe(5);
      component.cancelRequest();
      expect(component.requestFor).toBeNull();
    });

    it('sends the request for the chosen application and closes the form', () => {
      api.mine.mockReturnValue(of([fullFranchise()]));
      api.requestMoreAgents.mockReturnValue(of({}));
      const { component } = create();
      component.startRequest(component.fullApplications[0]);
      component.requestAgents = 10;
      component.requestNote = ' sold out ';

      component.sendRequest();

      expect(api.requestMoreAgents).toHaveBeenCalledWith('u1', MATRI, 10, 'sold out');
      expect(notify.success).toHaveBeenCalledWith('accessManagement.franchises.messages.requested');
      expect(component.requestFor).toBeNull();
    });

    it('does not send a request for no agents, or with no application chosen', () => {
      api.mine.mockReturnValue(of([fullFranchise()]));
      const { component } = create();

      component.sendRequest();
      component.startRequest(component.fullApplications[0]);
      component.requestAgents = 0;
      component.sendRequest();

      expect(api.requestMoreAgents).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.errors.requestAgents');
    });

    it('shows the reason when the request cannot be sent, such as no administrator email being set', () => {
      api.mine.mockReturnValue(of([fullFranchise()]));
      api.requestMoreAgents.mockReturnValueOnce(throwError(() => ({ error: { detail: 'No administrator email is set for this organization' } })));
      api.requestMoreAgents.mockReturnValueOnce(throwError(() => ({})));
      const { component } = create();
      component.startRequest(component.fullApplications[0]);

      component.sendRequest();
      expect(notify.error).toHaveBeenCalledWith('No administrator email is set for this organization');
      component.sendRequest();
      expect(notify.error).toHaveBeenCalledWith('accessManagement.franchises.messages.requestFailed');
      expect(component.sendingRequest).toBe(false);
    });

    it('shows the notice and the button only to an owner who may ask, and only for a full application', () => {
      api.mine.mockReturnValue(of([fullFranchise()]));
      granted = new Set(['FRANCHISE_MINE_LIST']);
      expect(create().fixture.nativeElement.querySelector('.franchise-notice')).toBeNull();

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        declarations: [MyFranchises], imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
        providers: [{ provide: FranchiseApi, useValue: api }, { provide: NotificationService, useValue: notify }, { provide: HqPrivilegeAccessService, useValue: access }, { provide: HqApplicationsService, useValue: applicationsStub }],
      });
      granted = new Set(['FRANCHISE_AGENT_REQUEST_CREATE']);
      const allowed = create();
      expect(allowed.fixture.nativeElement.querySelectorAll('.franchise-notice').length).toBe(1);
      expect(allowed.fixture.nativeElement.textContent).toContain('accessManagement.franchises.fullNotice');
    });
  });

  describe('what the owner sees', () => {
    it('shows the add-agent form only to someone who may add agents, and a notice when every place is used', () => {
      granted = new Set(['FRANCHISE_MINE_LIST']);
      expect(create().fixture.nativeElement.querySelector('#unused, hq-searchable-select[name="agentApplication"]')).toBeNull();

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        declarations: [MyFranchises], imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
        providers: [{ provide: FranchiseApi, useValue: api }, { provide: NotificationService, useValue: notify }, { provide: HqPrivilegeAccessService, useValue: access }, { provide: HqApplicationsService, useValue: applicationsStub }],
      });
      granted = new Set(['FRANCHISE_AGENT_CREATE']);
      const allowed = create();
      expect(allowed.fixture.nativeElement.querySelector('hq-searchable-select[name="agentApplication"]')).not.toBeNull();

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        declarations: [MyFranchises], imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
        providers: [{ provide: FranchiseApi, useValue: api }, { provide: NotificationService, useValue: notify }, { provide: HqPrivilegeAccessService, useValue: access }, { provide: HqApplicationsService, useValue: applicationsStub }],
      });
      api.mine.mockReturnValue(of([franchise('u1', 'RBM-00001', 20, 30)]));
      const full = create();
      expect(full.fixture.nativeElement.textContent).toContain('accessManagement.franchises.limitReached');
      expect(full.fixture.nativeElement.querySelector('hq-searchable-select[name="agentApplication"]')).toBeNull();
    });
  });

  it('removes an agent after asking, then reads the franchise again so the place shows as free', () => {
    dialogs.confirm.mockReturnValue(of(true));
    api.removeAgent.mockReturnValue(of(null));
    const { component, fixture } = create();
    api.mine.mockClear();

    (fixture.nativeElement.querySelector('[data-testid="remove-agent"]') as HTMLButtonElement).click();

    expect(dialogs.confirm).toHaveBeenCalled();
    expect(api.removeAgent).toHaveBeenCalledWith('u1', 'e1');
    expect(api.mine).toHaveBeenCalledTimes(1);
    expect(component.selected?.unitId).toBe('u1');
  });

  it('keeps the agent when the owner declines', () => {
    dialogs.confirm.mockReturnValue(of(false));
    const { fixture } = create();

    (fixture.nativeElement.querySelector('[data-testid="remove-agent"]') as HTMLButtonElement).click();

    expect(api.removeAgent).not.toHaveBeenCalled();
  });

  it('shows no remove action to someone without the delete privilege', () => {
    granted = new Set(['FRANCHISE_MINE_LIST', 'FRANCHISE_AGENT_LIST']);

    const { fixture } = create();

    expect(fixture.nativeElement.querySelector('[data-testid="remove-agent"]')).toBeNull();
  });

  it("opens the owner's setup of the selected franchise (legal details and onboarding as steps)", () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { fixture } = create();

    (fixture.nativeElement.querySelector('[data-testid="open-setup"]') as HTMLButtonElement).click();

    expect(navigate).toHaveBeenCalledWith(['/access-management', 'my-franchises', 'u1', 'setup'], { state: { name: 'RBM-00001' } });
    expect(fixture.nativeElement.querySelector('[data-testid="open-legal-profile"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="open-onboarding"]')).toBeNull();
  });

  it('opens the setup for an owner who may read only the onboarding', () => {
    granted = new Set(['FRANCHISE_MINE_LIST', 'FRANCHISE_MINE_ONBOARDING_READ']);
    const { fixture } = create();

    expect(fixture.nativeElement.querySelector('[data-testid="open-setup"]')).not.toBeNull();
  });

  it('hides the setup from an owner who may read neither the legal details nor the onboarding', () => {
    granted = new Set(['FRANCHISE_MINE_LIST']);
    const { fixture } = create();

    expect(fixture.nativeElement.querySelector('[data-testid="open-setup"]')).toBeNull();
  });

  it("opens the franchise's drivers, handing the code over", () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { fixture } = create();

    (fixture.nativeElement.querySelector('[data-testid="open-drivers"]') as HTMLButtonElement).click();

    expect(navigate).toHaveBeenCalledWith(['/access-management', 'franchises', 'u1', 'drivers'], { state: { code: 'RBM-00001' } });
  });

  it("opens the admin remote's driver onboarding form with the franchise preset", () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const { fixture } = create();

    (fixture.nativeElement.querySelector('[data-testid="onboard-driver"]') as HTMLButtonElement).click();

    expect(navigate).toHaveBeenCalledWith(['/admin', 'franchise-drivers', 'onboard'],
      { queryParams: { franchiseId: 'u1', franchiseCode: 'RBM-00001' } });
  });

  it('hides the driver actions from an owner without FRANCHISE_DRIVER_LIST and FRANCHISE_DRIVER_ONBOARDING_CREATE', () => {
    granted = new Set(['FRANCHISE_MINE_LIST']);
    const { fixture } = create();

    expect(fixture.nativeElement.querySelector('[data-testid="open-drivers"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="onboard-driver"]')).toBeNull();
  });
});
