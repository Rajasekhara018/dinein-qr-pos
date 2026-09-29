import { NgModule } from '@angular/core';
import { DisplayRoutingModule } from './display-routing-module';
import { DisplayPage } from './display-page';

/** Customer-facing "order ready" screen (`/display?r=<restaurantId>`); public, no login, lazy-loaded. */
@NgModule({
  declarations: [DisplayPage],
  imports: [DisplayRoutingModule],
})
export class DisplayModule {}
