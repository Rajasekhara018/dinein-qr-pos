import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminNewOrderPage } from './new-order-page';
import { OrderDetailPage } from './order-detail-page';
import { OrdersPage } from './orders-page';

const routes: Routes = [
  { path: '', component: OrdersPage, title: 'Orders · DineIn admin', data: { breadcrumb: 'Orders' } },
  {
    path: 'new',
    component: AdminNewOrderPage,
    title: 'New order · DineIn admin',
    data: { breadcrumb: 'New order' },
  },
  { path: ':id', component: OrderDetailPage, title: 'Order · DineIn admin', data: { breadcrumb: 'Order detail' } },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class OrdersRoutingModule {}
