import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ACCESS_SUBJECT_USER, APPLICATION_ACCESS_PRIVILEGES } from '../../application-access/application-access.constants';
import { accessFailureKey } from '../../application-access/application-access.helpers';
import { ApplicationAccessGrant } from '../../application-access/application-access.models';
import { ApplicationAccessService } from '../../application-access/application-access.service';
import { TenantApplicationView } from '../../shared/models/uam.models';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { UamService } from '../../shared/services/uam-service';
import { organizationApplicationsPath } from '../organization-applications/organization-applications.constants';

/** The applications one person has been given directly, with the means to add or withdraw one (the organization's active applications). */
@Component({
  selector: 'app-user-application-access',
  standalone: false,
  templateUrl: './user-application-access.html',
})
export class UserApplicationAccess implements OnInit {
  readonly privileges = APPLICATION_ACCESS_PRIVILEGES;
  @Input({ required: true }) userId = '';

  grants: ApplicationAccessGrant[] = [];
  /** The grid's columns (heading translated when the component opens). */
  columns: HqCommonTableColumn<ApplicationAccessGrant>[] = [];
  /** The organization's active applications, by code and name. */
  applications: { code: string; name: string }[] = [];
  chosen = '';
  isLoading = false;
  unavailable = false;
  saving = false;

  constructor(
    private access: ApplicationAccessService,
    private uam: UamService,
    private organizations: OrganizationContextService,
    private dialogs: AppDialogService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.columns = [{ key: 'applicationCode', label: this.translate.instant('accessManagement.userDetail.applications.application') }];
    this.load();
    const tenantId = this.organizations.currentTenantId();
    if (!tenantId) {
      return;
    }
    this.uam.get<TenantApplicationView[]>(organizationApplicationsPath(tenantId)).pipe(catchError(() => of([] as TenantApplicationView[])))
      .subscribe((rows) => {
        this.applications = (rows ?? []).filter((row) => row.subscription.status === 'ACTIVE')
          .map((row) => ({ code: row.subscription.applicationCode, name: row.applicationName || row.subscription.applicationCode }));
        this.cdr.detectChanges();
      });
  }

  load(): void {
    this.isLoading = true;
    this.access.listForUser(this.userId).subscribe((result) => {
      this.grants = result.grants.filter((grant) => grant.active !== false);
      this.unavailable = result.state !== 'ready';
      this.isLoading = false;
      this.cdr.detectChanges();
    });
  }

  /** The applications the person does not have yet. */
  get choices(): { code: string; name: string }[] {
    const held = new Set(this.grants.map((grant) => grant.applicationCode));
    return this.applications.filter((application) => !held.has(application.code));
  }

  nameOf(code: string): string {
    return this.applications.find((application) => application.code === code)?.name ?? code;
  }

  grant(): void {
    if (!this.chosen) {
      return;
    }
    this.saving = true;
    this.access.grant(this.chosen, ACCESS_SUBJECT_USER, [this.userId], '').subscribe((outcomes) => {
      this.saving = false;
      const failed = outcomes.find((outcome) => !outcome.ok);
      if (failed) {
        this.notify.error(this.translate.instant(accessFailureKey(failed.status)));
      } else {
        this.chosen = '';
        this.notify.success(this.translate.instant('accessManagement.userDetail.applications.granted'));
        this.load();
      }
      this.cdr.detectChanges();
    });
  }

  confirmRevoke(grant: ApplicationAccessGrant): void {
    this.dialogs.confirm({
      title: this.translate.instant('applicationAccess.revoke.title'),
      message: this.translate.instant('applicationAccess.revoke.message', { name: this.nameOf(grant.applicationCode) }),
      variant: 'danger',
      confirmText: this.translate.instant('applicationAccess.revoke.confirm'),
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.access.revoke(grant.id).subscribe({
        next: () => { this.notify.success(this.translate.instant('applicationAccess.revoke.done')); this.load(); },
        error: (err: unknown) => this.notify.error(this.translate.instant(accessFailureKey(err))),
      });
    });
  }
}
