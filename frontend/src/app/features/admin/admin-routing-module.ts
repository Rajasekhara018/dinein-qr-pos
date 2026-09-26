import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminComingSoon } from './coming-soon/coming-soon';

const routes: Routes = [{ path: '', component: AdminComingSoon, title: 'Admin · DineIn' }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
