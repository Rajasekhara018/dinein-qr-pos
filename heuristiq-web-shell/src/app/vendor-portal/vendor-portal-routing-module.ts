import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { VendorRegister } from './vendor-register/vendor-register';
import { VendorDocuments } from './vendor-documents/vendor-documents';

const routes: Routes = [
  { path: 'register', component: VendorRegister },
  { path: 'documents', component: VendorDocuments },
  { path: '', redirectTo: 'register', pathMatch: 'full' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class VendorPortalRoutingModule {}
