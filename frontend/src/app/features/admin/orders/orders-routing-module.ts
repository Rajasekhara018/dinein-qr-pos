import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { OrderDetailPage } from './order-detail-page';
import { OrdersPage } from './orders-page';

const routes: Routes = [
  { path: '', component: OrdersPage, title: 'Orders · DineIn admin' },
  { path: ':id', component: OrderDetailPage, title: 'Order · DineIn admin' },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class OrdersRoutingModule {}
