import { NgModule } from '@angular/core';
import { SharedModule } from '../../../shared/shared-module';
import { ItemSheet } from './item-sheet';

/**
 * The item options sheet (variant, add-ons, notes, quantity) on its own, so the guest app and the staff ordering
 * flow (waiter screen, admin counter orders) share one component. It only returns an `ItemSelection`; what happens
 * with it (guest cart, staff cart) is up to the caller.
 */
@NgModule({
  declarations: [ItemSheet],
  imports: [SharedModule],
  exports: [ItemSheet],
})
export class ItemOptionsModule {}
