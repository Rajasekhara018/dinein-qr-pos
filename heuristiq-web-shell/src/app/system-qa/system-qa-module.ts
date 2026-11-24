import { HqChartsModule } from 'hq-shared-lib';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { QaEvidenceThumbnail } from '../qa-dashboard/qa-evidence/qa-evidence-thumbnail';
import { SharedModule } from '../shared/shared-module';
import { SystemQa } from './system-qa';

const routes: Routes = [{ path: '', component: SystemQa }];

@NgModule({
  declarations: [SystemQa],
  imports: [SharedModule, HqChartsModule, QaEvidenceThumbnail, RouterModule.forChild(routes)],
})
export class SystemQaModule {}
