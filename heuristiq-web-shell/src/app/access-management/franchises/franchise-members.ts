import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqApplicationsService, HqCommonTableColumn, HqPrivilegeAccessService, HqTimezoneService } from 'hq-shared-lib';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { NotificationService } from '../../shared/services/notification-service';
import { FranchiseAddAgentDialog, FranchiseAddAgentDialogData } from './franchise-add-agent-dialog';
import { FranchiseAgentRemoval } from './franchise-agent-removal.service';
import { FranchiseApi } from './franchise-api';
import { FranchiseApplication, FranchiseMember, FranchiseMembers } from './franchise.models';
import {
  ADD_AGENT_DIALOG_TITLE_ID, ADD_AGENT_DIALOG_WIDTH, FRANCHISE_MEMBERS_PAGE_SIZE, FRANCHISE_MEMBERS_PRIVILEGES, MEMBER_STATUS_LABEL_KEYS,
  MEMBER_TYPE_LABEL_KEYS,
} from './franchise-members.constants';
import { dayOf, driversInWindow, driverWindow, memberErrorKey } from './franchise-members.helpers';
import { isLastPage, labelKey } from './franchise-earnings.helpers';
import { usagePercent } from './franchises.helpers';
import { driverAsMember, isDriverApplication, placesUsed } from './franchise-application.helpers';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { DriverSubscriptionDialog, DriverSubscriptionDialogData } from './driver-subscription-dialog';
import { DRIVER_SUBSCRIBE_PRIVILEGES, DRIVER_SUBSCRIPTION_DIALOG_TITLE_ID, DRIVER_SUBSCRIPTION_DIALOG_WIDTH } from './driver-subscription.constants';

/**
 * Members and agents. On its own (route `franchise-members`) the screen is a tree (FranchiseMemberTree): every application, its
 * franchises and their agents, for all the organization's franchises (administrators) or the person's own (owners). An
 * administrator opening one franchise's members from the franchise list (route `franchises/:id/members`) gets that franchise's
 * places and paged members, and may add an agent (a dialog) and remove one with the administrator's own privileges.
 */
@Component({
  selector: 'app-franchise-members',
  standalone: false,
  templateUrl: './franchise-members.html',
  styleUrl: './franchises-list.scss',
})
export class FranchiseMembersScreen implements OnInit {
  readonly privileges = FRANCHISE_MEMBERS_PRIVILEGES;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  pageSize = FRANCHISE_MEMBERS_PAGE_SIZE;
  /** Columns of the places grid and of the members grid (headings translated once the screen opens). */
  placeColumns: HqCommonTableColumn<FranchiseApplication>[] = [];
  memberColumns: HqCommonTableColumn<FranchiseMember>[] = [];
  readonly usagePercent = usagePercent;
  readonly placesUsed = placesUsed;
  /** An administrator reading any franchise's members (route `franchises/:id/members`); adds and removes go through the admin calls. */
  adminMode = false;
  /** The code of the franchise an administrator is reading, when it could be read. */
  adminFranchiseCode = '';

  franchiseId = '';
  applicationCode = '';
  result: FranchiseMembers | null = null;
  page = 0;
  isLoading = false;
  errorMessage = '';

  constructor(
    private api: FranchiseApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
    private applications: HqApplicationsService,
    private timezone: HqTimezoneService,
    private route: ActivatedRoute,
    private router: Router,
    private removal: FranchiseAgentRemoval,
    private dialog: MatDialog,
  ) {}

  /** The day of an instant in the reader's (user or organisation) time zone. */
  day(instant: string | null | undefined): string {
    return dayOf(instant, this.timezone.effectiveTimezone());
  }

