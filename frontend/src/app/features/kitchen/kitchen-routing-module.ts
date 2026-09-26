import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { kitchenAuthGuard } from '../../core/auth/auth.guards';
import { KitchenBoard } from './board/kitchen-board';
import { kitchenLoginGuard } from './kitchen-guards';
import { KitchenLogin } from './login/kitchen-login';
import { KitchenShell } from './shell/kitchen-shell';

/** `/kitchen` (board, device token required) and `/kitchen/login` (see `KITCHEN_PATHS`). */
const routes: Routes = [
  {
    path: '',
    component: KitchenShell,
    children: [
      {
        path: 'login',
        component: KitchenLogin,
        canActivate: [kitchenLoginGuard],
        title: 'Kitchen sign-in · DineIn',
      },
      {
        path: '',
        pathMatch: 'full',
        component: KitchenBoard,
        canActivate: [kitchenAuthGuard],
        title: 'Kitchen · DineIn',
      },
      { path: '**', redirectTo: '' },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class KitchenRoutingModule {}
