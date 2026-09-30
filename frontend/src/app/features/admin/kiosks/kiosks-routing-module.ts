import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { KioskAppearancePage } from './kiosk-appearance-page';
import { KiosksPage } from './kiosks-page';

const routes: Routes = [
  {
    path: '',
    component: KiosksPage,
    title: 'Kiosks · DineIn admin',
    data: { breadcrumb: 'Kiosks' },
  },
  {
    path: 'appearance',
    component: KioskAppearancePage,
    title: 'Kiosk appearance · DineIn admin',
    data: { breadcrumb: 'Kiosk appearance' },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class KiosksRoutingModule {}
