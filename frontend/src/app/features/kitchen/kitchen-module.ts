import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared-module';
import { KitchenRoutingModule } from './kitchen-routing-module';
import { KitchenComingSoon } from './coming-soon/coming-soon';

/** Placeholder kitchen app (replaced in a later phase). */
@NgModule({
  declarations: [KitchenComingSoon],
  imports: [SharedModule, KitchenRoutingModule],
})
export class KitchenModule {}
