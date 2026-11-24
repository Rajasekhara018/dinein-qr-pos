import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { Application } from '../../shared/models/uam.models';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { FranchiseApi } from './franchise-api';
import { FRANCHISE_DEMAND_LEAF_TYPES, FRANCHISE_DEMAND_PAGE_SIZE, FRANCHISE_DEMAND_PRIVILEGES } from './franchise-demand.constants';
import { trailUpTo } from './franchise-demand.helpers';
import { DemandTrailStep, PlaceDemand } from './franchise-demand.models';
import { labelKey } from './franchise-earnings.helpers';
import { LOCATION_TYPE_LABEL_KEYS } from './franchise-locations.constants';
import { APPLICATION_OPTIONS_LIMIT } from './franchises.constants';

/**
 * Where people asked for service no franchise gives yet: per place, the votes of the place and every place below it (customers,
 * people who want to work there, people who want to run the franchise), and whether a franchise already covers it. Starts at the
 * top places and drills down. Counts only; who voted is never shown.
 */
@Component({
  selector: 'app-franchise-demand',
  standalone: false,
  templateUrl: './franchise-demand.html',
  styleUrl: './franchises-list.scss',
})
export class FranchiseDemand implements OnInit {
  readonly privileges = FRANCHISE_DEMAND_PRIVILEGES;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  pageSize = FRANCHISE_DEMAND_PAGE_SIZE;
  columns: HqCommonTableColumn<PlaceDemand>[] = [];

  applications: Application[] = [];
  applicationCode = '';
  /** The places drilled into, top first; empty lists the top places. */
  trail: DemandTrailStep[] = [];
  items: PlaceDemand[] = [];
  total = 0;
  page = 0;
  isLoading = false;
  hasError = false;

  constructor(
    private api: FranchiseApi,
    private uam: UamService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
  ) {}

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.total / this.pageSize));
  }

  ngOnInit(): void {
    const heading = (key: string) => this.translate.instant(key);
    this.columns = [
      { key: 'name', label: heading('accessManagement.franchiseDemand.place') },
      { key: 'type', label: heading('accessManagement.franchiseDemand.type') },
      { key: 'total', label: heading('accessManagement.franchiseDemand.total') },
      { key: 'customers', label: heading('accessManagement.franchiseDemand.customers') },
      { key: 'providers', label: heading('accessManagement.franchiseDemand.providers') },
      { key: 'partners', label: heading('accessManagement.franchiseDemand.partners') },
      { key: 'covered', label: heading('accessManagement.franchiseDemand.coverage') },
    ];
    if (!this.access.can(this.privileges.summary)) {
      return;
    }
    this.uam.list<Application>('/applications', 0, APPLICATION_OPTIONS_LIMIT).subscribe({
      next: (page) => {
        this.applications = page.content ?? [];
        if (!this.applicationCode && this.applications.length) {
          this.applicationCode = this.applications[0].code;
          this.load();
        }
        this.cdr.detectChanges();
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  /** The type's translated label, or the raw code when there is none. */
  typeLabel(type: string): string {
    const key = labelKey(LOCATION_TYPE_LABEL_KEYS, type);
    return key ? this.translate.instant(key) : type;
  }

  load(): void {
    if (!this.applicationCode) {
      return;
    }
    this.isLoading = true;
    this.hasError = false;
    const parent = this.trail.length ? this.trail[this.trail.length - 1].locationId : undefined;
    this.api.demandSummary({ applicationCode: this.applicationCode, parentId: parent, page: this.page, size: this.pageSize }).subscribe({
      next: (result) => {
        this.items = result.items ?? [];
        this.total = result.totalElements ?? this.items.length;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.hasError = true;
        this.fail(err);
      },
    });
  }

  onApplicationChange(): void {
    this.trail = [];
    this.page = 0;
    this.load();
  }

  /** Whether a place can have places inside it (the lowest types cannot). */
  canDrill(place: PlaceDemand): boolean {
    return !FRANCHISE_DEMAND_LEAF_TYPES.includes(place.type);
  }

  /** Shows the places under one place. */
  drillInto(place: PlaceDemand): void {
    this.trail = [...this.trail, { locationId: place.locationId, name: place.name }];
    this.page = 0;
    this.load();
  }

  /** Goes back up to one step of the path (-1: the top places). */
  goUpTo(index: number): void {
    this.trail = trailUpTo(this.trail, index);
    this.page = 0;
    this.load();
  }

  goToPage(page: number): void {
    this.page = page;
    this.load();
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.page = 0;
    this.load();
  }

  private fail(err: unknown): void {
    this.isLoading = false;
    this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchiseDemand.errorMessage')));
    this.cdr.detectChanges();
  }
}
