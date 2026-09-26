import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../shared/admin-shared-module';
import { QrPreviewDialog } from './qr-preview-dialog';
import { TableDialog } from './table-dialog';
import { TablesPage } from './tables-page';
import { TablesRoutingModule } from './tables-routing-module';

/** Tables & QR codes (`/admin/tables`). Lazily loaded. */
@NgModule({
  declarations: [TablesPage, TableDialog, QrPreviewDialog],
  imports: [AdminSharedModule, TablesRoutingModule],
})
export class TablesModule {}
