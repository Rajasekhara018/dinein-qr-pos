import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { HqApplicationsService, HqCommonTableColumn, HqPrivilegeAccessService, HqTimezoneService } from 'hq-shared-lib';
import { Observable, Subscription, map, throwError } from 'rxjs';
import { watchColumns } from '../../shared/table-columns.helpers';
import { FranchiseAddAgentDialog, FranchiseAddAgentDialogData } from './franchise-add-agent-dialog';
import { FranchiseAgentRemoval } from './franchise-agent-removal.service';
import { FranchiseApi } from './franchise-api';
import { driverAsMember, isDriverApplication, placesUsed } from './franchise-application.helpers';
import { Franchise, FranchiseMember, PageResult } from './franchise.models';
import {
  ADD_AGENT_DIALOG_TITLE_ID, ADD_AGENT_DIALOG_WIDTH, FRANCHISE_MEMBERS_PRIVILEGES, MEMBER_STATUS_LABEL_KEYS, MEMBER_TYPE_LABEL_KEYS,
} from './franchise-members.constants';
import { dayOf, memberErrorKey } from './franchise-members.helpers';
import { MEMBER_TREE_COLUMNS, MEMBER_TREE_PAGE_SIZE, MEMBER_TREE_PRIVILEGES } from './franchise-member-tree.constants';
import {
  TreeApplication, TreeBranch, TreeFranchise, applicationCodesOf, branchKey, groupByApplication, hasDriverApplication, withLiveDriverCounts,
} from './franchise-member-tree.helpers';
import { labelKey } from './franchise-earnings.helpers';
import { usagePercent } from './franchises.helpers';
import { FRANCHISE_STATUS_LABEL_KEYS } from './franchises.constants';

/**
 * Members and agents as a tree: each application, the franchises that run it (places used of places allowed), and the agents
 * of a franchise once its branch is opened (read a page at a time). An administrator of the organization (FRANCHISE_LIST and
 * FRANCHISE_ADMIN_MEMBER_LIST) sees every franchise and adds or removes agents with the administrator's privileges; anyone else
 * sees the franchises they own, with the owner's privileges.
 */
@Component({
  selector: 'app-franchise-member-tree',
  standalone: false,
  templateUrl: './franchise-member-tree.html',
  styleUrls: ['./franchises-list.scss', './franchise-member-tree.scss'],
})
export class FranchiseMemberTree implements OnInit, OnDestroy {
  readonly isDriverApplication = isDriverApplication;
  private readonly api = inject(FranchiseApi);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly applications = inject(HqApplicationsService);
  private readonly timezone = inject(HqTimezoneService);
  private readonly removal = inject(FranchiseAgentRemoval);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly privileges = FRANCHISE_MEMBERS_PRIVILEGES;
  readonly usagePercent = usagePercent;
  readonly placesUsed = placesUsed;

