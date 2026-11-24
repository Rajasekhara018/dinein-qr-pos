import { HqChartsModule } from 'hq-shared-lib';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { SharedModule } from '../shared/shared-module';
import { SystemCoverage } from './system-coverage';

const routes: Routes = [{ path: '', component: SystemCoverage }];

@NgModule({
  declarations: [SystemCoverage],
  imports: [SharedModule, HqChartsModule, RouterModule.forChild(routes)],
})
export class SystemCoverageModule {}
