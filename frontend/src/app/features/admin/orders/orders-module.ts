import { NgModule } from '@angular/core';
import { StaffOrderingModule } from '../../staff-ordering/staff-ordering-module';
import { AdminSharedModule } from '../shared/admin-shared-module';
import { MarkPaidDialog } from './mark-paid-dialog';
import { AdminNewOrderPage } from './new-order-page';
import { OrderDetailPage } from './order-detail-page';
import { OrdersPage } from './orders-page';
import { OrdersRoutingModule } from './orders-routing-module';

/**
 * Orders (`/admin/orders/**`): list with filters, detail with status actions, cancel & refund and counter
 * settlement, and counter orders (the shared staff ordering flow). Lazily loaded.
 */
@NgModule({
  declarations: [OrdersPage, OrderDetailPage, AdminNewOrderPage, MarkPaidDialog],
  imports: [AdminSharedModule, StaffOrderingModule, OrdersRoutingModule],
})
export class OrdersModule {}
