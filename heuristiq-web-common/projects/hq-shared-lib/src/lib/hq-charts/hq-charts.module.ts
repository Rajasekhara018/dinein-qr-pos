import { NgModule } from '@angular/core';
import { HqBarList } from './hq-bar-list';
import { HqChart } from './hq-chart';
import { HqChartCard } from './hq-chart-card';
import { HqDashboardGrid } from './hq-dashboard-grid';
import { HqDateRangePicker } from './hq-date-range';
import { HqFunnel } from './hq-funnel';
import { HqHeatmap } from './hq-heatmap';
import { HqKpiCard } from './hq-kpi-card';
import { HqProgress } from './hq-progress';

const CHART_COMPONENTS = [
  HqChart, HqChartCard, HqKpiCard, HqBarList, HqProgress, HqHeatmap, HqFunnel, HqDashboardGrid, HqDateRangePicker,
];

/**
 * Every dashboard chart piece in one import, for NgModule-based screens. The pieces are standalone, so a standalone screen can
 * import just the ones it uses instead. Kept apart from HqSharedLibModule so ApexCharts loads only in screens that draw charts.
 */
@NgModule({
  imports: CHART_COMPONENTS,
  exports: CHART_COMPONENTS,
})
export class HqChartsModule {}
