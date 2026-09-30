import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../shared/admin-shared-module';
import { KioskAppearancePage } from './kiosk-appearance-page';
import { KioskPairingDialog } from './kiosk-pairing-dialog';
import { KiosksPage } from './kiosks-page';
import { KiosksRoutingModule } from './kiosks-routing-module';

/** Self-order kiosks (`/admin/kiosks`, `/admin/kiosks/appearance`) for owners and managers. Lazily loaded. */
@NgModule({
  declarations: [KiosksPage, KioskPairingDialog, KioskAppearancePage],
  imports: [AdminSharedModule, KiosksRoutingModule],
})
export class KiosksModule {}
