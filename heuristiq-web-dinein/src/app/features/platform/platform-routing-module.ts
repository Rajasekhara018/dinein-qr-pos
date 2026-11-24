import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PlatformPage } from './platform-page';

const routes: Routes = [{ path: '', component: PlatformPage, title: 'Platform admin' }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class PlatformRoutingModule {}
