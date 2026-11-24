import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { EMPTY, Subject, Subscription, of, timer } from 'rxjs';
import { catchError, finalize, switchMap, takeUntil } from 'rxjs/operators';
import { HqCommonTableColumn, HqStatCardItem } from 'hq-shared-lib';
import { watchColumns } from '../shared/table-columns.helpers';
import { ServiceRegistryService } from './service-registry-service';
import { RegistryApp, RegistryInstance } from './service-registry.models';
import {
  REGISTRY_INSTANCE_COLUMNS,
  REGISTRY_REFRESH_MS,
  REGISTRY_STATUSES,
  REGISTRY_STATUS_KEY_PREFIX,
  REGISTRY_STATUS_TONE,
  StatusTone,
} from './service-registry.constants';

/** A service with only the instances that match the current filters. */
interface AppRow {
  name: string;
  instances: RegistryInstance[];
  up: number;
  total: number;
}

/**
 * Service Registry overview: every registered service, its instances, their status and when they
 * last renewed their lease. Data comes from the AdminAPI, which reads the Eureka registry.
 */
@Component({
  selector: 'app-service-registry',
  standalone: false,
  templateUrl: './service-registry.html',
  styleUrl: './service-registry.scss',
})
export class ServiceRegistry implements OnInit, OnDestroy {
  readonly statuses = REGISTRY_STATUSES;
  columns: HqCommonTableColumn<RegistryInstance>[] = [];
  private columnsSub?: Subscription;

  apps: RegistryApp[] = [];
  rows: AppRow[] = [];
  stats: HqStatCardItem[] = [];
  isLoading = false;
  hasError = false;
  lastUpdated: Date | null = null;

  query = '';
  statusFilter = '';
  autoRefresh = true;

  private readonly destroy$ = new Subject<void>();
  /** Fires to cancel the current polling loop before a new one starts. */
  private readonly restart$ = new Subject<void>();

  constructor(
    private registry: ServiceRegistryService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, REGISTRY_INSTANCE_COLUMNS, (columns) => (this.columns = columns));
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.columnsSub?.unsubscribe();
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Reads the registry now and, while auto-refresh is on, every REGISTRY_REFRESH_MS after that. */
  private startPolling(): void {
    this.restart$.next();
    const ticks$ = this.autoRefresh ? timer(0, REGISTRY_REFRESH_MS) : of(0);
    ticks$
      .pipe(
        takeUntil(this.restart$),
        takeUntil(this.destroy$),
        switchMap(() => {
          this.isLoading = true;
          this.cdr.detectChanges();
          return this.registry.applications().pipe(
            finalize(() => { this.isLoading = false; this.cdr.detectChanges(); }),
            catchError(() => {
              this.hasError = true;
              this.apps = [];
              this.applyFilters();
              return EMPTY;
            }),
          );
        }),
      )
      .subscribe((apps) => {
        this.hasError = false;
        this.apps = apps;
        this.lastUpdated = new Date();
        this.applyFilters();
        this.cdr.detectChanges();
      });
  }

  refresh(): void {
    this.startPolling();
  }

  onAutoRefreshChange(): void {
    this.startPolling();
  }

  applyFilters(): void {
    const q = this.query.trim().toLowerCase();
    this.rows = this.apps
      .map((app) => {
        const instances = app.instances.filter((i) =>
          (!this.statusFilter || i.status === this.statusFilter) &&
          (!q || app.name.toLowerCase().includes(q) || (i.host ?? '').toLowerCase().includes(q) ||
            (i.ip ?? '').toLowerCase().includes(q) || i.instanceId.toLowerCase().includes(q)));
        return { name: app.name, instances, up: instances.filter((i) => i.status === 'UP').length, total: instances.length };
      })
      .filter((row) => row.instances.length > 0);
    this.stats = this.buildStats();
  }

  clearFilters(): void {
    this.query = '';
    this.statusFilter = '';
    this.applyFilters();
  }

  private buildStats(): HqStatCardItem[] {
    const all = this.apps.flatMap((a) => a.instances);
    const up = all.filter((i) => i.status === 'UP').length;
    return [
      { label: this.translate.instant('serviceRegistry.stats.services'), value: this.apps.length, icon: 'bi-hdd-network', tone: 'blue' },
      { label: this.translate.instant('serviceRegistry.stats.instances'), value: all.length, icon: 'bi-cpu', tone: 'purple' },
      { label: this.translate.instant('serviceRegistry.stats.up'), value: up, icon: 'bi-check-circle', tone: 'green' },
      { label: this.translate.instant('serviceRegistry.stats.notUp'), value: all.length - up, icon: 'bi-exclamation-triangle', tone: 'orange' },
    ];
  }

  statusLabel(status: string): string {
    const key = REGISTRY_STATUS_KEY_PREFIX + status;
    const label = this.translate.instant(key);
    return label === key ? status : label;
  }

  statusTone(status: string): StatusTone {
    return REGISTRY_STATUS_TONE[status] ?? 'neutral';
  }

  trackByApp(_: number, row: AppRow): string {
    return row.name;
  }

  trackByInstance(_: number, instance: RegistryInstance): string {
    return instance.instanceId;
  }
}
