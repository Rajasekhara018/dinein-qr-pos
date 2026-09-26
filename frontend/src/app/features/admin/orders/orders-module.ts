import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../shared/admin-shared-module';
import { OrderDetailPage } from './order-detail-page';
import { OrdersPage } from './orders-page';
import { OrdersRoutingModule } from './orders-routing-module';

/** Orders (`/admin/orders/**`): list with filters, detail with status actions and cancel & refund. Lazily loaded. */
@NgModule({
  declarations: [OrdersPage, OrderDetailPage],
  imports: [AdminSharedModule, OrdersRoutingModule],
})
export class OrdersModule {}
