import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TablesPage } from './tables-page';

const routes: Routes = [{ path: '', component: TablesPage, title: 'Tables & QR · DineIn admin' }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class TablesRoutingModule {}
