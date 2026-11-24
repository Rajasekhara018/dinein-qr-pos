import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared-module';
import { BillSummary } from './cart/bill-summary';
import { CartLines } from './cart/cart-lines';
import { CartPage } from './cart/cart-page';
import { GuestRoutingModule } from './guest-routing-module';
import { ItemOptionsModule } from './item-sheet/item-options-module';
import { CartBar } from './menu/cart-bar';
import { CartSidebar } from './menu/cart-sidebar';
import { MenuItemCard } from './menu/menu-item-card';
import { MenuPage } from './menu/menu-page';
import { MyOrdersPage } from './orders/my-orders-page';
import { OrderBill } from './orders/order-bill';
import { OrderProgress } from './orders/order-progress';
import { OrderStatusPage } from './orders/order-status-page';
import { ScanPage } from './scan/scan-page';
import { GuestHeader } from './shell/guest-header';
import { GuestShell } from './shell/guest-shell';

/** Guest ordering app (`/menu/**`), lazily loaded. */
@NgModule({
  declarations: [
    GuestShell,
    GuestHeader,
    ScanPage,
    MenuPage,
    MenuItemCard,
    CartBar,
    CartSidebar,
    CartPage,
    CartLines,
    BillSummary,
    OrderStatusPage,
    OrderProgress,
    OrderBill,
    MyOrdersPage,
  ],
  imports: [SharedModule, ItemOptionsModule, GuestRoutingModule],
})
export class GuestModule {}
