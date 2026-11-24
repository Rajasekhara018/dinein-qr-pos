import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { NotificationService } from '../../shared/services/notification-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { FranchiseApi } from './franchise-api';
import { Location, LocationLevel, LocationQuery, LocationType } from './franchise.models';
import {
  FRANCHISE_LOCATIONS_ROUTE, LOCATIONS_PAGE_SIZE, LOCATION_LEVELS, LOCATION_LEVEL_LABEL_KEYS, LOCATION_PRIVILEGES, LOCATION_TYPES,
  LOCATION_TYPE_LABEL_KEYS,
} from './franchise-locations.constants';
import { labelKey } from './franchise-earnings.helpers';
import { CountryOption, countryOptions } from './franchises.helpers';

/** What the filter bar holds; {@code active} is the select's text value ('' = any). */
export interface LocationFilters {
  countryCode: string;
  type: LocationType | '';
  level: LocationLevel | '';
  active: '' | 'true' | 'false';
  q: string;
}

/**
 * The shared places reference (states, districts, cities, towns, ...): browse and filter it, add and change places, switch
 * them on and off, drill into the places under one, and import many from a CSV file. Places are platform-wide data.
 */
@Component({
  selector: 'app-franchise-locations',
  standalone: false,
  templateUrl: './franchise-locations.html',
  styleUrl: './franchises-list.scss',
})
export class FranchiseLocations implements OnInit {
  readonly privileges = LOCATION_PRIVILEGES;
  readonly types = LOCATION_TYPES;
  readonly levels = LOCATION_LEVELS;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  pageSize = LOCATIONS_PAGE_SIZE;
  /** The grid's columns (headings translated once the screen opens); the type and status cells draw badges from the template. */
  columns: HqCommonTableColumn<Location>[] = [];
  readonly countries: CountryOption[] = countryOptions();

  filters: LocationFilters = { countryCode: '', type: '', level: '', active: '', q: '' };
  /** The place whose children are listed (a drill-down or the parent filter); null lists every place. */
  parent: Pick<Location, 'id' | 'name'> | null = null;
  items: Location[] = [];
  total = 0;
  page = 0;
  isLoading = false;
  hasError = false;

  showImport = false;

  constructor(
    private api: FranchiseApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`accessManagement.franchiseLocations.${key}`);
    this.columns = [
      { key: 'name', label: heading('name') },
      { key: 'type', label: heading('type') },
      { key: 'code', label: heading('code') },
      { key: 'countryCode', label: heading('country') },
      { key: 'parent.name', label: heading('parent') },
      { key: 'active', label: heading('status') },
      { key: 'sortOrder', label: heading('order') },
    ];
    this.load();
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.total / this.pageSize));
  }

  goToPage(page: number): void {
    this.load(page);
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.load(0);
  }

  query(page: number): LocationQuery {
    const query: LocationQuery = { page, size: this.pageSize };
    if (this.filters.countryCode) { query.countryCode = this.filters.countryCode; }
    if (this.filters.type) { query.type = this.filters.type; }
    if (this.filters.level) { query.level = this.filters.level; }
    if (this.filters.active) { query.active = this.filters.active === 'true'; }
    if (this.filters.q.trim()) { query.q = this.filters.q; }
    if (this.parent) { query.parentId = this.parent.id; }
    return query;
  }

  load(page = 0): void {
    if (!this.access.can(this.privileges.list)) {
      return;
    }
    this.page = Math.max(page, 0);
    this.isLoading = true;
    this.hasError = false;
    this.api.locations(this.query(this.page)).subscribe({
      next: (result) => {
        this.items = result?.items ?? [];
        this.total = result?.totalElements ?? 0;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.items = [];
        this.total = 0;
        this.isLoading = false;
        this.hasError = true;
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchiseLocations.errorMessage')));
        this.cdr.detectChanges();
      },
    });
  }

  clearFilters(): void {
    this.filters = { countryCode: '', type: '', level: '', active: '', q: '' };
    this.parent = null;
    this.load();
  }

  /** The parent filter picked in the cascade (null: cleared). */
  onParentFilter(place: Location | null): void {
    this.parent = place ? { id: place.id, name: place.name } : null;
    this.load();
  }

  /** Lists the places directly under a place, including the hidden ones. */
  drillInto(place: Location): void {
    this.parent = { id: place.id, name: place.name };
    this.load();
  }

  clearParent(): void {
    this.parent = null;
    this.load();
  }

  typeLabel(type: string): string {
    const key = labelKey(LOCATION_TYPE_LABEL_KEYS, type);
    return key ? this.translate.instant(key) : type;
  }

  levelLabel(level: string): string {
    const key = labelKey(LOCATION_LEVEL_LABEL_KEYS, level);
    return key ? this.translate.instant(key) : level;
  }

  // ── Add and change: each has its own page ────────────────────────────────

  /** Opens the page that adds a place, starting in the country the list is filtered to. */
  addPlace(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_LOCATIONS_ROUTE, 'new'],
      { queryParams: this.filters.countryCode ? { country: this.filters.countryCode } : {} });
  }

  /** Opens the page that changes a place; the row goes along so the page opens even without the privilege to read one place. */
  editPlace(place: Location): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_LOCATIONS_ROUTE, place.id, 'edit'], { state: { place } });
  }

  // ── On and off ─────────────────────────────────────────────────────────────

  toggleActive(place: Location): void {
    this.api.setLocationActive(place.id, { active: !place.active, version: place.version }).subscribe({
      next: () => {
        this.notify.success(this.translate.instant(place.active
          ? 'accessManagement.franchiseLocations.messages.deactivated' : 'accessManagement.franchiseLocations.messages.activated'));
        this.load(this.page);
      },
      error: (err: unknown) => {
        // The service's own text says why (for example "used by franchise plans"); a stale row is reloaded.
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchiseLocations.messages.statusFailed')));
        this.load(this.page);
      },
    });
  }

  toggleImport(): void {
    this.showImport = !this.showImport;
  }

  onImported(): void {
    this.load();
  }
}
