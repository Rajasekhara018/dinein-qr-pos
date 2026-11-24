import { Component } from '@angular/core';

import { QA_DEFAULT_OVERVIEW_MODE, QA_OVERVIEW_MODES, QA_OVERVIEW_MODE_KEYS } from '../qa-dashboard.constants';
import { QA_STATUS_SLOTS, QA_VOLUME_SLOT } from '../qa-charts/qa-charts.constants';
import { emptyFilters, qaDefaultPeriod, normalizeOverview } from '../qa-dashboard.helpers';
import { QaFilters, QaOverview, QaOverviewMode } from '../qa-dashboard.models';
import { QaLineSeries } from '../qa-charts/qa-charts.models';
import { QaScreenBase } from '../qa-screen-base';

/**
 * QA automation > Quality overview: the headline numbers (overall or unique), pass / fail per release, the pass / fail
 * split, the execution trend per day and the growth of the test volume.
 */
@Component({
  selector: 'app-qa-overview',
  standalone: false,
  templateUrl: './qa-overview.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-overview.scss'],
})
export class QaOverviewPage extends QaScreenBase {
  readonly modes = QA_OVERVIEW_MODES;
  readonly modeKeys = QA_OVERVIEW_MODE_KEYS;

  mode: QaOverviewMode = QA_DEFAULT_OVERVIEW_MODE;
  filters: QaFilters = emptyFilters(qaDefaultPeriod(this.timezones.effectiveTimezone()));
  overview: QaOverview = normalizeOverview(null);
  trendSeries: QaLineSeries[] = [];
  volumeSeries: QaLineSeries[] = [];
  hasData = false;

  protected onWorkspaceChange(): void {
    this.filters = emptyFilters(qaDefaultPeriod(this.timezones.effectiveTimezone()));
    this.facets = null;
    this.loadFacets();
    this.reload();
  }

  reload(): void {
    if (!this.access.can(this.privileges.overviewRead)) return;
    this.load(
      this.svc.overview(this.workspace, this.filters, this.mode),
      (overview) => this.show(overview),
      () => this.show(normalizeOverview(null)),
    );
  }

  setMode(mode: QaOverviewMode): void {
    if (mode === this.mode) return;
    this.mode = mode;
    this.reload();
  }

  onFiltersChange(filters: QaFilters): void {
    this.filters = filters;
    this.reload();
  }

  private show(overview: QaOverview): void {
    this.overview = overview;
    this.hasData = overview.totals.total > 0 || overview.releases.length > 0 || overview.trend.length > 0;
    this.trendSeries = [
      { key: 'PASSED', labelKey: 'qaDashboard.status.PASSED', slot: QA_STATUS_SLOTS.PASSED, values: overview.trend.map((d) => ({ label: d.date, value: d.passed })) },
      { key: 'FAILED', labelKey: 'qaDashboard.status.FAILED', slot: QA_STATUS_SLOTS.FAILED, values: overview.trend.map((d) => ({ label: d.date, value: d.failed })) },
    ];
    this.volumeSeries = [
      { key: 'VOLUME', labelKey: 'qaDashboard.overview.volumeSeries', slot: QA_VOLUME_SLOT, values: overview.volume.map((d) => ({ label: d.date, value: d.cumulativeScenarios })) },
    ];
  }
}