  /** The application's name, or its code while the names are not loaded. */
  appName(code: string | null | undefined): string {
    return this.applications.nameOf(code);
  }

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`accessManagement.franchiseMembers.${key}`);
    this.placeColumns = [
      { key: 'applicationCode', label: heading('application') },
      { key: 'places', label: heading('places') },
      { key: 'fee', label: heading('fee') },
    ];
    this.memberColumns = [
      { key: 'fullName', label: heading('name') },
      { key: 'email', label: heading('email') },
      { key: 'phone', label: heading('phone') },
      { key: 'applicationCode', label: heading('application') },
      { key: 'memberType', label: heading('type') },
      { key: 'status', label: heading('status') },
      { key: 'onboardedAt', label: heading('onboarded') },
    ];
    this.applications.load().subscribe({ next: () => this.cdr.markForCheck(), error: () => undefined });
    const adminId = this.route.snapshot.data['admin'] === true ? this.route.snapshot.paramMap.get('id') : null;
    if (adminId) {
      this.openAsAdministrator(adminId);
    }
  }

  private openAsAdministrator(franchiseId: string): void {
    this.adminMode = true;
    this.franchiseId = franchiseId;
    if (this.access.can(this.privileges.readFranchise)) {
      this.api.byId(franchiseId).subscribe({
        next: (franchise) => { this.adminFranchiseCode = franchise.code; this.cdr.detectChanges(); },
        error: () => undefined,
      });
    }
    this.load();
  }

  /** The privilege behind the remove action: the owner's own, or the administrator's in the administrator's view. */
  get removePrivilege(): string {
    return this.adminMode ? this.privileges.adminRemoveAgent : this.privileges.removeAgent;
  }

  /** Takes an agent out of the franchise (as its owner, or as an administrator in that view); the list is read again afterwards. */
  removeAgent(member: FranchiseMember): void {
    this.removal.confirmAndRemove(this.franchiseId, member.memberId, member.fullName, this.adminMode).subscribe((removed) => {
      if (removed) {
        this.load(this.page);
      }
    });
  }

  /** The row action is for active agents only, never the owner. */
  /** Opens the agent's KYC page: their documents and whether they are verified. */
  openKyc(member: FranchiseMember): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, 'franchises', this.franchiseId, 'agents', member.memberId, 'kyc']);
  }

  canRemove(member: FranchiseMember): boolean {
    return member.memberType === 'AGENT' && member.status !== 'REMOVED';
  }

  /** The administrator adds an agent in a dialog, for an application of this franchise that has a place free; the list is read again after. */
  openAddAgent(): void {
    if (!this.adminMode || !this.result) {
      return;
    }
    this.dialog
      .open<FranchiseAddAgentDialog, FranchiseAddAgentDialogData, boolean>(FranchiseAddAgentDialog, {
        data: { franchiseId: this.franchiseId, franchiseCode: this.adminFranchiseCode, applications: this.result.applications },
        width: ADD_AGENT_DIALOG_WIDTH,
        maxWidth: '94vw',
        ariaLabelledBy: ADD_AGENT_DIALOG_TITLE_ID,
        restoreFocus: true,
      })
      .afterClosed()
      .subscribe((added) => {
        if (added) {
          this.load(this.page);
        }
      });
  }

  /** Reads one page of members for the chosen franchise and application; the places are read in the same call. */
  load(page = 0): void {
    const allowed = this.adminMode ? this.privileges.listAdminMembers : this.privileges.listMembers;
    if (!this.franchiseId || !this.access.can(allowed)) {
      this.result = null;
      return;
    }
    this.page = Math.max(page, 0);
    this.isLoading = true;
    this.errorMessage = '';
    const read = this.adminMode ? this.api.adminMembers(this.franchiseId, this.applicationCode, this.page, this.pageSize)
      : this.api.members(this.franchiseId, this.applicationCode, this.page, this.pageSize);
    const pageIndex = this.page;
    const size = this.pageSize;
    read.pipe(switchMap((members) => this.withDrivers(members, pageIndex, size))).subscribe({
      next: (result) => {
        this.result = result;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err: { status?: number }) => {
        this.result = null;
        this.isLoading = false;
        this.errorMessage = this.translate.instant(memberErrorKey(err?.status));
        this.notify.error(this.errorMessage);
        this.cdr.detectChanges();
      },
    });
  }

  /**
   * The members page with the franchise's drivers added after its agents. Drivers are not agents, so the member list never
   * has them: they are read live from the driver list (only when the franchise takes drivers, the filter is "all" or the
   * driver application, and the reader may list them). The table pages over agents then drivers as one list. When the
   * drivers cannot be read the agents are still shown.
   */
  private withDrivers(members: FranchiseMembers, page: number, size: number): Observable<FranchiseMembers> {
    const driverApp = members?.applications?.find((application) => isDriverApplication(application.applicationCode))?.applicationCode;
    const allowed = this.adminMode ? this.privileges.listAdminDrivers : this.privileges.listDrivers;
    if (!driverApp || (this.applicationCode && !isDriverApplication(this.applicationCode)) || !this.access.can(allowed)) {
      return of(members);
    }
    const memberTotal = members.members?.totalElements ?? 0;
    const window = driverWindow(page, size, memberTotal);
    const readPage = (driverPage: number) => (this.adminMode
      ? this.api.adminDrivers(this.franchiseId, driverPage, size)
      : this.api.drivers(this.franchiseId, driverPage, size));
    return forkJoin(window.pages.map(readPage)).pipe(
      map((pages) => {
        const drivers = driversInWindow(window, size, pages.map((p) => p?.items ?? []))
          .map((driver) => driverAsMember(driver, driverApp));
        const driverTotal = pages[0]?.totalElements ?? 0;
        return {
          ...members,
          members: {
            ...members.members,
            items: [...(members.members?.items ?? []), ...drivers],
            totalElements: memberTotal + driverTotal,
          },
        };
      }),
      catchError(() => of(members)),
    );
  }

  /** The franchise has an application that takes agents (the driver application takes drivers, never agents). */
  get hasAgentApplications(): boolean {
    return (this.result?.applications ?? []).some((application) => !isDriverApplication(application.applicationCode));
  }

  /** An administrator may subscribe a driver (in the administrator's view only: the owner subscribes from My drivers). */
  get canSubscribeDrivers(): boolean {
    return this.adminMode && this.access.can(DRIVER_SUBSCRIBE_PRIVILEGES.adminPlans) && this.access.can(DRIVER_SUBSCRIBE_PRIVILEGES.adminSubscribe);
  }

  /** Opens the driver subscription dialog for a driver row. */
  openSubscription(member: FranchiseMember): void {
    if (!this.canSubscribeDrivers || !this.isDriver(member)) {
      return;
    }
    this.dialog.open<DriverSubscriptionDialog, DriverSubscriptionDialogData, boolean>(DriverSubscriptionDialog, {
      data: { driverUserId: member.userId, driverName: member.fullName, franchiseId: this.franchiseId, asAdmin: true },
      width: DRIVER_SUBSCRIPTION_DIALOG_WIDTH,
      maxWidth: '94vw',
      ariaLabelledBy: DRIVER_SUBSCRIPTION_DIALOG_TITLE_ID,
      restoreFocus: true,
    });
  }

  /** The KYC page is an agent's; drivers have their own onboarding. */
  isDriver(member: FranchiseMember): boolean {
    return member.memberType === 'DRIVER';
  }

  goToPage(page: number): void {
    this.load(page);
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.load(0);
  }

  chooseApplication(code: string): void {
    this.applicationCode = code;
    this.load();
  }

  get members(): FranchiseMember[] {
    return this.result?.members?.items ?? [];
  }

  get total(): number {
    return this.result?.members?.totalElements ?? 0;
  }

  get isFirstPage(): boolean {
    return this.page === 0;
  }

  get isLast(): boolean {
    return isLastPage(this.page, this.pageSize, this.total);
  }

  typeLabel(code: string): string {
    const key = labelKey(MEMBER_TYPE_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }

  statusLabel(code: string): string {
    const key = labelKey(MEMBER_STATUS_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }
}
