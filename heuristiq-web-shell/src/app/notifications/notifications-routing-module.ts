import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { InApp } from './in-app/in-app';

const routes: Routes = [
  { path: '', component: InApp },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class NotificationsRoutingModule {}
