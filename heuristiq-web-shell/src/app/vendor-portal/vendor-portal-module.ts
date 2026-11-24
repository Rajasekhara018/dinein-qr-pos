import { HqChartsModule } from 'hq-shared-lib';
import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { VendorPortalRoutingModule } from './vendor-portal-routing-module';
import { VendorPortal } from './vendor-portal';
import { VendorRegister } from './vendor-register/vendor-register';
import { VendorDocuments } from './vendor-documents/vendor-documents';

@NgModule({
  declarations: [
    VendorPortal,
    VendorRegister,
    VendorDocuments,
  ],
  imports: [
    SharedModule,
    HqChartsModule,
    VendorPortalRoutingModule,
  ],
})
export class VendorPortalModule {}
