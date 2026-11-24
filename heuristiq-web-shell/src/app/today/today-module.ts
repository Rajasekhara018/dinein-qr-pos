import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HqBriefing, HqChart, HqChartCard, HqDecisionList, HqKpiCard, HqTimeline } from 'hq-shared-lib';
import { SharedModule } from '../shared/shared-module';
import { Today } from './today';

const routes: Routes = [{ path: '', component: Today }];

/** The Today screen: a daily summary of the applications the signed-in user works in. */
@NgModule({
  declarations: [Today],
  // The chart components are standalone: ApexCharts is loaded with this screen, not with the rest of the app.
  imports: [SharedModule, HqKpiCard, HqChart, HqChartCard, HqBriefing, HqDecisionList, HqTimeline, RouterModule.forChild(routes)],
})
export class TodayModule {}
