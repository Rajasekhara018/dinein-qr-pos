import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { KitchenComingSoon } from './coming-soon/coming-soon';

const routes: Routes = [{ path: '', component: KitchenComingSoon, title: 'Kitchen · DineIn' }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class KitchenRoutingModule {}
