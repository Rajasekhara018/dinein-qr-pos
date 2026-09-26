import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared-module';
import { AdminRoutingModule } from './admin-routing-module';
import { AdminComingSoon } from './coming-soon/coming-soon';

/** Placeholder admin app (replaced in a later phase). */
@NgModule({
  declarations: [AdminComingSoon],
  imports: [SharedModule, AdminRoutingModule],
})
export class AdminModule {}
