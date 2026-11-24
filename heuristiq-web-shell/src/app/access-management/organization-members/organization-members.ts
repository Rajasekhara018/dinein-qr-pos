import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subscription, forkJoin, map, throwError } from 'rxjs';
import { HqCommonTableColumn, HqLookupCondition, HqLookupService, HqPickerPage } from 'hq-shared-lib';
import { NotificationService } from '../../shared/services/notification-service';
import { SearchApiService, UAM_SEARCH_MODELS } from '../../shared/services/search-api-service';
import { UamService } from '../../shared/services/uam-service';
import { TenantMemberView, TenantMembershipStatus, UamUser } from '../../shared/models/uam.models';
import { watchColumns } from '../../shared/table-columns.helpers';
import { errorDetail } from '../admin-form.utils';
import {
  MEMBERS_PAGE_SIZE, MEMBERS_PAGE_SIZE_OPTIONS, MEMBER_PICKER_FIELDS, MEMBER_USER_TYPE_KEYS, MEMBER_PICKER_OPERATORS, ORGANIZATION_MEMBER_COLUMNS, ORGANIZATION_MEMBER_PRIVILEGES,
  organizationMembersPath,
} from './organization-members.constants';
import { MemberSearchGroups, MemberSearchTooComplexError, memberAdvancedSearch, memberQuickSearch } from './organization-members.helpers';

/**
 * Who belongs to one organization. One account can belong to several organizations; here staff add an existing account to
 * this one, pause or remove a member, or bring them back. Shown on the organization's edit page.
 */
@Component({
  selector: 'app-organization-members',
  standalone: false,
  templateUrl: './organization-members.html',
  styleUrl: './organization-members.scss',
})
export class OrganizationMembers implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) tenantId!: string;

  readonly privileges = ORGANIZATION_MEMBER_PRIVILEGES;
  pageSize = MEMBERS_PAGE_SIZE;
  readonly pageSizeOptions = MEMBERS_PAGE_SIZE_OPTIONS;

  rows: TenantMemberView[] = [];
  columns: HqCommonTableColumn<TenantMemberView>[] = [];
  private columnsSub?: Subscription;
  /** Dims a removed member's row. */
  readonly rowClass = (row: TenantMemberView): string | null =>
    row.membership.status === 'REMOVED' ? 'om__row--removed' : null;
  page = 0;
  totalPages = 0;
  totalElements = 0;
  isLoading = false;
  busy = false;

  constructor(
    private uam: UamService,
    private searchApi: SearchApiService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private lookup: HqLookupService,
  ) {}

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, ORGANIZATION_MEMBER_COLUMNS, (columns) => (this.columns = columns));
  }

  ngOnChanges(): void {
    this.page = 0;
    this.load();
  }

  ngOnDestroy(): void {
    this.columnsSub?.unsubscribe();
  }

  load(): void {
    this.isLoading = true;
    this.uam.list<TenantMemberView>(organizationMembersPath(this.tenantId), this.page, this.pageSize).subscribe({
      next: (result) => {
        this.rows = result.content ?? [];
        this.totalElements = result.totalElements ?? this.rows.length;
        this.totalPages = result.totalPages ?? 1;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err: unknown) => this.fail(err, 'accessManagement.organizations.members.messages.loadFailed'),
    });
  }

  goTo(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.page = page;
    this.load();
  }

  /** Shows another page size from the first page. */
  changePageSize(size: number): void {
    if (size === this.pageSize) return;
    this.pageSize = size;
    this.page = 0;
    this.load();
  }

  /** The translation key of the member's user type, or null to show the code itself (unknown code) or nothing (none). */
  userTypeKey(row: TenantMemberView): string | null {
    return row.userType ? MEMBER_USER_TYPE_KEYS[row.userType] ?? null : null;
  }

  isMember(user: UamUser): boolean {
    return this.rows.some((row) => row.membership.userId === user.id && row.membership.status === 'ACTIVE');
  }

  /** Opens the paged search dialog; everyone ticked who is not already an active member is added to the organization. */
  addPeople(): void {
    this.lookup.open<UamUser>({
      title: this.translate.instant('accessManagement.organizations.members.pickTitle'),
      subtitle: this.translate.instant('accessManagement.organizations.members.pickSubtitle'),
      multiple: true,
      idOf: (user) => user.id,
      columns: [
        { key: 'userName', label: this.translate.instant('accessManagement.organizations.members.colUserName') },
        { key: 'displayName', label: this.translate.instant('accessManagement.organizations.members.colName'), render: (user) => user.displayName || user.userName },
        { key: 'email', label: this.translate.instant('accessManagement.organizations.members.colEmail') },
      ],
      chipLabel: (user) => user.displayName || user.userName,
      // The search runs on the server: in the chosen column (or all three), or with the advanced conditions.
      scopedSearch: true,
      advanced: true,
      advancedColumns: MEMBER_PICKER_FIELDS,
      operators: MEMBER_PICKER_OPERATORS,
      fetch: (query, page, size, field, conditions) => this.findUsers(query, page, size, field, conditions),
    }).subscribe((chosen) => {
      const toAdd = (chosen ?? []).filter((user) => !this.isMember(user));
      if (toAdd.length === 0) return;
      this.run(
        forkJoin(toAdd.map((user) => this.uam.create(organizationMembersPath(this.tenantId), { userId: user.id }))),
        'accessManagement.organizations.members.messages.added',
      );
    });
  }

  setStatus(row: TenantMemberView, status: TenantMembershipStatus): void {
    this.run(
      this.uam.update(organizationMembersPath(this.tenantId), row.membership.userId, { status }),
      'accessManagement.organizations.members.messages.updated',
    );
  }

  nameOf(row: TenantMemberView): string {
    return row.displayName || row.userName || row.membership.userId;
  }

  trackRow(_: number, row: TenantMemberView): string {
    return row.membership.id;
  }

  /**
   * One page of accounts for the "Add people" picker: the quick search in the chosen column (all three columns for
   * "All fields"), or the advanced conditions. Everything is filtered by the search index, so the total is exact.
   */
  private findUsers(query: string, page: number, size: number, field?: string,
                    conditions?: readonly HqLookupCondition[]): Observable<HqPickerPage<UamUser>> {
    let groups: MemberSearchGroups;
    try {
      groups = conditions?.length ? memberAdvancedSearch(conditions) : memberQuickSearch(query, field);
    } catch (err) {
      if (!(err instanceof MemberSearchTooComplexError)) throw err;
      const message = this.translate.instant('accessManagement.organizations.members.pickTooComplex');
      return throwError(() => ({ error: { detail: message, message } }));
    }
    const users = UAM_SEARCH_MODELS.users;
    return this.searchApi.getAll(users.model, groups.all, groups.any, page, size)
      .pipe(map((result) => ({ items: (result.content ?? []).map(users.toRow), total: result.totalElements })));
  }

  private run(call: Observable<unknown>, successKey: string): void {
    this.busy = true;
    call.subscribe({
      next: () => {
        this.busy = false;
        this.notify.success(this.translate.instant(successKey));
        this.load();
      },
      error: (err: unknown) => this.fail(err, 'accessManagement.organizations.members.messages.actionFailed'),
    });
  }

  private fail(err: unknown, fallbackKey: string): void {
    this.isLoading = false;
    this.busy = false;
    this.notify.error(errorDetail(err) ?? this.translate.instant(fallbackKey));
    this.cdr.detectChanges();
  }
}
