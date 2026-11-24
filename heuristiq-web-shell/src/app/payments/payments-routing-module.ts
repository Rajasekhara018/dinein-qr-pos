import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { MakePayment } from './make-payment/make-payment';
import { PaymentInvoice } from './payment-invoice/payment-invoice';

const routes: Routes = [
  { path: '', redirectTo: 'make-payment', pathMatch: 'full' },
  { path: 'make-payment', component: MakePayment },
  { path: 'invoice/:paymentId', component: PaymentInvoice },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class PaymentsRoutingModule {}
