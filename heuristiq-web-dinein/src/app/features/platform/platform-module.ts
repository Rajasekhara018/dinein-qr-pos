import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../admin/shared/admin-shared-module';
import { PlatformPage } from './platform-page';
import { PlatformRoutingModule } from './platform-routing-module';

/**
 * Restaurant onboarding + platform-wide restaurant list. Reached two ways: nested under `AdminModule` at
 * `/admin/platform` (inside `AdminShell`'s header/sidenav, guarded by `platformAdminGuard` — the normal path
 * once signed in with a `platformAdmin` account) and standalone at the top-level `/platform` (no shell, no
 * login, for the shared-key flow — see `PlatformApi`/`PlatformPrefs`). Reuses `AdminSharedModule` for its
 * form/field/input pieces either way.
 */
@NgModule({
  declarations: [PlatformPage],
  imports: [AdminSharedModule, PlatformRoutingModule],
})
export class PlatformModule {}
