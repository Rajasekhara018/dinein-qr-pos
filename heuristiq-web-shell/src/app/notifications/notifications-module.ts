import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { NotificationsRoutingModule } from './notifications-routing-module';
import { InApp } from './in-app/in-app';

@NgModule({
  declarations: [InApp],
  imports: [SharedModule, NotificationsRoutingModule],
})
export class NotificationsModule {}
