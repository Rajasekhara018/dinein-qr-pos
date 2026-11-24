import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../shared/admin-shared-module';
import { StaffDialog } from './staff-dialog';
import { StaffPage } from './staff-page';
import { StaffRoutingModule } from './staff-routing-module';

/** OWNER staff & kitchen devices (`/admin/staff`). Lazily loaded. */
@NgModule({
  declarations: [StaffPage, StaffDialog],
  imports: [AdminSharedModule, StaffRoutingModule],
})
export class StaffModule {}
