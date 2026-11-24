import { ChangeDetectorRef, Directive, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqTimezoneService } from 'hq-shared-lib';
import { Observable, Subject, Subscription } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';

import {
  QA_DASHBOARD_PRIVILEGES,
  QA_DEFAULT_WORKSPACE,
  QA_DURATION_KEYS,
  QA_ENVIRONMENT_KEYS,
  QA_FAILURE_CATEGORY_KEYS,
  QA_LINK_REL,
  QA_ROUTES,
  QA_STATUS_KEYS,
  QA_SUITE_KEYS,
  QA_WORKSPACE_PARAM,
  QaTone,
} from './qa-dashboard.constants';
import { durationParts, formatPct, labelFor, parseWorkspace, reportLink, statusTone } from './qa-dashboard.helpers';
import { QaFacets, QaWorkspace } from './qa-dashboard.models';
import { QaDashboardService } from './qa-dashboard.service';

/**
 * What every QA dashboard screen shares: the workspace (UI or API) kept in the `workspace` query parameter, the
 * loading and error flags, subscription clean-up and the translated labels of status, suite, category and duration.
 */
@Directive()
export abstract class QaScreenBase implements OnInit, OnDestroy {
  protected readonly route = inject(ActivatedRoute);
  protected readonly router = inject(Router);
  protected readonly cdr = inject(ChangeDetectorRef);
  protected readonly translate = inject(TranslateService);
  protected readonly access = inject(HqPrivilegeAccessService);
  protected readonly svc = inject(QaDashboardService);
  /** The user's timezone, for the default period of screens with a date range. */
  protected readonly timezones = inject(HqTimezoneService);
  protected readonly destroy$ = new Subject<void>();

  readonly privileges = QA_DASHBOARD_PRIVILEGES;
  readonly routes = QA_ROUTES;
  readonly linkRel = QA_LINK_REL;

  workspace: QaWorkspace = QA_DEFAULT_WORKSPACE;
  /** The workspace a screen opens in when the address names none (the endpoint inventory is for API tests). */
  protected defaultWorkspace: QaWorkspace = QA_DEFAULT_WORKSPACE;
  isLoading = false;
  hasError = false;
  /** What the filter bars offer; null until loaded (or when the user may not read it). */
  facets: QaFacets | null = null;

  private started = false;
  private loadSub: Subscription | null = null;

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const named = params.get(QA_WORKSPACE_PARAM);
      const next = named ? parseWorkspace(named) : this.defaultWorkspace;
      const changed = !this.started || next !== this.workspace;
      this.workspace = next;
      this.started = true;
      if (changed) this.onWorkspaceChange();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Called once on entry and again each time the workspace changes: load the screen's data. */
  protected abstract onWorkspaceChange(): void;

  /**
   * Runs the screen's main load: a newer load cancels the one still running, the loading and error flags follow it,
   * and the screen is told to redraw.
   */
  protected load<T>(source: Observable<T>, next: (value: T) => void, onError?: () => void): void {
    this.loadSub?.unsubscribe();
    this.isLoading = true;
    this.hasError = false;
    this.loadSub = source
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.isLoading = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (value) => {
          next(value);
          this.cdr.markForCheck();
        },
        error: () => {
          this.hasError = true;
          onError?.();
          this.cdr.markForCheck();
        },
      });
  }

  /** Loads the filter options of the workspace; a screen works without them (the lists are just empty). */
  protected loadFacets(): void {
    if (!this.access.can(QA_DASHBOARD_PRIVILEGES.filterRead)) return;
    this.svc
      .facets(this.workspace)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (facets) => {
          this.facets = facets;
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });
  }

  /** Puts the workspace in the address so the choice survives a reload and can be shared. */
  setWorkspace(workspace: QaWorkspace): void {
    if (workspace === this.workspace) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { [QA_WORKSPACE_PARAM]: workspace },
      queryParamsHandling: 'merge',
    });
  }

  statusLabel(status: string): string {
    return labelFor((k) => this.translate.instant(k), QA_STATUS_KEYS, status);
  }

  suiteLabel(suite: string): string {
    return labelFor((k) => this.translate.instant(k), QA_SUITE_KEYS, suite);
  }

  environmentLabel(environment: string): string {
    return labelFor((k) => this.translate.instant(k), QA_ENVIRONMENT_KEYS, environment);
  }

  categoryLabel(category: string | null | undefined): string {
    return labelFor((k) => this.translate.instant(k), QA_FAILURE_CATEGORY_KEYS, category);
  }

  statusTone(status: string): QaTone {
    return statusTone(status);
  }

  durationText(ms: number): string {
    return durationParts(ms)
      .map((p) => this.translate.instant(QA_DURATION_KEYS[p.unit], { value: p.value }))
      .join(' ');
  }

  /** The run's report as a safe link, or null when it has none. */
  reportLink(url: string | null | undefined): string | null {
    return reportLink(url);
  }

  pct(value: number | null | undefined): string {
    return formatPct(value);
  }
}
