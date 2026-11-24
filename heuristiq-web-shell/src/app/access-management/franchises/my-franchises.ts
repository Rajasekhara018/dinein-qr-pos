import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqApplicationsService, HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseAgentRemoval } from './franchise-agent-removal.service';
import { FranchiseApi } from './franchise-api';
import { AddFranchiseAgentRequest, Franchise, FranchiseAgent, FranchiseApplication } from './franchise.models';
import { DEFAULT_REQUESTED_AGENTS, FRANCHISE_PRIVILEGES } from './franchises.constants';
import { DRIVER_ONBOARDING_PATH, FRANCHISE_DRIVERS_PRIVILEGES, FRANCHISE_DRIVERS_ROUTE } from './franchise-drivers.constants';
import { driverOnboardingQuery } from './franchise-drivers.helpers';
import { isDriverApplication, placesUsed } from './franchise-application.helpers';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { FRANCHISE_SETUP_BACK, FRANCHISE_SETUP_ROUTE } from '../franchise-setup/franchise-setup.constants';
import { addAgentError, applicationsWithRoom, emptyAgentRequest, placesLeft, toAgentRequest, usagePercent } from './franchises.helpers';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { onboardingError } from '../franchise-onboarding/franchise-onboarding.helpers';


/**
 * The owner's screen: the franchises they run, how many of each application's agent places are used, and the agents,
 * who are added here while the chosen application has a place free. Only an administrator can change the limits.
 */
@Component({
  selector: 'app-my-franchises',
  standalone: false,
  templateUrl: './my-franchises.html',
  styleUrls: ['./franchises-list.scss', './my-franchises.scss'],
})
export class MyFranchises implements OnInit {
  get hasAgentApplication(): boolean {
    return !!this.selected?.applications.some((app) => !isDriverApplication(app.applicationCode));
  }
  readonly privileges = FRANCHISE_PRIVILEGES;
  readonly driverPrivileges = FRANCHISE_DRIVERS_PRIVILEGES;
  readonly usagePercent = usagePercent;
  readonly placesLeft = placesLeft;
  readonly placesUsed = placesUsed;
  readonly isDriverApplication = isDriverApplication;

  /** The grids' columns (headings translated once the screen opens). */
  placeColumns: HqCommonTableColumn<FranchiseApplication>[] = [];
  agentColumns: HqCommonTableColumn<FranchiseAgent>[] = [];
  franchises: Franchise[] = [];
  selected: Franchise | null = null;
  agents: FranchiseAgent[] = [];
  agent: AddFranchiseAgentRequest = emptyAgentRequest();
  isLoading = false;
  hasError = false;
  saving = false;

  /** The application whose "ask for more agents" form is open, and what is typed in it. */
  requestFor: string | null = null;
  requestAgents = DEFAULT_REQUESTED_AGENTS;
  requestNote = '';
  sendingRequest = false;

  private readonly router = inject(Router);

