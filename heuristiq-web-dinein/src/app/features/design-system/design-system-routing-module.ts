import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DesignSystemPage } from './design-system-page';
import { devOnlyGuard } from './dev-only.guard';

const routes: Routes = [
  {
    path: '',
    component: DesignSystemPage,
    canActivate: [devOnlyGuard],
    title: 'Design system',
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DesignSystemRoutingModule {}
