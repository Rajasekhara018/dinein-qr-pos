import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../shared/admin-shared-module';
import { SettingsPage } from './settings-page';
import { SettingsRoutingModule } from './settings-routing-module';

/** OWNER restaurant settings (`/admin/settings`). Lazily loaded. */
@NgModule({
  declarations: [SettingsPage],
  imports: [AdminSharedModule, SettingsRoutingModule],
})
export class SettingsModule {}