  /** The owner's guided setup of their franchise: legal and tax details, KYC documents, bank account and the agreement's state. */
  openSetup(franchise: Franchise): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_SETUP_BACK.myFranchise.route, franchise.unitId, FRANCHISE_SETUP_ROUTE],
      { state: { name: franchise.code } });
  }

  /** The franchise's drivers ("My drivers"); the code goes along so the screen can name the franchise without reading it again. */
  openDrivers(franchise: Franchise): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, 'franchises', franchise.unitId, FRANCHISE_DRIVERS_ROUTE],
      { state: { code: franchise.code } });
  }

  /** The driver onboarding form of the admin remote, with this franchise (and so its organization) preset. */
  onboardDriver(franchise: Franchise): void {
    void this.router.navigate([...DRIVER_ONBOARDING_PATH], { queryParams: driverOnboardingQuery(franchise.unitId, franchise.code) });
  }

  constructor(
    private api: FranchiseApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
    private applications: HqApplicationsService,
    private removal: FranchiseAgentRemoval,
  ) {}

  /** The application's name, or its code while the names are not loaded. */
  appName(code: string | null | undefined): string {
    return this.applications.nameOf(code);
  }

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`accessManagement.franchises.${key}`);
    this.placeColumns = [
      { key: 'applicationCode', label: heading('application') },
      { key: 'agents', label: heading('agents') },
      { key: 'placesLeft', label: heading('placesLeft') },
    ];
    this.agentColumns = [
      { key: 'fullName', label: heading('fullName') },
      { key: 'email', label: heading('email') },
      { key: 'phone', label: heading('phone') },
      { key: 'applicationCode', label: heading('application') },
    ];
    this.applications.load().subscribe({ next: () => this.cdr.markForCheck(), error: () => undefined });
    this.load();
  }

  load(selectId: string | null = null): void {
    this.isLoading = true;
    this.hasError = false;
    this.api.mine().subscribe({
      next: (rows) => {
        this.franchises = rows ?? [];
        this.isLoading = false;
        const keep = this.franchises.find((franchise) => franchise.unitId === (selectId ?? this.selected?.unitId));
        this.select(keep ?? this.franchises[0] ?? null);
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.franchises.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  select(franchise: Franchise | null): void {
    this.selected = franchise;
    this.agent = emptyAgentRequest();
    this.agents = [];
    if (!franchise) {
      this.cdr.detectChanges();
      return;
    }
    // A person who may not list agents sees no agents section, so the call (and its error toast) is skipped.
    if (!this.access.can(this.privileges.listAgents)) {
      this.cdr.detectChanges();
      return;
    }
    this.api.agents(franchise.unitId).subscribe({
      next: (agents) => { this.agents = agents ?? []; this.cdr.detectChanges(); },
      error: () => { this.notify.error(this.translate.instant('accessManagement.franchises.errors.agentsUnavailable')); this.cdr.detectChanges(); },
    });
  }

  /** Takes an agent out of the selected franchise (after asking); the place becomes free, so the franchise is read again. */
  removeAgent(agent: FranchiseAgent): void {
    const franchise = this.selected;
    if (!franchise) {
      return;
    }
    this.removal.confirmAndRemove(franchise.unitId, agent.employeeId, agent.fullName).subscribe((removed) => {
      if (removed) {
        this.load(franchise.unitId);
      }
    });
  }

  /** Agent places of the selected franchise (the driver application's places are drivers, shown on its own row). */
  get agentTotals(): { used: number; limit: number } {
    const agentApps = (this.selected?.applications ?? []).filter((app) => !isDriverApplication(app.applicationCode));
    return {
      used: agentApps.reduce((sum, app) => sum + (app.agentCount ?? 0), 0),
      limit: agentApps.reduce((sum, app) => sum + (app.agentLimit ?? 0), 0),
    };
  }

  /** Places left on a row: drivers for the driver application, agents for any other. */
  rowPlacesLeft(application: FranchiseApplication): number {
    return Math.max(0, (application.agentLimit ?? 0) - placesUsed(application));
  }

  /** Applications of the selected franchise whose places are all used: the ones the owner can ask more for. */
  get fullApplications(): FranchiseApplication[] {
    return (this.selected?.applications ?? []).filter((application) => placesLeft(application) === 0);
  }

  startRequest(application: FranchiseApplication): void {
    this.requestFor = application.applicationCode;
    this.requestAgents = DEFAULT_REQUESTED_AGENTS;
    this.requestNote = '';
  }

  cancelRequest(): void {
    this.requestFor = null;
  }

  sendRequest(): void {
    const franchise = this.selected;
    const application = this.requestFor;
    if (!franchise || !application) {
      return;
    }
    if (!Number.isInteger(this.requestAgents) || this.requestAgents < 1) {
      this.notify.error(this.translate.instant('accessManagement.franchises.errors.requestAgents'));
      return;
    }
    this.sendingRequest = true;
    this.api.requestMoreAgents(franchise.unitId, application, this.requestAgents, this.requestNote.trim()).subscribe({
      next: () => {
        this.sendingRequest = false;
        this.requestFor = null;
        this.notify.success(this.translate.instant('accessManagement.franchises.messages.requested'));
        this.cdr.detectChanges();
      },
      error: (err: { error?: { detail?: string; message?: string } }) => {
        this.sendingRequest = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchises.messages.requestFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  /** Applications of the selected franchise that still have a place free: the ones an agent can be added for. */
  get openApplications(): FranchiseApplication[] {
    return applicationsWithRoom(this.selected).filter((app) => !isDriverApplication(app.applicationCode));
  }

  /** @param phoneInvalid the phone number was typed but is not a valid number (the phone field says so) */
  addAgent(phoneInvalid = false): void {
    if (isDriverApplication(this.agent.applicationCode)) return;
    const franchise = this.selected;
    if (!franchise) {
      return;
    }
    if (phoneInvalid) {
      this.notify.error(this.translate.instant('accessManagement.franchises.errors.phone'));
      return;
    }
    const error = addAgentError(this.agent);
    if (error) {
      this.notify.error(this.translate.instant(error));
      return;
    }
    this.saving = true;
    this.api.addAgent(franchise.unitId, toAgentRequest(this.agent)).subscribe({
      next: () => {
        this.saving = false;
        this.notify.success(this.translate.instant('accessManagement.franchises.messages.agentAdded'));
        this.load(franchise.unitId);
      },
      error: (err: { error?: { detail?: string; message?: string } }) => {
        this.saving = false;
        this.notify.error(onboardingError(err, this.translate, 'accessManagement.franchises.messages.agentFailed'));
        this.cdr.detectChanges();
      },
    });
  }
}
