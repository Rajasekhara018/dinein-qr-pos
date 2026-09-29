import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DisplayPage } from './display-page';

const routes: Routes = [{ path: '', component: DisplayPage, title: 'Order status' }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DisplayRoutingModule {}