  /** True for an administrator of the organization (every franchise); false for an owner (their own). */
  orgWide = false;
  /** Whether the person may see a tree at all. */
  allowed = false;
  franchises: Franchise[] = [];
  tree: TreeApplication[] = [];
  applicationCodes: string[] = [];
  query = '';
  applicationCode = '';
  isLoading = false;
  errorMessage = '';
  /** Applications that are folded (all open by default) and franchise branches that are open, by key. */
  readonly folded = new Set<string>();
  readonly branches = new Map<string, TreeBranch>();
  /** Columns of a branch's agents table (headings follow the language). */
  memberColumns: HqCommonTableColumn<FranchiseMember>[] = [];
  private columnsSub?: Subscription;

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, MEMBER_TREE_COLUMNS, (columns) => (this.memberColumns = columns));
    this.orgWide = this.access.can(MEMBER_TREE_PRIVILEGES.listAllFranchises) && this.access.can(MEMBER_TREE_PRIVILEGES.listAdminMembers);
    this.allowed = this.orgWide || this.access.can(MEMBER_TREE_PRIVILEGES.listOwnFranchises);
    this.applications.load().subscribe({ next: () => this.regroup(), error: () => undefined });
    this.load();
  }

  ngOnDestroy(): void {
    this.columnsSub?.unsubscribe();
  }

  load(): void {
    if (!this.allowed) return;
    this.isLoading = true;
    this.errorMessage = '';
    (this.orgWide ? this.api.list() : this.api.mine()).subscribe({
      next: (rows) => {
        this.franchises = rows ?? [];
        this.applicationCodes = applicationCodesOf(this.franchises);
        this.branches.clear();
        this.isLoading = false;
        this.regroup();
        this.loadLiveDriverCounts();
      },
      error: (err: { status?: number }) => {
        this.franchises = [];
        this.tree = [];
        this.isLoading = false;
        this.errorMessage = this.translate.instant(memberErrorKey(err?.status));
        this.cdr.markForCheck();
      },
    });
  }

  /**
   * The driver application's places used, read live from Rider_FranchiseService (the index is not updated when a driver is linked),
   * so they agree with the live driver list of a branch. Agents keep the figures of the list. A failed read leaves the list's figures.
   */
  private loadLiveDriverCounts(): void {
    if (!hasDriverApplication(this.franchises)) return;
    this.api.livePlaces(this.orgWide).subscribe({
      next: (live) => {
        this.franchises = withLiveDriverCounts(this.franchises, live ?? []);
        this.regroup();
      },
      error: () => undefined,
    });
  }

  /** Rebuilds the tree from the loaded franchises with the current search and application filter. */
  regroup(): void {
    this.tree = groupByApplication(this.franchises, (code) => this.appName(code), this.query, this.applicationCode);
    this.cdr.markForCheck();
  }

  setQuery(value: string): void {
    this.query = value;
    this.regroup();
  }

  setApplication(code: string): void {
    this.applicationCode = code ?? '';
    this.regroup();
  }

  appName(code: string | null | undefined): string {
    return this.applications.nameOf(code);
  }

  isFolded(applicationCode: string): boolean {
    return this.folded.has(applicationCode);
  }

  toggleApplication(applicationCode: string): void {
    if (this.folded.has(applicationCode)) this.folded.delete(applicationCode);
    else this.folded.add(applicationCode);
  }

  branch(applicationCode: string, franchiseId: string): TreeBranch | undefined {
    return this.branches.get(branchKey(applicationCode, franchiseId));
  }

  /** Opens a franchise's branch (reading its first page of agents for that application) or closes it. */
  toggleFranchise(applicationCode: string, row: TreeFranchise): void {
    const key = branchKey(applicationCode, row.franchise.unitId);
    if (this.branches.has(key)) {
      this.branches.delete(key);
      return;
    }
    this.readPage(applicationCode, row.franchise.unitId, 0);
  }

  showMore(applicationCode: string, franchiseId: string): void {
    const current = this.branch(applicationCode, franchiseId);
    if (current && !current.loading) this.readPage(applicationCode, franchiseId, current.page + 1);
  }

  hasMore(branch: TreeBranch): boolean {
    return branch.members.length < branch.total;
  }

  /** The privilege behind removing an agent here: the administrator's, or the owner's. */
  get removePrivilege(): string {
    return this.orgWide ? this.privileges.adminRemoveAgent : this.privileges.removeAgent;
  }

  canRemove(member: FranchiseMember): boolean {
    return member.memberType === 'AGENT' && member.status !== 'REMOVED';
  }

  removeAgent(applicationCode: string, row: TreeFranchise, member: FranchiseMember): void {
    this.removal.confirmAndRemove(row.franchise.unitId, member.memberId, member.fullName, this.orgWide).subscribe((removed) => {
      if (removed) this.load();
    });
  }

  /** An administrator adds an agent to a franchise in a dialog; afterwards the tree is read again. */
  addAgent(row: TreeFranchise): void {
    if (!this.orgWide) return;
    this.dialog.open<FranchiseAddAgentDialog, FranchiseAddAgentDialogData, boolean>(FranchiseAddAgentDialog, {
      data: { franchiseId: row.franchise.unitId, franchiseCode: row.franchise.code, applications: row.franchise.applications },
      width: ADD_AGENT_DIALOG_WIDTH,
      maxWidth: '94vw',
      ariaLabelledBy: ADD_AGENT_DIALOG_TITLE_ID,
      restoreFocus: true,
    }).afterClosed().subscribe((added) => {
      if (added) this.load();
    });
  }

  ownerOf(franchise: Franchise): string {
    return franchise.ownerName || franchise.ownerEmail || '—';
  }

  franchiseStatus(code: string): string {
    const key = labelKey(FRANCHISE_STATUS_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }

  typeLabel(code: string): string {
    const key = labelKey(MEMBER_TYPE_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }

  statusLabel(code: string): string {
    const key = labelKey(MEMBER_STATUS_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }

  day(instant: string | null | undefined): string {
    return dayOf(instant, this.timezone.effectiveTimezone());
  }

  trackApplication(_: number, group: TreeApplication): string {
    return group.applicationCode;
  }

  trackFranchise(_: number, row: TreeFranchise): string {
    return row.franchise.unitId;
  }

  trackMember(_: number, member: FranchiseMember): string {
    return member.memberId;
  }

  /**
   * One page of a branch, read live from Rider_FranchiseService: the franchise's ACTIVE drivers for the driver application
   * (drivers are not agents, so the member list never has them), its agents for any other.
   */
  private branchPage(applicationCode: string, franchiseId: string, page: number): Observable<PageResult<FranchiseMember> | null> {
    if (isDriverApplication(applicationCode)) {
      const allowed = this.orgWide ? MEMBER_TREE_PRIVILEGES.listAdminDrivers : MEMBER_TREE_PRIVILEGES.listOwnDrivers;
      if (!this.access.can(allowed)) {
        return throwError(() => ({ status: 403 }));
      }
      const drivers = this.orgWide
        ? this.api.adminDrivers(franchiseId, page, MEMBER_TREE_PAGE_SIZE)
        : this.api.drivers(franchiseId, page, MEMBER_TREE_PAGE_SIZE);
      return drivers.pipe(map((result) => result && { ...result, items: (result.items ?? []).map((d) => driverAsMember(d, applicationCode)) }));
    }
    const members = this.orgWide
      ? this.api.adminMembers(franchiseId, applicationCode, page, MEMBER_TREE_PAGE_SIZE)
      : this.api.members(franchiseId, applicationCode, page, MEMBER_TREE_PAGE_SIZE);
    return members.pipe(map((result) => result?.members ?? null));
  }

  private readPage(applicationCode: string, franchiseId: string, page: number): void {
    const key = branchKey(applicationCode, franchiseId);
    const previous = this.branches.get(key);
    this.branches.set(key, { loading: true, failed: false, members: previous?.members ?? [], total: previous?.total ?? 0, page: previous?.page ?? 0 });
    this.branchPage(applicationCode, franchiseId, page).subscribe({
      next: (result) => {
        const items = result?.items ?? [];
        const members = page === 0 ? items : [...(previous?.members ?? []), ...items];
        this.branches.set(key, { loading: false, failed: false, members, total: result?.totalElements ?? members.length, page });
        this.cdr.markForCheck();
      },
      error: () => {
        this.branches.set(key, { loading: false, failed: true, members: previous?.members ?? [], total: previous?.total ?? 0, page: previous?.page ?? 0 });
        this.cdr.markForCheck();
      },
    });
  }
}
