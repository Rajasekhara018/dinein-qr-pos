import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared-module';
import { PaymentsRoutingModule } from './payments-routing-module';
import { MakePayment } from './make-payment/make-payment';
import { PaymentInvoice } from './payment-invoice/payment-invoice';

@NgModule({
  declarations: [MakePayment, PaymentInvoice],
  imports: [SharedModule, PaymentsRoutingModule],
})
export class PaymentsModule {}
