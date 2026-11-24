import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { Application } from '../../shared/models/uam.models';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { FranchiseApi } from './franchise-api';
import { Franchise } from './franchise.models';
import { FRANCHISE_SETUP_DEFAULT_STEP, FRANCHISE_SETUP_ROUTE } from '../franchise-setup/franchise-setup.constants';
import { HIERARCHY_ROUTE } from './franchise-hierarchy.constants';
import { APPLICATION_OPTIONS_LIMIT, FRANCHISES_ROUTE, FRANCHISE_PRIVILEGES, FRANCHISE_STATUS_LABEL_KEYS } from './franchises.constants';
import { activeInDirectCount, ownerLabel, placeLabel, usagePercent } from './franchises.helpers';

/**
 * Administrators see the franchises of the organization here. Opening one and changing the terms of its applications each have their
 * own page; the list only shows the franchises (one owner, one code, a fee and agent places for every application).
 */
@Component({
  selector: 'app-franchises-list',
  standalone: false,
  templateUrl: './franchises-list.html',
  styleUrl: './franchises-list.scss',
})
export class FranchisesList implements OnInit {
  readonly privileges = FRANCHISE_PRIVILEGES;
  readonly usagePercent = usagePercent;
  readonly ownerLabel = ownerLabel;
  readonly placeLabel = placeLabel;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;

  /** Every franchise (the service answers with all of them); the footer pages through them here. */
  rows: Franchise[] = [];
  applications: Application[] = [];
  columns: HqCommonTableColumn<Franchise>[] = [];
  page = 0;
  pageSize = this.pageSizeOptions[0];
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
    const heading = (key: string): string => this.translate.instant(`accessManagement.franchises.${key}`);
    this.columns = [
      { key: 'code', label: heading('code') },
      { key: 'owner', label: heading('owner') },
      { key: 'place', label: heading('place') },
      { key: 'status', label: heading('status') },
      { key: 'applications', label: heading('applicationsTitle') },
      { key: 'usage', label: heading('agents') },
      { key: 'createdAt', label: heading('created') },
    ];
    this.load();
    this.uam.list<Application>('/applications', 0, APPLICATION_OPTIONS_LIMIT).subscribe({
      next: (page) => { this.applications = page.content ?? []; this.cdr.detectChanges(); },
      error: () => this.notify.error(this.translate.instant('accessManagement.franchises.errors.applicationsUnavailable')),
    });
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    this.api.list().subscribe({
      next: (rows) => {
        this.rows = rows ?? [];
        this.page = Math.min(this.page, this.pageCount - 1);
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.franchises.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  /**
   * Franchises still active although the organization now runs its sales itself (DIRECT). New sales already go to the
   * organization; the notice asks the administrator to close these (nothing is closed automatically).
   */
  get activeInDirect(): number {
    return activeInDirectCount(this.rows);
  }

  // ── Paging (the service answers with every franchise) ────────────────────

  get pageCount(): number {
    return Math.max(1, Math.ceil(this.rows.length / this.pageSize));
  }

  /** The franchises on the page being shown. */
  get pageRows(): Franchise[] {
    return this.rows.slice(this.page * this.pageSize, (this.page + 1) * this.pageSize);
  }

  goToPage(page: number): void {
    this.page = Math.min(Math.max(page, 0), this.pageCount - 1);
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.page = 0;
  }

  // ── The form pages ────────────────────────────────────────────────────────

  /** Opens the page that opens a franchise. */
  openFranchise(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISES_ROUTE, 'new']);
  }

  /**
   * Opens the franchise's guided setup (terms, legal details, KYC, bank account, agreement, activation); the row goes along so
   * its first step opens without reading the franchises again.
   */
  manage(franchise: Franchise): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISES_ROUTE, franchise.unitId, FRANCHISE_SETUP_ROUTE, FRANCHISE_SETUP_DEFAULT_STEP],
      { state: { franchise, name: franchise.code } });
  }

  /** Where the franchise sits in the hierarchy: its parent, its sub-franchises, and a picker to move it. */
  openHierarchy(franchise: Franchise): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISES_ROUTE, franchise.unitId, HIERARCHY_ROUTE]);
  }

  /** Opens the read-only members page of any franchise (an administrator's view). */
  openMembers(franchise: Franchise): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISES_ROUTE, franchise.unitId, 'members']);
  }

  /** The translated status, or the raw code when it has no label. */
  statusLabel(code: string): string {
    const key = FRANCHISE_STATUS_LABEL_KEYS[code];
    return key ? this.translate.instant(key) : code;
  }

  nameOf(code: string): string {
    return this.applications.find((application) => application.code === code)?.name ?? code;
  }
}
