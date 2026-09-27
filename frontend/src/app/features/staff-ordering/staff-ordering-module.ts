import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SharedModule } from '../../shared/shared-module';
import { ItemOptionsModule } from '../guest/item-sheet/item-options-module';
import { StaffBill } from './bill/staff-bill';
import { StaffCartPanel } from './cart/staff-cart-panel';
import { StaffOrderFlow } from './flow/staff-order-flow';
import { StaffMenu } from './menu/staff-menu';
import { PaymentMethodPicker } from './payment/payment-method-picker';
import { StaffOrderSuccess } from './success/staff-order-success';
import { TableGrid } from './table-grid/table-grid';

const EXPORTED = [StaffOrderFlow, StaffBill, TableGrid, PaymentMethodPicker];

/**
 * Staff-assisted ordering (not routed): the new-order flow and its parts. Imported by the waiter app
 * (`WaiterModule`, `/waiter/new`) and the admin orders section (`OrdersModule`, `/admin/orders/new`), so both use
 * the same components. Reuses the guest item options sheet through `ItemOptionsModule`.
 */
@NgModule({
  declarations: [...EXPORTED, StaffMenu, StaffCartPanel, StaffOrderSuccess],
  imports: [SharedModule, RouterModule, ItemOptionsModule],
  exports: [...EXPORTED],
})
export class StaffOrderingModule {}
