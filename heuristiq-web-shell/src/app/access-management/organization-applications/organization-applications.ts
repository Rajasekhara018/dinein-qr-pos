import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { Observable, Subscription, forkJoin } from 'rxjs';
import { GoLiveGovernanceService } from '../../application-readiness/go-live-governance.service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { Application, TenantApplicationStatus, TenantApplicationView } from '../../shared/models/uam.models';
import { watchColumns } from '../../shared/table-columns.helpers';
import { errorDetail } from '../admin-form.utils';
import {
  ACTIVE, APPLICATION_CHOICES_LIMIT, CANCELLED, ORGANIZATION_APPLICATION_COLUMNS, ORGANIZATION_APPLICATION_PRIVILEGES, SUSPENDED,
  organizationApplicationsPath,
} from './organization-applications.constants';

/**
 * The applications one organization may use: the first step of making UAM multi-tenant. Applications belong to the
 * platform; here staff link them to the organization, with an optional member limit and end date, pause them, or take
 * them away. Shown on the organization's edit page.
 */
@Component({
  selector: 'app-organization-applications',
  standalone: false,
  templateUrl: './organization-applications.html',
  styleUrl: './organization-applications.scss',
})
export class OrganizationApplications implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) tenantId!: string;

  readonly privileges = ORGANIZATION_APPLICATION_PRIVILEGES;
  readonly active = ACTIVE;
  readonly suspended = SUSPENDED;
  readonly cancelled = CANCELLED;

  rows: TenantApplicationView[] = [];
  columns: HqCommonTableColumn<TenantApplicationView>[] = [];
  private columnsSub?: Subscription;
  /** Dims a cancelled subscription's row. */
  readonly rowClass = (row: TenantApplicationView): string | null =>
    row.subscription.status === CANCELLED ? 'oa__row--cancelled' : null;
  applications: Application[] = [];
  isLoading = false;
  busy = false;

  selectedCode = '';
  maxUsers: number | null = null;
  validUntil = '';

  constructor(
    private uam: UamService,
    private notify: NotificationService,
    private dialogs: AppDialogService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private goLive: GoLiveGovernanceService,
  ) {}

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, ORGANIZATION_APPLICATION_COLUMNS, (columns) => (this.columns = columns));
  }

  ngOnChanges(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.columnsSub?.unsubscribe();
  }

  /** Applications the organization does not use yet and that are switched on. */
  get choices(): Application[] {
    const used = new Set(this.rows.filter((r) => r.subscription.status !== CANCELLED).map((r) => r.subscription.applicationCode));
    return this.applications.filter((a) => a.active !== false && !used.has(a.code.toUpperCase()));
  }

  load(): void {
    this.isLoading = true;
    forkJoin({
      rows: this.uam.get<TenantApplicationView[]>(organizationApplicationsPath(this.tenantId)),
      apps: this.uam.list<Application>('/applications', 0, APPLICATION_CHOICES_LIMIT),
    }).subscribe({
      next: ({ rows, apps }) => {
        this.rows = [...rows].sort((a, b) => a.subscription.applicationCode.localeCompare(b.subscription.applicationCode));
        this.applications = apps.content ?? [];
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err: unknown) => this.fail(err, 'accessManagement.organizations.applications.messages.loadFailed'),
    });
  }

  subscribe(): void {
    if (!this.selectedCode || this.busy) return;
    this.run(
      this.uam.create(organizationApplicationsPath(this.tenantId), {
        applicationCode: this.selectedCode,
        maxUsers: this.maxUsers,
        validUntil: this.validUntil || null,
      }),
      'accessManagement.organizations.applications.messages.subscribed',
      () => { this.selectedCode = ''; this.maxUsers = null; this.validUntil = ''; },
    );
  }

  setStatus(row: TenantApplicationView, status: TenantApplicationStatus): void {
    this.run(
      this.uam.update(organizationApplicationsPath(this.tenantId), row.subscription.applicationCode, { status }),
      'accessManagement.organizations.applications.messages.updated',
    );
  }

  cancel(row: TenantApplicationView): void {
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.organizations.applications.cancelDialog.title'),
      message: this.translate.instant('accessManagement.organizations.applications.cancelDialog.message', {
        name: row.applicationName || row.subscription.applicationCode,
      }),
      variant: 'danger',
      confirmText: this.translate.instant('accessManagement.organizations.applications.cancel'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.run(
        this.uam.remove(organizationApplicationsPath(this.tenantId), row.subscription.applicationCode),
        'accessManagement.organizations.applications.messages.cancelled',
      );
    });
  }

  trackRow(_: number, row: TenantApplicationView): string {
    return row.subscription.applicationCode;
  }

  private run(call: Observable<unknown>, successKey: string, afterSuccess?: () => void): void {
    this.busy = true;
    call.subscribe({
      next: () => {
        this.busy = false;
        afterSuccess?.();
        this.notify.success(this.translate.instant(successKey));
        this.load();
      },
      error: (err: unknown) => this.refusedOrFail(err),
    });
  }

  /**
   * A refused go-live (HTTP 409) opens the blocked dialog, where a SUPER_ADMIN may override; the list is read again when the
   * application was switched on there. Any other failure is the usual error toast.
   */
  private refusedOrFail(err: unknown): void {
    this.goLive.handleRefusal(err).subscribe((result) => {
      if (result === null) {
        this.fail(err, 'accessManagement.organizations.applications.messages.actionFailed');
        return;
      }
      this.busy = false;
      if (result === 'overridden') this.load();
      this.cdr.detectChanges();
    });
  }

  private fail(err: unknown, fallbackKey: string): void {
    this.isLoading = false;
    this.busy = false;
    this.notify.error(errorDetail(err) ?? this.translate.instant(fallbackKey));
    this.cdr.detectChanges();
  }
}
