import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/shared-module';
import { StaffOrderingModule } from '../staff-ordering/staff-ordering-module';
import { ActivePage } from './active/active-page';
import { WaiterOrderCard } from './board/waiter-order-card';
import { WaiterLogin } from './login/waiter-login';
import { WaiterNewOrderPage } from './new-order/new-order-page';
import { WaiterOrderPage } from './order/waiter-order-page';
import { KioskCounterPage } from './kiosk-counter/kiosk-counter-page';
import { ReadyPage } from './ready/ready-page';
import { WaiterBell } from './shell/waiter-bell';
import { WaiterHeader } from './shell/waiter-header';
import { WaiterShell } from './shell/waiter-shell';
import { TablesPage } from './tables/tables-page';
import { WaiterRoutingModule } from './waiter-routing-module';

/**
 * Waiter app (`/waiter/**`), lazily loaded and mobile first: sign-in, Ready / Active / Tables tabs, new orders (the
 * shared `StaffOrderingModule` flow) and the order page.
 */
@NgModule({
  declarations: [
    WaiterShell,
    WaiterHeader,
    WaiterBell,
    WaiterLogin,
    WaiterOrderCard,
    ReadyPage,
    ActivePage,
    TablesPage,
    KioskCounterPage,
    WaiterNewOrderPage,
    WaiterOrderPage,
  ],
  imports: [SharedModule, StaffOrderingModule, WaiterRoutingModule],
})
export class WaiterModule {}
