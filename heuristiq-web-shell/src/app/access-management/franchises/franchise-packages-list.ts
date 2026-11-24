import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { Application } from '../../shared/models/uam.models';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { FranchiseApi } from './franchise-api';
import { NAMED_PLAN_LABEL_KEYS, NAMED_PLAN_RULES_APPLY_KEY, PACKAGES_PAGE_SIZE, PACKAGE_PRIVILEGES } from './franchise-packages.constants';
import { pickedPlanName, withActive } from './franchise-packages.helpers';
import { NamedPlanKind, PackageTemplate } from './franchise-packages.models';
import { APPLICATION_OPTIONS_LIMIT, FRANCHISE_PACKAGES_ROUTE } from './franchises.constants';

/** What the package filter bar holds ('' = any). */
export interface PackageFilters {
  applicationCode: string;
  active: '' | 'true' | 'false';
  q: string;
}

/**
 * Administrators keep the franchise package templates here: for an application, the agent places a franchise starts with and the
 * named fee, commission and referral plans it follows (none picked = the rules that apply). A franchise opened from a template is
 * linked to those plans.
 */
@Component({
  selector: 'app-franchise-packages-list',
  standalone: false,
  templateUrl: './franchise-packages-list.html',
  styleUrl: './franchises-list.scss',
})
export class FranchisePackagesList implements OnInit {
  readonly privileges = PACKAGE_PRIVILEGES;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  pageSize = PACKAGES_PAGE_SIZE;
  /** The grid's columns (headings translated once the screen opens); combined cells are drawn from the template. */
  columns: HqCommonTableColumn<PackageTemplate>[] = [];

  rows: PackageTemplate[] = [];
  applications: Application[] = [];
  filters: PackageFilters = { applicationCode: '', active: '', q: '' };
  page = 0;
  total = 0;
  isLoading = false;
  hasError = false;

  constructor(
    private api: FranchiseApi,
    private uam: UamService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private router: Router,
  ) {}

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(key);
    this.columns = [
      { key: 'name', label: heading('accessManagement.franchisePackages.name') },
      { key: 'applicationCode', label: heading('accessManagement.franchises.application') },
      { key: 'commissionPlanCode', label: heading(NAMED_PLAN_LABEL_KEYS.commission) },
      { key: 'referralPlanCode', label: heading(NAMED_PLAN_LABEL_KEYS.referral) },
      { key: 'active', label: heading('accessManagement.franchisePackages.status') },
    ];
    this.load();
    this.uam.list<Application>('/applications', 0, APPLICATION_OPTIONS_LIMIT).subscribe({
      next: (page) => { this.applications = page.content ?? []; this.cdr.detectChanges(); },
      error: () => this.notify.error(this.translate.instant('accessManagement.franchises.errors.applicationsUnavailable')),
    });
  }

  get pageCount(): number {
    return Math.max(1, Math.ceil(this.total / this.pageSize));
  }

  goToPage(page: number): void {
    this.load(page);
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.load(0);
  }

  load(page = 0): void {
    this.page = Math.max(page, 0);
    this.isLoading = true;
    this.hasError = false;
    this.api.packageTemplates({
      applicationCode: this.filters.applicationCode,
      ...(this.filters.active ? { active: this.filters.active === 'true' } : {}),
      q: this.filters.q, page: this.page, size: this.pageSize,
    }).subscribe({
      next: (result) => {
        this.rows = result?.items ?? [];
        this.total = result?.totalElements ?? this.rows.length;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.franchisePackages.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  clearFilters(): void {
    this.filters = { applicationCode: '', active: '', q: '' };
    this.load();
  }

  /** Opens the page that adds a package template. */
  addPackage(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_PACKAGES_ROUTE, 'new']);
  }

  /** Opens the page that changes a template; the row goes along for someone who may not read a single template. */
  editPackage(template: PackageTemplate): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_PACKAGES_ROUTE, template.id, 'edit'], { state: { template } });
  }

  /** Switches a template on or off (a PUT of the whole template with the new switch). */
  toggleActive(template: PackageTemplate): void {
    const active = !template.active;
    this.api.changePackageTemplate(template.id, withActive(template, active)).subscribe({
      next: () => {
        this.notify.success(this.translate.instant(active
          ? 'accessManagement.franchisePackages.messages.switchedOn' : 'accessManagement.franchisePackages.messages.switchedOff'));
        this.load(this.page);
      },
      error: (err: unknown) =>
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchisePackages.messages.saveFailed'))),
    });
  }

  nameOf(code: string): string {
    return this.applications.find((application) => application.code === code)?.name ?? code;
  }

  /** The plan a template picked for a kind, by name, or "Rules that apply" when it picked none. */
  planText(template: PackageTemplate, kind: NamedPlanKind): string {
    return pickedPlanName(template, kind) || this.translate.instant(NAMED_PLAN_RULES_APPLY_KEY);
  }
}
