import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../shared/admin-shared-module';
import { BarChart } from './bar-chart';
import { ReportsPage } from './reports-page';
import { ReportsRoutingModule } from './reports-routing-module';

/** OWNER sales reports (`/admin/reports`). Lazily loaded. */
@NgModule({
  declarations: [ReportsPage, BarChart],
  imports: [AdminSharedModule, ReportsRoutingModule],
})
export class ReportsModule {}
