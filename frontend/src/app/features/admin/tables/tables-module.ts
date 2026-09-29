import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../shared/admin-shared-module';
import { MoveTableDialog } from './move-table-dialog';
import { QrPreviewDialog } from './qr-preview-dialog';
import { ReserveTableDialog } from './reserve-table-dialog';
import { TableDialog } from './table-dialog';
import { TablesPage } from './tables-page';
import { TablesRoutingModule } from './tables-routing-module';

/** Tables & QR codes (`/admin/tables`): add/rename/(de)activate/regenerate, reserve, move/merge orders. Lazily loaded. */
@NgModule({
  declarations: [TablesPage, TableDialog, QrPreviewDialog, ReserveTableDialog, MoveTableDialog],
  imports: [AdminSharedModule, TablesRoutingModule],
})
export class TablesModule {}
