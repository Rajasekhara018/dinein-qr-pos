import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CartPage } from './cart/cart-page';
import { guestSessionGuard } from './data/guest-session.guard';
import { MenuPage } from './menu/menu-page';
import { MyOrdersPage } from './orders/my-orders-page';
import { OrderStatusPage } from './orders/order-status-page';
import { ScanPage } from './scan/scan-page';
import { GuestShell } from './shell/guest-shell';

const routes: Routes = [
  { path: 'scan', component: ScanPage, title: 'Scan the QR code · DineIn' },
  {
    path: '',
    component: GuestShell,
    canActivate: [guestSessionGuard],
    children: [
      { path: '', component: MenuPage, title: 'Menu · DineIn' },
      { path: 'cart', component: CartPage, title: 'Your cart · DineIn' },
      { path: 'orders', component: MyOrdersPage, title: 'My orders · DineIn' },
      { path: 'orders/:id', component: OrderStatusPage, title: 'Order status · DineIn' },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class GuestRoutingModule {}
