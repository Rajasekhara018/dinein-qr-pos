import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../admin/shared/admin-shared-module';
import { PlatformPage } from './platform-page';
import { PlatformRoutingModule } from './platform-routing-module';

/**
 * Restaurant onboarding + platform-wide restaurant list (`/platform`). Not part of the staff-authenticated
 * `AdminModule` — see `PlatformApi`/`PlatformPrefs` for its key-based auth. Reuses `AdminSharedModule` only for its
 * form/field/input pieces (which carry no admin-auth requirement themselves), not because this page is admin-only.
 */
@NgModule({
  declarations: [PlatformPage],
  imports: [AdminSharedModule, PlatformRoutingModule],
})
export class PlatformModule {}
